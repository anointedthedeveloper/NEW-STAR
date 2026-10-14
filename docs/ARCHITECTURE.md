# Architecture

One process, one Maven module (`com.drift`). A multi-module build was rejected: the boundaries below are enforced by packages and
interfaces, and splitting would only add ceremony.

```
JavaFX UI (ui/)  ──events──▶  StateController (ui/state, pure)        ┌─ Windows bridges (windows/, JNA)
   │  ▲                                                                │   hotkeys, window styles, SendInput, registry, wallpaper
   │  └── DriftEventBus ◀── services publish real events ──────────────┤
   ▼                                                                   └─ PowerShell media helper (media/, resources/bridge)
AppCoordinator ─▶ CommandOrchestrator ─▶ IntentParser ─▶ IntentResolver ─▶ SafetyPolicy ─▶ ActionRegistry ─▶ handlers
   ▲                    │ history, learning                                                    (CoreActions, SystemActions)
VoiceService ◀─ SpeechProvider (Vosk) / AudioSource (javax.sound)
SQLite (persistence/): settings, themes, widget layouts, aliases, learned commands, history, projects
```

- **Spring Boot** supplies dependency injection and lifecycle (no web server, no HTTP). JavaFX talks to services through plain Java
  calls (`UiServices`), so no loopback API exists to secure. UI code never contains command logic; command logic never imports JavaFX.
- **Events:** `DriftEventBus` carries `DriftEvent`s. The interface state (`IDLE LISTENING TRANSCRIBING PROCESSING EXECUTING MUSIC NOTIFICATION
  ASSISTANT ERROR`) is a pure function of those events (`StateController`), so animations (waveform from measured mic levels, progress,
  mic indicator) can only appear when the application reported the activity.
- **Threading:** speech capture and recognition run on their own threads with a bounded queue; commands execute on virtual threads with
  per-action timeouts; the JavaFX thread only renders (`Fx.ui`). Widgets read cached data or do network/IO off-thread.
- **Replaceable integrations** (interfaces; fakes in tests, honest "unavailable" implementations in production when impossible):
  `SpeechProvider`, `AudioSource`, `MediaProvider`, `SystemMonitor`, `AppCatalog`, `AppLauncher`, `SystemOpener`, `TextInserter`,
  `ForegroundWindow`, `StartupRegistrar`, `WallpaperSetter`.
- **Persistence:** SQLite via a small JDBC helper; versioned migrations `db/migration/V{n}__*.sql` applied in order, each in a transaction.
  Settings are JSON in `kv`, merged over defaults on load so adding fields never breaks old data. Retention: history by days (default 30),
  logs by days (default 7, applied at startup) plus size-capped rolling log files.
- **Command memory schema** (V2): `aliases(kind, phrase, target, usage, confidence, disabled)`, `learned_commands(utterance, action, params,
  success/reject counts)`, `corrections`, `workflows`. A smarter local intent model can later sit in front of `IntentResolver` (it only has to
  return an `Intent`), without touching safety or persistence.

## Floating interface and widgets

- The floating UI is a transparent undecorated JavaFX `Stage`. On Windows, `WindowStyler` applies `WS_EX_NOACTIVATE | WS_EX_TOOLWINDOW` so
  clicking it does not steal focus (relaxed only while the user types into the assistant box) and, if enabled, `WS_EX_TRANSPARENT` for idle
  click-through. The window is found by its unique title. **Unverified on Windows.**
- Placement (`WindowPlacement`, pure and tested) is display-aware: anchors, multi-monitor clamp, fallback when a monitor disappears,
  edge snapping, and growing leftwards when expanded at the right edge.
- **Widgets** are small transparent always-on-top windows, one per widget, with drag, resize grip, context menu and per-widget settings.
  Embedding widgets in the actual desktop (WorkerW trick) is **not** attempted: it is undocumented and breaks across Windows builds. Documented
  limitation: widgets float above other windows; they can be toggled off "always on top" in Settings > Widgets.
- Widget extension contract: add a `WidgetType` constant (id, defaults), a `WidgetView` in `ui/WidgetViews` (`node/start/stop/resized`), a case
  in `WidgetViews.create`. Configuration (`WidgetConfig`) is validated data, never code.

## Native dependencies

| Dependency | Use | Native part |
|---|---|---|
| JavaFX 21 | UI | platform jars (`-win`, `-linux`) chosen by Maven from the build OS - **build on Windows for the Windows installer** |
| JNA 5.15 + jna-platform | Win32 calls | loads `user32`, `advapi32`, `kernel32` already on Windows |
| Vosk 0.3.45 | offline speech | `libvosk.dll` and its MinGW runtime DLLs bundled in the Vosk jar; extracted at runtime |
| sqlite-jdbc | SQLite | bundled native per platform |
| OSHI | system info | uses JNA + Windows performance counters |
| PowerShell 5.1 (inbox on Windows) | media bridge (WinRT `GlobalSystemMediaTransportControls`) | none to install |

## Windows vs. other systems

Drift detects the OS (`PlatformInfo`) and reports Windows 10/11 build in Settings > Diagnostics. Off Windows: app discovery, typing, media,
startup, wallpaper, hotkeys are unavailable and say so; everything else (UI, settings, widgets, speech, learning) runs.
