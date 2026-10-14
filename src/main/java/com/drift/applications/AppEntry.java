package com.drift.applications;

/** An application Drift may launch. {@code id} is stable (derived from the path) and is what action requests carry. */
public record AppEntry(String id, String name, String path) {}
