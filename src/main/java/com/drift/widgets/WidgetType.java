package com.drift.widgets;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

/** The widget kinds Drift ships. Pure data; JavaFX rendering lives in the ui package. */
public enum WidgetType {
    DIGITAL_CLOCK("digital-clock", "Digital clock", "clock", 160, 70),
    ANALOG_CLOCK("analog-clock", "Analog clock", "analog clock", 150, 150),
    CALENDAR("calendar", "Calendar", "calendar", 220, 200),
    WEATHER("weather", "Weather", "weather", 200, 100),
    SYSTEM_MONITOR("system-monitor", "System monitor", "system monitor", 220, 130),
    BATTERY("battery", "Battery", "battery", 150, 60),
    MEDIA_PLAYER("media-player", "Media player", "music", 280, 110),
    QUICK_LAUNCH("quick-launch", "Quick launch", "quick launch", 220, 100),
    NOTES("notes", "Notes", "notes", 220, 160),
    TIMER("timer", "Timer", "timer", 180, 100),
    COUNTDOWN("countdown", "Countdown", "countdown", 200, 100);

    public final String id;
    public final String displayName;
    public final String spoken;
    public final double defaultWidth;
    public final double defaultHeight;

    WidgetType(String id, String displayName, String spoken, double w, double h) {
        this.id = id;
        this.displayName = displayName;
        this.spoken = spoken;
        this.defaultWidth = w;
        this.defaultHeight = h;
    }

    public static Optional<WidgetType> fromId(String id) {
        return Arrays.stream(values()).filter(t -> t.id.equals(id)).findFirst();
    }

    /** Matches spoken text such as "the clock", "analog clock", "music player". */
    public static Optional<WidgetType> fromSpoken(String text) {
        String s = text.toLowerCase(Locale.ROOT).strip().replaceAll("^(the|my|a)\\s+", "").replaceAll("\\s+widget$", "");
        if (s.equals("clock") || s.equals("digital clock") || s.equals("time")) return Optional.of(DIGITAL_CLOCK);
        if (s.equals("music player") || s.equals("media") || s.equals("player") || s.equals("media player")) return Optional.of(MEDIA_PLAYER);
        if (s.equals("cpu") || s.equals("resources") || s.equals("system resources")) return Optional.of(SYSTEM_MONITOR);
        return Arrays.stream(values()).filter(t -> t.spoken.equals(s) || t.displayName.equalsIgnoreCase(s) || t.id.equals(s)).findFirst();
    }
}
