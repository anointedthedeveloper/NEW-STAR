package com.drift.monitoring;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import oshi.SystemInfo;
import oshi.hardware.CentralProcessor;
import oshi.hardware.GlobalMemory;
import oshi.hardware.NetworkIF;
import oshi.hardware.PowerSource;

/** Reads CPU, memory, battery, network and disk usage through OSHI (which uses documented OS APIs / performance counters). */
public class OshiSystemMonitor implements SystemMonitor {
    private final SystemInfo si = new SystemInfo();
    private final CentralProcessor cpu = si.getHardware().getProcessor();
    private long[] prevTicks = cpu.getSystemCpuLoadTicks();

    @Override
    public synchronized SystemSnapshot sample() {
        Optional<Double> load = Optional.empty();
        try {
            double l = cpu.getSystemCpuLoadBetweenTicks(prevTicks) * 100.0;
            prevTicks = cpu.getSystemCpuLoadTicks();
            if (l >= 0 && l <= 100) load = Optional.of(l);
        } catch (RuntimeException ignored) {
            // leave unavailable
        }
        GlobalMemory mem = si.getHardware().getMemory();
        Optional<SystemSnapshot.Battery> battery = Optional.empty();
        try {
            for (PowerSource ps : si.getHardware().getPowerSources()) {
                if (ps.getMaxCapacity() > 0 || ps.getRemainingCapacityPercent() > 0) {
                    battery = Optional.of(new SystemSnapshot.Battery(ps.getRemainingCapacityPercent() * 100.0, ps.isCharging()));
                    break;
                }
            }
        } catch (RuntimeException ignored) {
            // no battery info
        }
        Optional<Boolean> net = Optional.empty();
        try {
            boolean up = false;
            for (NetworkIF n : si.getHardware().getNetworkIFs()) {
                if (n.getIfOperStatus() == NetworkIF.IfOperStatus.UP && !n.isKnownVmMacAddr() && n.getIPv4addr().length > 0
                        && !n.getName().toLowerCase().startsWith("lo")) up = true;
            }
            net = Optional.of(up);
        } catch (RuntimeException ignored) {
            // unknown
        }
        List<SystemSnapshot.Disk> disks = new ArrayList<>();
        for (File root : File.listRoots()) {
            long total = root.getTotalSpace();
            if (total > 0) disks.add(new SystemSnapshot.Disk(root.getPath(), total - root.getUsableSpace(), total));
        }
        return new SystemSnapshot(load, mem.getTotal() - mem.getAvailable(), mem.getTotal(), battery, net, disks, System.currentTimeMillis());
    }
}
