package com.drift.widgets;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Persisted configuration of one widget instance. Pure data with strict validation. */
public record WidgetConfig(String id, String type, double x, double y, double width, double height, double opacity,
                           String layout, boolean showLabel, int refreshSeconds, Map<String, String> settings) {
    public static final int MAX_SETTING_COUNT = 20;
    public static final int MAX_SETTING_VALUE = 20_000;

    public WidgetConfig {
        settings = settings == null ? Map.of() : Map.copyOf(settings);
    }

    public static WidgetConfig create(WidgetType t, double x, double y) {
        return new WidgetConfig(UUID.randomUUID().toString().substring(0, 8), t.id, x, y, t.defaultWidth, t.defaultHeight, 0.92,
                "compact", true, defaultRefresh(t), Map.of());
    }

    public static int defaultRefresh(WidgetType t) {
        return switch (t) { case SYSTEM_MONITOR -> 5; case WEATHER -> 900; case BATTERY -> 30; case CALENDAR -> 60; default -> 1; };
    }

    public WidgetConfig withPosition(double nx, double ny) { return new WidgetConfig(id, type, nx, ny, width, height, opacity, layout, showLabel, refreshSeconds, settings); }
    public WidgetConfig withSize(double w, double h) { return new WidgetConfig(id, type, x, y, w, h, opacity, layout, showLabel, refreshSeconds, settings); }
    public WidgetConfig withOpacity(double o) { return new WidgetConfig(id, type, x, y, width, height, o, layout, showLabel, refreshSeconds, settings); }
    public WidgetConfig withLayout(String l) { return new WidgetConfig(id, type, x, y, width, height, opacity, l, showLabel, refreshSeconds, settings); }
    public WidgetConfig withShowLabel(boolean s) { return new WidgetConfig(id, type, x, y, width, height, opacity, layout, s, refreshSeconds, settings); }
    public WidgetConfig withRefresh(int r) { return new WidgetConfig(id, type, x, y, width, height, opacity, layout, showLabel, r, settings); }
    public WidgetConfig withSetting(String k, String v) {
        Map<String, String> m = new LinkedHashMap<>(settings);
        m.put(k, v);
        return new WidgetConfig(id, type, x, y, width, height, opacity, layout, showLabel, refreshSeconds, m);
    }
    public String setting(String key, String fallback) { return settings.getOrDefault(key, fallback); }

    public List<String> validate() {
        List<String> e = new ArrayList<>();
        if (id == null || !id.matches("[A-Za-z0-9_-]{1,40}")) e.add("Widget id is invalid");
        if (type == null || WidgetType.fromId(type).isEmpty()) e.add("Unknown widget type: " + type);
        if (!Double.isFinite(x) || !Double.isFinite(y) || Math.abs(x) > 20000 || Math.abs(y) > 20000) e.add("Widget position is out of range");
        if (!Double.isFinite(width) || width < 60 || width > 1400) e.add("Widget width must be 60-1400");
        if (!Double.isFinite(height) || height < 40 || height > 1400) e.add("Widget height must be 40-1400");
        if (!(opacity >= 0.2 && opacity <= 1.0)) e.add("Widget opacity must be 0.2-1.0");
        if (!"compact".equals(layout) && !"expanded".equals(layout)) e.add("Layout must be compact or expanded");
        if (refreshSeconds < 1 || refreshSeconds > 3600) e.add("Refresh interval must be 1-3600 seconds");
        if (settings.size() > MAX_SETTING_COUNT) e.add("Too many widget settings");
        settings.forEach((k, v) -> {
            if (!k.matches("[A-Za-z0-9_.-]{1,40}")) e.add("Invalid setting name: " + k);
            if (v != null && v.length() > MAX_SETTING_VALUE) e.add("Setting too large: " + k);
        });
        return e;
    }
}
