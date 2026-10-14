# Drift - implementation status

Last updated at the end of the first implementation session. This file separates **what was verified, and how**, from
**what is implemented but could not be verified here**. The development sandbox was Linux (no Windows, no microphone,
no media player), so every Windows-only feature is in the second group.

Run summary: 100 tests, 0 failures, with the display, Vosk-model and Maven tests enabled (`xvfb-run -a mvn verify` plus the env vars listed under "Commands run"). Without those extras the display/model/Maven tests skip themselves.

## Verified (automated tests or direct runs, on Linux + Xvfb)

| Area | Evidence |
|---|---|
| Maven build, flat jar + `lib/`, `jpackage --type app-image` | `scripts/package.sh`; the packaged `Drift` launcher was started as a plain process (no IDE/Maven), created its data folder/DB/logs, and a second launch exited with "already running" |
| SQLite persistence + versioned migrations (V1-V3) | `PersistenceTest` (migrations idempotent, schema present, settings restore after reopening DB, defaults fill missing fields, invalid settings rejected, reset) |
| Settings validation | `PersistenceTest.invalidSettingsAreRejectedWithoutChangingAnything` |
| Intent parsing for every example command in the brief | `IntentParserTest` |
| Typed pipeline: normalise -> parse -> resolve -> validate -> safety -> confirm -> execute -> record -> reply | `OrchestratorTest` (13 tests) |
| App launch via discovered catalog only (mocked launcher), alias learning from a user choice, **persists across a restart and can be deleted** | `OrchestratorTest.learnsAliasFromUserChoiceAndResolvesItAfterRestart` |
| Ask-before-replace for an existing alias | `OrchestratorTest.rememberByVoice...` |
| Arbitrary spoken commands are never run | `OrchestratorTest.arbitraryCommandsAreNeverExecuted` |
| Developer mode off -> refused; destructive template needs confirmation showing the exact command; unapproved projects never built | `OrchestratorTest` |
| Real Maven run through the pipeline with real output + exit status (success=0, compile error=1) | `DevCommandIntegrationTest` (run with `DRIFT_TEST_MAVEN=1`) |
| Process runner: output, exit code, timeout, cancellation, bounded output | `ProcessRunnerTest` |
| Path validation: traversal, symlink escape, executables/scripts refused, excluded dirs | `PathValidatorTest` |
| Learning rules: privileged actions can't be learned; export/import/reset | `OrchestratorTest`, `PacksWidgetsTest` |
| **Real offline speech recognition** with Vosk + `vosk-model-small-en-us-0.15` on a 16 kHz WAV (confidence reported) | `VoskIntegrationTest` (needs `DRIFT_TEST_MODELS_DIR`/`DRIFT_TEST_WAV`); result: transcribed the sample, confidence 0.88 |
| Missing-model state is explicit with install instructions | `VoskIntegrationTest.reportsMissingModelHonestly`, Settings > Voice screenshot |
| Capture/recognition pipeline: bounded queue, push-to-talk, device failure + reopen, permanent loss, time cap, no capture when model missing | `VoiceServiceTest` (fake mic + fake engine; test-only classes) |
| Dictation: preview before typing, target-window check, punctuation commands, undo hook, refused where unsupported | `MediaDictationTest` (fake inserter) |
| Media layer: protocol with a helper process, capability-gated controls, crash restart, empty/unavailable states | `MediaDictationTest` (stand-in helper script; **not** the Windows API) |
| Interface state machine (9 states) driven only by events; auto-dismiss; errors persist | `StateControllerTest` |
| Window placement: anchors, multi-monitor clamp, unplugged monitor fallback, edge snap, grow-left near right edge | `WindowPlacementTest` |
| Real JavaFX windows: floating window creation, state transitions from events, hide/show, settings applied live, "disable floating interface", 11 widgets created and running, clock shows system time, honest empty states, widget removal/persistence, all 15 settings sections build | `DesktopSmokeTest` (Xvfb); screenshots written to `target/screenshots` and inspected |
| Widget config/layout validation, presets, restore previous | `PacksWidgetsTest` |
| Theme serialisation/import validation (size cap, injection attempts), light/dark | `PersistenceTest`, `ThemeAndMaintenanceTest` |
| Icon/wallpaper pack import: manifest, type allow-list, magic bytes, traversal, size bombs, duplicates, apply/revert (mock wallpaper API) | `PacksWidgetsTest` |
| Monitoring is opt-in and doesn't sample when disabled; OSHI returns real memory | `PacksWidgetsTest` |
| Weather: unavailable when not configured/offline, no network call unless enabled | `WidgetLogicTest` |
| Retention (history, logs), redaction, transcripts off by default | `ThemeAndMaintenanceTest`, `MiscTest` |

## Implemented but NOT verified (needs a Windows machine)

These compile, sit behind small interfaces, and fall back honestly when unavailable, but have never run on Windows.

| Feature | Code | What must be checked |
|---|---|---|
| Real microphone capture | `JavaSoundAudioSource` | device list, selection, permission denial message, 16 kHz capture |
| Vosk native library on Windows | `VoskSpeechProvider` | `libvosk.dll` loads from the jar; recognition with a real mic |
| Global shortcuts + push-to-talk release | `GlobalHotkeys` (RegisterHotKey, GetAsyncKeyState) | hotkeys fire, conflicts are reported, key release stops listening |
| Non-activating floating window / click-through | `WindowStyler` (WS_EX_NOACTIVATE/TRANSPARENT via JNA) | clicking the pill does not steal focus; assistant text box can type; click-through can always be undone from tray/hotkey |
| Start menu app discovery and launch | `StartMenuAppCatalog`, `ShellAppLauncher` | shortcuts listed, `Desktop.open` launches them |
| Typing into other apps | `WindowsTextInserter` (SendInput), `WindowsForegroundWindow` | text lands in Notepad/VS Code; elevated targets are reported |
| Start with Windows | `WindowsStartupRegistrar` (HKCU Run) | enable/disable from the installed app |
| Wallpaper apply/revert | `WindowsWallpaperSetter` | SPI calls work, revert restores |
| System volume keys | `MediaKeys` | volume changes |
| **Media sessions (track info, controls, artwork)** | `resources/bridge/media-bridge.ps1` + `BridgeMediaProvider` | the WinRT PowerShell script runs on Windows PowerShell 5.1, lists sessions, controls Spotify/browser/etc. |
| Tray icon, minimize-to-tray | `TrayController` (AWT) | icon, menu, show/hide |
| Windows version detection | `PlatformInfo` | Windows 10 vs 11 reporting |
| **Installer** | `scripts/package.ps1` (jpackage + WiX), `.github/workflows/build.yml` | installer builds, installs per-user, launches, uninstalls; first CI run on `windows-latest` has not happened |
| Maven wrapper download | `mvnw`, `mvnw.cmd` | could not download Maven in the sandbox (proxy); verify on a normal machine |

## Not implemented (by design or deferred)

- **Wake word**: not implemented and not claimed. Push-to-talk and explicit dictation only.
- **Desktop-embedded (WorkerW) widgets**: not attempted (undocumented, fragile). Widgets are a floating always-on-top layer.
- **Workflow templates engine**: the `workflows` table exists (migration V2) but there is no execution engine or UI. Developer mode uses fixed command templates only.
- **User corrections UI**: corrections are stored (`corrections` table) but nothing in the UI creates them yet; learned commands can be deleted/disabled.
- **Active application / OS notification capture**: the "Notification" state shows Drift's own events only; Windows notifications are not read.
- **Credential store**: nothing in Drift currently needs a secret (weather uses a keyless provider), so no Credential Manager integration exists.
- **Per-app volume**: not exposed by Windows media sessions; only system volume keys.
- **Icon-pack effect on Windows shell icons**: not attempted; an applied icon pack is a stored setting for Drift's own UI.
- **Auto-update**: none (documented in Settings > Updates).
- **jpackage on Windows with a signed installer**: unsigned; Windows SmartScreen will warn.

## Commands run (sandbox)

```
mvn -B test                                  # all unit/integration tests
xvfb-run -a mvn -B test                      # + JavaFX desktop tests
DRIFT_TEST_MODELS_DIR=... DRIFT_TEST_WAV=... mvn test -Dtest=VoskIntegrationTest
DRIFT_TEST_MAVEN=1 mvn test -Dtest=DevCommandIntegrationTest
./scripts/package.sh                         # jpackage app-image (Linux) + manual launch test
```

## Next concrete tasks

1. Run `.github/workflows/build.yml` on `windows-latest`; fix whatever fails; download and install the produced `.exe`.
2. On a Windows 11 machine walk through the "Implemented but NOT verified" table above, in order: launch, floating window focus behaviour, tray, hotkeys, mic + Vosk, app launch, dictation into Notepad, media bridge, installer.
3. Add a UI for recording corrections and for choosing alternatives when a learned mapping is wrong.
4. Decide on workflow templates (build the engine or remove the table).
5. Code-sign the installer.
