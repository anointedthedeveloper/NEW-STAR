package com.drift;

import com.drift.applications.AppCatalog;
import com.drift.applications.AppLauncher;
import com.drift.applications.ShellAppLauncher;
import com.drift.applications.StartMenuAppCatalog;
import com.drift.core.DriftPaths;
import com.drift.files.DesktopSystemOpener;
import com.drift.files.FolderService;
import com.drift.files.PathValidator;
import com.drift.files.SystemOpener;
import com.drift.persistence.Database;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Production wiring of replaceable integrations. Tests construct fakes instead. */
@Configuration
public class DriftConfig {
    @Bean DriftPaths driftPaths() { return DriftPaths.resolve().ensureCreated(); }

    @Bean Database database(DriftPaths paths) { return new Database(paths.database()); }

    @Bean PathValidator pathValidator(FolderService folders) { return new PathValidator(folders::allowedRoots); }

    @Bean AppCatalog appCatalog() { return new StartMenuAppCatalog(StartMenuAppCatalog.defaultRoots()); }

    @Bean AppLauncher appLauncher() { return new ShellAppLauncher(); }

    @Bean SystemOpener systemOpener() { return new DesktopSystemOpener(); }
}
