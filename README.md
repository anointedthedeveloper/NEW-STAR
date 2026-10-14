# Drift

**A customizable desktop companion for Windows. Your desktop. Your rules.**

![Drift](packaging/drift.png)

Drift is a native Windows desktop application (Java 21 + JavaFX + Spring Boot + SQLite) that combines:

- a floating, always-available interface (a real transparent desktop window) with nine live states,
- offline voice commands and explicit dictation (Vosk - no cloud, no API key),
- an action system that can only do what is on an approved list,
- a personal command memory (aliases and preferences) that stays on your PC,
- media info and controls from Windows media sessions,
- floating desktop widgets (clocks, calendar, weather, system monitor, battery, media, quick launch, notes, timer, countdown),
- themes, icon/wallpaper packs, and a full settings application.

> **Status:** the core is built and tested, but it was developed on Linux. The Windows-only integrations compile and have
> honest fallbacks, but have **not** yet run on Windows. Read [`docs/IMPLEMENTATION_STATUS.md`](docs/IMPLEMENTATION_STATUS.md)
> for exactly what is verified and what is not before relying on any feature.

## Requirements

- Windows 10 or 11 (Windows 11 is the primary target). Other systems can run the tests and most of the UI, but features that
  need Windows say so.
- To build: JDK 21. For the installer: WiX Toolset (preinstalled on GitHub's `windows-latest` runners).
- For voice: a Vosk model (see [`docs/SPEECH_SETUP.md`](docs/SPEECH_SETUP.md)). Models are not shipped.

## Build, test, run

```powershell
.\mvnw.cmd -B verify          # build + tests (use ./mvnw on Linux/macOS)
.\mvnw.cmd -B -DskipTests package
java -cp "target/drift.jar;target/lib/*" com.drift.DriftLauncher      # run from the build output
```

Use `-Ddrift.home=<folder>` to point Drift at a different data folder (handy for experiments).

Tests that need extras are skipped unless enabled: JavaFX desktop tests need a display (`xvfb-run -a mvn test` on Linux);
real recognition needs `DRIFT_TEST_MODELS_DIR` + `DRIFT_TEST_WAV`; the real-Maven test needs `DRIFT_TEST_MAVEN=1`.

## Installing / releasing

```powershell
.\scripts\package.ps1                    # installer (.exe), per-user install, no admin rights
.\scripts\package.ps1 -Type app-image    # portable folder, no WiX needed
```

The installer is per-user, adds a Start Menu entry, and installs to a folder of your choice. Uninstall from Windows Settings > Apps;
this removes the program but **keeps your data** in `%APPDATA%\Drift` (see [`docs/PRIVACY.md`](docs/PRIVACY.md) to delete it).
The installer is unsigned. Release checklist: bump `<version>` in `pom.xml`, run CI, install the artifact on a clean Windows 11 VM,
walk through the verification table in `docs/IMPLEMENTATION_STATUS.md`, then publish.

## Using Drift

1. Start Drift. The compact pill appears on the right edge of your main screen; drag it anywhere, it snaps to screen edges.
   The tray icon and `ctrl+alt+n` show/hide it.
2. **Voice:** install a model first (Settings > Voice recognition). Then hold `ctrl+alt+space` (or the microphone button) and speak.
   The microphone indicator is on only while you hold it.
3. **Typing commands:** click the chevron to open the assistant and type, e.g. `open my downloads folder`.
4. **Teach it names:** say or type *"Remember that I call Visual Studio Code my coding editor"*. Or just say *"open my coding editor"* -
   Drift asks which app you mean and can remember the answer. Manage everything in Settings > Personal learning.
5. **Dictation:** say *"start dictation"* (or `ctrl+alt+d`), speak, stop. You get a preview naming the window it will type into;
   nothing is typed until you press **Insert**.
6. **Developer mode** (off by default): Settings > Developer > enable, add and **approve** a project folder, then *"run the maven tests"*.

Things you can say: *open Visual Studio Code, open my downloads folder, find my Java projects, open the project I worked on yesterday,
type hello world (preview first), run the approved Maven tests, what song is playing, pause the music, switch to my coding theme,
make the widgets more transparent, show the clock, remember that I call X my Y.* Anything else gets an honest "I can't do that".

## What it will never do

Run arbitrary commands from speech, type into a window without a preview, record without the visible mic indicator, upload audio,
open executables/scripts by voice, or learn privileged actions. See [`SECURITY.md`](SECURITY.md).

## Troubleshooting

| Symptom | Fix |
|---|---|
| "Speech recognition unavailable - MODEL_MISSING" | Install a Vosk model into the models folder shown in Settings > Voice recognition, press Refresh |
| "Microphone unavailable" | Windows Settings > Privacy & security > Microphone: allow desktop apps; pick the device in Settings > Voice recognition |
| Shortcut does nothing / "already used by another program" | Change it in Settings > Keyboard shortcuts |
| Interface disappeared | Tray icon > Show, or your toggle shortcut. If click-through is on, that is the only way back |
| No tray icon | Your system tray is unavailable or tray is disabled; Drift then stays visible |
| Media widget says unavailable | Needs Windows 10/11 media sessions; the player must register with Windows media controls |
| Developer command refused | Turn on Developer mode and approve the project folder in Settings > Developer |
| Weather "unavailable" | Enable it and set latitude/longitude in Settings > Widgets; needs internet |
| Where are my files/logs? | Settings > Diagnostics > Open data/logs folder (`%APPDATA%\Drift`) |
| JavaFX prints "Unsupported JavaFX configuration" | Harmless: Drift runs JavaFX from the classpath |

## Documentation

[`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) | [`docs/SPEECH_SETUP.md`](docs/SPEECH_SETUP.md) | [`docs/PRIVACY.md`](docs/PRIVACY.md) |
[`docs/THIRD_PARTY_LICENSES.md`](docs/THIRD_PARTY_LICENSES.md) | [`docs/IMPLEMENTATION_STATUS.md`](docs/IMPLEMENTATION_STATUS.md) | [`SECURITY.md`](SECURITY.md)
