package com.drift.actions;

import static com.drift.actions.ActionDefinition.Confirmation.NEVER;
import static com.drift.actions.ActionDefinition.optional;
import static com.drift.actions.ActionDefinition.required;

import com.drift.applications.AppService;
import com.drift.config.SettingsService;
import com.drift.dev.DevProjectRepository;
import com.drift.dev.DevTemplates;
import com.drift.dev.ProcessRunner;
import com.drift.files.FileSearchService;
import com.drift.files.FolderService;
import com.drift.files.PathValidator;
import com.drift.files.SystemOpener;
import com.drift.learning.AliasRepository.AliasKind;
import com.drift.learning.LearningService;
import com.drift.windows.PlatformInfo;
import jakarta.annotation.PostConstruct;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Registers the application, file, project, developer and learning actions. */
@Component
public class CoreActions {
    private final ActionRegistry registry;
    private final AppService apps;
    private final SystemOpener opener;
    private final PathValidator paths;
    private final FileSearchService search;
    private final FolderService folders;
    private final DevProjectRepository projects;
    private final DevTemplates templates;
    private final SettingsService settings;
    private final LearningService learning;

    public CoreActions(ActionRegistry registry, AppService apps, SystemOpener opener, PathValidator paths, FileSearchService search,
                       FolderService folders, DevProjectRepository projects, DevTemplates templates, SettingsService settings,
                       LearningService learning) {
        this.registry = registry;
        this.apps = apps;
        this.opener = opener;
        this.paths = paths;
        this.search = search;
        this.folders = folders;
        this.projects = projects;
        this.templates = templates;
        this.settings = settings;
        this.learning = learning;
    }

    @PostConstruct
    public void register() {
        registry.register(new ActionDefinition("app.open", "Open an installed application", List.of(required("appId", 64, "Id from the discovered application list")),
                Permission.LAUNCH, NEVER, Duration.ofSeconds(15), "none", (r, c) -> {
                    apps.launch(r.param("appId"));
                    return ActionResult.ok(r.summary());
                }));
        registry.register(new ActionDefinition("folder.open", "Open a folder", List.of(required("path", 1024, "Folder path")),
                Permission.LAUNCH, NEVER, Duration.ofSeconds(10), "none", (r, c) -> {
                    Path p = paths.validateDirectory(r.param("path"));
                    opener.open(p);
                    return ActionResult.ok("Opened " + p.getFileName());
                }));
        registry.register(new ActionDefinition("file.open", "Open a file with its associated application", List.of(required("path", 1024, "File path")),
                Permission.LAUNCH, NEVER, Duration.ofSeconds(10), "none", (r, c) -> {
                    Path p = paths.validateOpenableFile(r.param("path"));
                    opener.open(p);
                    return ActionResult.ok("Opened " + p.getFileName());
                }));
        registry.register(new ActionDefinition("file.search", "Search for files by name in your documents, desktop and downloads",
                List.of(required("query", 200, "Words that must appear in the file name")), Permission.FILE_READ, NEVER, Duration.ofSeconds(10),
                "data.matches: list of paths; data.truncated: boolean", (r, c) -> {
                    var res = search.searchByName(r.param("query"), folders.searchRoots());
                    List<String> m = res.matches().stream().map(Path::toString).toList();
                    String msg = m.isEmpty() ? "No files matched \"" + r.param("query") + "\"."
                            : "Found " + m.size() + (res.truncated() ? "+" : "") + " file(s): " + String.join(", ", m.stream().limit(5).map(s -> Path.of(s).getFileName().toString()).toList());
                    return ActionResult.ok(msg, Map.of("matches", m, "truncated", res.truncated()));
                }));
        registry.register(new ActionDefinition("project.find", "Find Maven/Gradle projects in the configured project roots", List.of(),
                Permission.FILE_READ, NEVER, Duration.ofSeconds(15), "data.matches: list of project paths", (r, c) -> {
                    var res = search.findJavaProjects(folders.searchRoots());
                    List<String> m = res.matches().stream().map(Path::toString).toList();
                    res.matches().forEach(projects::upsert);
                    String msg = m.isEmpty() ? "No Java projects found. Add project folders in Settings > Developer." : "Found " + m.size() + " project(s).";
                    return ActionResult.ok(msg, Map.of("matches", m, "truncated", res.truncated()));
                }));
        registry.register(new ActionDefinition("project.open", "Open a development project (in the configured IDE, else the file manager)",
                List.of(required("path", 1024, "Project folder")), Permission.LAUNCH, NEVER, Duration.ofSeconds(15), "none", (r, c) -> {
                    Path p = paths.validateDirectory(r.param("path"));
                    String ide = settings.get().developer().ide();
                    String how;
                    if (ide != null && !ide.isBlank() && java.nio.file.Files.isRegularFile(Path.of(ide))) {
                        opener.startDetached(List.of(ide, p.toString()), p);
                        how = "in your IDE";
                    } else {
                        opener.open(p);
                        how = "in the file manager (no IDE configured in Settings > Developer)";
                    }
                    projects.markOpened(p);
                    return ActionResult.ok("Opened project " + p.getFileName() + " " + how);
                }));
        registry.register(new ActionDefinition("dev.terminal", "Open a terminal in a folder", List.of(required("path", 1024, "Working directory")),
                Permission.LAUNCH, NEVER, Duration.ofSeconds(10), "none", (r, c) -> {
                    Path p = paths.validateDirectory(r.param("path"));
                    List<String> cmd = PlatformInfo.isWindows() ? List.of("cmd.exe", "/c", "start", "Drift terminal", "cmd.exe") : List.of("x-terminal-emulator");
                    opener.startDetached(cmd, p);
                    return ActionResult.ok("Opened a terminal in " + p.getFileName());
                }));
        registry.register(new ActionDefinition("dev.run", "Run an approved developer command template",
                List.of(required("task", 40, "Template id"), required("projectPath", 1024, "Project folder")), Permission.PROCESS, NEVER,
                Duration.ofHours(1), "data.exitCode, data.output, data.timedOut, data.cancelled", this::runDev));
        registry.register(new ActionDefinition("learn.remember", "Remember a spoken name for an application or folder",
                List.of(required("kind", 10, "APP or FOLDER"), required("phrase", 80, "Spoken phrase"), required("target", 1024, "App id or folder path"),
                        optional("label", 200, "Display label")), Permission.SETTINGS, NEVER, Duration.ofSeconds(5), "none", (r, c) -> {
                    AliasKind kind = AliasKind.valueOf(r.param("kind"));
                    if (kind == AliasKind.APP && apps.byId(r.param("target")).isEmpty()) return ActionResult.failure("That application is not available.");
                    if (kind == AliasKind.FOLDER) paths.validateDirectory(r.param("target"));
                    if (kind == AliasKind.PHRASE) return ActionResult.failure("Phrase aliases cannot be created by voice.");
                    learning.remember(kind, r.param("phrase"), r.param("target"));
                    return ActionResult.ok("Okay. \"" + r.param("phrase") + "\" now means " + r.param("label") + ".");
                }));
    }

    private ActionResult runDev(ActionRequest r, ActionHandler.Context ctx) throws Exception {
        DevTemplates.Template t = templates.find(r.param("task")).orElse(null);
        if (t == null) return ActionResult.failure("Unknown developer task.");
        Path dir = paths.validateDirectory(r.param("projectPath"));
        if (!projects.isApproved(dir.toString())) return ActionResult.failure("This project is not approved for build commands. Approve it in Settings > Developer.");
        if (!DevTemplates.isMavenProject(dir)) return ActionResult.failure("This folder has no pom.xml, so the Maven task cannot run.");
        List<String> cmd = templates.commandLine(t, dir, PlatformInfo.isWindows());
        int timeoutSeconds = settings.get().developer().commandTimeoutSeconds();
        ProcessRunner.Result res = new ProcessRunner(256 * 1024).run(cmd, dir, Duration.ofSeconds(timeoutSeconds), ctx.cancel(), ctx.progress());
        String msg = res.cancelled() ? "Cancelled." : res.timedOut() ? "Stopped after the " + timeoutSeconds + "s time limit."
                : t.description() + (res.success() ? " succeeded" : " failed") + " (exit code " + res.exitCode() + ").";
        return new ActionResult(res.success(), msg, Map.of("exitCode", res.exitCode(), "output", res.output(), "timedOut", res.timedOut(),
                "cancelled", res.cancelled(), "truncated", res.outputTruncated(), "command", String.join(" ", cmd)));
    }
}
