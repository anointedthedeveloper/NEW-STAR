package com.drift.ui;

import com.drift.app.AppCoordinator;
import com.drift.actions.ActionRegistry;
import com.drift.actions.CommandOrchestrator;
import com.drift.applications.AppService;
import com.drift.assets.AssetPackService;
import com.drift.config.SettingsService;
import com.drift.core.DriftEventBus;
import com.drift.core.DriftPaths;
import com.drift.dev.DevProjectRepository;
import com.drift.dictation.DictationService;
import com.drift.history.OperationHistory;
import com.drift.learning.AliasRepository;
import com.drift.learning.LearnedCommandRepository;
import com.drift.learning.LearningService;
import com.drift.media.MediaService;
import com.drift.monitoring.MonitorService;
import com.drift.speech.VoiceService;
import com.drift.speech.VoskSpeechProvider;
import com.drift.themes.ThemeService;
import com.drift.widgets.LayoutService;
import com.drift.windows.StartupRegistrar;
import org.springframework.stereotype.Component;

/** Everything the JavaFX layer needs, injected once. UI classes depend on this instead of the Spring context. */
@Component
public class UiServices {
    public final SettingsService settings;
    public final ThemeService themes;
    public final DriftEventBus bus;
    public final AppCoordinator coordinator;
    public final CommandOrchestrator orchestrator;
    public final VoiceService voice;
    public final VoskSpeechProvider vosk;
    public final DictationService dictation;
    public final MediaService media;
    public final MonitorService monitor;
    public final AppService apps;
    public final LayoutService layouts;
    public final AliasRepository aliases;
    public final LearnedCommandRepository learned;
    public final LearningService learning;
    public final OperationHistory history;
    public final DevProjectRepository projects;
    public final ActionRegistry registry;
    public final AssetPackService packs;
    public final StartupRegistrar startup;
    public final DriftPaths paths;

    public UiServices(SettingsService settings, ThemeService themes, DriftEventBus bus, AppCoordinator coordinator,
                      CommandOrchestrator orchestrator, VoiceService voice, VoskSpeechProvider vosk, DictationService dictation,
                      MediaService media, MonitorService monitor, AppService apps, LayoutService layouts, AliasRepository aliases,
                      LearnedCommandRepository learned, LearningService learning, OperationHistory history, DevProjectRepository projects,
                      ActionRegistry registry, AssetPackService packs, StartupRegistrar startup, DriftPaths paths) {
        this.settings = settings; this.themes = themes; this.bus = bus; this.coordinator = coordinator; this.orchestrator = orchestrator;
        this.voice = voice; this.vosk = vosk; this.dictation = dictation; this.media = media; this.monitor = monitor; this.apps = apps;
        this.layouts = layouts; this.aliases = aliases; this.learned = learned; this.learning = learning; this.history = history;
        this.projects = projects; this.registry = registry; this.packs = packs; this.startup = startup; this.paths = paths;
    }
}
