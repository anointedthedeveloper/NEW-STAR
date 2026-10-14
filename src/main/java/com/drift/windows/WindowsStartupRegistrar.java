package com.drift.windows;

import com.sun.jna.platform.win32.Advapi32Util;
import com.sun.jna.platform.win32.WinReg;

/** HKCU\Software\Microsoft\Windows\CurrentVersion\Run - per-user, no administrator rights. */
public class WindowsStartupRegistrar implements StartupRegistrar {
    private static final String KEY = "Software\\Microsoft\\Windows\\CurrentVersion\\Run";
    private static final String VALUE = "Drift";

    @Override public boolean supported() { return PlatformInfo.isWindows(); }

    @Override public boolean isEnabled() {
        if (!supported()) return false;
        try { return Advapi32Util.registryValueExists(WinReg.HKEY_CURRENT_USER, KEY, VALUE); }
        catch (RuntimeException | LinkageError e) { return false; }
    }

    @Override public void enable(String commandLine) {
        if (!supported()) throw new IllegalStateException("Start with Windows is only available on Windows");
        Advapi32Util.registrySetStringValue(WinReg.HKEY_CURRENT_USER, KEY, VALUE, commandLine);
    }

    @Override public void disable() {
        if (supported() && isEnabled()) Advapi32Util.registryDeleteValue(WinReg.HKEY_CURRENT_USER, KEY, VALUE);
    }

    /** The command to register: only the installed launcher executable, never java.exe from a dev run. */
    public static java.util.Optional<String> installedLauncherCommand() {
        return ProcessHandle.current().info().command()
                .filter(c -> c.toLowerCase().endsWith("drift.exe"))
                .map(c -> "\"" + c + "\" --background");
    }
}
