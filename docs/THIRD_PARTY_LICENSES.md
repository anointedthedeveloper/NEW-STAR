# Third-party components

Versions are those in `pom.xml` at the time of writing. **Check the licence texts of the exact artifacts you redistribute**; this table is a
summary, not legal advice.

| Component | Version | Licence (summary) |
|---|---|---|
| OpenJFX (JavaFX) | 21.0.5 | GPLv2 + Classpath Exception |
| Spring Boot / Spring Framework | 3.3.5 / 6.1.x | Apache-2.0 |
| Jackson | 2.17.x | Apache-2.0 |
| sqlite-jdbc (Xerial) / SQLite | 3.46.1.3 | Apache-2.0 / public domain |
| JNA, jna-platform | 5.15.0 | Apache-2.0 or LGPL-2.1 (dual) |
| Vosk API | 0.3.45 | Apache-2.0 (bundled native libs include MinGW/GCC runtime components - review `libgcc`/`libstdc++`/`libwinpthread` terms when redistributing) |
| Vosk models | user-installed | per model (many are Apache-2.0; some are not). Not distributed by Drift |
| OSHI | 6.6.5 | MIT |
| Logback / SLF4J | 1.5.11 / 2.0.16 | EPL-1.0 or LGPL-2.1 / MIT |
| Micrometer, SnakeYAML, Log4j API bridge | transitive | Apache-2.0 |

The installer is produced with the JDK's `jpackage` (GPLv2+CPE JDK) and bundles a trimmed Java runtime (see the JDK distribution's licence).
