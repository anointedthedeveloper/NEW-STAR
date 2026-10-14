package com.drift.ui;

import com.drift.actions.ActionDefinition;
import com.drift.applications.AppEntry;
import com.drift.assets.AssetPackService;
import com.drift.config.DriftSettings;
import com.drift.config.SettingsService;
import com.drift.learning.AliasRepository;
import com.drift.learning.AliasRepository.AliasKind;
import com.drift.persistence.KvStore;
import com.drift.themes.ThemeDefinition;
import com.drift.widgets.WidgetConfig;
import com.drift.widgets.WidgetLayout;
import com.drift.widgets.WidgetType;
import com.drift.windows.PlatformInfo;
import com.drift.windows.WindowsStartupRegistrar;
import java.awt.Desktop;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ColorPicker;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Slider;
import javafx.scene.control.Spinner;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

/** The Drift settings application. Every control writes through {@link SettingsService}, which validates before persisting. */
public class SettingsWindow {
    static final List<String> SECTIONS = List.of("Appearance", "Floating interface", "Voice recognition", "Command preferences", "Application aliases",
            "Personal learning", "Media integrations", "Widgets", "Startup and tray", "Keyboard shortcuts", "Developer mode", "Privacy",
            "Diagnostics", "Updates", "About");
    private static final String IMMEDIATE = "Applies immediately", RESTART = "Needs restart";

    private final UiServices svc;
    private final KvStore kv;
    private final WidgetManager widgets;
    private final Stage stage = new Stage();
    private final ListView<String> nav = new ListView<>(FXCollections.observableArrayList(SECTIONS));
    private final BorderPane content = new BorderPane();
    private final Label status = new Label();
    private boolean hideOnClose;

    public SettingsWindow(UiServices svc, ThemeApplier theme, KvStore kv, WidgetManager widgets) {
        this.svc = svc;
        this.kv = kv;
        this.widgets = widgets;
        stage.setTitle("Drift Settings");
        stage.getIcons().add(Fx.toFx(DriftIcon.render(64)));
        nav.getStyleClass().add("settings-nav");
        nav.setPrefWidth(200);
        Label brand = Fx.label("Drift", "drift-section-title");
        Label tag = Fx.label("Your desktop. Your rules.", "drift-muted");
        VBox navBox = new VBox(8, new VBox(2, brand, tag), nav, Fx.button("Reset all settings…", this::resetAll, "danger"));
        navBox.setPadding(new Insets(12, 0, 12, 0));
        ((VBox) navBox.getChildren().get(0)).setPadding(new Insets(0, 14, 0, 14));
        VBox.setVgrow(nav, Priority.ALWAYS);
        navBox.getStyleClass().add("settings-nav");
        BorderPane root = new BorderPane();
        root.getStyleClass().add("settings-root");
        root.setLeft(navBox);
        status.getStyleClass().add("drift-muted");
        status.setPadding(new Insets(6, 16, 8, 16));
        root.setCenter(content);
        root.setBottom(status);
        BorderPane.setMargin(navBox, Insets.EMPTY);
        nav.getSelectionModel().selectedItemProperty().addListener((o, a, b) -> { if (b != null) showSection(b); });
        Scene scene = new Scene(root, 1000, 680);
        theme.register(scene);
        stage.setScene(scene);
        stage.setOnCloseRequest(e -> { if (hideOnClose) { e.consume(); stage.hide(); } });
        nav.getSelectionModel().select(0);
    }

    public static List<String> SECTIONS_FOR_TEST() { return SECTIONS; }

    public void setHideOnClose(boolean hide) { this.hideOnClose = hide; }

    public void show(String section) {
        Fx.ui(() -> {
            if (section != null && SECTIONS.contains(section)) nav.getSelectionModel().select(section);
            else showSection(nav.getSelectionModel().getSelectedItem());
            stage.show();
            stage.toFront();
        });
    }

    public void close() { Fx.ui(() -> { hideOnClose = false; stage.close(); }); }

    public Stage stage() { return stage; }

    // ---------- plumbing ----------

    private void showSection(String name) {
        Node body = switch (name) {
            case "Appearance" -> appearance();
            case "Floating interface" -> floating();
            case "Voice recognition" -> voice();
            case "Command preferences" -> commands();
            case "Application aliases" -> aliases();
            case "Personal learning" -> learning();
            case "Media integrations" -> media();
            case "Widgets" -> widgetsSection();
            case "Startup and tray" -> startup();
            case "Keyboard shortcuts" -> shortcuts();
            case "Developer mode" -> developer();
            case "Privacy" -> privacy();
            case "Diagnostics" -> diagnostics();
            case "Updates" -> updates();
            default -> about();
        };
        VBox page = new VBox(14, Fx.label(name, "drift-section-title"), body);
        page.setPadding(new Insets(16, 20, 16, 20));
        ScrollPane sp = new ScrollPane(page);
        sp.setFitToWidth(true);
        content.setCenter(sp);
        status.setText("");
    }

    /** Applies a settings change, showing validation errors instead of failing silently. */
    private boolean change(UnaryOperator<DriftSettings> op) {
        try {
            svc.settings.update(op);
            status.setText("Saved.");
            return true;
        } catch (SettingsService.InvalidSettingsException e) {
            status.setText("Not saved: " + String.join("; ", e.errors()));
            return false;
        }
    }

    private static VBox group(String title, String badge, Node... nodes) {
        VBox g = new VBox(8);
        g.getStyleClass().add("settings-group");
        HBox head = new HBox(8, Fx.label(title, "drift-title"));
        if (badge != null) head.getChildren().add(Fx.label(badge, "badge"));
        g.getChildren().add(head);
        g.getChildren().addAll(nodes);
        return g;
    }

    private static HBox row(String label, Node control) {
        Label l = new Label(label);
        l.setMinWidth(190);
        l.getStyleClass().add("drift-label");
        HBox r = new HBox(10, l, control);
        r.setAlignment(Pos.CENTER_LEFT);
        return r;
    }

    private static Label note(String text) {
        Label l = Fx.label(text, "drift-muted");
        l.setWrapText(true);
        return l;
    }

    private CheckBox check(String text, boolean value, java.util.function.Consumer<Boolean> onChange) {
        CheckBox c = new CheckBox(text);
        c.setSelected(value);
        c.selectedProperty().addListener((o, a, b) -> onChange.accept(b));
        return c;
    }

    private Slider slider(double min, double max, double value, java.util.function.DoubleConsumer onChange) {
        Slider s = new Slider(min, max, value);
        s.setPrefWidth(220);
        s.valueProperty().addListener((o, a, b) -> { if (!s.isValueChanging()) onChange.accept(b.doubleValue()); });
        s.valueChangingProperty().addListener((o, was, now) -> { if (!now) onChange.accept(s.getValue()); });
        return s;
    }

    private static Spinner<Integer> intSpinner(int min, int max, int value) {
        Spinner<Integer> sp = new Spinner<>(min, max, value);
        sp.setEditable(true);
        sp.setPrefWidth(110);
        return sp;
    }

    private void confirm(String message, Runnable onYes) {
        Alert a = new Alert(Alert.AlertType.CONFIRMATION, message, ButtonType.YES, ButtonType.NO);
        a.initOwner(stage);
        a.showAndWait().filter(b -> b == ButtonType.YES).ifPresent(b -> onYes.run());
    }

    private void info(String message) {
        Alert a = new Alert(Alert.AlertType.INFORMATION, message, ButtonType.OK);
        a.initOwner(stage);
        a.setHeaderText(null);
        a.showAndWait();
    }

    private void resetAll() {
        confirm("Reset all settings to their defaults? Learned data, widgets and history are kept.", () -> { svc.settings.resetToDefaults(); showSection(nav.getSelectionModel().getSelectedItem()); });
    }

    private static void openFolder(Path p) {
        try { if (Desktop.isDesktopSupported()) Desktop.getDesktop().open(p.toFile()); } catch (Exception ignored) { /* nothing to open with */ }
    }

    private <T> ListView<T> listView(List<T> items, double height) {
        ListView<T> lv = new ListView<>(FXCollections.observableArrayList(items));
        lv.setPrefHeight(height);
        return lv;
    }

    // ---------- sections ----------

    private Node appearance() {
        var a = svc.settings.get().appearance();
        ComboBox<String> themeBox = new ComboBox<>(FXCollections.observableArrayList(svc.themes.all().keySet()));
        themeBox.setValue(a.themeName());
        themeBox.setOnAction(e -> { if (themeBox.getValue() != null) { svc.themes.apply(themeBox.getValue()); status.setText("Theme applied."); } });

        ThemeDefinition cur = svc.themes.active();
        TextField name = new TextField(cur.name() + " copy");
        CheckBox dark = new CheckBox("Dark mode");
        dark.setSelected(cur.dark());
        ColorPicker accent = new ColorPicker(Color.web(cur.accent()));
        Slider surface = new Slider(0.3, 1.0, cur.surfaceOpacity());
        ComboBox<String> font = new ComboBox<>(FXCollections.observableArrayList(javafx.scene.text.Font.getFamilies()));
        font.setEditable(true);
        font.setValue(cur.fontFamily());
        Spinner<Integer> size = intSpinner(9, 24, (int) cur.fontSize());
        Spinner<Integer> radius = intSpinner(0, 24, (int) cur.cornerRadius());
        Slider anim = new Slider(0, 1.5, cur.animationIntensity());
        ComboBox<String> density = new ComboBox<>(FXCollections.observableArrayList("compact", "comfortable", "spacious"));
        density.setValue(cur.density());
        Supplier<ThemeDefinition> build = () -> new ThemeDefinition(name.getText().strip(), dark.isSelected(), hex(accent.getValue()),
                Math.round(surface.getValue() * 100) / 100.0, font.getValue(), size.getValue(), radius.getValue(), Math.round(anim.getValue() * 100) / 100.0, density.getValue());
        VBox editor = group("Create or edit a theme", IMMEDIATE, row("Name", name), dark, row("Accent colour", accent), row("Surface opacity", surface),
                row("Font", font), row("Font size", size), row("Corner radius", radius), row("Animation intensity", anim), row("Density", density),
                new HBox(8, Fx.button("Save and apply", () -> {
                    try { ThemeDefinition t = build.get(); svc.themes.save(t); svc.themes.apply(t.name()); showSection("Appearance"); status.setText("Theme saved."); }
                    catch (IllegalArgumentException ex) { status.setText(ex.getMessage()); }
                }, "primary"),
                        Fx.button("Delete this theme", () -> { String n = themeBox.getValue(); if (n != null && !List.of("Drift Dark", "Drift Light", "Coding").contains(n)) { svc.themes.delete(n); showSection("Appearance"); } else status.setText("Built-in themes cannot be deleted."); }),
                        Fx.button("Export…", () -> {
                            FileChooser fc = new FileChooser(); fc.setInitialFileName(cur.name() + ".drift-theme.json");
                            File f = fc.showSaveDialog(stage);
                            if (f != null) try { Files.writeString(f.toPath(), svc.themes.export(cur)); status.setText("Exported."); } catch (Exception ex) { status.setText(ex.getMessage()); }
                        }),
                        Fx.button("Import…", () -> {
                            File f = new FileChooser().showOpenDialog(stage);
                            if (f != null) try { ThemeDefinition t = svc.themes.importFile(f.toPath()); showSection("Appearance"); status.setText("Imported \"" + t.name() + "\" (not applied yet)."); } catch (Exception ex) { status.setText("Import rejected: " + ex.getMessage()); }
                        })));
        CheckBox motion = check("Reduce motion (no fades or pulsing)", a.reducedMotion(), v -> change(s -> s.withAppearance(s.appearance().withReducedMotion(v))));

        // icon packs & wallpapers
        ListView<String> packs = listView(svc.packs.installed(), 90);
        TextArea previewArea = new TextArea();
        previewArea.setEditable(false);
        previewArea.setPrefRowCount(4);
        VBox packBox = group("Icon packs and wallpapers", IMMEDIATE, note("Packs are zip files with a manifest.json and PNG/JPEG/BMP/ICO images. They are validated and never executed."),
                packs, previewArea,
                new HBox(8, Fx.button("Import pack…", () -> importPack(previewArea, packs)),
                        Fx.button("Use as icon pack", () -> { String id = packs.getSelectionModel().getSelectedItem(); if (id != null) { svc.packs.applyIconPack(id); status.setText("Icon pack set."); } }),
                        Fx.button("No icon pack", () -> { svc.packs.applyIconPack(""); status.setText("Icon pack cleared."); }),
                        Fx.button("Apply first wallpaper", () -> applyWallpaper(packs)),
                        Fx.button("Revert wallpaper", () -> { try { status.setText(svc.packs.revertWallpaper() ? "Wallpaper reverted." : "No previous wallpaper recorded."); } catch (Exception ex) { status.setText(ex.getMessage()); } }),
                        Fx.button("Remove pack", () -> { String id = packs.getSelectionModel().getSelectedItem(); if (id != null) try { svc.packs.remove(id); showSection("Appearance"); } catch (Exception ex) { status.setText(ex.getMessage()); } })));
        return new VBox(14, group("Theme", IMMEDIATE, row("Active theme", themeBox), motion), editor, packBox);
    }

    private void importPack(TextArea preview, ListView<String> packs) {
        File f = new FileChooser().showOpenDialog(stage);
        if (f == null) return;
        AssetPackService.Inspection ins = svc.packs.inspect(f.toPath());
        StringBuilder sb = new StringBuilder();
        if (ins.manifest() != null) sb.append(ins.manifest().name()).append(" ").append(ins.manifest().version()).append(" (").append(ins.manifest().type()).append("), ").append(ins.manifest().files().size()).append(" files\n");
        ins.errors().forEach(e -> sb.append("ERROR: ").append(e).append('\n'));
        ins.warnings().forEach(e -> sb.append("Note: ").append(e).append('\n'));
        preview.setText(sb.toString());
        if (!ins.valid() || ins.duplicate()) { status.setText("Pack not imported."); return; }
        confirm("Install this pack?\n\n" + sb, () -> {
            try { String id = svc.packs.install(f.toPath()); packs.getItems().setAll(svc.packs.installed()); status.setText("Installed " + id); }
            catch (Exception ex) { status.setText(ex.getMessage()); }
        });
    }

    private void applyWallpaper(ListView<String> packs) {
        String id = packs.getSelectionModel().getSelectedItem();
        if (id == null) { status.setText("Select a wallpaper pack first."); return; }
        List<Path> assets = svc.packs.assets(id);
        if (assets.isEmpty()) { status.setText("That pack has no images."); return; }
        confirm("Set " + assets.get(0).getFileName() + " as your desktop wallpaper? You can revert afterwards.", () -> {
            try { svc.packs.applyWallpaper(id, assets.get(0)); status.setText("Wallpaper applied."); } catch (Exception ex) { status.setText(ex.getMessage()); }
        });
    }

    private static String hex(Color c) {
        return String.format("#%02X%02X%02X", (int) Math.round(c.getRed() * 255), (int) Math.round(c.getGreen() * 255), (int) Math.round(c.getBlue() * 255));
    }

    private Node floating() {
        var f = svc.settings.get().floating();
        Spinner<Integer> w = intSpinner(200, 900, (int) f.width()), h = intSpinner(40, 900, (int) f.height());
        ComboBox<String> anchor = new ComboBox<>(FXCollections.observableArrayList("RIGHT", "LEFT", "TOP", "BOTTOM"));
        anchor.setValue(f.anchor());
        anchor.setOnAction(e -> change(s -> s.withFloating(s.floating().withAnchor(anchor.getValue()).withCustomPosition(false))));
        w.valueProperty().addListener((o, a, b) -> change(s -> s.withFloating(s.floating().withWidth(b))));
        h.valueProperty().addListener((o, a, b) -> change(s -> s.withFloating(s.floating().withHeight(b))));
        return new VBox(14, group("Floating interface", IMMEDIATE,
                check("Show the floating interface", f.enabled(), v -> change(s -> s.withFloating(s.floating().withEnabled(v)))),
                check("Keep above other windows", f.alwaysOnTop(), v -> change(s -> s.withFloating(s.floating().withAlwaysOnTop(v)))),
                check("Start expanded", f.startExpanded(), v -> change(s -> s.withFloating(s.floating().withStartExpanded(v)))),
                row("Transparency (opacity)", slider(0.2, 1.0, f.opacity(), v -> change(s -> s.withFloating(s.floating().withOpacity(Math.round(v * 100) / 100.0))))),
                row("Width", w), row("Compact height", h), row("Default side", anchor),
                new HBox(8, Fx.button("Reset position", () -> { change(s -> s.withFloating(s.floating().withCustomPosition(false))); status.setText("Position reset to the chosen side."); })),
                note("Drag the interface anywhere; it snaps to screen edges and stays on the monitor you leave it on.")),
                group("Click-through", IMMEDIATE,
                        check("Let clicks pass through the idle interface", f.clickThroughWhenIdle(), v -> change(s -> s.withFloating(s.floating().withClickThroughWhenIdle(v)))),
                        note("Experimental and Windows-only. While idle and click-through is on, you cannot click the interface - use the tray icon or your toggle shortcut to bring it back. Off by default.")));
    }

    private Node voice() {
        var v = svc.settings.get().voice();
        var st = svc.voice.providerStatus();
        Label stateL = Fx.label(st.state() + (st.ready() ? "" : " - not ready"), st.ready() ? "drift-ok-text" : "drift-error-text");
        Label detail = note(st.detail());
        Path modelsDir = svc.vosk.locator().modelsDir();
        TextField modelPath = new TextField(v.modelPath());
        modelPath.setPromptText("Blank = pick automatically from the models folder");
        modelPath.setPrefWidth(380);
        TextField language = new TextField(v.language());
        language.setPrefWidth(100);
        ComboBox<String> mic = new ComboBox<>();
        mic.getItems().add("System default");
        mic.getItems().addAll(svc.voice.microphones());
        mic.setValue(v.microphone().isBlank() ? "System default" : v.microphone());
        VBox setup = group("Speech engine (offline)", null, row("Engine", new Label(svc.vosk.displayName())), row("Status", stateL), detail,
                row("Models folder", new HBox(8, new Label(modelsDir.toString()), Fx.button("Open", () -> { try { Files.createDirectories(modelsDir); } catch (Exception ignored) { /* shown below */ } openFolder(modelsDir); }))),
                row("Installed models", new Label(svc.vosk.locator().installed().isEmpty() ? "none" : String.join(", ", svc.vosk.locator().installed().stream().map(p -> p.getFileName().toString()).toList()))),
                note("Download a model from https://alphacephei.com/vosk/models (for example vosk-model-small-en-us-0.15), unzip it into the models folder, then press Refresh. Models are never uploaded or bundled. See docs/SPEECH_SETUP.md."),
                new HBox(8, Fx.button("Refresh status", () -> showSection("Voice recognition"))));
        VBox options = group("Microphone and language", IMMEDIATE + " (next time you listen)",
                row("Microphone", mic), row("Language", language), row("Model folder (optional)", new HBox(8, modelPath, Fx.button("Browse…", () -> { File d = new DirectoryChooser().showDialog(stage); if (d != null) modelPath.setText(d.getAbsolutePath()); }))),
                check("Push-to-talk (hold the mic button or shortcut)", v.pushToTalk(), val -> change(s -> s.withVoice(s.voice().withPushToTalk(val)))),
                new HBox(8, Fx.button("Apply", () -> {
                    if (change(s -> s.withVoice(s.voice().withMicrophone("System default".equals(mic.getValue()) ? "" : mic.getValue()).withLanguage(language.getText().strip()).withModelPath(modelPath.getText().strip())))) showSection("Voice recognition");
                }, "primary")),
                note("Drift only records while you hold push-to-talk or have started dictation, and the red microphone indicator is visible the whole time. Audio is processed locally, kept only in a short in-memory buffer, and never saved or uploaded. A wake word is not implemented."));
        return new VBox(14, setup, options);
    }

    private Node commands() {
        TableView<ActionDefinition> t = new TableView<>(FXCollections.observableArrayList(svc.registry.all()));
        t.getColumns().add(col("Action", a -> a.id(), 160));
        t.getColumns().add(col("What it does", a -> a.description(), 330));
        t.getColumns().add(col("Permission", a -> a.permission().name(), 90));
        t.getColumns().add(col("Confirms", a -> a.confirmation() == ActionDefinition.Confirmation.ALWAYS ? "always" : "when risky", 80));
        t.getColumns().add(col("Timeout", a -> a.timeout().toSeconds() + "s", 70));
        t.setPrefHeight(360);
        return new VBox(14, group("Registered actions", null, note("Drift can only run actions on this list. Speech is never turned into arbitrary system commands. Process execution needs Developer mode; typing into other apps always shows a preview first."), t));
    }

    private <S> TableColumn<S, String> col(String title, java.util.function.Function<S, String> f, double width) {
        TableColumn<S, String> c = new TableColumn<>(title);
        c.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(f.apply(d.getValue())));
        c.setPrefWidth(width);
        return c;
    }

    private Node aliases() {
        List<AppEntry> apps = svc.apps.apps();
        TableView<AliasRepository.Alias> table = new TableView<>(FXCollections.observableArrayList(svc.aliases.listAll().stream().filter(a -> a.kind() != AliasKind.PHRASE).toList()));
        table.getColumns().add(col("Kind", a -> a.kind().name(), 70));
        table.getColumns().add(col("When you say", a -> a.phrase(), 200));
        table.getColumns().add(col("Opens", a -> a.kind() == AliasKind.APP ? svc.apps.byId(a.target()).map(AppEntry::name).orElse("(missing) " + a.target()) : a.target(), 320));
        table.getColumns().add(col("Used", a -> Integer.toString(a.usageCount()), 50));
        table.setPrefHeight(200);
        TextField phrase = new TextField();
        phrase.setPromptText("e.g. coding editor");
        ComboBox<AppEntry> appBox = new ComboBox<>(FXCollections.observableArrayList(apps));
        appBox.setPrefWidth(260);
        appBox.setPromptText(apps.isEmpty() ? "No applications discovered" : "Choose an application");
        TextField folderPhrase = new TextField();
        folderPhrase.setPromptText("e.g. my projects");
        VBox aliasBox = group("Application and folder aliases", IMMEDIATE, table,
                new HBox(8, Fx.button("Delete selected", () -> { var a = table.getSelectionModel().getSelectedItem(); if (a != null) { svc.aliases.delete(a.id()); showSection("Application aliases"); } })),
                row("New app alias", new HBox(8, phrase, appBox, Fx.button("Add", () -> {
                    if (appBox.getValue() == null || phrase.getText().isBlank()) { status.setText("Enter a phrase and choose an application."); return; }
                    try { svc.aliases.put(AliasKind.APP, phrase.getText(), appBox.getValue().id()); showSection("Application aliases"); } catch (IllegalArgumentException ex) { status.setText(ex.getMessage()); }
                }))),
                row("New folder alias", new HBox(8, folderPhrase, Fx.button("Choose folder and add…", () -> {
                    File d = new DirectoryChooser().showDialog(stage);
                    if (d != null && !folderPhrase.getText().isBlank()) try { svc.aliases.put(AliasKind.FOLDER, folderPhrase.getText(), d.getAbsolutePath()); showSection("Application aliases"); } catch (IllegalArgumentException ex) { status.setText(ex.getMessage()); }
                }))));
        ListView<AppEntry> favs = new ListView<>(FXCollections.observableArrayList(apps));
        favs.setPrefHeight(200);
        List<AppEntry> current = svc.apps.favorites();
        favs.setCellFactory(lv -> new javafx.scene.control.ListCell<>() {
            @Override protected void updateItem(AppEntry item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setGraphic(null); setText(null); return; }
                CheckBox cb = new CheckBox(item.name());
                cb.setSelected(svc.apps.favorites().stream().anyMatch(f -> f.id().equals(item.id())));
                cb.setOnAction(e -> svc.apps.setFavorite(item, cb.isSelected()));
                setGraphic(cb);
            }
        });
        VBox favBox = group("Favourite applications (used by the Quick launch widget)", IMMEDIATE, favs,
                new HBox(8, Fx.button("Rescan applications", () -> { svc.apps.refresh(); showSection("Application aliases"); }), new Label(apps.size() + " applications discovered" + (PlatformInfo.isWindows() ? "" : " (application discovery reads the Windows Start Menu)"))));
        VBox recent = group("Recently launched", null, new Label(svc.apps.recentNames(8).isEmpty() ? "Nothing launched through Drift yet." : String.join(", ", svc.apps.recentNames(8))));
        return new VBox(14, aliasBox, favBox, recent);
    }

    private Node learning() {
        TableView<com.drift.learning.LearnedCommandRepository.LearnedCommand> t = new TableView<>(FXCollections.observableArrayList(svc.learned.list()));
        t.getColumns().add(col("Phrase", c -> c.utterance(), 240));
        t.getColumns().add(col("Action", c -> c.actionId(), 110));
        t.getColumns().add(col("Worked", c -> Integer.toString(c.successCount()), 60));
        t.getColumns().add(col("Rejected", c -> Integer.toString(c.rejectCount()), 70));
        t.getColumns().add(col("State", c -> c.disabled() ? "disabled" : "active", 70));
        t.setPrefHeight(220);
        return new VBox(14, group("What Drift has learned (stored only on this computer)", IMMEDIATE,
                note("Only low-risk actions (opening apps/folders/files/projects, media control, themes) can be learned. Anything that runs processes or types text is never learned, and a learned phrase can never skip a confirmation."),
                t,
                new HBox(8, Fx.button("Delete selected", () -> { var c = t.getSelectionModel().getSelectedItem(); if (c != null) { svc.learned.delete(c.id()); showSection("Personal learning"); } }),
                        Fx.button("Export…", () -> { FileChooser fc = new FileChooser(); fc.setInitialFileName("drift-learned.json"); File f = fc.showSaveDialog(stage);
                            if (f != null) try { Files.writeString(f.toPath(), svc.learning.exportJson()); status.setText("Exported."); } catch (Exception ex) { status.setText(ex.getMessage()); } }),
                        Fx.button("Import…", () -> { File f = new FileChooser().showOpenDialog(stage);
                            if (f != null) try { if (Files.size(f.toPath()) > 2_000_000) throw new IllegalArgumentException("File too large"); status.setText("Imported " + svc.learning.importJson(Files.readString(f.toPath())) + " entries."); showSection("Personal learning"); } catch (Exception ex) { status.setText("Import failed: " + ex.getMessage()); } }),
                        Fx.button("Reset all learned data…", () -> confirm("Delete every learned alias, command and correction?", () -> { svc.learning.reset(); showSection("Personal learning"); }), "danger"))));
    }

    private Node media() {
        var av = svc.media.availability();
        Label avail = Fx.label(av.available() ? "Available: " + av.detail() : "Unavailable: " + av.detail(), av.available() ? "drift-ok-text" : "drift-error-text");
        avail.setWrapText(true);
        ListView<String> sessions = new ListView<>();
        sessions.setPrefHeight(120);
        java.util.Map<String, String> idByLabel = new LinkedHashMap<>();
        javafx.scene.control.Button refreshBtn = Fx.button("Refresh sessions", () -> new Thread(() -> {
            try {
                var list = svc.media.refresh();
                Fx.ui(() -> { idByLabel.clear(); sessions.getItems().clear(); for (var s : list) { String label = s.app() + " - " + s.display() + " (" + s.status().name().toLowerCase() + ")"; idByLabel.put(label, s.id()); sessions.getItems().add(label); }
                    if (list.isEmpty()) status.setText("No media session is active."); });
            } catch (Exception ex) { Fx.ui(() -> status.setText(ex.getMessage())); }
        }, "drift-media-refresh").start());
        sessions.getSelectionModel().selectedItemProperty().addListener((o, a, b) -> { if (b != null) { svc.media.select(idByLabel.get(b)); status.setText("Using " + b); } });
        return new VBox(14, group("Windows media sessions", null, avail,
                note("Drift reads track info and sends play/pause/next/previous to whichever apps register with Windows media controls (Spotify, browsers, media players...). What each app allows is reported by that app; unsupported buttons are disabled. Volume keys change the system volume, not per-app volume. Verified only on Windows."),
                check("Read media sessions while Drift runs (restart to apply)", kv.getBool("media.enabled", true), v -> kv.putBool("media.enabled", v)),
                refreshBtn, sessions));
    }

    private Node widgetsSection() {
        List<WidgetConfig> list = widgets.widgets();
        ListView<String> lv = listView(list.stream().map(w -> w.id() + " - " + WidgetType.fromId(w.type()).map(t -> t.displayName).orElse(w.type())).toList(), 140);
        ComboBox<WidgetType> add = new ComboBox<>(FXCollections.observableArrayList(WidgetType.values()));
        add.setConverter(new javafx.util.StringConverter<>() { public String toString(WidgetType t) { return t == null ? "" : t.displayName; } public WidgetType fromString(String s) { return null; } });
        add.setPromptText("Choose a widget");
        ComboBox<String> layouts = new ComboBox<>(FXCollections.observableArrayList(svc.layouts.names()));
        layouts.setValue(svc.layouts.activeName());
        TextField newLayout = new TextField();
        newLayout.setPromptText("New preset name");
        var wth = svc.settings.get().weather();
        CheckBox wEnabled = new CheckBox("Fetch weather (sends only the coordinates below to open-meteo.com)");
        wEnabled.setSelected(wth.enabled());
        TextField lat = new TextField(Double.toString(wth.latitude())), lon = new TextField(Double.toString(wth.longitude()));
        lat.setPrefWidth(90); lon.setPrefWidth(90);
        ComboBox<String> units = new ComboBox<>(FXCollections.observableArrayList("metric", "imperial"));
        units.setValue(wth.units());
        return new VBox(14,
                group("Widgets on your desktop", IMMEDIATE, lv,
                        new HBox(8, add, Fx.button("Add", () -> { if (add.getValue() != null) { widgets.add(add.getValue()); status.setText("Added. Drag it where you want it; right-click for settings."); javafx.application.Platform.runLater(() -> showSection("Widgets")); } }, "primary"),
                                Fx.button("Remove selected", () -> { String s = lv.getSelectionModel().getSelectedItem(); if (s != null) { widgets.remove(s.split(" - ")[0]); javafx.application.Platform.runLater(() -> showSection("Widgets")); } })),
                        check("Keep widgets above other windows", widgets.alwaysOnTop(), widgets::setAlwaysOnTop),
                        note("Widgets float as small always-available windows. Embedding them into the Windows desktop layer is not supported (undocumented and unreliable); this is the documented fallback.")),
                group("Layout presets", IMMEDIATE, row("Active preset", new HBox(8, layouts, Fx.button("Use", () -> { if (layouts.getValue() != null) { widgets.useLayout(layouts.getValue()); status.setText("Preset switched."); } }))),
                        row("Save current as", new HBox(8, newLayout, Fx.button("Save", () -> {
                            try { String n = newLayout.getText().strip(); WidgetLayout cur = svc.layouts.active(); svc.layouts.save(new WidgetLayout(n, cur.widgets())); svc.layouts.setActive(n); widgets.reload(); showSection("Widgets"); }
                            catch (IllegalArgumentException ex) { status.setText(ex.getMessage()); } }))),
                        new HBox(8, Fx.button("Restore previous version of this preset", () -> { var r = svc.layouts.restorePrevious(svc.layouts.activeName()); status.setText(r.isPresent() ? "Restored." : "No previous version saved."); widgets.reload(); }),
                                Fx.button("Delete preset", () -> { String n = layouts.getValue(); if (n != null && !n.equals("Default")) { svc.layouts.delete(n); svc.layouts.setActive("Default"); widgets.reload(); showSection("Widgets"); } else status.setText("The Default preset cannot be deleted.") ; }))),
                group("Weather widget", IMMEDIATE, wEnabled, row("Latitude", lat), row("Longitude", lon), row("Units", units),
                        new HBox(8, Fx.button("Apply", () -> {
                            try { change(s -> s.withWeather(new DriftSettings.Weather(wEnabled.isSelected(), Double.parseDouble(lat.getText().strip()), Double.parseDouble(lon.getText().strip()), units.getValue()))); }
                            catch (NumberFormatException ex) { status.setText("Latitude and longitude must be numbers."); } }, "primary")),
                        note("No weather provider is contacted unless this is enabled. If you are offline the widget says so instead of showing stale numbers.")));
    }

    private Node startup() {
        var st = svc.settings.get().startup();
        boolean winOk = svc.startup.supported();
        var cmd = WindowsStartupRegistrar.installedLauncherCommand();
        CheckBox launch = new CheckBox("Start Drift when I sign in to Windows");
        launch.setSelected(svc.startup.isEnabled());
        launch.setDisable(!winOk || (cmd.isEmpty() && !svc.startup.isEnabled()));
        launch.setOnAction(e -> {
            try {
                if (launch.isSelected()) svc.startup.enable(cmd.orElseThrow());
                else svc.startup.disable();
                change(s -> s.withStartup(s.startup().withLaunchWithWindows(launch.isSelected())));
            } catch (RuntimeException ex) { launch.setSelected(svc.startup.isEnabled()); status.setText("Could not change startup: " + ex.getMessage()); }
        });
        String why = !winOk ? "Only available on Windows." : cmd.isEmpty() ? "Only available in the installed app (not when started from an IDE or java -jar)." : "Adds a per-user entry in HKCU\\...\\Run. No administrator rights needed. Untick to remove it.";
        return new VBox(14,
                group("Start with Windows", IMMEDIATE, launch, note(why), check("Start hidden in the tray", st.startHidden(), v -> change(s -> s.withStartup(s.startup().withStartHidden(v))))),
                group("Tray", RESTART, check("Keep Drift in the system tray when windows are closed", st.minimizeToTray(), v -> change(s -> s.withStartup(s.startup().withMinimizeToTray(v)))),
                        note(TrayController.supported() ? "System tray is available." : "System tray is not available on this system, so Drift stays visible.")));
    }

    private Node shortcuts() {
        var sc = svc.settings.get().shortcuts();
        TextField ptt = new TextField(sc.pushToTalk()), toggle = new TextField(sc.toggleInterface()), dict = new TextField(sc.dictation());
        for (TextField f : List.of(ptt, toggle, dict)) f.setPrefWidth(200);
        return new VBox(14, group("Global shortcuts", IMMEDIATE,
                row("Push-to-talk (hold)", ptt), row("Show / hide interface", toggle), row("Start / stop dictation", dict),
                note("Format: modifiers + key, for example ctrl+alt+space, ctrl+shift+f9. At least one of ctrl, alt, shift or win is required. Leave blank to disable. If another program already owns a shortcut, Drift tells you."),
                new HBox(8, Fx.button("Apply", () -> change(s -> s.withShortcuts(new DriftSettings.Shortcuts(ptt.getText().strip(), toggle.getText().strip(), dict.getText().strip()))), "primary"),
                        Fx.button("Defaults", () -> { var d = DriftSettings.defaults().shortcuts(); change(s -> s.withShortcuts(d)); showSection("Keyboard shortcuts"); }))));
    }

    private Node developer() {
        var d = svc.settings.get().developer();
        TextField ide = new TextField(d.ide());
        ide.setPromptText("Path to your IDE executable (optional)");
        ide.setPrefWidth(380);
        Spinner<Integer> timeout = intSpinner(5, 3600, d.commandTimeoutSeconds());
        ListView<String> roots = listView(d.projectRoots(), 90);
        ListView<String> projects = listView(svc.projects.list().stream().map(p -> p.path()).toList(), 120);
        return new VBox(14,
                group("Developer mode", IMMEDIATE, check("Allow approved build and test commands", d.enabled(), v -> change(s -> s.withDeveloper(s.developer().withEnabled(v)))),
                        row("IDE executable", new HBox(8, ide, Fx.button("Browse…", () -> { File f = new FileChooser().showOpenDialog(stage); if (f != null) ide.setText(f.getAbsolutePath()); }),
                                Fx.button("Save", () -> change(s -> s.withDeveloper(s.developer().withIde(ide.getText().strip())))))),
                        row("Command time limit (s)", new HBox(8, timeout, Fx.button("Save", () -> change(s -> s.withDeveloper(s.developer().withCommandTimeoutSeconds(timeout.getValue())))))),
                        note("Only fixed templates run: " + String.join("; ", new com.drift.dev.DevTemplates().all().stream().map(t -> t.description()).toList()) + ". Each runs with a time limit and bounded output, can be cancelled, and risky ones show the exact command and wait for confirmation.")),
                group("Project folders searched by \"find my Java projects\"", IMMEDIATE, roots,
                        new HBox(8, Fx.button("Add folder…", () -> { File f = new DirectoryChooser().showDialog(stage); if (f != null) { List<String> n = new ArrayList<>(d.projectRoots()); n.add(f.getAbsolutePath()); if (change(s -> s.withDeveloper(s.developer().withProjectRoots(n)))) showSection("Developer mode"); } }),
                                Fx.button("Remove selected", () -> { String sel = roots.getSelectionModel().getSelectedItem(); if (sel != null) { List<String> n = new ArrayList<>(d.projectRoots()); n.remove(sel); change(s -> s.withDeveloper(s.developer().withProjectRoots(n))); showSection("Developer mode"); } }))),
                group("Known projects (most recent first)", null, projects,
                        new HBox(8, Fx.button("Add project folder…", () -> { File f = new DirectoryChooser().showDialog(stage); if (f != null) { svc.projects.upsert(f.toPath()); showSection("Developer mode"); } }),
                                Fx.button("Forget selected", () -> { String sel = projects.getSelectionModel().getSelectedItem(); if (sel != null) { svc.projects.remove(sel); showSection("Developer mode"); } }))));
    }

    private Node privacy() {
        var p = svc.settings.get().privacy();
        Spinner<Integer> hist = intSpinner(1, 3650, p.historyRetentionDays()), logs = intSpinner(1, 365, p.logRetentionDays());
        ListView<String> history = listView(svc.history.recent(100).stream().map(e -> e.ts() + "  " + e.actionId() + "  " + e.status() + "  " + (e.message() == null ? "" : e.message())).toList(), 220);
        return new VBox(14,
                group("Your data", IMMEDIATE,
                        note("Drift works offline. Speech is recognised on this computer, microphone audio is never saved or uploaded, and nothing is sent anywhere unless you enable the weather widget. Learned data stays in " + svc.paths.database() + "."),
                        check("Keep recognised phrases in the operation history (off by default)", p.storeTranscripts(), v -> change(s -> s.withPrivacy(s.privacy().withStoreTranscripts(v)))),
                        row("Keep history for (days)", new HBox(8, hist, Fx.button("Save", () -> change(s -> s.withPrivacy(s.privacy().withHistoryRetentionDays(hist.getValue())))))),
                        row("Keep logs for (days)", new HBox(8, logs, Fx.button("Save", () -> change(s -> s.withPrivacy(s.privacy().withLogRetentionDays(logs.getValue())))))),
                        note("Log retention applies the next time Drift starts.")),
                group("Operation history (audit trail)", null, history,
                        new HBox(8, Fx.button("Clear history…", () -> confirm("Delete the operation history?", () -> { svc.history.clear(); showSection("Privacy"); }), "danger"))));
    }

    private Node diagnostics() {
        var mon = svc.settings.get().monitoring();
        Spinner<Integer> interval = intSpinner(1, 300, mon.intervalSeconds());
        String info = diagnosticsText();
        TextArea ta = new TextArea(info);
        ta.setEditable(false);
        ta.setPrefRowCount(14);
        return new VBox(14, group("System", null, ta, new HBox(8,
                Fx.button("Copy", () -> { var cc = new javafx.scene.input.ClipboardContent(); cc.putString(info); javafx.scene.input.Clipboard.getSystemClipboard().setContent(cc); status.setText("Copied."); }),
                Fx.button("Open logs folder", () -> openFolder(svc.paths.logs())), Fx.button("Open data folder", () -> openFolder(svc.paths.home())))),
                group("System monitoring (CPU, memory, battery, network, disk)", IMMEDIATE, check("Collect system information for the monitor and battery widgets", mon.enabled(), v -> change(s -> s.withMonitoring(s.monitoring().withEnabled(v)))),
                        row("Sample every (seconds)", new HBox(8, interval, Fx.button("Save", () -> change(s -> s.withMonitoring(s.monitoring().withIntervalSeconds(interval.getValue())))))),
                        note("Off by default. When off, no sampling happens at all. Sensors the system doesn't expose (for example a battery on a desktop PC) are shown as unavailable.")));
    }

    private String diagnosticsText() {
        StringBuilder sb = new StringBuilder();
        sb.append("Drift ").append(version()).append('\n');
        sb.append("OS: ").append(PlatformInfo.osDescription()).append(" / ").append(PlatformInfo.windowsEdition()).append('\n');
        sb.append("Java: ").append(System.getProperty("java.version")).append(" (").append(System.getProperty("java.vendor")).append(")\n");
        sb.append("Data folder: ").append(svc.paths.home()).append('\n');
        var st = svc.voice.providerStatus();
        sb.append("Speech: ").append(st.state()).append(" - ").append(st.detail()).append('\n');
        sb.append("Microphones: ").append(svc.voice.microphones().isEmpty() ? "none detected" : String.join(", ", svc.voice.microphones())).append('\n');
        sb.append("Tray available: ").append(TrayController.supported()).append('\n');
        sb.append("Media: ").append(svc.media.availability()).append('\n');
        sb.append("Applications discovered: ").append(svc.apps.apps().size()).append('\n');
        sb.append("Widgets open: ").append(widgets.openStages()).append('\n');
        sb.append("Operations recorded: ").append(svc.history.recent(1000).size()).append('\n');
        return com.drift.history.Redactor.redact(sb.toString());
    }

    private Node updates() {
        return new VBox(14, group("Updates", null, note("Drift does not check for updates or contact any server on its own. To update, run the newer installer over the existing installation; your settings and learned data live in " + svc.paths.home() + " and are kept."),
                Fx.label("Installed version: " + version(), "drift-label")));
    }

    private Node about() {
        return new VBox(14, group("Drift", null, Fx.label("A customizable desktop companion for Windows", "drift-title"), Fx.label("Your desktop. Your rules.", "drift-muted"),
                Fx.label("Version " + version(), "drift-label"),
                note("Local-first: speech recognition (Vosk), command memory and widgets all run on this computer. Third-party components and their licences are listed in docs/THIRD_PARTY_LICENSES.md."),
                note("Verified status of each feature is in docs/IMPLEMENTATION_STATUS.md.")));
    }

    static String version() {
        String v = SettingsWindow.class.getPackage().getImplementationVersion();
        return v != null ? v : "0.1.0";
    }
}
