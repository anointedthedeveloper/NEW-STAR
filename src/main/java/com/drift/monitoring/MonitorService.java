package com.drift.monitoring;

import com.drift.config.SettingsService;
import com.drift.core.DriftEvent;
import com.drift.core.DriftEventBus;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Service;

/** Opt-in sampling. When monitoring is disabled in settings no sampling thread work is scheduled at all. */
@Service
public class MonitorService {
    private final SystemMonitor monitor;
    private final SettingsService settings;
    private final DriftEventBus bus;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> Thread.ofPlatform().daemon().name("drift-monitor").unstarted(r));
    private ScheduledFuture<?> task;
    private volatile SystemSnapshot latest;
    private int scheduledInterval = -1;

    public MonitorService(SystemMonitor monitor, SettingsService settings, DriftEventBus bus) {
        this.monitor = monitor;
        this.settings = settings;
        this.bus = bus;
    }

    @PostConstruct
    void init() {
        apply();
        bus.subscribe(e -> { if (e instanceof DriftEvent.SettingsChanged) apply(); });
    }

    public boolean enabled() { return settings.get().monitoring().enabled(); }

    /** Latest sample, or empty when monitoring is disabled or nothing has been sampled yet. */
    public Optional<SystemSnapshot> latest() { return enabled() ? Optional.ofNullable(latest) : Optional.empty(); }

    public synchronized void apply() {
        var m = settings.get().monitoring();
        if (!m.enabled()) {
            if (task != null) { task.cancel(false); task = null; }
            latest = null;
            scheduledInterval = -1;
            return;
        }
        if (task != null && scheduledInterval == m.intervalSeconds()) return;
        if (task != null) task.cancel(false);
        scheduledInterval = m.intervalSeconds();
        task = scheduler.scheduleWithFixedDelay(() -> {
            try { latest = monitor.sample(); } catch (RuntimeException ignored) { /* keep the previous sample */ }
        }, 0, m.intervalSeconds(), TimeUnit.SECONDS);
    }

    public boolean scheduled() { return task != null; }

    @PreDestroy public void shutdown() { scheduler.shutdownNow(); monitor.close(); }
}
