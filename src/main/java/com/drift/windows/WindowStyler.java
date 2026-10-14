package com.drift.windows;

import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.BaseTSD;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef.HWND;
import com.sun.jna.platform.win32.WinUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Native window flags for the floating UI, applied to the window found by its unique title.
 * NOACTIVATE stops the floating window from taking keyboard focus when clicked; TRANSPARENT makes it click-through.
 */
public class WindowStyler {
    private static final Logger log = LoggerFactory.getLogger(WindowStyler.class);
    static final int WS_EX_TRANSPARENT = 0x20, WS_EX_TOOLWINDOW = 0x80, WS_EX_LAYERED = 0x80000, WS_EX_NOACTIVATE = 0x08000000;
    private static final int SWP_NOSIZE = 1, SWP_NOMOVE = 2, SWP_NOZORDER = 4, SWP_NOACTIVATE = 0x10, SWP_FRAMECHANGED = 0x20;

    public static boolean supported() { return PlatformInfo.isWindows(); }

    /** Sets or clears the given extended style bits. Returns false if the window could not be found or the call failed. */
    public static boolean setExStyle(String windowTitle, int bits, boolean on) {
        if (!supported()) return false;
        try {
            HWND h = User32.INSTANCE.FindWindow(null, windowTitle);
            if (h == null) return false;
            long cur = User32.INSTANCE.GetWindowLongPtr(h, WinUser.GWL_EXSTYLE).longValue();
            long next = on ? (cur | bits) : (cur & ~(long) bits);
            if (next != cur) {
                User32.INSTANCE.SetWindowLongPtr(h, WinUser.GWL_EXSTYLE, new Pointer(next));
                User32.INSTANCE.SetWindowPos(h, null, 0, 0, 0, 0, SWP_NOMOVE | SWP_NOSIZE | SWP_NOZORDER | SWP_NOACTIVATE | SWP_FRAMECHANGED);
            }
            return true;
        } catch (RuntimeException | LinkageError e) {
            log.warn("Could not change window style", e);
            return false;
        }
    }

    public static boolean setNoActivate(String title, boolean on) { return setExStyle(title, WS_EX_NOACTIVATE | WS_EX_TOOLWINDOW, on); }

    public static boolean setClickThrough(String title, boolean on) { return setExStyle(title, WS_EX_TRANSPARENT | WS_EX_LAYERED, on); }
}
