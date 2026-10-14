# Privacy and your data

**Local by default.** Drift needs no account, no API key and no internet for any core feature.

| Data | Where | Kept for | Notes |
|---|---|---|---|
| Microphone audio | RAM only (a ~6 s buffer) | seconds | never written to disk, never uploaded; capture only while you hold push-to-talk or run dictation; a red indicator is visible the whole time |
| Settings, themes, widget layouts | `%APPDATA%\Drift\drift.db` | until you reset/delete | |
| Learned aliases / commands | same database | until you delete/reset | Settings > Personal learning: inspect, delete, export, import, reset |
| Operation history (what Drift did/refused) | same database | 30 days by default (configurable) | secrets redacted; **recognised phrases are not stored unless you enable it** |
| Logs | `%APPDATA%\Drift\logs` | 7 days by default, size-capped | message text redacted |
| Imported packs | `%APPDATA%\Drift\packs` | until removed | |
| Weather request | sent to open-meteo.com | - | only when you enable the weather widget; only your typed latitude/longitude |

Drift never reads or sends your files for training; file search touches only folders it is allowed to use and only returns names.

## Uninstall and deleting everything

Uninstalling removes the program but keeps your data. To remove it too: quit Drift, delete `%APPDATA%\Drift`, and (if you enabled
it) turn off *Start with Windows* first, or delete the `Drift` value under `HKCU\Software\Microsoft\Windows\CurrentVersion\Run`.
"Reset all settings" restores defaults without touching learned data; "Reset all learned data" and "Clear history" are in the settings pages.

Credentials: Drift currently stores none. If a feature ever needs one it must use Windows Credential Manager, never the database.
