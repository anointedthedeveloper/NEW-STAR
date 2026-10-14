package com.drift.actions;

public enum Permission {
    /** No effect outside Drift's own state/UI. */
    SAFE,
    /** Starts an application or opens a folder/file in its associated program. */
    LAUNCH,
    /** Reads user file listings. */
    FILE_READ,
    /** Sends keystrokes to another application. */
    INPUT,
    /** Runs a process (developer mode only). */
    PROCESS,
    /** Changes Drift settings or learned data. */
    SETTINGS
}
