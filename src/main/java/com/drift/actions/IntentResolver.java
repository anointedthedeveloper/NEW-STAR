package com.drift.actions;

import com.drift.applications.AppEntry;
import com.drift.applications.AppService;
import com.drift.dev.DevProjectRepository;
import com.drift.dev.DevTemplates;
import com.drift.files.FileSearchService;
import com.drift.files.FolderService;
import com.drift.intents.Intent;
import com.drift.learning.AliasRepository;
import com.drift.learning.AliasRepository.AliasKind;
import com.drift.learning.LearningService;
import com.drift.themes.ThemeService;
import com.drift.widgets.WidgetType;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** Steps 4-6 of the pipeline: validated parameters, alias/preference resolution, typed action request. */
@Component
public class IntentResolver {
    public sealed interface Resolution {
        record Ready(ActionRequest request) implements Resolution {}
        record Choice(String prompt, List<Option> options, String rememberPhrase) implements Resolution {}
        record Unsupported(String reason) implements Resolution {}
    }

    /** One selectable alternative; {@code rememberTarget} is what an alias would map to if the user opts to remember. */
    public record Option(String label, ActionRequest request, String rememberTarget) {}

    private final AppService apps;
    private final FolderService folders;
    private final FileSearchService search;
    private final DevProjectRepository projects;
    private final DevTemplates templates;
    private final ThemeService themes;
    private final LearningService learning;

    public IntentResolver(AppService apps, FolderService folders, FileSearchService search, DevProjectRepository projects,
                          DevTemplates templates, ThemeService themes, LearningService learning) {
        this.apps = apps;
        this.folders = folders;
        this.search = search;
        this.projects = projects;
        this.templates = templates;
        this.themes = themes;
        this.learning = learning;
    }

    private static ActionRequest req(String id, String utterance, String summary, String... kv) {
        return reqWithConfirm(id, utterance, summary, null, kv);
    }

    private static ActionRequest reqWithConfirm(String id, String utterance, String summary, String confirm, String... kv) {
        Map<String, String> p = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) p.put(kv[i], kv[i + 1]);
        return new ActionRequest(id, p, utterance, Source.PARSED, confirm, summary);
    }

    public Resolution resolve(Intent intent, String utterance) {
        return switch (intent) {
            case Intent.OpenApp i -> openApp(i.name(), utterance);
            case Intent.OpenFolder i -> folders.resolve(i.name())
                    .<Resolution>map(p -> ready(req("folder.open", utterance, "Open folder " + p.getFileName(), "path", p.toString())))
                    .orElseGet(() -> new Resolution.Unsupported("I don't know a folder called \"" + i.name()
                            + "\". You can add a folder alias in Settings > Command preferences."));
            case Intent.OpenFile i -> openFile(i.name(), utterance);
            case Intent.FindFiles i -> ready(req("file.search", utterance, "Search for files matching \"" + i.query() + "\"", "query", i.query()));
            case Intent.FindProjects i -> ready(req("project.find", utterance, "Find development projects"));
            case Intent.OpenRecentProject i -> openRecentProject(i.when(), utterance);
            case Intent.OpenProject i -> openProject(i.name(), utterance);
            case Intent.OpenTerminal i -> openTerminal(utterance);
            case Intent.RunDevTask i -> runDevTask(i.task(), utterance);
            case Intent.RunRawCommand i -> new Resolution.Unsupported("Drift never runs arbitrary commands from speech. "
                    + "In developer mode it can run these approved tasks: " + String.join(", ", templates.all().stream().map(DevTemplates.Template::description).toList()) + ".");
            case Intent.StartDictation i -> ready(req("dictation.start", utterance, "Start dictation"));
            case Intent.StopDictation i -> ready(req("dictation.stop", utterance, "Stop dictation"));
            case Intent.TypeText i -> ready(req("text.type", utterance, "Stage text for typing (preview first)", "text", i.text()));
            case Intent.MediaQuery i -> ready(req("media.query", utterance, "Check what is playing"));
            case Intent.MediaControl i -> ready(req("media.control", utterance, "Media: " + i.action().replace('_', ' '), "action", i.action()));
            case Intent.SetTheme i -> setTheme(i.name(), utterance);
            case Intent.AdjustTransparency i -> ready(req("widgets.transparency", utterance, "Make widgets " + (i.direction().startsWith("more") ? "more" : "less") + " transparent", "direction", i.direction()));
            case Intent.ShowWidget i -> WidgetType.fromSpoken(i.type())
                    .<Resolution>map(t -> ready(req("widget.show", utterance, "Show the " + t.displayName.toLowerCase(Locale.ROOT), "type", t.id)))
                    .orElseGet(() -> new Resolution.Unsupported("I don't have a widget called \"" + i.type() + "\"."));
            case Intent.Remember i -> remember(i.phrase(), i.target(), utterance);
            case Intent.StopListening i -> ready(req("listening.stop", utterance, "Stop listening"));
            case Intent.Unknown i -> new Resolution.Unsupported("I didn't understand \"" + i.text() + "\", and I won't guess at an action.");
        };
    }

    private static Resolution ready(ActionRequest r) { return new Resolution.Ready(r); }

    private Resolution openApp(String name, String utterance) {
        AppService.Resolution r = apps.resolve(name);
        if (r instanceof AppService.Resolution.Match m) return ready(openAppReq(m.entry(), utterance));
        if (r instanceof AppService.Resolution.Ambiguous a) return appChoice("Which application did you mean by \"" + name + "\"?", a.options(), name, utterance);
        Optional<Path> folder = folders.resolve(name);
        if (folder.isPresent()) return ready(req("folder.open", utterance, "Open folder " + folder.get().getFileName(), "path", folder.get().toString()));
        // Unknown phrase: offer favourites, then recently used applications, and offer to remember the choice.
        List<AppEntry> offer = new ArrayList<>(apps.favorites());
        for (String recent : apps.recentNames(8)) {
            apps.apps().stream().filter(a -> a.name().equals(recent) && !offer.contains(a)).findFirst().ifPresent(offer::add);
        }
        if (offer.isEmpty()) return new Resolution.Unsupported("I couldn't find an application called \"" + name + "\" on this computer.");
        return appChoice("I don't know \"" + name + "\" yet. Which application should I use?", offer.stream().limit(8).toList(), name, utterance);
    }

    private static ActionRequest openAppReq(AppEntry e, String utterance) {
        return req("app.open", utterance, "Open " + e.name(), "appId", e.id());
    }

    private Resolution appChoice(String prompt, List<AppEntry> options, String phrase, String utterance) {
        List<Option> opts = options.stream().map(e -> new Option(e.name(), openAppReq(e, utterance).withSource(Source.USER_CHOICE), e.id())).toList();
        return new Resolution.Choice(prompt, opts, phrase);
    }

    private Resolution openFile(String name, String utterance) {
        FileSearchService.SearchResult found = search.searchByName(name, folders.searchRoots());
        if (found.matches().isEmpty()) return new Resolution.Unsupported("I couldn't find a file matching \"" + name + "\" in your documents, desktop or downloads.");
        if (found.matches().size() == 1) return ready(req("file.open", utterance, "Open " + found.matches().get(0).getFileName(), "path", found.matches().get(0).toString()));
        List<Option> opts = found.matches().stream().limit(8)
                .map(p -> new Option(p.toString(), req("file.open", utterance, "Open " + p.getFileName(), "path", p.toString()).withSource(Source.USER_CHOICE), null)).toList();
        return new Resolution.Choice("Several files match \"" + name + "\". Which one?", opts, null);
    }

    private Resolution openRecentProject(String when, String utterance) {
        Optional<DevProjectRepository.Project> p = when.equals("yesterday") ? projects.openedYesterday() : projects.mostRecent();
        if (p.isEmpty()) return new Resolution.Unsupported(when.equals("yesterday")
                ? "I have no record of a project you opened yesterday. Drift only knows projects opened through it."
                : "I have no record of a recent project yet.");
        return ready(req("project.open", utterance, "Open project " + p.get().name(), "path", p.get().path()));
    }

    private Resolution openProject(String name, String utterance) {
        List<DevProjectRepository.Project> known = projects.matching(name);
        List<String> paths = new ArrayList<>(known.stream().map(DevProjectRepository.Project::path).toList());
        if (paths.isEmpty()) {
            for (Path p : search.findJavaProjects(folders.searchRoots()).matches()) {
                String n = p.getFileName().toString().toLowerCase(Locale.ROOT);
                if (n.contains(name.toLowerCase(Locale.ROOT).replace(' ', '-')) || n.replace('-', ' ').contains(name.toLowerCase(Locale.ROOT))) paths.add(p.toString());
            }
        }
        if (paths.isEmpty()) return new Resolution.Unsupported("I couldn't find a project called \"" + name + "\".");
        if (paths.size() == 1) return ready(req("project.open", utterance, "Open project " + Path.of(paths.get(0)).getFileName(), "path", paths.get(0)));
        List<Option> opts = paths.stream().limit(8).map(p -> new Option(p, req("project.open", utterance, "Open project " + Path.of(p).getFileName(), "path", p).withSource(Source.USER_CHOICE), null)).toList();
        return new Resolution.Choice("Several projects match \"" + name + "\". Which one?", opts, null);
    }

    private Resolution openTerminal(String utterance) {
        Path dir = projects.mostRecent().map(p -> Path.of(p.path())).orElse(Path.of(System.getProperty("user.home")));
        return ready(req("dev.terminal", utterance, "Open a terminal in " + dir.getFileName(), "path", dir.toString()));
    }

    private Resolution runDevTask(String task, String utterance) {
        Optional<DevTemplates.Template> t = templates.find(task);
        if (t.isEmpty()) return new Resolution.Unsupported("There is no approved command template for \"" + task + "\" yet, so I can't start the application that way.");
        Optional<DevProjectRepository.Project> project = projects.mostRecentApproved();
        if (project.isEmpty()) return new Resolution.Unsupported("No approved development project. Building runs a project's own build scripts, so add and approve one in Settings > Developer first.");
        Path dir = Path.of(project.get().path());
        String exact = String.join(" ", templates.commandLine(t.get(), dir, com.drift.windows.PlatformInfo.isWindows()));
        String confirm = t.get().requiresConfirmation() ? "Run `" + exact + "` in " + dir + "?" : null;
        return ready(reqWithConfirm("dev.run", utterance, t.get().description() + " in " + project.get().name() + " (`" + exact + "`)", confirm,
                "task", t.get().id(), "projectPath", dir.toString()));
    }

    private Resolution setTheme(String spoken, String utterance) {
        String q = spoken.toLowerCase(Locale.ROOT).replace("theme", "").strip();
        List<String> names = themes.all().keySet().stream().filter(n -> n.toLowerCase(Locale.ROOT).replace("drift", "").strip().equals(q) || n.toLowerCase(Locale.ROOT).equals(q)).toList();
        if (names.isEmpty()) names = themes.all().keySet().stream().filter(n -> n.toLowerCase(Locale.ROOT).contains(q) && !q.isEmpty()).toList();
        if (names.size() == 1) return ready(req("theme.set", utterance, "Switch to the " + names.get(0) + " theme", "name", names.get(0)));
        return new Resolution.Unsupported("I couldn't find a single theme matching \"" + spoken + "\". Available: " + String.join(", ", themes.all().keySet()) + ".");
    }

    private Resolution remember(String phrase, String target, String utterance) {
        String p = AliasRepository.key(phrase);
        if (p.isEmpty() || p.length() > 80) return new Resolution.Unsupported("That phrase is too long or empty to remember.");
        AliasKind kind;
        String stored, label;
        AppService.Resolution r = apps.resolve(target);
        if (r instanceof AppService.Resolution.Match m) { kind = AliasKind.APP; stored = m.entry().id(); label = m.entry().name(); }
        else if (r instanceof AppService.Resolution.Ambiguous) return new Resolution.Unsupported("\"" + target + "\" matches several applications; say the full name.");
        else {
            Optional<Path> f = folders.resolve(target);
            if (f.isEmpty()) return new Resolution.Unsupported("I can only remember names for applications or folders I can find, and I couldn't find \"" + target + "\".");
            kind = AliasKind.FOLDER; stored = f.get().toString(); label = f.get().toString();
        }
        String confirm = null;
        Optional<AliasRepository.Alias> existing = learning.existing(kind, p);
        if (existing.isPresent() && !existing.get().target().equals(stored)) {
            confirm = "\"" + p + "\" already points to " + existing.get().target() + ". Replace it with " + label + "?";
        }
        return ready(reqWithConfirm("learn.remember", utterance, "Remember \"" + p + "\" as " + label, confirm,
                "kind", kind.name(), "phrase", p, "target", stored, "label", label));
    }
}
