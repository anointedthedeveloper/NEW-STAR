package com.drift.diagnostics;

import com.drift.config.SettingsService;
import com.drift.core.DriftPaths;
import com.drift.history.OperationHistory;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** Enforces the retention limits for operation history and log files at startup. */
@Service
public class MaintenanceService {
    private static final Logger log = LoggerFactory.getLogger(MaintenanceService.class);
    private final OperationHistory history;
    private final SettingsService settings;
    private final DriftPaths paths;

    public MaintenanceService(OperationHistory history, SettingsService settings, DriftPaths paths) {
        this.history = history;
        this.settings = settings;
        this.paths = paths;
    }

    @PostConstruct
    public void run() {
        int h = history.purgeExpired();
        int l = purgeLogs(paths.logs(), settings.get().privacy().logRetentionDays());
        if (h + l > 0) log.info("Retention: removed {} history rows and {} old log files", h, l);
    }

    public static int purgeLogs(Path dir, int keepDays) {
        if (!Files.isDirectory(dir)) return 0;
        Instant cutoff = Instant.now().minus(keepDays, ChronoUnit.DAYS);
        int removed = 0;
        try (Stream<Path> s = Files.list(dir)) {
            for (Path p : s.filter(Files::isRegularFile).filter(f -> f.getFileName().toString().startsWith("drift")).toList()) {
                try {
                    if (Files.getLastModifiedTime(p).toInstant().isBefore(cutoff)) { Files.delete(p); removed++; }
                } catch (IOException ignored) {
                    // file in use: try next time
                }
            }
        } catch (IOException ignored) {
            // unreadable log folder
        }
        return removed;
    }
}
