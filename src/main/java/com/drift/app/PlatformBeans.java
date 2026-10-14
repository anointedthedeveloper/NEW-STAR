package com.drift.app;

import com.drift.assets.WallpaperSetter;
import com.drift.dictation.ForegroundWindow;
import com.drift.dictation.TextInserter;
import com.drift.media.BridgeMediaProvider;
import com.drift.media.MediaBridgeLauncher;
import com.drift.media.MediaProvider;
import com.drift.media.UnavailableMediaProvider;
import com.drift.monitoring.OshiSystemMonitor;
import com.drift.monitoring.SystemMonitor;
import com.drift.windows.PlatformInfo;
import com.drift.windows.StartupRegistrar;
import com.drift.windows.WindowsForegroundWindow;
import com.drift.windows.WindowsStartupRegistrar;
import com.drift.windows.WindowsTextInserter;
import com.drift.windows.WindowsWallpaperSetter;
import java.io.IOException;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Production selection of OS integrations. Where an integration is impossible the beans say so rather than fake data. */
@Configuration
public class PlatformBeans {
    @Bean MediaProvider mediaProvider() {
        if (!PlatformInfo.isWindows()) return new UnavailableMediaProvider("Media sessions are read through Windows 10/11 System Media Transport Controls; this is " + PlatformInfo.osDescription());
        try { return new BridgeMediaProvider(MediaBridgeLauncher.powershellCommand()); }
        catch (IOException e) { return new UnavailableMediaProvider(e.getMessage()); }
    }

    @Bean SystemMonitor systemMonitor() { return new OshiSystemMonitor(); }

    @Bean TextInserter textInserter() { return new WindowsTextInserter(); }

    @Bean ForegroundWindow foregroundWindow() { return new WindowsForegroundWindow(); }

    @Bean StartupRegistrar startupRegistrar() { return new WindowsStartupRegistrar(); }

    @Bean WallpaperSetter wallpaperSetter() { return new WindowsWallpaperSetter(); }
}
