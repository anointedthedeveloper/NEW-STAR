package com.drift.ui;

import com.drift.actions.ActionRequest;
import com.drift.actions.Source;
import com.drift.applications.AppEntry;
import com.drift.core.DriftEvent;
import com.drift.media.MediaSession;
import com.drift.monitoring.SystemSnapshot;
import com.drift.widgets.CalendarModel;
import com.drift.widgets.WeatherService;
import com.drift.widgets.WidgetConfig;
import com.drift.widgets.WidgetType;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;
import javafx.animation.KeyFrame;
import javafx.animation.PauseTransition;
import javafx.animation.Timeline;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TextArea;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.TextAlignment;
import javafx.util.Duration;

/** JavaFX content of each widget kind. Every view is a {@link WidgetView} with its own lifecycle (start/stop). */
public final class WidgetViews {
    private WidgetViews() {}

    public interface WidgetView {
        Node node();
        void start();
        void stop();
        default void resized(double w, double h) { }
    }

    /** Services a widget may use; the save callback persists changed widget settings (e.g. notes text). */
    public record Ctx(UiServices svc, WeatherService weather, Clock clock, ExecutorService background, Consumer<WidgetConfig> save) {}

    public static WidgetView create(WidgetConfig c, Ctx ctx) {
        WidgetType t = WidgetType.fromId(c.type()).orElseThrow();
        return switch (t) {
            case DIGITAL_CLOCK -> new DigitalClock(c, ctx);
            case ANALOG_CLOCK -> new AnalogClock(c, ctx);
            case CALENDAR -> new CalendarView(c, ctx);
            case WEATHER -> new WeatherView(c, ctx);
            case SYSTEM_MONITOR -> new MonitorView(c, ctx);
            case BATTERY -> new BatteryView(c, ctx);
            case MEDIA_PLAYER -> new MediaView(c, ctx);
            case QUICK_LAUNCH -> new QuickLaunchView(c, ctx);
            case NOTES -> new NotesView(c, ctx);
            case TIMER -> new TimerView(c, ctx);
            case COUNTDOWN -> new CountdownView(c, ctx);
        };
    }

    private abstract static class Base implements WidgetView {
        final WidgetConfig cfg;
        final Ctx ctx;
        final VBox root = new VBox(4);
        Timeline ticker;

        Base(WidgetConfig cfg, Ctx ctx) {
            this.cfg = cfg;
            this.ctx = ctx;
            root.setAlignment(Pos.CENTER);
            if (cfg.showLabel()) {
                Label l = Fx.label(WidgetType.fromId(cfg.type()).orElseThrow().displayName, "widget-label");
                root.getChildren().add(l);
            }
        }

        boolean compact() { return "compact".equals(cfg.layout()); }

        void every(double seconds, Runnable r) {
            ticker = new Timeline(new KeyFrame(Duration.seconds(seconds), e -> r.run()));
            ticker.setCycleCount(Timeline.INDEFINITE);
            ticker.play();
            r.run();
        }

        @Override public Node node() { return root; }
        @Override public void stop() { if (ticker != null) ticker.stop(); }
    }

    // ---- clocks ----
    static final class DigitalClock extends Base {
        final Label time = Fx.label("", "widget-big");
        final Label date = Fx.label("", "drift-muted");
        DigitalClock(WidgetConfig c, Ctx ctx) { super(c, ctx); root.getChildren().add(time); if (!compact()) root.getChildren().add(date); }
        @Override public void start() {
            boolean h24 = Boolean.parseBoolean(cfg.setting("hour24", "true"));
            boolean secs = Boolean.parseBoolean(cfg.setting("seconds", "false"));
            every(secs ? 1 : Math.max(1, cfg.refreshSeconds()), () -> {
                ZonedDateTime now = ZonedDateTime.now(ctx.clock());
                time.setText(CalendarModel.time(now, h24, secs));
                date.setText(CalendarModel.date(now.toLocalDate()));
            });
        }
    }

    static final class AnalogClock extends Base {
        final Canvas canvas = new Canvas(120, 120);
        AnalogClock(WidgetConfig c, Ctx ctx) { super(c, ctx); root.getChildren().add(canvas); }
        @Override public void start() { every(1, this::draw); }
        @Override public void resized(double w, double h) {
            double s = Math.max(60, Math.min(w - 20, h - (cfg.showLabel() ? 40 : 20)));
            canvas.setWidth(s); canvas.setHeight(s); draw();
        }
        void draw() {
            GraphicsContext g = canvas.getGraphicsContext2D();
            double s = canvas.getWidth(), cx = s / 2, cy = s / 2, r = s / 2 - 4;
            g.clearRect(0, 0, s, s);
            g.setStroke(Color.web("#2B3340")); g.setLineWidth(2); g.strokeOval(cx - r, cy - r, 2 * r, 2 * r);
            g.setStroke(Color.web("#8C97A8"));
            for (int i = 0; i < 12; i++) {
                double a = Math.toRadians(i * 30 - 90), inner = r - (i % 3 == 0 ? 10 : 5);
                g.setLineWidth(i % 3 == 0 ? 2 : 1);
                g.strokeLine(cx + Math.cos(a) * inner, cy + Math.sin(a) * inner, cx + Math.cos(a) * (r - 2), cy + Math.sin(a) * (r - 2));
            }
            ZonedDateTime n = ZonedDateTime.now(ctx.clock());
            hand(g, cx, cy, (n.getHour() % 12 + n.getMinute() / 60.0) * 30, r * 0.5, 3.5, Color.web("#E8ECF3"));
            hand(g, cx, cy, (n.getMinute() + n.getSecond() / 60.0) * 6, r * 0.75, 2.5, Color.web("#E8ECF3"));
            hand(g, cx, cy, n.getSecond() * 6, r * 0.85, 1.2, Color.web("#38BDF8"));
        }
        static void hand(GraphicsContext g, double cx, double cy, double deg, double len, double w, Color c) {
            double a = Math.toRadians(deg - 90);
            g.setStroke(c); g.setLineWidth(w);
            g.strokeLine(cx, cy, cx + Math.cos(a) * len, cy + Math.sin(a) * len);
        }
    }

    static final class CalendarView extends Base {
        final GridPane grid = new GridPane();
        final Label heading = Fx.label("", "drift-title");
        YearMonth shown;
        CalendarView(WidgetConfig c, Ctx ctx) {
            super(c, ctx);
            grid.setHgap(4); grid.setVgap(2); grid.setAlignment(Pos.CENTER);
            Button prev = Fx.button("‹", () -> { shown = shown.minusMonths(1); draw(); }, "drift-icon-button");
            Button next = Fx.button("›", () -> { shown = shown.plusMonths(1); draw(); }, "drift-icon-button");
            root.getChildren().addAll(new HBox(8, prev, heading, next), grid);
            ((HBox) root.getChildren().get(root.getChildren().size() - 2)).setAlignment(Pos.CENTER);
        }
        @Override public void start() { shown = YearMonth.now(ctx.clock()); every(Math.max(30, cfg.refreshSeconds()), this::draw); }
        void draw() {
            grid.getChildren().clear();
            LocalDate today = LocalDate.now(ctx.clock());
            heading.setText(shown.getMonth().getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.getDefault()) + " " + shown.getYear());
            DayOfWeek first = DayOfWeek.MONDAY;
            for (int i = 0; i < 7; i++) {
                Label h = Fx.label(first.plus(i).getDisplayName(java.time.format.TextStyle.NARROW, java.util.Locale.getDefault()), "widget-label");
                h.setMinWidth(22); h.setAlignment(Pos.CENTER); grid.add(h, i, 0);
            }
            List<int[]> weeks = CalendarModel.weeks(shown, first);
            for (int r = 0; r < weeks.size(); r++) for (int cI = 0; cI < 7; cI++) {
                int d = weeks.get(r)[cI];
                if (d == 0) continue;
                Label l = new Label(Integer.toString(d));
                l.setMinWidth(22); l.setAlignment(Pos.CENTER);
                if (shown.atDay(d).equals(today)) l.getStyleClass().add("calendar-today");
                grid.add(l, cI, r + 1);
            }
        }
    }

    // ---- data widgets ----
    static final class WeatherView extends Base {
        final Label main = Fx.label("…", "widget-big");
        final Label sub = Fx.label("", "drift-muted");
        WeatherView(WidgetConfig c, Ctx ctx) { super(c, ctx); sub.setWrapText(true); sub.setTextAlignment(TextAlignment.CENTER); root.getChildren().addAll(main, sub); }
        @Override public void start() {
            every(Math.max(60, cfg.refreshSeconds()), () -> ctx.background().submit(() -> {
                WeatherService.State s = ctx.weather().fetch();
                Fx.ui(() -> {
                    if (s instanceof WeatherService.State.Data d) {
                        main.setText(String.format("%.0f%s", d.temperature(), d.unit()));
                        sub.setText(d.description() + (compact() ? "" : String.format(" · wind %.0f %s", d.windSpeed(), d.windUnit())));
                    } else if (s instanceof WeatherService.State.Unavailable u) {
                        main.setText("—");
                        sub.setText(u.reason());
                    }
                });
            }));
        }
    }

    static final class MonitorView extends Base {
        final ProgressBar cpu = new ProgressBar(0), mem = new ProgressBar(0);
        final Label cpuL = Fx.label("CPU", "drift-label"), memL = Fx.label("Memory", "drift-label"), note = Fx.label("", "drift-muted");
        MonitorView(WidgetConfig c, Ctx ctx) {
            super(c, ctx);
            cpu.setMaxWidth(Double.MAX_VALUE); mem.setMaxWidth(Double.MAX_VALUE); note.setWrapText(true);
            root.getChildren().addAll(cpuL, cpu, memL, mem, note);
            root.setAlignment(Pos.CENTER_LEFT);
        }
        @Override public void start() {
            every(Math.max(1, cfg.refreshSeconds()), () -> {
                var snap = ctx.svc().monitor.latest();
                if (snap.isEmpty()) {
                    cpu.setVisible(false); mem.setVisible(false); cpuL.setVisible(false); memL.setVisible(false);
                    note.setText(ctx.svc().monitor.enabled() ? "Collecting first sample…" : "System monitoring is off. Turn it on in Settings > Diagnostics.");
                    return;
                }
                SystemSnapshot s = snap.get();
                cpu.setVisible(true); mem.setVisible(true); cpuL.setVisible(true); memL.setVisible(true);
                s.cpuPercent().ifPresentOrElse(v -> { cpu.setProgress(v / 100); cpuL.setText(String.format("CPU %.0f%%", v)); }, () -> { cpu.setProgress(0); cpuL.setText("CPU unavailable"); });
                double frac = s.memoryTotalBytes() == 0 ? 0 : (double) s.memoryUsedBytes() / s.memoryTotalBytes();
                mem.setProgress(frac);
                memL.setText(String.format("Memory %.1f / %.1f GB", s.memoryUsedBytes() / 1e9, s.memoryTotalBytes() / 1e9));
                StringBuilder n = new StringBuilder();
                if (!compact()) {
                    s.networkUp().ifPresent(up -> n.append("Network: ").append(up ? "connected" : "disconnected"));
                    for (var d : s.disks().stream().limit(2).toList()) n.append("\n").append(d.name()).append(String.format(" %.0f%% used", 100.0 * d.usedBytes() / d.totalBytes()));
                }
                note.setText(n.toString());
            });
        }
    }

    static final class BatteryView extends Base {
        final Label pct = Fx.label("—", "widget-big");
        final Label sub = Fx.label("", "drift-muted");
        BatteryView(WidgetConfig c, Ctx ctx) { super(c, ctx); sub.setWrapText(true); sub.setTextAlignment(TextAlignment.CENTER); root.getChildren().addAll(pct, sub); }
        @Override public void start() {
            every(Math.max(5, cfg.refreshSeconds()), () -> {
                var snap = ctx.svc().monitor.latest();
                if (snap.isEmpty()) { pct.setText("—"); sub.setText(ctx.svc().monitor.enabled() ? "Reading…" : "Monitoring is off (Settings > Diagnostics)"); return; }
                snap.get().battery().ifPresentOrElse(b -> { pct.setText(String.format("%.0f%%", b.percent())); sub.setText(b.charging() ? "Charging" : "On battery"); },
                        () -> { pct.setText("—"); sub.setText("No battery detected"); });
            });
        }
    }

    static final class MediaView extends Base {
        final Label title = Fx.label("", "drift-title"), artist = Fx.label("", "drift-muted");
        final HBox controls = new HBox(6);
        final ProgressBar progress = new ProgressBar(0);
        Runnable release = () -> { };
        MediaView(WidgetConfig c, Ctx ctx) {
            super(c, ctx);
            title.setWrapText(true); artist.setWrapText(true); progress.setMaxWidth(Double.MAX_VALUE);
            controls.setAlignment(Pos.CENTER);
            root.getChildren().addAll(title, artist, progress, controls);
        }
        @Override public void start() {
            release = ctx.svc().media.observe();
            every(Math.max(1, cfg.refreshSeconds()), this::refresh);
        }
        void refresh() {
            var av = ctx.svc().media.availability();
            controls.getChildren().clear();
            if (!av.available()) { title.setText("Media unavailable"); artist.setText(av.detail()); progress.setVisible(false); return; }
            var cur = ctx.svc().media.current();
            if (cur.isEmpty()) { title.setText("Nothing playing"); artist.setText("No media session is active"); progress.setVisible(false); return; }
            MediaSession s = cur.get();
            title.setText(s.title() == null ? "Unknown title" : s.title());
            artist.setText((s.artist() == null ? "" : s.artist()) + (compact() || s.album() == null ? "" : " · " + s.album()));
            boolean known = s.durationSeconds() > 0 && s.positionSeconds() >= 0;
            progress.setVisible(known && !compact());
            if (known) progress.setProgress(Math.min(1, s.positionSeconds() / s.durationSeconds()));
            boolean playing = s.status() == MediaSession.Status.PLAYING;
            controls.getChildren().addAll(button("⏮", "previous", s.canPrevious()), button(playing ? "⏸" : "▶", playing ? "pause" : "play", s.canPlayPause()), button("⏭", "next", s.canNext()));
        }
        Button button(String glyph, String action, boolean enabled) {
            Button b = Fx.button(glyph, () -> ctx.background().submit(() -> ctx.svc().orchestrator.handle(
                    new ActionRequest("media.control", Map.of("action", action), "media widget", Source.UI, null, "Media " + action), "media widget")));
            b.setDisable(!enabled);
            return b;
        }
        @Override public void stop() { super.stop(); release.run(); }
    }

    static final class QuickLaunchView extends Base {
        final FlowPane pane = new FlowPane(6, 6);
        QuickLaunchView(WidgetConfig c, Ctx ctx) { super(c, ctx); pane.setAlignment(Pos.CENTER); root.getChildren().add(pane); }
        @Override public void start() { every(Math.max(5, cfg.refreshSeconds() * 5), this::refresh); }
        void refresh() {
            pane.getChildren().clear();
            List<AppEntry> favs = ctx.svc().apps.favorites();
            if (favs.isEmpty()) { Label l = Fx.label("No favourites yet. Mark applications as favourites in Settings > Application aliases.", "drift-muted"); l.setWrapText(true); l.prefWidthProperty().bind(pane.widthProperty()); pane.getChildren().add(l); return; }
            for (AppEntry a : favs.stream().limit(12).toList()) {
                Button b = Fx.button(a.name(), () -> ctx.background().submit(() -> ctx.svc().orchestrator.handle(
                        new ActionRequest("app.open", Map.of("appId", a.id()), "quick launch", Source.UI, null, "Open " + a.name()), "quick launch")));
                b.setMaxWidth(160);
                pane.getChildren().add(b);
            }
        }
    }

    static final class NotesView extends Base {
        final TextArea area = new TextArea();
        final PauseTransition debounce = new PauseTransition(Duration.millis(700));
        NotesView(WidgetConfig c, Ctx ctx) {
            super(c, ctx);
            area.setWrapText(true);
            area.setText(c.setting("text", ""));
            area.setPromptText("Notes…");
            area.textProperty().addListener((o, a, b) -> { if (b.length() > WidgetConfig.MAX_SETTING_VALUE) area.setText(a); else debounce.playFromStart(); });
            debounce.setOnFinished(e -> persist());
            VBox.setVgrow(area, javafx.scene.layout.Priority.ALWAYS);
            root.getChildren().add(area);
        }
        void persist() { ctx.save().accept(cfg.withSetting("text", area.getText())); }
        @Override public void start() { }
        @Override public void stop() { debounce.stop(); persist(); }
    }

    static final class TimerView extends Base {
        final Label display = Fx.label("", "widget-big");
        final Button startPause;
        long remainingNanos, startedAt;
        boolean running;
        final long total;
        TimerView(WidgetConfig c, Ctx ctx) {
            super(c, ctx);
            int minutes = 5;
            try { minutes = Math.max(1, Math.min(999, Integer.parseInt(c.setting("minutes", "5")))); } catch (NumberFormatException ignored) { /* default */ }
            total = minutes * 60_000_000_000L;
            remainingNanos = total;
            startPause = Fx.button("Start", this::toggle, "primary");
            Button reset = Fx.button("Reset", () -> { running = false; remainingNanos = total; startPause.setText("Start"); update(); });
            HBox row = new HBox(6, startPause, reset);
            row.setAlignment(Pos.CENTER);
            root.getChildren().addAll(display, row);
        }
        void toggle() {
            if (running) { remainingNanos -= System.nanoTime() - startedAt; running = false; startPause.setText("Resume"); }
            else if (remainingNanos > 0) { startedAt = System.nanoTime(); running = true; startPause.setText("Pause"); }
            update();
        }
        long left() { return running ? Math.max(0, remainingNanos - (System.nanoTime() - startedAt)) : remainingNanos; }
        void update() {
            long s = (left() + 999_999_999L) / 1_000_000_000L;
            display.setText(String.format("%02d:%02d", s / 60, s % 60));
            if (running && left() == 0) {
                running = false; remainingNanos = 0; startPause.setText("Start");
                ctx.svc().bus.publish(new DriftEvent.Notification("Timer", "Time's up!"));
            }
        }
        @Override public void start() { every(0.25, this::update); }
    }

    static final class CountdownView extends Base {
        final Label remaining = Fx.label("", "widget-big"), title = Fx.label("", "drift-muted");
        CountdownView(WidgetConfig c, Ctx ctx) { super(c, ctx); remaining.setWrapText(true); root.getChildren().addAll(remaining, title); }
        @Override public void start() {
            every(Math.max(1, cfg.refreshSeconds()), () -> {
                title.setText(cfg.setting("title", ""));
                String target = cfg.setting("target", "");
                if (target.isBlank()) { remaining.setText("Set a date"); title.setText("Right-click the widget › Settings"); return; }
                try {
                    ZonedDateTime t = java.time.LocalDateTime.parse(target).atZone(ctx.clock().getZone());
                    String r = CalendarModel.remaining(ZonedDateTime.now(ctx.clock()), t);
                    remaining.setText(r.isEmpty() ? "Reached" : r);
                } catch (java.time.format.DateTimeParseException e) {
                    remaining.setText("Invalid date");
                    title.setText("Use yyyy-MM-ddTHH:mm");
                }
            });
        }
    }
}
