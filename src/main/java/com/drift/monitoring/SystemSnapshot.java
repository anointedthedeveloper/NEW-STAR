package com.drift.monitoring;

import java.util.List;
import java.util.Optional;

/** One sampling of the system. Absent values mean "not available", never zero-as-placeholder. */
public record SystemSnapshot(Optional<Double> cpuPercent, long memoryUsedBytes, long memoryTotalBytes, Optional<Battery> battery,
                             Optional<Boolean> networkUp, List<Disk> disks, long sampledAtMillis) {
    public record Battery(double percent, boolean charging) {}
    public record Disk(String name, long usedBytes, long totalBytes) {}
}
