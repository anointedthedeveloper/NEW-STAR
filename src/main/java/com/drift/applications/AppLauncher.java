package com.drift.applications;

public interface AppLauncher {
    void launch(AppEntry entry) throws AppService.LaunchException;
}
