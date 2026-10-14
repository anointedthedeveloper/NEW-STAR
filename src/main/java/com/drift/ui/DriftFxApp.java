package com.drift.ui;

import com.drift.DriftApplication;
import com.drift.core.DriftEvent;
import com.drift.core.DriftPaths;
import com.drift.persistence.KvStore;
import com.drift.windows.PlatformInfo;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

/** JavaFX entry: boots the Spring context, then builds the floating interface, widgets, settings, tray and shortcuts. */
public class DriftFxApp extends Application {
    private static final Logger log = LoggerFactory.getLogger(DriftFxApp.class);
    private ConfigurableApplicationContext context;
    private FileLock instanceLock;
    private FloatingWindow floating;
    private SettingsWindow settingsWindow;
    private WidgetManager widgets;
    private HotkeyController hotkeys;
    private TrayController tray;
    private Runnable stopObserving = () -> { };
    private boolean quitting;

    @Override
    public void init() {
        System.setProperty("java.awt.headless", "false");
        DriftPaths paths = DriftPaths.resolve().ensureCreated();
        System.setProperty("drift.home", paths.home().toString());
        try {
            FileChannel ch = new RandomAccessFile(paths.home().resolve("instance.lock").toFile(), "rw").getChannel();
            instanceLock = ch.tryLock();
        } catch (IOException | java.nio.channels.OverlappingFileLockException e) {
            instanceLock = null;
        }
        if (instanceLock == null) {
            System.err.println("Drift is already running (see the tray icon). Exiting.");
            Platform.exit();
            return;
        }
        context = new SpringApplicationBuilder(DriftApplication.class).web(WebApplicationType.NONE).headless(false).run(getParameters().getRaw().toArray(new String[0]));
    }

    @Override
    public void start(Stage ignored) {
        if (context == null) { Platform.exit(); return; }
        Platform.setImplicitExit(false);
        UiServices svc = context.getBean(UiServices.class);
        KvStore kv = context.getBean(KvStore.class);
        ThemeApplier themeApplier = new ThemeApplier(svc.themes, svc.bus);
        floating = new FloatingWindow(svc, themeApplier, this::openSettings);
        widgets = new WidgetManager(svc, themeApplier, kv, this::openSettings);
        widgets.reload();
        svc.bus.subscribe(e -> { if (e instanceof DriftEvent.LayoutChanged) Fx.ui(widgets::reload); });
        hotkeys = new HotkeyController(svc, floating);
        hotkeys.rebind();
        settingsWindow = new SettingsWindow(svc, themeApplier, kv, widgets);

        boolean trayOk = svc.settings.get().startup().minimizeToTray() && TrayController.supported();
        if (trayOk) {
            tray = new TrayController();
            trayOk = tray.install(floating::toggle, () -> { floating.show(); svc.bus.publish(new DriftEvent.AssistantOpened()); }, this::openSettings, this::quit);
        }
        settingsWindow.setHideOnClose(trayOk);

        if (kv.getBool("media.enabled", true)) stopObserving = svc.media.observe();
        boolean background = getParameters().getRaw().contains("--background") || svc.settings.get().startup().startHidden();
        boolean firstRun = kv.get("firstRunDone").isEmpty();
        if (!background || !trayOk) floating.show();
        if (firstRun) {
            kv.put("firstRunDone", "true");
            settingsWindow.show("Voice recognition");
            svc.bus.publish(new DriftEvent.Notification("Welcome to Drift", "Install a speech model to enable voice input (Settings > Voice recognition)."));
        }
        log.info("Drift started on {} ({})", PlatformInfo.osDescription(), PlatformInfo.windowsEdition());
    }

    private void openSettings() { settingsWindow.show(null); }

    private void quit() {
        if (quitting) return;
        quitting = true;
        stopObserving.run();
        if (hotkeys != null) hotkeys.close();
        if (tray != null) tray.remove();
        if (widgets != null) widgets.closeAll();
        if (floating != null) floating.close();
        if (settingsWindow != null) settingsWindow.close();
        Platform.exit();
    }

    @Override
    public void stop() {
        if (context != null) context.close();
        try { if (instanceLock != null) instanceLock.release(); } catch (IOException ignored) { /* exiting anyway */ }
    }
}
