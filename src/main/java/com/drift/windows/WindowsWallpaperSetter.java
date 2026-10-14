package com.drift.windows;

import com.drift.assets.WallpaperSetter;
import com.sun.jna.Native;
import com.sun.jna.win32.W32APIOptions;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;

/** Uses the documented SystemParametersInfo SPI_GETDESKWALLPAPER / SPI_SETDESKWALLPAPER calls. */
public class WindowsWallpaperSetter implements WallpaperSetter {
    private static final int SPI_SETDESKWALLPAPER = 0x14, SPI_GETDESKWALLPAPER = 0x73, SPIF_UPDATEINIFILE = 1, SPIF_SENDCHANGE = 2;

    interface User32Ext extends com.sun.jna.Library {
        User32Ext INSTANCE = Native.load("user32", User32Ext.class, W32APIOptions.UNICODE_OPTIONS);
        boolean SystemParametersInfoW(int uiAction, int uiParam, char[] pvParam, int fWinIni);
        boolean SystemParametersInfoW(int uiAction, int uiParam, String pvParam, int fWinIni);
    }

    @Override public boolean supported() { return PlatformInfo.isWindows(); }

    @Override public Optional<String> current() {
        if (!supported()) return Optional.empty();
        try {
            char[] buf = new char[1024];
            if (!User32Ext.INSTANCE.SystemParametersInfoW(SPI_GETDESKWALLPAPER, buf.length, buf, 0)) return Optional.empty();
            String s = Native.toString(buf);
            return s.isBlank() ? Optional.empty() : Optional.of(s);
        } catch (RuntimeException | LinkageError e) { return Optional.empty(); }
    }

    @Override public void set(Path image) throws IOException {
        if (!supported()) throw new IOException("Wallpaper changes are only supported on Windows");
        try {
            if (!User32Ext.INSTANCE.SystemParametersInfoW(SPI_SETDESKWALLPAPER, 0, image.toAbsolutePath().toString(), SPIF_UPDATEINIFILE | SPIF_SENDCHANGE)) {
                throw new IOException("Windows rejected the wallpaper change");
            }
        } catch (RuntimeException | LinkageError e) { throw new IOException("Windows API unavailable: " + e.getMessage()); }
    }
}
