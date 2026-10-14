package com.drift.ui;

import com.drift.config.DriftSettings;
import com.drift.core.DriftEvent;
import com.drift.ui.WindowPlacement.Rect;
import com.drift.ui.state.InterfaceState;
import com.drift.ui.state.StateController;
import com.drift.ui.state.UiModel;
import com.drift.speech.VoiceService;
import com.drift.windows.WindowStyler;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import javafx.animation.FadeTransition;
import javafx.animation.Timeline;
import javafx.animation.KeyFrame;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.SVGPath;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;

/**
 * The always-available floating interface: a real transparent, undecorated desktop window.
 * Rendering is a pure function of {@link UiModel}, which is itself derived only from application events.
 */
public class FloatingWindow {
    public static final String TITLE = "Drift Floating Interface";
    private static final String MIC_PATH = "M12 14a3 3 0 0 0 3-3V5a3 3 0 0 0-6 0v6a3 3 0 0 0 3 3zm5-3a5 5 0 0 1-10 0H5a7 7 0 0 0 6 6.92V21h2v-3.08A7 7 0 0 0 19 11z";

    private final UiServices svc;
    private final ThemeApplier themeApplier;
    private final Runnable openSettings;
    private final Stage stage = new Stage(StageStyle.TRANSPARENT);
    private final VBox panel = new VBox(8);
    private final VBox body = new VBox(8);
    private final Label title = new Label();
    private final Label detail = new Label();
    private final Waveform waveform = new Waveform(300, 36);
    private final ImageView logo = new ImageView(Fx.toFx(DriftIcon.render(40)));
    private final Circle micDot = new Circle(7, Color.web("#F87171"));
    private final Button micButton = new Button();
    private final Button expandButton = new Button("⌄");
    private final StateController controller;
    private final ScheduledExecutorService timer = Executors.newSingleThreadScheduledExecutor(r -> Thread.ofPlatform().daemon().name("drift-ui-timer").unstarted(r));
    private final ExecutorService background = Executors.newVirtualThreadPerTaskExecutor();
    private final TextField input = new TextField();
    private final Timeline pulse = new Timeline();
    private boolean userExpanded;
    private boolean dragging;
    private double dragDX, dragDY;
    private UiModel model = UiModel.idle();
    private Runnable unsubscribe;

    public FloatingWindow(UiServices svc, ThemeApplier themeApplier, Runnable openSettings) {
        this.svc = svc;
        this.themeApplier = themeApplier;
        this.openSettings = openSettings;
        this.controller = new StateController(timer, m -> Fx.ui(() -> render(m)));
        build();
    }

    public Stage stage() { return stage; }
    public UiModel model() { return controller.model(); }
    public StateController controller() { return controller; }

    private void build() {
        stage.setTitle(TITLE);
        logo.setFitWidth(24);
        logo.setFitHeight(24);
        title.getStyleClass().add("drift-title");
        detail.getStyleClass().add("drift-detail");
        detail.setMaxWidth(Double.MAX_VALUE);
        VBox texts = new VBox(1, title, detail);
        HBox.setHgrow(texts, Priority.ALWAYS);
        texts.setMinWidth(0);

        StackPane indicator = new StackPane(logo, micDot);
        indicator.setMinSize(26, 26);
        micDot.setVisible(false);

        SVGPath mic = new SVGPath();
        mic.setContent(MIC_PATH);
        mic.setScaleX(0.8);
        mic.setScaleY(0.8);
        mic.setFill(Color.web("#8C97A8"));
        micButton.setGraphic(mic);
        micButton.getStyleClass().add("drift-icon-button");
        micButton.setTooltip(new javafx.scene.control.Tooltip("Hold to talk (or click to toggle if push-to-talk is off)"));
        micButton.setFocusTraversable(false);
        micButton.addEventHandler(javafx.scene.input.MouseEvent.MOUSE_PRESSED, e -> micPressed());
        micButton.addEventHandler(javafx.scene.input.MouseEvent.MOUSE_RELEASED, e -> micReleased());

        expandButton.getStyleClass().add("drift-icon-button");
        expandButton.setFocusTraversable(false);
        expandButton.setOnAction(e -> { userExpanded = !userExpanded; if (userExpanded && model.state() == InterfaceState.IDLE) svc.bus.publish(new DriftEvent.AssistantOpened()); else if (!userExpanded) controller.dismiss(); });
        Button settingsButton = iconButton("⚙", "Open settings", openSettings);
        Button hide = iconButton("–", "Hide (use the tray icon or shortcut to bring me back)", this::hide);
        HBox header = new HBox(8, indicator, texts, micButton, expandButton, settingsButton, hide);
        header.setAlignment(Pos.CENTER_LEFT);

        panel.getStyleClass().add("drift-panel");
        panel.getChildren().addAll(header, body);
        body.setManaged(false);
        body.setVisible(false);

        StackPane root = new StackPane(panel);
        root.getStyleClass().add("drift-window");
        root.setPadding(new Insets(10));
        root.setStyle("-fx-background-color: transparent;");
        Scene scene = new Scene(root);
        scene.setFill(Color.TRANSPARENT);
        themeApplier.register(scene);
        stage.setScene(scene);
        stage.setAlwaysOnTop(true);

        // dragging anywhere on the panel that is not an interactive control
        panel.setOnMousePressed(e -> { if (e.getTarget() instanceof Node n && isDragSurface(n)) { dragging = true; dragDX = e.getScreenX() - stage.getX(); dragDY = e.getScreenY() - stage.getY(); panel.setCursor(Cursor.MOVE); } });
        panel.setOnMouseDragged(e -> { if (dragging) { stage.setX(e.getScreenX() - dragDX); stage.setY(e.getScreenY() - dragDY); } });
        panel.setOnMouseReleased(e -> { if (dragging) { dragging = false; panel.setCursor(Cursor.DEFAULT); settleAfterDrag(); } });
        scene.setOnKeyPressed(e -> { if (e.getCode() == KeyCode.ESCAPE) { userExpanded = false; controller.dismiss(); } });

        input.setPromptText("Type a command, e.g. open my downloads folder");
        input.setOnAction(e -> {
            String text = input.getText().strip();
            if (!text.isEmpty()) { input.clear(); svc.coordinator.submit(text); }
        });
        input.focusedProperty().addListener((o, was, now) -> { if (now) allowFocus(true); });

        pulse.getKeyFrames().setAll(new KeyFrame(Duration.ZERO, new javafx.animation.KeyValue(micDot.opacityProperty(), 1.0)),
                new KeyFrame(Duration.millis(700), new javafx.animation.KeyValue(micDot.opacityProperty(), 0.35)));
        pulse.setAutoReverse(true);
        pulse.setCycleCount(Timeline.INDEFINITE);

        unsubscribe = svc.bus.subscribe(e -> {
            controller.onEvent(e);
            if (e instanceof DriftEvent.AudioLevel l) Fx.ui(() -> waveform.push(l.level()));
            if (e instanceof DriftEvent.SettingsChanged) Fx.ui(this::applySettings);
        });
        render(controller.model());
    }

    private static boolean isDragSurface(Node n) {
        for (Node x = n; x != null; x = x.getParent()) {
            if (x instanceof Button || x instanceof TextField || x instanceof TextArea || x instanceof CheckBox) return false;
        }
        return true;
    }

    private Button iconButton(String glyph, String tip, Runnable action) {
        Button b = new Button(glyph);
        b.getStyleClass().add("drift-icon-button");
        b.setTooltip(new javafx.scene.control.Tooltip(tip));
        b.setFocusTraversable(false);
        b.setOnAction(e -> action.run());
        return b;
    }

    // ---------- show / hide / settings ----------

    public void show() {
        Fx.ui(() -> {
            DriftSettings.Floating f = svc.settings.get().floating();
            if (!f.enabled()) { openSettings.run(); return; }
            applySettings();
            if (!stage.isShowing()) {
                stage.setOpacity(0);
                stage.show();
                applyNativeStyle();
                fadeIn();
            }
            stage.toFront();
            positionStage(true);
        });
    }

    public void hide() { Fx.ui(stage::hide); }

    public void toggle() { Fx.ui(() -> { if (stage.isShowing()) hide(); else show(); }); }

    public void close() {
        if (unsubscribe != null) unsubscribe.run();
        timer.shutdownNow();
        background.shutdownNow();
        stage.close();
    }

    private void fadeIn() {
        double target = svc.settings.get().floating().opacity();
        if (reducedMotion()) { stage.setOpacity(target); return; }
        FadeTransition ft = new FadeTransition(Duration.millis(160), panel);
        panel.setOpacity(0);
        ft.setToValue(1);
        ft.play();
        stage.setOpacity(target);
    }

    private boolean reducedMotion() {
        return svc.settings.get().appearance().reducedMotion() || themeApplier.theme().animationIntensity() <= 0.0;
    }

    private void applySettings() {
        DriftSettings.Floating f = svc.settings.get().floating();
        stage.setAlwaysOnTop(f.alwaysOnTop());
        stage.setOpacity(f.opacity());
        if (!f.enabled() && stage.isShowing()) { stage.hide(); return; }
        if (stage.isShowing()) { positionStage(false); applyNativeStyle(); }
    }

    private void applyNativeStyle() {
        if (!WindowStyler.supported()) return;
        boolean assistant = model.state() == InterfaceState.ASSISTANT && model.dictationText().isEmpty();
        WindowStyler.setNoActivate(TITLE, !assistant);   // never steal focus, except while the user types into the assistant
        boolean clickThrough = svc.settings.get().floating().clickThroughWhenIdle() && model.state() == InterfaceState.IDLE && !userExpanded;
        WindowStyler.setClickThrough(TITLE, clickThrough);
    }

    private void allowFocus(boolean allow) {
        if (WindowStyler.supported()) WindowStyler.setNoActivate(TITLE, !allow);
    }

    // ---------- geometry ----------

    private static List<Rect> screens() {
        List<Rect> out = new ArrayList<>();
        for (Screen s : Screen.getScreens()) { var b = s.getVisualBounds(); out.add(new Rect(b.getMinX(), b.getMinY(), b.getWidth(), b.getHeight())); }
        return out;
    }

    private static Rect primary() {
        var b = Screen.getPrimary().getVisualBounds();
        return new Rect(b.getMinX(), b.getMinY(), b.getWidth(), b.getHeight());
    }

    double contentHeight() {
        double compact = svc.settings.get().floating().height();
        double extra = switch (model.state()) {
            case IDLE -> userExpanded ? 150 : 0;
            case LISTENING, TRANSCRIBING -> 110;
            case PROCESSING -> model.options().isEmpty() ? 100 : 60 + 34 * Math.min(8, model.options().size());
            case EXECUTING -> 96;
            case MUSIC -> 120;
            case NOTIFICATION -> 40;
            case ASSISTANT -> model.dictationText().isEmpty() ? 150 : 270;
            case ERROR -> 110;
        };
        return compact + extra + 20;   // + window padding
    }

    private void positionStage(boolean initial) {
        DriftSettings.Floating f = svc.settings.get().floating();
        double w = f.width() + 20, h = contentHeight();
        if (body.isVisible()) {
            // size to the real content (wrapped text included) rather than a fixed estimate
            panel.applyCss();
            double pref = panel.prefHeight(f.width());
            if (pref > 0) h = Math.min(760, Math.max(f.height() + 20, pref + 22));
        }
        Rect desired;
        if (!stage.isShowing() || (initial && !f.customPosition())) {
            desired = f.customPosition() ? new Rect(f.x(), f.y(), w, h) : WindowPlacement.anchored(primary(), f.anchor(), w, h);
        } else {
            Rect cur = new Rect(stage.getX(), stage.getY(), stage.getWidth(), stage.getHeight());
            desired = WindowPlacement.resized(screens(), primary(), cur, w, h, "RIGHT".equals(f.anchor()));
        }
        Rect r = WindowPlacement.clamp(screens(), primary(), desired);
        stage.setX(r.x());
        stage.setY(r.y());
        stage.setWidth(r.w());
        stage.setHeight(r.h());
    }

    private void settleAfterDrag() {
        Rect cur = new Rect(stage.getX(), stage.getY(), stage.getWidth(), stage.getHeight());
        Rect snapped = WindowPlacement.snap(screens(), primary(), cur);
        stage.setX(snapped.x());
        stage.setY(snapped.y());
        background.submit(() -> svc.settings.update(s -> new DriftSettings(s.appearance(),
                new DriftSettings.Floating(s.floating().enabled(), s.floating().alwaysOnTop(), s.floating().opacity(), s.floating().width(), s.floating().height(),
                        s.floating().anchor(), snapped.x(), snapped.y(), true, s.floating().clickThroughWhenIdle(), s.floating().startExpanded()),
                s.voice(), s.startup(), s.developer(), s.privacy(), s.monitoring(), s.shortcuts(), s.weather())));
    }

    // ---------- microphone ----------

    private void micPressed() {
        boolean ptt = svc.settings.get().voice().pushToTalk();
        background.submit(() -> {
            if (ptt) svc.voice.start(VoiceService.Mode.COMMAND);
            else if (svc.voice.isListening()) svc.voice.stop();
            else svc.voice.start(VoiceService.Mode.COMMAND);
        });
    }

    private void micReleased() {
        if (svc.settings.get().voice().pushToTalk()) background.submit(svc.voice::stop);
    }

    // ---------- rendering ----------

    private void render(UiModel m) {
        this.model = m;
        title.setText(m.title());
        detail.setText(m.detail() == null ? "" : m.detail());
        detail.setTextOverrun(javafx.scene.control.OverrunStyle.ELLIPSIS);
        micDot.setVisible(m.micActive());
        logo.setVisible(!m.micActive());
        if (m.micActive() && !reducedMotion()) pulse.play(); else { pulse.stop(); micDot.setOpacity(1); }
        waveform.setActive(m.micActive());
        panel.getStyleClass().removeAll("error", "listening");
        if (m.state() == InterfaceState.ERROR || (m.state() == InterfaceState.NOTIFICATION && !m.ok())) panel.getStyleClass().add("error");
        if (m.micActive()) panel.getStyleClass().add("listening");
        expandButton.setText(userExpanded || m.state() == InterfaceState.ASSISTANT ? "⌃" : "⌄");
        if (micButton.getGraphic() instanceof SVGPath p) p.setFill(m.micActive() ? Color.web("#F87171") : Color.web("#8C97A8"));

        body.getChildren().clear();
        boolean hasBody = fillBody(m);
        body.setManaged(hasBody);
        body.setVisible(hasBody);
        if (stage.isShowing()) {
            positionStage(false);
            applyNativeStyle();
            // wrapped labels only know their real height after one layout pass: measure again once
            javafx.application.Platform.runLater(() -> { if (stage.isShowing() && model == m) positionStage(false); });
        }
    }

    private boolean fillBody(UiModel m) {
        switch (m.state()) {
            case LISTENING, TRANSCRIBING -> {
                body.getChildren().add(waveform);
                Label live = new Label(m.state() == InterfaceState.TRANSCRIBING ? m.detail() : "Release the mic button to finish");
                live.setWrapText(true);
                live.getStyleClass().add("drift-label");
                body.getChildren().add(live);
                detail.setText(m.micActive() ? "Microphone is on" : "Microphone is off");
                if (!m.dictationState().equals("IDLE") && m.dictationState().equals("DICTATING")) body.getChildren().add(dictationStopRow());
                return true;
            }
            case PROCESSING -> {
                Label plan = new Label(m.pendingPrompt() != null ? m.pendingPrompt() : m.detail());
                plan.setWrapText(true);
                body.getChildren().add(plan);
                if (m.pendingId() != null && m.options().isEmpty()) {
                    body.getChildren().add(new HBox(8, Fx.button("Confirm", () -> svc.coordinator.confirm(m.pendingId(), true), "primary"),
                            Fx.button("Cancel", () -> svc.coordinator.confirm(m.pendingId(), false))));
                } else if (m.pendingId() != null) {
                    CheckBox remember = new CheckBox("Remember this choice");
                    VBox opts = new VBox(6);
                    for (int i = 0; i < Math.min(8, m.options().size()); i++) {
                        int idx = i;
                        Button b = Fx.button(m.options().get(i), () -> svc.coordinator.choose(m.pendingId(), idx, remember.isSelected()));
                        b.setMaxWidth(Double.MAX_VALUE);
                        opts.getChildren().add(b);
                    }
                    body.getChildren().addAll(opts, remember, Fx.button("Cancel", () -> svc.coordinator.confirm(m.pendingId(), false)));
                }
                return true;
            }
            case EXECUTING -> {
                ProgressBar bar = new ProgressBar(ProgressBar.INDETERMINATE_PROGRESS);
                bar.setMaxWidth(Double.MAX_VALUE);
                Label current = new Label(m.detail());
                current.setWrapText(true);
                body.getChildren().addAll(bar, current, Fx.button("Cancel", svc.orchestrator::cancelRunning));
                return true;
            }
            case MUSIC -> {
                var s = svc.media.current();
                Label t = new Label(s.map(x -> x.title() == null ? "Unknown title" : x.title()).orElse(m.track() == null ? "" : m.track()));
                t.getStyleClass().add("drift-title");
                Label a = new Label(s.map(x -> (x.artist() == null ? "" : x.artist()) + (x.album() == null ? "" : " · " + x.album())).orElse(""));
                a.getStyleClass().add("drift-detail");
                HBox controls = new HBox(6,
                        mediaButton("⏮", "previous", s.map(x -> x.canPrevious()).orElse(false)),
                        mediaButton(m.playing() ? "⏸" : "▶", m.playing() ? "pause" : "play", s.map(x -> x.canPlayPause()).orElse(false)),
                        mediaButton("⏭", "next", s.map(x -> x.canNext()).orElse(false)));
                body.getChildren().addAll(t, a, controls);
                return true;
            }
            case NOTIFICATION -> {
                Label l = new Label(m.detail());
                l.setWrapText(true);
                l.getStyleClass().add(m.ok() ? "drift-ok-text" : "drift-error-text");
                body.getChildren().add(l);
                detail.setText("");
                return true;
            }
            case ASSISTANT -> {
                body.getChildren().add(input);
                if (!m.dictationText().isEmpty() && "PREVIEW".equals(m.dictationState())) {
                    Label into = new Label("Will be typed into: " + (m.dictationTarget().isBlank() ? "(unknown window)" : m.dictationTarget()));
                    into.getStyleClass().add("drift-detail");
                    TextArea preview = new TextArea(m.dictationText());
                    preview.setEditable(false);
                    preview.setPrefRowCount(3);
                    preview.setWrapText(true);
                    body.getChildren().addAll(into, preview, new HBox(8,
                            Fx.button("Insert", this::commitDictation, "primary"),
                            Fx.button("Discard", () -> svc.dictation.discard()),
                            Fx.button("Undo last", () -> background.submit(() -> { try { svc.dictation.undoLastInsert(); } catch (Exception ex) { svc.bus.publish(new DriftEvent.Failure("Couldn't undo", ex.getMessage(), "Use Ctrl+Z in the application")); } }))));
                } else {
                    Label hint = new Label("Hold the mic button to speak, or type above. Say \"start dictation\" to dictate text.");
                    hint.setWrapText(true);
                    hint.getStyleClass().add("drift-detail");
                    body.getChildren().add(hint);
                }
                return true;
            }
            case ERROR -> {
                Label d = new Label(m.detail());
                d.setWrapText(true);
                d.getStyleClass().add("drift-error-text");
                body.getChildren().add(d);
                if (m.recovery() != null) {
                    Label r = new Label("What to do: " + m.recovery());
                    r.setWrapText(true);
                    body.getChildren().add(r);
                }
                body.getChildren().add(new HBox(8, Fx.button("Dismiss", controller::dismiss, "primary"), Fx.button("Open settings", openSettings)));
                return true;
            }
            default -> {
                if (userExpanded) { body.getChildren().add(input); return true; }
                return false;
            }
        }
    }

    private Node dictationStopRow() {
        return new HBox(8, Fx.button("Stop dictation", () -> background.submit(() -> { svc.voice.stop(); svc.dictation.stop(); }), "danger"));
    }

    private Button mediaButton(String glyph, String action, boolean enabled) {
        Button b = Fx.button(glyph, () -> svc.coordinator.submit(switch (action) { case "previous" -> "previous song"; case "next" -> "next song"; case "pause" -> "pause the music"; default -> "play the music"; }));
        b.setDisable(!enabled);
        if (!enabled) b.setTooltip(new javafx.scene.control.Tooltip("Not supported by this media app right now"));
        return b;
    }

    private void commitDictation() {
        background.submit(() -> {
            try { svc.dictation.commit(); }
            catch (Exception ex) { svc.bus.publish(new DriftEvent.Failure("Nothing was typed", ex.getMessage(), "Focus the target window, then press Insert again")); }
        });
    }

    /** Test hook. */
    Label titleLabel() { return title; }
    VBox bodyBox() { return body; }
}
