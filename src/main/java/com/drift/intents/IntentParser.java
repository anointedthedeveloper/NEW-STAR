package com.drift.intents;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/** Rule-based parser over normalised text. Ordered rules; first match wins. */
@Component
public class IntentParser {
    private record Rule(Pattern pattern, Function<Matcher, Intent> build) {}

    private static Rule rule(String regex, Function<Matcher, Intent> build) {
        return new Rule(Pattern.compile(regex), build);
    }

    private final List<Rule> rules = List.of(
            rule("^(?:stop|cancel|end) listening$", m -> new Intent.StopListening()),
            rule("^(?:start|begin) dictation$|^dictate$|^start dictating$", m -> new Intent.StartDictation()),
            rule("^(?:stop|end|finish) dictation$|^stop dictating$", m -> new Intent.StopDictation()),
            rule("^remember (?:that )?i call (.+?) (?:my|the|a) (.+)$", m -> new Intent.Remember(m.group(2).strip(), m.group(1).strip())),
            rule("^(?:remember|learn) (?:that )?(.+?) means (.+)$", m -> new Intent.Remember(m.group(1).strip(), m.group(2).strip())),
            rule("^type (.+)$", m -> new Intent.TypeText(m.group(1).strip())),
            rule("^write (.+?) (?:into|in) (?:the )?(?:active )?(?:editor|window|app|application)$", m -> new Intent.TypeText(m.group(1).strip())),
            rule("^what(?:'s| is)? (?:song|track|music|playing).*|^what song is (?:playing|on)$|^what(?:'s| is) playing$|^which song.*",
                    m -> new Intent.MediaQuery()),
            rule("^(?:pause|stop) (?:the )?(?:music|song|track|media|playback)$|^pause$", m -> new Intent.MediaControl("pause")),
            rule("^(?:play|resume) (?:the )?(?:music|song|track|media|playback)$|^resume$|^play$", m -> new Intent.MediaControl("play")),
            rule("^(?:next|skip)(?: (?:song|track))?$", m -> new Intent.MediaControl("next")),
            rule("^(?:previous|last|back)(?: (?:song|track))$|^previous$", m -> new Intent.MediaControl("previous")),
            rule("^(?:volume up|turn (?:the )?volume up|louder)$", m -> new Intent.MediaControl("volume_up")),
            rule("^(?:volume down|turn (?:the )?volume down|quieter)$", m -> new Intent.MediaControl("volume_down")),
            rule("^mute$|^(?:mute|unmute) (?:the )?(?:volume|sound)$", m -> new Intent.MediaControl("mute")),
            rule("^(?:switch|change|set) (?:to |the )?(?:theme to )?(?:my )?(.+?) theme$|^(?:switch|change|set) theme to (.+)$",
                    m -> new Intent.SetTheme(m.group(1) != null ? m.group(1).strip() : m.group(2).strip())),
            rule("^make (?:the )?widgets? (?:more|less) (transparent|opaque)$|^make (?:the )?widgets? (more|less) transparent$",
                    m -> new Intent.AdjustTransparency(adjust(m.group(0)))),
            rule("^(?:show|add|display) (?:the |my )?(.+?)(?: widget)?$", m -> new Intent.ShowWidget(m.group(1).strip())),
            rule("^find (?:all )?(?:my )?(?:java|maven|gradle|code|development|dev) projects?$", m -> new Intent.FindProjects()),
            rule("^(?:find|search for|look for|search) (?:my )?(?:files? (?:named|called|matching) )?(.+)$", m -> new Intent.FindFiles(m.group(1).strip())),
            rule("^open (?:the )?(?:project|code) (?:i )?(?:worked on|was working on|used) (yesterday|last)$|^open (?:the )?(yesterday)'?s? project$",
                    m -> new Intent.OpenRecentProject(m.group(1) != null ? m.group(1) : m.group(2))),
            rule("^open (?:my )?(?:last|latest|recent|previous) project$", m -> new Intent.OpenRecentProject("last")),
            rule("^open (?:the |my )?project (.+)$", m -> new Intent.OpenProject(m.group(1).strip())),
            rule("^open (?:a |the )?(?:terminal|command prompt|console)$", m -> new Intent.OpenTerminal()),
            rule("^(?:run|execute) (?:the )?(?:approved )?(?:maven|mvn) tests?$|^run (?:the )?tests?$", m -> new Intent.RunDevTask("maven-test")),
            rule("^(?:run|execute) (?:the )?(?:approved )?(?:maven|mvn) (?:build|package)$|^build (?:the )?(?:project|application)$|^run (?:the )?build$",
                    m -> new Intent.RunDevTask("maven-package")),
            rule("^(?:run|execute) (?:the )?(?:approved )?(?:maven|mvn) clean$|^clean (?:the )?project$", m -> new Intent.RunDevTask("maven-clean")),
            rule("^(?:run|execute) (?:the )?command (.+)$|^execute (.+)$|^run ((?:mvn|npm|git|gradle|cmd|powershell|pwsh|del|rm|rd|format|sudo|curl|wget|python|node|sh|bash|reg|net|shutdown)\\b.*)$",
                    m -> new Intent.RunRawCommand(firstNonNull(m.group(1), m.group(2), m.group(3)))),
            rule("^start (?:the )?(?:application|app|project)$", m -> new Intent.RunDevTask("run-app")),
            rule("^open (?:my |the )?(.+?) (?:folder|directory)$", m -> new Intent.OpenFolder(m.group(1).strip())),
            rule("^open (?:my |the )?(.+?) file$", m -> new Intent.OpenFile(m.group(1).strip())),
            rule("^(?:open|launch|start|run) (.+)$", m -> new Intent.OpenApp(Normalizer.stripDeterminer(m.group(1).strip()))));

    private static String firstNonNull(String... values) {
        for (String v : values) if (v != null) return v.strip();
        return "";
    }

    private static String adjust(String text) {
        boolean more = text.contains("more");
        boolean transparent = text.contains("transparent");
        return (more == transparent) ? "more_transparent" : "less_transparent";
    }

    public Intent parse(String rawText) {
        String text = Normalizer.normalize(rawText);
        if (text.isEmpty()) return new Intent.Unknown(rawText == null ? "" : rawText);
        for (Rule r : rules) {
            Matcher m = r.pattern().matcher(text);
            if (m.matches()) {
                Optional<Intent> built = Optional.ofNullable(r.build().apply(m));
                if (built.isPresent()) return built.get();
            }
        }
        return new Intent.Unknown(text);
    }
}
