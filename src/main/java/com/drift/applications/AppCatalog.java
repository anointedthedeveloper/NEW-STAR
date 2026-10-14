package com.drift.applications;

import java.util.List;

/** Source of launchable applications. Replaceable (real Start Menu scan vs test fakes). */
public interface AppCatalog {
    List<AppEntry> scan();
}
