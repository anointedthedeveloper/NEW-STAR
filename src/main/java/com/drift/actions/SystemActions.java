package com.drift.actions;

import static com.drift.actions.ActionDefinition.Confirmation.NEVER;
import static com.drift.actions.ActionDefinition.required;

import com.drift.core.DriftEvent;
import com.drift.core.DriftEventBus;
import com.drift.dictation.DictationService;
import com.drift.media.MediaProvider;
import com.drift.media.MediaService;
import com.drift.speech.VoiceService;
import com.drift.themes.ThemeService;
import com.drift.widgets.LayoutService;
import com.drift.widgets.WidgetConfig;
import com.drift.widgets.WidgetLayout;
import com.drift.widgets.WidgetType;
import com.drift.windows.MediaKeys;
import jakarta.annotation.PostConstruct;
import java.time.Duration;
import java.util.List;
import org.springframework.stereotype.Component;

/** Registers voice, dictation, media, theme and widget actions. */
@Component
public class SystemActions {
    private final ActionRegistry registry;
    private final VoiceService voice;
    private final DictationService dictation;
    private final MediaService media;
    private final ThemeService themes;
    private final LayoutService layouts;
    private final DriftEventBus bus;

    public SystemActions(ActionRegistry registry, VoiceService voice, DictationService dictation, MediaService media,
                         ThemeService themes, LayoutService layouts, DriftEventBus bus) {
        this.registry = registry;
        this.voice = voice;
        this.dictation = dictation;
        this.media = media;
        this.themes = themes;
        this.layouts = layouts;
        this.bus = bus;
    }

    @PostConstruct
    public void register() {
        registry.register(new ActionDefinition("listening.stop", "Stop listening", List.of(), Permission.SAFE, NEVER, Duration.ofSeconds(5), "none",
                (r, c) -> { voice.stop(); return ActionResult.ok("Stopped listening."); }));
        registry.register(new ActionDefinition("dictation.start", "Start dictation (text is previewed before anything is typed)", List.of(), Permission.INPUT, NEVER,
                Duration.ofSeconds(10), "none", (r, c) -> {
                    dictation.start();
                    if (!voice.start(VoiceService.Mode.DICTATION)) {
                        dictation.discard();
                        return ActionResult.failure("Dictation could not start: speech recognition or the microphone is unavailable.");
                    }
                    return ActionResult.ok("Dictating into \"" + dictation.targetTitle() + "\". Say or press stop to review the text before it is typed.");
                }));
        registry.register(new ActionDefinition("dictation.stop", "Stop dictation and show the preview", List.of(), Permission.SAFE, NEVER,
                Duration.ofSeconds(5), "none", (r, c) -> {
                    voice.stop();
                    dictation.stop();
                    return ActionResult.ok("Dictation stopped. Review the preview, then choose Insert or Discard.");
                }));
        registry.register(new ActionDefinition("text.type", "Stage text for typing into the active application (always previewed first)",
                List.of(required("text", 5000, "Text to stage")), Permission.INPUT, NEVER, Duration.ofSeconds(5), "none", (r, c) -> {
                    dictation.stage(r.param("text"));
                    return ActionResult.ok("Text is ready in the preview for \"" + dictation.targetTitle() + "\". Nothing is typed until you choose Insert.");
                }));
        registry.register(new ActionDefinition("media.query", "Report the current track", List.of(), Permission.SAFE, NEVER, Duration.ofSeconds(15), "none",
                (r, c) -> ActionResult.ok(media.describeNowPlaying())));
        registry.register(new ActionDefinition("media.control", "Control media playback",
                List.of(required("action", 20, "play, pause, next, previous, volume_up, volume_down, mute")), Permission.SAFE, NEVER, Duration.ofSeconds(15), "none", (r, c) -> {
                    String a = r.param("action");
                    switch (a) {
                        case "volume_up", "volume_down", "mute" -> {
                            if (!MediaKeys.supported()) return ActionResult.failure("System volume control is only available on Windows.");
                            MediaKeys.volume(a);
                            return ActionResult.ok("System volume: " + a.replace('_', ' ') + ". (Per-app volume is not available.)");
                        }
                        default -> {
                            MediaProvider.Command cmd = switch (a) {
                                case "play" -> MediaProvider.Command.PLAY; case "pause" -> MediaProvider.Command.PAUSE;
                                case "next" -> MediaProvider.Command.NEXT; case "previous" -> MediaProvider.Command.PREVIOUS;
                                default -> throw new IllegalArgumentException("Unknown media action " + a); };
                            return ActionResult.ok(media.control(cmd));
                        }
                    }
                }));
        registry.register(new ActionDefinition("theme.set", "Switch theme", List.of(required("name", 40, "Theme name")), Permission.SETTINGS, NEVER,
                Duration.ofSeconds(5), "none", (r, c) -> { themes.apply(r.param("name")); return ActionResult.ok("Switched to " + r.param("name") + "."); }));
        registry.register(new ActionDefinition("widget.show", "Show a widget", List.of(required("type", 40, "Widget type id")), Permission.SETTINGS, NEVER,
                Duration.ofSeconds(5), "none", (r, c) -> {
                    WidgetType t = WidgetType.fromId(r.param("type")).orElseThrow(() -> new IllegalArgumentException("Unknown widget"));
                    WidgetLayout layout = layouts.active();
                    if (layout.widgets().stream().anyMatch(w -> w.type().equals(t.id))) return ActionResult.ok("The " + t.displayName.toLowerCase() + " is already on your desktop.");
                    double offset = 40.0 + 24 * layout.widgets().size();
                    layouts.save(layout.with(WidgetConfig.create(t, offset, offset)));
                    layouts.setActive(layout.name());
                    bus.publish(new DriftEvent.LayoutChanged());
                    return ActionResult.ok("Added the " + t.displayName.toLowerCase() + " widget.");
                }));
        registry.register(new ActionDefinition("widgets.transparency", "Adjust widget transparency",
                List.of(required("direction", 20, "more_transparent or less_transparent")), Permission.SETTINGS, NEVER, Duration.ofSeconds(5), "none", (r, c) -> {
                    WidgetLayout layout = layouts.active();
                    if (layout.widgets().isEmpty()) return ActionResult.failure("There are no widgets to adjust. Say \"show the clock\" to add one.");
                    double delta = r.param("direction").equals("more_transparent") ? -0.1 : 0.1;
                    WidgetLayout next = layout;
                    for (WidgetConfig w : layout.widgets()) next = next.with(w.withOpacity(Math.round(Math.max(0.2, Math.min(1.0, w.opacity() + delta)) * 100) / 100.0));
                    layouts.save(next);
                    layouts.setActive(next.name());
                    bus.publish(new DriftEvent.LayoutChanged());
                    return ActionResult.ok("Widgets are now " + (delta < 0 ? "more" : "less") + " transparent.");
                }));
    }
}
