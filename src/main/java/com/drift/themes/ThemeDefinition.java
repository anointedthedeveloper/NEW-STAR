package com.drift.themes;

import com.drift.config.DriftSettings;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/** A validated, serialisable theme. Only data - themes never carry code or stylesheet URLs. */
public record ThemeDefinition(String name, boolean dark, String accent, double surfaceOpacity, String fontFamily,
                              double fontSize, double cornerRadius, double animationIntensity, String density) {
    private static final Pattern NAME = Pattern.compile("^[\\p{L}\\p{N} _.-]{1,40}$");
    private static final Pattern FONT = Pattern.compile("^[\\p{L}\\p{N} _-]{1,40}$");

    public static ThemeDefinition driftDark() {
        return new ThemeDefinition("Drift Dark", true, "#38BDF8", 0.88, "Segoe UI", 13, 10, 1.0, "comfortable");
    }

    public static ThemeDefinition driftLight() {
        return new ThemeDefinition("Drift Light", false, "#2563EB", 0.92, "Segoe UI", 13, 10, 1.0, "comfortable");
    }

    public static ThemeDefinition coding() {
        return new ThemeDefinition("Coding", true, "#A78BFA", 0.9, "Consolas", 13, 6, 0.6, "compact");
    }

    public List<String> validate() {
        List<String> e = new ArrayList<>();
        if (name == null || !NAME.matcher(name).matches()) e.add("Theme name must be 1-40 letters, digits, spaces or _.-");
        if (accent == null || !DriftSettings.HEX.matcher(accent).matches()) e.add("Accent must be a #RRGGBB colour");
        if (surfaceOpacity < 0.3 || surfaceOpacity > 1.0) e.add("Surface opacity must be 0.3-1.0");
        if (fontFamily == null || !FONT.matcher(fontFamily).matches()) e.add("Font family contains unsupported characters");
        if (fontSize < 9 || fontSize > 24) e.add("Font size must be 9-24");
        if (cornerRadius < 0 || cornerRadius > 24) e.add("Corner radius must be 0-24");
        if (animationIntensity < 0 || animationIntensity > 1.5) e.add("Animation intensity must be 0-1.5");
        if (density == null || !List.of("compact", "comfortable", "spacious").contains(density)) e.add("Density must be compact, comfortable or spacious");
        return e;
    }

    public double spacing() {
        return switch (density) { case "compact" -> 4; case "spacious" -> 12; default -> 8; };
    }

    /** Inline style for the scene root; the shared stylesheet resolves these looked-up colours. All values are validated first. */
    public String toRootStyle() {
        if (!validate().isEmpty()) throw new IllegalStateException("Invalid theme: " + validate());
        String bg = dark ? "#0F1217" : "#F4F6FA";
        String surface = dark ? "#171B22" : "#FFFFFF";
        String surfaceHi = dark ? "#1F252E" : "#E9EDF4";
        String border = dark ? "#2B3340" : "#CBD3E0";
        String text = dark ? "#E8ECF3" : "#10151D";
        String muted = dark ? "#8C97A8" : "#5A6577";
        return String.format(java.util.Locale.ROOT,
                "-drift-bg:%s;-drift-surface:%s;-drift-surface-hi:%s;-drift-border:%s;-drift-text:%s;-drift-muted:%s;"
                        + "-drift-accent:%s;-drift-radius:%.0f;-drift-space:%.0f;-fx-font-family:'%s';-fx-font-size:%.0fpx;",
                bg, surface, surfaceHi, border, text, muted, accent, cornerRadius, spacing(), fontFamily, fontSize);
    }
}
