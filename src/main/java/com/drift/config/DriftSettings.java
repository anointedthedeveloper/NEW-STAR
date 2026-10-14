package com.drift.config;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/** All persisted user settings. Sections are immutable records; update via {@link SettingsService}. */
public record DriftSettings(
        Appearance appearance,
        Floating floating,
        Voice voice,
        Startup startup,
        Developer developer,
        Privacy privacy,
        Monitoring monitoring,
        Shortcuts shortcuts,
        Weather weather) {

    public static final Pattern HEX = Pattern.compile("^#[0-9a-fA-F]{6}$");

    public record Appearance(String themeName, boolean reducedMotion, String iconPack) {
        public Appearance withThemeName(String v) { return new Appearance(v, reducedMotion, iconPack); }
        public Appearance withReducedMotion(boolean v) { return new Appearance(themeName, v, iconPack); }
        public Appearance withIconPack(String v) { return new Appearance(themeName, reducedMotion, v); }
    }

    public record Floating(boolean enabled, boolean alwaysOnTop, double opacity, double width, double height,
                           String anchor, double x, double y, boolean customPosition, boolean clickThroughWhenIdle,
                           boolean startExpanded) {
        public Floating withEnabled(boolean v) { return new Floating(v, alwaysOnTop, opacity, width, height, anchor, x, y, customPosition, clickThroughWhenIdle, startExpanded); }
        public Floating withAlwaysOnTop(boolean v) { return new Floating(enabled, v, opacity, width, height, anchor, x, y, customPosition, clickThroughWhenIdle, startExpanded); }
        public Floating withOpacity(double v) { return new Floating(enabled, alwaysOnTop, v, width, height, anchor, x, y, customPosition, clickThroughWhenIdle, startExpanded); }
        public Floating withWidth(double v) { return new Floating(enabled, alwaysOnTop, opacity, v, height, anchor, x, y, customPosition, clickThroughWhenIdle, startExpanded); }
        public Floating withHeight(double v) { return new Floating(enabled, alwaysOnTop, opacity, width, v, anchor, x, y, customPosition, clickThroughWhenIdle, startExpanded); }
        public Floating withAnchor(String v) { return new Floating(enabled, alwaysOnTop, opacity, width, height, v, x, y, customPosition, clickThroughWhenIdle, startExpanded); }
        public Floating withX(double v) { return new Floating(enabled, alwaysOnTop, opacity, width, height, anchor, v, y, customPosition, clickThroughWhenIdle, startExpanded); }
        public Floating withY(double v) { return new Floating(enabled, alwaysOnTop, opacity, width, height, anchor, x, v, customPosition, clickThroughWhenIdle, startExpanded); }
        public Floating withCustomPosition(boolean v) { return new Floating(enabled, alwaysOnTop, opacity, width, height, anchor, x, y, v, clickThroughWhenIdle, startExpanded); }
        public Floating withClickThroughWhenIdle(boolean v) { return new Floating(enabled, alwaysOnTop, opacity, width, height, anchor, x, y, customPosition, v, startExpanded); }
        public Floating withStartExpanded(boolean v) { return new Floating(enabled, alwaysOnTop, opacity, width, height, anchor, x, y, customPosition, clickThroughWhenIdle, v); }
    }

    public record Voice(String provider, String modelPath, String language, String microphone,
                        boolean pushToTalk) {
        public Voice withProvider(String v) { return new Voice(v, modelPath, language, microphone, pushToTalk); }
        public Voice withModelPath(String v) { return new Voice(provider, v, language, microphone, pushToTalk); }
        public Voice withLanguage(String v) { return new Voice(provider, modelPath, v, microphone, pushToTalk); }
        public Voice withMicrophone(String v) { return new Voice(provider, modelPath, language, v, pushToTalk); }
        public Voice withPushToTalk(boolean v) { return new Voice(provider, modelPath, language, microphone, v); }
    }

    public record Startup(boolean launchWithWindows, boolean minimizeToTray, boolean startHidden) {
        public Startup withLaunchWithWindows(boolean v) { return new Startup(v, minimizeToTray, startHidden); }
        public Startup withMinimizeToTray(boolean v) { return new Startup(launchWithWindows, v, startHidden); }
        public Startup withStartHidden(boolean v) { return new Startup(launchWithWindows, minimizeToTray, v); }
    }

    public record Developer(boolean enabled, String ide, List<String> projectRoots, int commandTimeoutSeconds) {
        public Developer withEnabled(boolean v) { return new Developer(v, ide, projectRoots, commandTimeoutSeconds); }
        public Developer withIde(String v) { return new Developer(enabled, v, projectRoots, commandTimeoutSeconds); }
        public Developer withProjectRoots(List<String> v) { return new Developer(enabled, ide, v, commandTimeoutSeconds); }
        public Developer withCommandTimeoutSeconds(int v) { return new Developer(enabled, ide, projectRoots, v); }
    }

    public record Privacy(int historyRetentionDays, int logRetentionDays, boolean storeTranscripts) {
        public Privacy withHistoryRetentionDays(int v) { return new Privacy(v, logRetentionDays, storeTranscripts); }
        public Privacy withLogRetentionDays(int v) { return new Privacy(historyRetentionDays, v, storeTranscripts); }
        public Privacy withStoreTranscripts(boolean v) { return new Privacy(historyRetentionDays, logRetentionDays, v); }
    }

    public record Monitoring(boolean enabled, int intervalSeconds) {
        public Monitoring withEnabled(boolean v) { return new Monitoring(v, intervalSeconds); }
        public Monitoring withIntervalSeconds(int v) { return new Monitoring(enabled, v); }
    }

    public record Shortcuts(String pushToTalk, String toggleInterface, String dictation) {
        public Shortcuts withPushToTalk(String v) { return new Shortcuts(v, toggleInterface, dictation); }
        public Shortcuts withToggleInterface(String v) { return new Shortcuts(pushToTalk, v, dictation); }
        public Shortcuts withDictation(String v) { return new Shortcuts(pushToTalk, toggleInterface, v); }
    }

    public record Weather(boolean enabled, double latitude, double longitude, String units) {
        public Weather withEnabled(boolean v) { return new Weather(v, latitude, longitude, units); }
        public Weather withLatitude(double v) { return new Weather(enabled, v, longitude, units); }
        public Weather withLongitude(double v) { return new Weather(enabled, latitude, v, units); }
        public Weather withUnits(String v) { return new Weather(enabled, latitude, longitude, v); }
    }

    public DriftSettings withAppearance(Appearance v) { return new DriftSettings(v, floating, voice, startup, developer, privacy, monitoring, shortcuts, weather); }
    public DriftSettings withFloating(Floating v) { return new DriftSettings(appearance, v, voice, startup, developer, privacy, monitoring, shortcuts, weather); }
    public DriftSettings withVoice(Voice v) { return new DriftSettings(appearance, floating, v, startup, developer, privacy, monitoring, shortcuts, weather); }
    public DriftSettings withStartup(Startup v) { return new DriftSettings(appearance, floating, voice, v, developer, privacy, monitoring, shortcuts, weather); }
    public DriftSettings withDeveloper(Developer v) { return new DriftSettings(appearance, floating, voice, startup, v, privacy, monitoring, shortcuts, weather); }
    public DriftSettings withPrivacy(Privacy v) { return new DriftSettings(appearance, floating, voice, startup, developer, v, monitoring, shortcuts, weather); }
    public DriftSettings withMonitoring(Monitoring v) { return new DriftSettings(appearance, floating, voice, startup, developer, privacy, v, shortcuts, weather); }
    public DriftSettings withShortcuts(Shortcuts v) { return new DriftSettings(appearance, floating, voice, startup, developer, privacy, monitoring, v, weather); }
    public DriftSettings withWeather(Weather v) { return new DriftSettings(appearance, floating, voice, startup, developer, privacy, monitoring, shortcuts, v); }

    public static DriftSettings defaults() {
        return new DriftSettings(
                new Appearance("Drift Dark", false, ""),
                new Floating(true, true, 0.95, 360, 64, "RIGHT", 0, 0, false, false, false),
                new Voice("vosk", "", "en-US", "", true),
                new Startup(false, true, false),
                new Developer(false, "", List.of(), 600),
                new Privacy(30, 7, false),
                new Monitoring(false, 5),
                new Shortcuts("ctrl+alt+space", "ctrl+alt+n", "ctrl+alt+d"),
                new Weather(false, 0, 0, "metric"));
    }

    /** Returns human-readable validation errors; empty when the settings are acceptable. */
    public List<String> validate() {
        List<String> e = new ArrayList<>();
        if (appearance == null || floating == null || voice == null || startup == null || developer == null
                || privacy == null || monitoring == null || shortcuts == null || weather == null) {
            e.add("Settings sections must not be missing");
            return e;
        }
        if (appearance.themeName() == null || appearance.themeName().isBlank()) e.add("Theme name is required");
        if (floating.opacity() < 0.2 || floating.opacity() > 1.0) e.add("Floating opacity must be between 0.2 and 1.0");
        if (floating.width() < 200 || floating.width() > 900) e.add("Floating width must be between 200 and 900");
        if (floating.height() < 40 || floating.height() > 900) e.add("Floating height must be between 40 and 900");
        if (!List.of("LEFT", "RIGHT", "TOP", "BOTTOM").contains(floating.anchor())) e.add("Anchor must be LEFT, RIGHT, TOP or BOTTOM");
        if (voice.language() == null || !voice.language().matches("[A-Za-z]{2,3}(-[A-Za-z0-9]{2,8})?")) e.add("Language must look like en-US");
        if (!"vosk".equals(voice.provider())) e.add("Unknown speech provider: " + voice.provider());
        if (developer.commandTimeoutSeconds() < 5 || developer.commandTimeoutSeconds() > 3600) e.add("Command timeout must be 5-3600 seconds");
        if (privacy.historyRetentionDays() < 1 || privacy.historyRetentionDays() > 3650) e.add("History retention must be 1-3650 days");
        if (privacy.logRetentionDays() < 1 || privacy.logRetentionDays() > 365) e.add("Log retention must be 1-365 days");
        if (monitoring.intervalSeconds() < 1 || monitoring.intervalSeconds() > 300) e.add("Monitoring interval must be 1-300 seconds");
        for (String s : List.of(shortcuts.pushToTalk(), shortcuts.toggleInterface(), shortcuts.dictation())) {
            if (s != null && !s.isBlank() && !com.drift.windows.Hotkey.isValid(s)) e.add("Invalid shortcut: " + s);
        }
        if (weather.latitude() < -90 || weather.latitude() > 90) e.add("Latitude out of range");
        if (weather.longitude() < -180 || weather.longitude() > 180) e.add("Longitude out of range");
        if (!List.of("metric", "imperial").contains(weather.units())) e.add("Weather units must be metric or imperial");
        return e;
    }
}
