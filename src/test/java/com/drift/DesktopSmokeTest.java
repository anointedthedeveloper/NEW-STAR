package com.drift;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.drift.core.DriftEvent;
import com.drift.persistence.KvStore;
import com.drift.ui.FloatingWindow;
import com.drift.ui.SettingsWindow;
import com.drift.ui.ThemeApplier;
import com.drift.ui.UiServices;
import com.drift.ui.WidgetManager;
import com.drift.ui.state.InterfaceState;
import com.drift.widgets.WidgetConfig;
import com.drift.widgets.WidgetLayout;
import com.drift.widgets.WidgetType;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.image.WritableImage;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * Real desktop tests: boots the full Spring context and creates the actual JavaFX windows. Needs a display
 * (run with `xvfb-run -a mvn test -Dtest=DesktopSmokeTest` on Linux); skipped automatically when none is available.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class DesktopSmokeTest {
    static ConfigurableApplicationContext ctx;
    static UiServices svc;
    static ThemeApplier theme;
    static boolean fx;
    static Path shots;

    @BeforeAll
    static void boot() throws Exception {
        Path home = Files.createTempDirectory("drift-desktop-test");
        System.setProperty("drift.home", home.toString());
        ctx = new SpringApplicationBuilder(DriftApplication.class).web(WebApplicationType.NONE).headless(true).run();
        svc = ctx.getBean(UiServices.class);
        shots = Path.of("target", "screenshots");
        Files.createDirectories(shots);
        if (System.getenv("DISPLAY") != null || System.getenv("WAYLAND_DISPLAY") != null) {
            CountDownLatch up = new CountDownLatch(1);
            try {
                Platform.startup(up::countDown);
                fx = up.await(30, TimeUnit.SECONDS);
                Platform.setImplicitExit(false);   // same as the real app: hiding the last window must not end the toolkit
            } catch (IllegalStateException alreadyStarted) {
                fx = true;
                Platform.setImplicitExit(false);
            } catch (Throwable t) {
                fx = false;
            }
        }
    }

    @AfterAll
    static void shutdown() { if (ctx != null) ctx.close(); }

    static <T> T onFx(Supplier<T> s) throws Exception {
        CompletableFuture<T> f = new CompletableFuture<>();
        Platform.runLater(() -> { try { f.complete(s.get()); } catch (Throwable t) { f.completeExceptionally(t); } });
        try {
            return f.get(20, TimeUnit.SECONDS);
        } catch (java.util.concurrent.TimeoutException e) {
            Thread.getAllStackTraces().forEach((t, st) -> { if (t.getName().contains("JavaFX")) { System.err.println("FX THREAD STACK: " + t.getName()); for (StackTraceElement el : st) System.err.println("    at " + el); } });
            throw e;
        }
    }

    static void settle() throws Exception { onFx(() -> null); onFx(() -> null); }

    static void snapshot(Scene scene, String name) throws Exception {
        onFx(() -> {
            try {
                WritableImage img = scene.snapshot(null);
                int w = (int) img.getWidth(), h = (int) img.getHeight();
                java.awt.image.BufferedImage bi = new java.awt.image.BufferedImage(w, h, java.awt.image.BufferedImage.TYPE_INT_ARGB);
                for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) bi.setRGB(x, y, img.getPixelReader().getArgb(x, y));
                javax.imageio.ImageIO.write(bi, "png", shots.resolve(name + ".png").toFile());
            } catch (java.io.IOException e) { throw new IllegalStateException(e); }
            return null;
        });
    }

    @Test @Order(0) void springContextStartsWithAllServices() {
        assertThat(svc.settings.get().floating().enabled()).isTrue();
        assertThat(svc.registry.all()).extracting("id").contains("app.open", "folder.open", "dev.run", "media.control", "dictation.start", "learn.remember", "widget.show");
        assertThat(ctx.getBean(com.drift.persistence.Database.class)).isNotNull();
        assertThat(svc.voice.providerStatus().state()).isIn(com.drift.speech.SpeechProvider.State.MODEL_MISSING, com.drift.speech.SpeechProvider.State.READY);
        if (!com.drift.windows.PlatformInfo.isWindows()) assertThat(svc.media.availability().available()).isFalse(); // honest "unavailable" off Windows
        assertThat(svc.monitor.enabled()).isFalse();
    }

    @Test @Order(1) void floatingWindowStatesTransitionFromRealEvents() throws Exception {
        assumeTrue(fx, "no display");
        theme = onFx(() -> new ThemeApplier(svc.themes, svc.bus));
        FloatingWindow fw = onFx(() -> new FloatingWindow(svc, theme, () -> { }));
        fw.show();
        settle();
        assertThat(onFx(() -> fw.stage().isShowing())).isTrue();
        assertThat(onFx(() -> fw.stage().getTitle())).isEqualTo(FloatingWindow.TITLE);
        assertThat(fw.model().state()).isEqualTo(InterfaceState.IDLE);
        double compactHeight = onFx(() -> fw.stage().getHeight());
        Thread.sleep(600); settle();
        snapshot(fw.stage().getScene(), "01-idle");

        svc.bus.publish(new DriftEvent.ListeningStarted("COMMAND"));
        for (int i = 0; i < 40; i++) svc.bus.publish(new DriftEvent.AudioLevel(0.1 + 0.8 * Math.abs(Math.sin(i / 3.0))));
        svc.bus.publish(new DriftEvent.PartialTranscript("open my downloads"));
        settle();
        assertThat(fw.model().state()).isEqualTo(InterfaceState.TRANSCRIBING);
        assertThat(onFx(() -> fw.stage().getHeight())).isGreaterThan(compactHeight);
        snapshot(fw.stage().getScene(), "02-transcribing");

        svc.bus.publish(new DriftEvent.ListeningStopped());
        svc.bus.publish(new DriftEvent.CommandPlanned("run maven clean", "Maven clean in demo"));
        svc.bus.publish(new DriftEvent.ConfirmationRequested("p1", "Run `mvn -B clean` in /home/me/demo?"));
        settle();
        assertThat(fw.model().pendingId()).isEqualTo("p1");
        snapshot(fw.stage().getScene(), "03-confirm");

        svc.bus.publish(new DriftEvent.ExecutionStarted("dev.run", "Maven tests"));
        settle();
        assertThat(fw.model().state()).isEqualTo(InterfaceState.EXECUTING);
        snapshot(fw.stage().getScene(), "04-executing");

        svc.bus.publish(new DriftEvent.ExecutionFinished("dev.run", true, "Maven tests succeeded (exit code 0)."));
        settle();
        assertThat(fw.model().state()).isEqualTo(InterfaceState.NOTIFICATION);
        snapshot(fw.stage().getScene(), "05-notification");

        svc.bus.publish(new DriftEvent.Failure("Microphone unavailable", "Cannot open the microphone: denied", "Choose another microphone in Settings"));
        settle();
        assertThat(fw.model().state()).isEqualTo(InterfaceState.ERROR);
        Thread.sleep(300); settle();
        // the window must be tall enough to show the whole panel (no clipped wrapped text)
        assertThat(onFx(() -> fw.stage().getHeight())).isGreaterThanOrEqualTo(onFx(() -> fw.stage().getScene().getRoot().prefHeight(-1)) - 2);
        snapshot(fw.stage().getScene(), "06-error");
        fw.controller().dismiss();
        settle();
        assertThat(fw.model().state()).isEqualTo(InterfaceState.IDLE);

        svc.bus.publish(new DriftEvent.MediaChanged("Midnight City", "M83", "PLAYING"));
        settle();
        assertThat(fw.model().state()).isEqualTo(InterfaceState.MUSIC);
        snapshot(fw.stage().getScene(), "07-music");

        svc.bus.publish(new DriftEvent.MediaChanged(null, null, "NONE"));
        svc.bus.publish(new DriftEvent.AssistantOpened());
        svc.bus.publish(new DriftEvent.DictationChanged("PREVIEW", "Untitled - Notepad", "Hello, world."));
        settle();
        snapshot(fw.stage().getScene(), "08-assistant-dictation");

        // events from a background thread must be marshalled safely onto the UI thread
        Thread t = new Thread(() -> { for (int i = 0; i < 200; i++) svc.bus.publish(new DriftEvent.AudioLevel(0.5)); });
        t.start(); t.join();
        settle();

        // hide / toggle
        fw.hide(); settle();
        assertThat(onFx(() -> fw.stage().isShowing())).isFalse();
        fw.toggle(); settle();
        assertThat(onFx(() -> fw.stage().isShowing())).isTrue();

        // window stays inside a screen
        var b = javafx.stage.Screen.getPrimary().getVisualBounds();
        double x = onFx(() -> fw.stage().getX()), y = onFx(() -> fw.stage().getY());
        assertThat(x).isBetween(b.getMinX() - 1, b.getMaxX());
        assertThat(y).isBetween(b.getMinY() - 1, b.getMaxY());
        onFx(() -> { fw.close(); return null; });
    }

    @Test @Order(2) void settingsChangesApplyToTheLiveWindow() throws Exception {
        assumeTrue(fx, "no display");
        FloatingWindow fw = onFx(() -> new FloatingWindow(svc, theme, () -> { }));
        fw.show(); settle();
        svc.settings.update(s -> s.withFloating(s.floating().withOpacity(0.6).withAlwaysOnTop(false)));
        settle();
        assertThat(onFx(() -> fw.stage().getOpacity())).isEqualTo(0.6);
        assertThat(onFx(() -> fw.stage().isAlwaysOnTop())).isFalse();
        svc.settings.update(s -> s.withFloating(s.floating().withEnabled(false)));
        settle();
        assertThat(onFx(() -> fw.stage().isShowing())).isFalse();   // "disable the floating interface" setting
        svc.settings.resetToDefaults();
        onFx(() -> { fw.close(); return null; });
    }

    @Test @Order(3) void widgetsCreateRunAndPersistLayout() throws Exception {
        assumeTrue(fx, "no display");
        KvStore kv = ctx.getBean(KvStore.class);
        WidgetManager wm = onFx(() -> new WidgetManager(svc, theme, kv, () -> { }));
        WidgetLayout layout = new WidgetLayout("Default", List.of(
                WidgetConfig.create(WidgetType.DIGITAL_CLOCK, 100, 100).withSetting("seconds", "true"),
                WidgetConfig.create(WidgetType.ANALOG_CLOCK, 300, 100),
                WidgetConfig.create(WidgetType.CALENDAR, 500, 100),
                WidgetConfig.create(WidgetType.NOTES, 100, 300).withSetting("text", "buy milk"),
                WidgetConfig.create(WidgetType.TIMER, 300, 300),
                WidgetConfig.create(WidgetType.COUNTDOWN, 500, 300),
                WidgetConfig.create(WidgetType.WEATHER, 100, 450),
                WidgetConfig.create(WidgetType.SYSTEM_MONITOR, 300, 450),
                WidgetConfig.create(WidgetType.BATTERY, 500, 450),
                WidgetConfig.create(WidgetType.MEDIA_PLAYER, 100, 600),
                WidgetConfig.create(WidgetType.QUICK_LAUNCH, 300, 600)));
        svc.layouts.save(layout);
        svc.layouts.setActive("Default");
        wm.reload();
        settle();
        Thread.sleep(500);
        settle();
        assertThat(wm.openStages()).isEqualTo(11);

        // the digital clock shows the actual system time (within a couple of seconds)
        javafx.stage.Window clockWindow = onFx(() -> javafx.stage.Window.getWindows().stream()
                .filter(w -> w instanceof javafx.stage.Stage s && s.getTitle().equals("Drift Widget " + layout.widgets().get(0).id())).findFirst().orElseThrow());
        String shown = onFx(() -> ((Label) findByStyle(clockWindow.getScene().getRoot(), "widget-big")).getText());
        java.time.LocalTime parsed = java.time.LocalTime.parse(shown);
        long diff = Math.abs(java.time.Duration.between(parsed, java.time.LocalTime.now()).getSeconds());
        assertThat(diff).isLessThanOrEqualTo(3);

        // honest empty states
        String weather = onFx(() -> collectText(javafx.stage.Window.getWindows().stream().filter(w -> w instanceof javafx.stage.Stage s && s.getTitle().equals("Drift Widget " + layout.widgets().get(6).id())).findFirst().orElseThrow().getScene().getRoot()));
        assertThat(weather).containsIgnoringCase("weather is off");
        String monitor = onFx(() -> collectText(javafx.stage.Window.getWindows().stream().filter(w -> w instanceof javafx.stage.Stage s && s.getTitle().equals("Drift Widget " + layout.widgets().get(7).id())).findFirst().orElseThrow().getScene().getRoot()));
        assertThat(monitor).containsIgnoringCase("monitoring is off");
        String media = onFx(() -> collectText(javafx.stage.Window.getWindows().stream().filter(w -> w instanceof javafx.stage.Stage s && s.getTitle().equals("Drift Widget " + layout.widgets().get(9).id())).findFirst().orElseThrow().getScene().getRoot()));
        assertThat(media).containsIgnoringCase("unavailable");
        for (int i = 0; i < 11; i++) {
            final int idx = i;
            javafx.stage.Window w = onFx(() -> javafx.stage.Window.getWindows().stream().filter(x -> x instanceof javafx.stage.Stage s && s.getTitle().equals("Drift Widget " + layout.widgets().get(idx).id())).findFirst().orElseThrow());
            snapshot(w.getScene(), "widget-" + layout.widgets().get(i).type());
        }

        // removal and persistence
        wm.remove(layout.widgets().get(0).id());
        Thread.sleep(500); settle();
        assertThat(wm.openStages()).isEqualTo(10);
        assertThat(svc.layouts.active().widgets()).hasSize(10);
        onFx(() -> { wm.closeAll(); return null; });
        settle();
        assertThat(wm.openStages()).isZero();
    }

    @Test @Order(4) void everySettingsSectionBuilds() throws Exception {
        assumeTrue(fx, "no display");
        KvStore kv = ctx.getBean(KvStore.class);
        WidgetManager wm = onFx(() -> new WidgetManager(svc, theme, kv, () -> { }));
        SettingsWindow sw = onFx(() -> new SettingsWindow(svc, theme, kv, wm));
        for (String section : SettingsWindow.SECTIONS_FOR_TEST()) {
            onFx(() -> { sw.show(section); return null; });
            settle();
            Scene sc = onFx(() -> sw.stage().getScene());
            assertThat(onFx(() -> collectText(sc.getRoot()))).as(section).contains(section);
            if (true) snapshot(sc, "settings-" + section.replace(' ', '-'));
        }
        onFx(() -> { sw.close(); return null; });
    }

    @Test @Order(5) void lightThemeRendersEverywhere() throws Exception {
        assumeTrue(fx, "no display");
        svc.themes.apply("Drift Light");
        settle();
        FloatingWindow fw = onFx(() -> new FloatingWindow(svc, theme, () -> { }));
        fw.show(); settle();
        svc.bus.publish(new DriftEvent.ConfirmationRequested("p9", "Run `mvn -B clean` in /home/me/demo?"));
        settle();
        Thread.sleep(600); settle();
        snapshot(fw.stage().getScene(), "light-confirm");
        KvStore kv = ctx.getBean(KvStore.class);
        WidgetManager wm = onFx(() -> new WidgetManager(svc, theme, kv, () -> { }));
        SettingsWindow sw = onFx(() -> new SettingsWindow(svc, theme, kv, wm));
        onFx(() -> { sw.show("Appearance"); return null; });
        settle();
        snapshot(onFx(() -> sw.stage().getScene()), "light-settings");
        onFx(() -> { sw.close(); fw.close(); return null; });
        svc.themes.apply("Drift Dark");
    }

    static void waitFor(java.util.function.BooleanSupplier cond, long millis) throws Exception {
        long end = System.currentTimeMillis() + millis;
        while (!cond.getAsBoolean() && System.currentTimeMillis() < end) Thread.sleep(50);
    }

    @Test @Order(6) void typingACommandAndPressingTheMicWorkEndToEnd() throws Exception {
        assumeTrue(fx, "no display");
        FloatingWindow fw = onFx(() -> new FloatingWindow(svc, theme, () -> { }));
        fw.show(); settle();
        svc.bus.publish(new DriftEvent.AssistantOpened());
        settle();
        assertThat(fw.model().state()).isEqualTo(InterfaceState.ASSISTANT);

        // type into the assistant box and press Enter
        javafx.scene.control.TextField input = onFx(() -> (javafx.scene.control.TextField) lookupType(fw.stage().getScene().getRoot(), javafx.scene.control.TextField.class));
        onFx(() -> { input.setText("what song is playing"); input.fireEvent(new javafx.event.ActionEvent()); return null; });
        waitFor(() -> fw.model().state() == InterfaceState.NOTIFICATION, 8000);
        assertThat(fw.model().state()).isEqualTo(InterfaceState.NOTIFICATION);
        assertThat(fw.model().detail()).containsIgnoringCase("can't read media sessions");

        // an unsupported request is explained, not invented
        svc.bus.publish(new DriftEvent.AssistantOpened());
        settle();
        javafx.scene.control.TextField input2 = onFx(() -> (javafx.scene.control.TextField) lookupType(fw.stage().getScene().getRoot(), javafx.scene.control.TextField.class));
        onFx(() -> { input2.setText("run command del everything"); input2.fireEvent(new javafx.event.ActionEvent()); return null; });
        waitFor(() -> fw.model().detail() != null && fw.model().detail().contains("never runs arbitrary"), 8000);
        assertThat(fw.model().detail()).contains("never runs arbitrary commands");
        assertThat(fw.model().ok()).isFalse();
        fw.controller().dismiss();
        settle();

        // pressing the mic with no speech model installed gives a clear error state and never opens the microphone
        javafx.scene.control.Button mic = onFx(() -> (javafx.scene.control.Button) lookupMic(fw.stage().getScene().getRoot()));
        onFx(() -> { mic.fireEvent(new javafx.scene.input.MouseEvent(javafx.scene.input.MouseEvent.MOUSE_PRESSED, 1, 1, 1, 1, javafx.scene.input.MouseButton.PRIMARY, 1,
                false, false, false, false, true, false, false, true, false, true, null)); return null; });
        waitFor(() -> fw.model().state() == InterfaceState.ERROR, 8000);
        assertThat(fw.model().state()).isEqualTo(InterfaceState.ERROR);
        assertThat(fw.model().title()).contains("Speech recognition unavailable");
        assertThat(fw.model().recovery()).isNotBlank();
        assertThat(fw.model().micActive()).isFalse();
        assertThat(svc.voice.isListening()).isFalse();
        Thread.sleep(300);
        snapshot(fw.stage().getScene(), "09-mic-error");
        onFx(() -> { fw.close(); return null; });
    }

    static Node lookupType(Node root, Class<? extends Node> type) {
        if (type.isInstance(root)) return root;
        if (root instanceof javafx.scene.Parent p) for (Node c : p.getChildrenUnmodifiable()) { Node r = lookupType(c, type); if (r != null) return r; }
        return null;
    }

    static Node lookupMic(Node root) {
        if (root instanceof javafx.scene.control.Button b && b.getTooltip() != null && b.getTooltip().getText().startsWith("Hold to talk")) return b;
        if (root instanceof javafx.scene.Parent p) for (Node c : p.getChildrenUnmodifiable()) { Node r = lookupMic(c); if (r != null) return r; }
        return null;
    }

    static Node findByStyle(Node root, String style) {
        if (root.getStyleClass().contains(style)) return root;
        if (root instanceof javafx.scene.Parent p) for (Node c : p.getChildrenUnmodifiable()) { Node r = findByStyle(c, style); if (r != null) return r; }
        return null;
    }

    static String collectText(Node n) {
        StringBuilder sb = new StringBuilder();
        if (n instanceof javafx.scene.control.Labeled l && l.getText() != null) sb.append(l.getText()).append('\n');
        if (n instanceof javafx.scene.control.TextInputControl t && t.getText() != null) sb.append(t.getText()).append('\n');
        if (n instanceof javafx.scene.control.ScrollPane sp && sp.getContent() != null) sb.append(collectText(sp.getContent()));
        if (n instanceof javafx.scene.Parent p) for (Node c : p.getChildrenUnmodifiable()) sb.append(collectText(c));
        return sb.toString();
    }
}
