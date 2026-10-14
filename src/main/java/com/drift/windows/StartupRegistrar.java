package com.drift.windows;

/** Per-user start-with-Windows registration. Visible in Settings and removable with one click. */
public interface StartupRegistrar {
    boolean supported();
    boolean isEnabled();
    void enable(String commandLine);
    void disable();
}
