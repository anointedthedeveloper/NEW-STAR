package com.drift.applications;

import com.drift.learning.AliasRepository;
import com.drift.learning.AliasRepository.AliasKind;
import com.drift.persistence.Database;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.stereotype.Service;

/** Application discovery, name resolution (aliases first), usage tracking and validated launching. */
@Service
public class AppService {
    public sealed interface Resolution {
        record Match(AppEntry entry) implements Resolution {}
        record Ambiguous(List<AppEntry> options) implements Resolution {}
        record None() implements Resolution {}
    }

    public static class LaunchException extends Exception {
        public LaunchException(String m) { super(m); }
    }

    private final AppCatalog catalog;
    private final AppLauncher launcher;
    private final AliasRepository aliases;
    private final Database db;
    private final AtomicReference<List<AppEntry>> cache = new AtomicReference<>();

    public AppService(AppCatalog catalog, AppLauncher launcher, AliasRepository aliases, Database db) {
        this.catalog = catalog;
        this.launcher = launcher;
        this.aliases = aliases;
        this.db = db;
    }

    public List<AppEntry> apps() {
        List<AppEntry> c = cache.get();
        if (c == null) { c = catalog.scan(); cache.set(c); }
        return c;
    }

    public void refresh() { cache.set(null); }

    public Optional<AppEntry> byId(String id) { return apps().stream().filter(a -> a.id().equals(id)).findFirst(); }

    public Resolution resolve(String spoken) {
        String phrase = spoken.toLowerCase(Locale.ROOT).strip();
        Optional<AliasRepository.Alias> alias = aliases.find(AliasKind.APP, phrase);
        if (alias.isPresent()) {
            Optional<AppEntry> target = apps().stream().filter(a -> a.id().equals(alias.get().target()) || a.name().equalsIgnoreCase(alias.get().target())).findFirst();
            if (target.isPresent()) { aliases.recordUse(alias.get().id()); return new Resolution.Match(target.get()); }
        }
        List<NameMatcher.Scored> ranked = NameMatcher.rank(phrase, apps());
        if (ranked.isEmpty()) return new Resolution.None();
        double top = ranked.get(0).score();
        List<AppEntry> near = ranked.stream().filter(s -> top - s.score() < 0.06).map(NameMatcher.Scored::entry).limit(5).toList();
        if (near.size() == 1) return new Resolution.Match(near.get(0));
        return new Resolution.Ambiguous(near);
    }

    /** Launches only entries discovered by the catalog - never an arbitrary path. */
    public void launch(String appId) throws LaunchException {
        AppEntry entry = byId(appId).orElseThrow(() -> new LaunchException("That application is not in Drift's discovered list"));
        launcher.launch(entry);
        db.update("INSERT INTO app_usage(path,name,launch_count,last_launched) VALUES(?,?,1,datetime('now')) "
                + "ON CONFLICT(path) DO UPDATE SET launch_count=launch_count+1,last_launched=datetime('now')", entry.path(), entry.name());
    }

    public List<String> recentNames(int limit) {
        return db.query("SELECT name FROM app_usage ORDER BY last_launched DESC LIMIT ?", rs -> rs.getString(1), limit);
    }

    public void setFavorite(AppEntry e, boolean favorite) {
        db.update("INSERT INTO app_usage(path,name,favorite) VALUES(?,?,?) ON CONFLICT(path) DO UPDATE SET favorite=excluded.favorite", e.path(), e.name(), favorite ? 1 : 0);
    }

    public List<AppEntry> favorites() {
        List<String> paths = db.query("SELECT path FROM app_usage WHERE favorite=1", rs -> rs.getString(1));
        return apps().stream().filter(a -> paths.contains(a.path())).toList();
    }
}
