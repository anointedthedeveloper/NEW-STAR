package com.drift.windows;

import java.util.Locale;
import java.util.Map;

/** Parsed keyboard shortcut such as "ctrl+alt+space". Pure parsing; registration lives in {@link GlobalHotkeys}. */
public record Hotkey(int modifiers, int virtualKey, String text) {
    public static final int MOD_ALT = 0x1, MOD_CONTROL = 0x2, MOD_SHIFT = 0x4, MOD_WIN = 0x8;

    private static final Map<String, Integer> NAMED = Map.ofEntries(
            Map.entry("space", 0x20), Map.entry("enter", 0x0D), Map.entry("tab", 0x09), Map.entry("esc", 0x1B),
            Map.entry("up", 0x26), Map.entry("down", 0x28), Map.entry("left", 0x25), Map.entry("right", 0x27));

    public static boolean isValid(String text) {
        try { parse(text); return true; } catch (IllegalArgumentException e) { return false; }
    }

    public static Hotkey parse(String text) {
        if (text == null || text.isBlank()) throw new IllegalArgumentException("Empty shortcut");
        int mods = 0, key = -1;
        for (String part : text.toLowerCase(Locale.ROOT).split("\\+")) {
            String p = part.strip();
            switch (p) {
                case "ctrl", "control" -> mods |= MOD_CONTROL;
                case "alt" -> mods |= MOD_ALT;
                case "shift" -> mods |= MOD_SHIFT;
                case "win", "meta" -> mods |= MOD_WIN;
                default -> {
                    if (key != -1) throw new IllegalArgumentException("Only one non-modifier key allowed: " + text);
                    key = keyCode(p, text);
                }
            }
        }
        if (key == -1) throw new IllegalArgumentException("Shortcut has no key: " + text);
        if (mods == 0) throw new IllegalArgumentException("Global shortcuts need at least one modifier: " + text);
        return new Hotkey(mods, key, text.toLowerCase(Locale.ROOT));
    }

    private static int keyCode(String p, String whole) {
        if (p.length() == 1) {
            char c = p.charAt(0);
            if (c >= 'a' && c <= 'z') return Character.toUpperCase(c);
            if (c >= '0' && c <= '9') return c;
        }
        if (p.matches("f([1-9]|1[0-2])")) return 0x70 + Integer.parseInt(p.substring(1)) - 1;
        Integer named = NAMED.get(p);
        if (named != null) return named;
        throw new IllegalArgumentException("Unknown key '" + p + "' in " + whole);
    }
}
