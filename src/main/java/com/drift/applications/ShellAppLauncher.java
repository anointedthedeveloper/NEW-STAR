package com.drift.applications;

import java.awt.Desktop;
import java.io.File;
import java.io.IOException;

/** Hands the shortcut to the OS shell (equivalent to double-clicking it). No command line is constructed. */
public class ShellAppLauncher implements AppLauncher {
    @Override
    public void launch(AppEntry entry) throws AppService.LaunchException {
        File f = new File(entry.path());
        if (!f.isFile()) throw new AppService.LaunchException("The shortcut no longer exists: " + entry.name());
        if (!Desktop.isDesktopSupported() || !Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
            throw new AppService.LaunchException("This system cannot open applications from Drift");
        }
        try {
            Desktop.getDesktop().open(f);
        } catch (IOException | RuntimeException e) {
            throw new AppService.LaunchException("Could not start " + entry.name() + ": " + e.getMessage());
        }
    }
}
