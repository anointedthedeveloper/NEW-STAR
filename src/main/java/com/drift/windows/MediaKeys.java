package com.drift.windows;

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Pointer;

/** System volume via the standard media virtual keys (SMTC exposes no per-app volume). */
public final class MediaKeys {
    private static final int VK_VOLUME_MUTE = 0xAD, VK_VOLUME_DOWN = 0xAE, VK_VOLUME_UP = 0xAF, KEYEVENTF_KEYUP = 2;

    interface User32Keys extends Library {
        User32Keys INSTANCE = Native.load("user32", User32Keys.class);
        void keybd_event(byte bVk, byte bScan, int dwFlags, Pointer dwExtraInfo);
    }

    private MediaKeys() {}

    public static boolean supported() { return PlatformInfo.isWindows(); }

    public static void volume(String action) {
        int vk = switch (action) { case "volume_up" -> VK_VOLUME_UP; case "volume_down" -> VK_VOLUME_DOWN; case "mute" -> VK_VOLUME_MUTE;
            default -> throw new IllegalArgumentException("Unknown volume action " + action); };
        if (!supported()) throw new IllegalStateException("System volume control is only available on Windows");
        User32Keys.INSTANCE.keybd_event((byte) vk, (byte) 0, 0, null);
        User32Keys.INSTANCE.keybd_event((byte) vk, (byte) 0, KEYEVENTF_KEYUP, null);
    }
}
