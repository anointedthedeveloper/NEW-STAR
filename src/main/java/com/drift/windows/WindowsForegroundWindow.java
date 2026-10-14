package com.drift.windows;

import com.drift.dictation.ForegroundWindow;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef.HWND;
import java.util.Optional;

public class WindowsForegroundWindow implements ForegroundWindow {
    @Override
    public Optional<Info> current() {
        if (!PlatformInfo.isWindows()) return Optional.empty();
        try {
            HWND h = User32.INSTANCE.GetForegroundWindow();
            if (h == null) return Optional.empty();
            char[] buf = new char[512];
            int n = User32.INSTANCE.GetWindowText(h, buf, buf.length);
            return Optional.of(new Info(com.sun.jna.Pointer.nativeValue(h.getPointer()), new String(buf, 0, Math.max(0, n))));
        } catch (RuntimeException | LinkageError e) {
            return Optional.empty();
        }
    }
}
