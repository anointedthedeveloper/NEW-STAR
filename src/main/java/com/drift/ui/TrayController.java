package com.drift.ui;

import java.awt.AWTException;
import java.awt.MenuItem;
import java.awt.PopupMenu;
import java.awt.SystemTray;
import java.awt.TrayIcon;
import javafx.application.Platform;

/** System-tray presence (AWT SystemTray). Absent trays are reported, never silently ignored. */
public class TrayController {
    private TrayIcon icon;

    public static boolean supported() {
        try { return !java.awt.GraphicsEnvironment.isHeadless() && SystemTray.isSupported(); } catch (RuntimeException | LinkageError e) { return false; }
    }

    public boolean install(Runnable toggle, Runnable assistant, Runnable settings, Runnable quit) {
        if (!supported()) return false;
        PopupMenu menu = new PopupMenu();
        menu.add(item("Show / hide Drift", toggle));
        menu.add(item("Open assistant", assistant));
        menu.add(item("Settings", settings));
        menu.addSeparator();
        menu.add(item("Quit Drift", quit));
        int size = SystemTray.getSystemTray().getTrayIconSize().width;
        icon = new TrayIcon(DriftIcon.render(Math.max(16, size)), "Drift", menu);
        icon.setImageAutoSize(true);
        icon.addActionListener(e -> Platform.runLater(toggle));
        try {
            SystemTray.getSystemTray().add(icon);
            return true;
        } catch (AWTException e) {
            icon = null;
            return false;
        }
    }

    public void remove() {
        if (icon != null) { SystemTray.getSystemTray().remove(icon); icon = null; }
    }

    private static MenuItem item(String label, Runnable action) {
        MenuItem m = new MenuItem(label);
        m.addActionListener(e -> Platform.runLater(action));
        return m;
    }
}
