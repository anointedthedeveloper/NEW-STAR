# Drift security model

Drift can control a user's computer, so its safety rules are deliberately boring and enforced in code (and tested).

## Boundaries

1. **Recognition is not authority.** Speech becomes text; text becomes a typed `Intent`; an intent becomes an `ActionRequest` for a
   *registered* action; a `SafetyPolicy` decides allow / confirm / deny; only then does a handler run. There is no code path from
   recognised text to a shell or to an arbitrary path.
2. **Action registry** (`ActionRegistry`): every action declares id, description, parameters (with length limits), permission,
   confirmation, timeout and result shape. Unknown actions and unexpected parameters are rejected before execution. Every handler runs
   under its timeout.
3. **No arbitrary commands.** "Run command ..." is recognised only to be refused. Developer mode runs fixed templates (`mvn -B test|package|clean`),
   as an argument list (no shell string), with timeout, 256 KB output cap and cancellation. Developer mode is off by default; projects must be
   approved explicitly in Settings (building executes the project's own build scripts); `clean` shows the exact command and waits for confirmation.
4. **Path safety** (`PathValidator`): paths must exist, resolve (through symlinks) inside an allowed root (standard user folders, configured project
   roots, explicit folder aliases), avoid system/secret folders (Windows, Program Files, AppData, .ssh ...), and files with executable or script
   extensions are never opened by voice. Searches are bounded by depth, count, time, and explicit roots - never whole drives.
5. **Applications** launch only from the discovered Start Menu catalog by id; Drift never builds a command line for them.
6. **Typing into other apps** only through a preview naming the target window, committed by an explicit button, and only if that same window is
   still in front. Dictation always has a visible state and an immediate Stop.
7. **Learning cannot escalate.** Only low-risk actions can be learned; process execution and typing never are. A learned mapping goes through the same
   safety policy (privileged actions from a learned source always need confirmation). Replacing an alias asks first. Everything is inspectable,
   editable, exportable, deletable and resettable.
8. **Microphone:** capture starts only from push-to-talk or an explicit dictation session; the red indicator and a Stop control are always visible;
   audio lives in a 6-second bounded in-memory queue, is never written to disk and never leaves the machine (offline engine; no network code in the
   speech path). A hard time cap stops runaway sessions.
9. **Imports** (themes, icon packs, wallpapers) are data only: size caps, schema validation, extension allow-list, magic-byte checks, path-traversal
   and zip-bomb protection, duplicate detection. Nothing from a pack is executed. Theme values are validated before they reach CSS.
10. **Privilege:** Drift never requests administrator rights, never touches UAC or security settings, and start-with-Windows is an explicit,
    visible, per-user toggle that is easy to remove.
11. **Network:** no server is run (no HTTP listener at all). The only outbound request is the optional weather widget (open-meteo.com, only the
    coordinates you typed, only when enabled). No telemetry, no update checks.
12. **Audit & logs:** every action, refusal, cancellation and timeout is recorded in a local operation history (bounded retention). Logs and history
    pass through a redactor (passwords/tokens/bearer strings/long secrets/user names). Recognised phrases are not stored unless you opt in.

## Known limitations

- The Windows-specific enforcement (non-activating window, hotkeys, SendInput target check) has not yet been verified on Windows (see
  `docs/IMPLEMENTATION_STATUS.md`).
- Approving a project trusts that project's build scripts, exactly as running `mvn` yourself would.
- Click-through for the idle interface is experimental, off by default, and relies on the tray/shortcut to recover.
- The installer is unsigned.

## Reporting

Open an issue on the project repository or contact the maintainer privately for anything that looks like a bypass of the rules above.
