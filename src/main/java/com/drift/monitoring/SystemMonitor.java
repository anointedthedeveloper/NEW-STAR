package com.drift.monitoring;

public interface SystemMonitor {
    SystemSnapshot sample();
    default void close() { }
}
