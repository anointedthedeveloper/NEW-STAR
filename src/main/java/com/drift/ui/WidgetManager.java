package com.drift.ui;

import com.drift.persistence.KvStore;
import com.drift.ui.WindowPlacement.Rect;
import com.drift.widgets.WeatherService;
import com.drift.widgets.WidgetConfig;
import com.drift.widgets.WidgetLayout;
import com.drift.widgets.WidgetType;
import com.drift.windows.WindowStyler;
import java.time.Clock;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Scene;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.Slider;
import javafx.scene.control.Spinner;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

/**
 * The floating widget layer. Each widget is its own small transparent always-on-top window, positioned in screen coordinates.
 * Embedding widgets into the Windows desktop (WorkerW) is NOT attempted: it is undocumented and unreliable across builds, so
 * widgets float above other windows instead (see docs/ARCHITECTURE.md).
 */
public class WidgetManager {
    private static final class Handle {
        WidgetConfig cfg;
        WidgetViews.WidgetView view;
        Stage stage;
    }

    private final UiServices svc;
    private final ThemeApplier theme;
    private final KvStore kv;
    private final Runnable openSettings;
    private final Map<String, Handle> handles = new LinkedHashMap<>();
    private final ExecutorService background = Executors.newVirtualThreadPerTaskExecutor();
    private final WeatherService weather;

    public WidgetManager(UiServices svc, ThemeApplier theme, KvStore kv, Runnable openSettings) {
        this.svc = svc;
        this.theme = theme;
        this.kv = kv;
        this.openSettings = openSettings;
        this.weather = new WeatherService(svc.settings);
    }

    public boolean alwaysOnTop() { return kv.getBool("widgets.alwaysOnTop", true); }

    public void setAlwaysOnTop(boolean on) {
        kv.putBool("widgets.alwaysOnTop", on);
        Fx.ui(() -> handles.values().forEach(h -> h.stage.setAlwaysOnTop(on)));
    }

    public List<WidgetConfig> widgets() { return svc.layouts.active().widgets(); }

    public int openStages() { return handles.size(); }

    /** Brings the open widget windows in line with the active layout. */
    public void reload() {
        Fx.ui(() -> {
            WidgetLayout layout = svc.layouts.active();
            Map<String, WidgetConfig> wanted = new HashMap<>();
            layout.widgets().forEach(w -> wanted.put(w.id(), w));
            for (String id : new ArrayList<>(handles.keySet())) {
                Handle h = handles.get(id);
                WidgetConfig w = wanted.get(id);
                if (w == null || !w.equals(h.cfg)) { destroy(id); }
            }
            for (WidgetConfig w : layout.widgets()) if (!handles.containsKey(w.id())) create(w);
        });
    }

    private void destroy(String id) {
        Handle h = handles.remove(id);
        if (h != null) { h.view.stop(); h.stage.close(); }
    }

    public void closeAll() { Fx.ui(() -> new ArrayList<>(handles.keySet()).forEach(this::destroy)); background.shutdownNow(); }

    private void create(WidgetConfig cfg) {
        Handle h = new Handle();
        h.cfg = cfg;
        WidgetViews.Ctx ctx = new WidgetViews.Ctx(svc, weather, Clock.systemDefaultZone(), background, c -> saveConfig(h, c));
        h.view = WidgetViews.create(cfg, ctx);

        StackPane panel = new StackPane(h.view.node());
        panel.getStyleClass().add("widget-panel");
        Label grip = new Label("◢");
        grip.getStyleClass().add("drift-muted");
        grip.setCursor(Cursor.SE_RESIZE);
        StackPane.setAlignment(grip, Pos.BOTTOM_RIGHT);
        panel.getChildren().add(grip);
        StackPane root = new StackPane(panel);
        root.setPadding(new Insets(6));
        root.setStyle("-fx-background-color: transparent;");
        Scene scene = new Scene(root, cfg.width(), cfg.height());
        scene.setFill(Color.TRANSPARENT);
        theme.register(scene);

        Stage stage = new Stage(StageStyle.TRANSPARENT);
        stage.setTitle("Drift Widget " + cfg.id());
        stage.setScene(scene);
        stage.setAlwaysOnTop(alwaysOnTop());
        stage.setOpacity(cfg.opacity());
        Rect r = WindowPlacement.clamp(screens(), primary(), new Rect(cfg.x(), cfg.y(), cfg.width(), cfg.height()));
        stage.setX(r.x()); stage.setY(r.y()); stage.setWidth(r.w()); stage.setHeight(r.h());
        h.stage = stage;

        double[] d = new double[2];
        panel.setOnMousePressed(e -> { if (e.getTarget() != grip && !isInteractive(e.getTarget())) { d[0] = e.getScreenX() - stage.getX(); d[1] = e.getScreenY() - stage.getY(); } else d[0] = Double.NaN; });
        panel.setOnMouseDragged(e -> { if (!Double.isNaN(d[0])) { stage.setX(e.getScreenX() - d[0]); stage.setY(e.getScreenY() - d[1]); } });
        panel.setOnMouseReleased(e -> { if (!Double.isNaN(d[0])) { snapAndSave(h); d[0] = Double.NaN; } });
        grip.setOnMouseDragged(e -> {
            double w = Math.max(80, Math.min(1400, e.getScreenX() - stage.getX() + 6)), hh = Math.max(50, Math.min(1400, e.getScreenY() - stage.getY() + 6));
            stage.setWidth(w); stage.setHeight(hh);
            h.view.resized(w - 12, hh - 12);
        });
        grip.setOnMouseReleased(e -> saveConfig(h, h.cfg.withSize(stage.getWidth(), stage.getHeight())));

        ContextMenu menu = new ContextMenu();
        MenuItem settings = new MenuItem("Widget settings…");
        settings.setOnAction(e -> showSettings(h));
        MenuItem layout = new MenuItem("Toggle compact / expanded");
        layout.setOnAction(e -> { saveConfig(h, h.cfg.withLayout(h.cfg.layout().equals("compact") ? "expanded" : "compact")); reload(); });
        MenuItem remove = new MenuItem("Remove widget");
        remove.setOnAction(e -> remove(cfg.id()));
        menu.getItems().addAll(settings, layout, remove);
        panel.setOnContextMenuRequested(e -> menu.show(panel, e.getScreenX(), e.getScreenY()));

        handles.put(cfg.id(), h);
        stage.show();
        if (WindowStyler.supported()) WindowStyler.setNoActivate(stage.getTitle(), !cfg.type().equals(WidgetType.NOTES.id));
        h.view.start();
        h.view.resized(cfg.width() - 12, cfg.height() - 12);
    }

    private static boolean isInteractive(Object target) {
        for (javafx.scene.Node n = target instanceof javafx.scene.Node x ? x : null; n != null; n = n.getParent())
            if (n instanceof javafx.scene.control.Button || n instanceof javafx.scene.control.TextInputControl) return true;
        return false;
    }

    private void snapAndSave(Handle h) {
        Rect cur = new Rect(h.stage.getX(), h.stage.getY(), h.stage.getWidth(), h.stage.getHeight());
        Rect s = WindowPlacement.snap(screens(), primary(), cur);
        h.stage.setX(s.x()); h.stage.setY(s.y());
        saveConfig(h, h.cfg.withPosition(s.x(), s.y()));
    }

    /** Persists a changed widget config into the active layout (off the UI thread). */
    private void saveConfig(Handle h, WidgetConfig c) {
        h.cfg = c;
        background.submit(() -> {
            try {
                WidgetLayout l = svc.layouts.active();
                svc.layouts.save(l.with(c));
                svc.layouts.setActive(l.name());
            } catch (RuntimeException ex) {
                svc.bus.publish(new com.drift.core.DriftEvent.Failure("Could not save widget", ex.getMessage(), "Check the widget settings"));
            }
        });
    }

    public void add(WidgetType type) {
        background.submit(() -> {
            WidgetLayout l = svc.layouts.active();
            double off = 60 + 28 * l.widgets().size();
            svc.layouts.save(l.with(WidgetConfig.create(type, off, off)));
            svc.layouts.setActive(l.name());
            reload();
        });
    }

    public void remove(String id) {
        background.submit(() -> {
            WidgetLayout l = svc.layouts.active();
            svc.layouts.save(l.without(id));
            svc.layouts.setActive(l.name());
            reload();
        });
    }

    public void useLayout(String name) {
        background.submit(() -> { svc.layouts.setActive(name); reload(); });
    }

    private void showSettings(Handle h) {
        WidgetConfig c = h.cfg;
        Stage dlg = new Stage();
        dlg.setTitle("Widget settings - " + WidgetType.fromId(c.type()).orElseThrow().displayName);
        Slider opacity = new Slider(0.2, 1.0, c.opacity());
        ComboBox<String> layout = new ComboBox<>();
        layout.getItems().addAll("compact", "expanded");
        layout.setValue(c.layout());
        CheckBox label = new CheckBox("Show label");
        label.setSelected(c.showLabel());
        Spinner<Integer> refresh = new Spinner<>(1, 3600, c.refreshSeconds());
        refresh.setEditable(true);
        Spinner<Integer> w = new Spinner<>(80, 1400, (int) c.width(), 10);
        Spinner<Integer> hh = new Spinner<>(50, 1400, (int) c.height(), 10);
        GridPane grid = new GridPane();
        grid.setHgap(10); grid.setVgap(8); grid.setPadding(new Insets(14));
        int row = 0;
        grid.addRow(row++, new Label("Transparency (opacity)"), opacity);
        grid.addRow(row++, new Label("Layout"), layout);
        grid.addRow(row++, new Label(""), label);
        grid.addRow(row++, new Label("Refresh every (seconds)"), refresh);
        grid.addRow(row++, new Label("Width"), w);
        grid.addRow(row++, new Label("Height"), hh);
        Map<String, javafx.scene.Node> extra = new LinkedHashMap<>();
        WidgetType t = WidgetType.fromId(c.type()).orElseThrow();
        switch (t) {
            case DIGITAL_CLOCK -> {
                CheckBox h24 = new CheckBox("24-hour time"); h24.setSelected(Boolean.parseBoolean(c.setting("hour24", "true")));
                CheckBox secs = new CheckBox("Show seconds"); secs.setSelected(Boolean.parseBoolean(c.setting("seconds", "false")));
                extra.put("hour24", h24); extra.put("seconds", secs);
            }
            case TIMER -> { TextField m = new TextField(c.setting("minutes", "5")); m.setPromptText("minutes (1-999)"); extra.put("minutes", m); }
            case COUNTDOWN -> {
                TextField title = new TextField(c.setting("title", "")); title.setPromptText("Event name");
                TextField target = new TextField(c.setting("target", "")); target.setPromptText("yyyy-MM-ddTHH:mm  e.g. 2026-12-31T00:00");
                extra.put("title", title); extra.put("target", target);
            }
            default -> { }
        }
        for (var e : extra.entrySet()) grid.addRow(row++, new Label(e.getKey()), e.getValue());
        Label error = new Label();
        error.getStyleClass().add("drift-error-text");
        HBox buttons = new HBox(8, Fx.button("Apply", () -> {
            try {
                WidgetConfig n = h.cfg.withOpacity(Math.round(opacity.getValue() * 100) / 100.0).withLayout(layout.getValue()).withShowLabel(label.isSelected())
                        .withRefresh(refresh.getValue()).withSize(w.getValue(), hh.getValue());
                for (var e : extra.entrySet()) {
                    String v = e.getValue() instanceof CheckBox cb ? Boolean.toString(cb.isSelected()) : ((TextField) e.getValue()).getText().strip();
                    n = n.withSetting(e.getKey(), v);
                }
                List<String> errors = n.validate();
                if (!errors.isEmpty()) { error.setText(String.join("; ", errors)); return; }
                saveConfig(h, n);
                reload();
                dlg.close();
            } catch (RuntimeException ex) { error.setText(ex.getMessage()); }
        }, "primary"), Fx.button("Cancel", dlg::close));
        VBox root = new VBox(10, grid, error, buttons);
        root.setPadding(new Insets(0, 14, 14, 14));
        Scene scene = new Scene(root);
        theme.register(scene);
        dlg.setScene(scene);
        dlg.show();
    }

    private static List<Rect> screens() {
        List<Rect> out = new ArrayList<>();
        for (Screen s : Screen.getScreens()) { var b = s.getVisualBounds(); out.add(new Rect(b.getMinX(), b.getMinY(), b.getWidth(), b.getHeight())); }
        return out;
    }

    private static Rect primary() { var b = Screen.getPrimary().getVisualBounds(); return new Rect(b.getMinX(), b.getMinY(), b.getWidth(), b.getHeight()); }
}
