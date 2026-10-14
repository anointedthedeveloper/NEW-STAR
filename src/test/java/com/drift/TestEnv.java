package com.drift;

import com.drift.actions.ActionRegistry;
import com.drift.actions.CommandOrchestrator;
import com.drift.actions.CoreActions;
import com.drift.actions.IntentResolver;
import com.drift.actions.SafetyPolicy;
import com.drift.applications.AppCatalog;
import com.drift.applications.AppEntry;
import com.drift.applications.AppLauncher;
import com.drift.applications.AppService;
import com.drift.config.SettingsService;
import com.drift.core.DriftEvent;
import com.drift.core.DriftEventBus;
import com.drift.dev.DevProjectRepository;
import com.drift.dev.DevTemplates;
import com.drift.files.FileSearchService;
import com.drift.files.FolderService;
import com.drift.files.PathValidator;
import com.drift.files.SystemOpener;
import com.drift.history.OperationHistory;
import com.drift.intents.IntentParser;
import com.drift.learning.AliasRepository;
import com.drift.learning.LearnedCommandRepository;
import com.drift.learning.LearningService;
import com.drift.persistence.Database;
import com.drift.themes.ThemeService;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** Wires the real services against a temporary SQLite database with fake OS integrations. */
public class TestEnv {
    public final Database db;
    public final DriftEventBus bus = new DriftEventBus();
    public final SettingsService settings;
    public final AliasRepository aliases;
    public final LearningService learning;
    public final List<AppEntry> catalog = new ArrayList<>();
    public final List<String> launched = new CopyOnWriteArrayList<>();
    public final List<String> opened = new CopyOnWriteArrayList<>();
    public final List<List<String>> started = new CopyOnWriteArrayList<>();
    public final List<DriftEvent> events = new CopyOnWriteArrayList<>();
    public final AppService apps;
    public final FolderService folders;
    public final DevProjectRepository projects;
    public final OperationHistory history;
    public final ThemeService themes;
    public final ActionRegistry registry = new ActionRegistry();
    public final CommandOrchestrator orchestrator;
    public final PathValidator paths;

    public TestEnv(Path dir) {
        db = new Database(dir.resolve("test.db"));
        settings = new SettingsService(db, bus);
        aliases = new AliasRepository(db);
        learning = new LearningService(aliases, new LearnedCommandRepository(db), db);
        AppCatalog cat = () -> List.copyOf(catalog);
        AppLauncher launcher = e -> launched.add(e.name());
        apps = new AppService(cat, launcher, aliases, db);
        folders = new FolderService(aliases, settings);
        projects = new DevProjectRepository(db);
        history = new OperationHistory(db, settings);
        themes = new ThemeService(db, settings);
        SystemOpener opener = new SystemOpener() {
            public void open(Path p) { opened.add(p.toString()); }
            public void startDetached(List<String> c, Path d) { started.add(c); }
        };
        paths = new PathValidator(() -> {
            List<Path> roots = new ArrayList<>(folders.allowedRoots());
            roots.add(dir);
            return roots;
        });
        var templates = new DevTemplates();
        var search = new FileSearchService();
        new CoreActions(registry, apps, opener, paths, search, folders, projects, templates, settings, learning).register();
        var resolver = new IntentResolver(apps, folders, search, projects, templates, themes, learning);
        orchestrator = new CommandOrchestrator(new IntentParser(), resolver, registry, new SafetyPolicy(settings), history, learning, bus);
        bus.subscribe(events::add);
    }

    public AppEntry addApp(String name) {
        AppEntry e = new AppEntry("id-" + name.toLowerCase().replace(' ', '-'), name, "C:\\fake\\" + name + ".lnk");
        catalog.add(e);
        apps.refresh();
        return e;
    }
}
