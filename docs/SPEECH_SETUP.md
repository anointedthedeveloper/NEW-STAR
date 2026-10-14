# Speech setup (offline, no API key)

Drift uses [Vosk](https://alphacephei.com/vosk/) (Apache-2.0). The recognition library ships inside the application; **models are
large and are never bundled or committed** - you install one yourself.

1. Pick a model from <https://alphacephei.com/vosk/models>. For a quick start use `vosk-model-small-en-us-0.15` (about 40 MB, fast).
   Larger models are more accurate but slower and use more memory. Check each model's licence.
2. Unzip it so the folder (containing `am`, `conf`, `graph`, ...) sits in Drift's models folder:
   `%APPDATA%\Drift\models\vosk-model-small-en-us-0.15\`
   (Settings > Voice recognition > **Open** opens the folder.)
3. Press **Refresh status**. It should say `READY - Model: vosk-model-small-en-us-0.15`.
4. Language: the model is picked from the models folder by the language code in its folder name (`-en-us-`). You can also point at a specific
   model folder in the same settings page.
5. Microphone: choose the device in the same page. Windows must allow microphone access for desktop apps
   (Settings > Privacy & security > Microphone).

Use: hold the push-to-talk shortcut (default `ctrl+alt+space`) or the microphone button. Release to finish. Live text appears while you speak;
the final text is then interpreted. Confidence is reported by the engine when it has it. A wake word is **not** implemented.

Failure states you will see instead of silence: model missing, native library not loadable, microphone unavailable/denied, device unplugged
(Drift retries three times and then stops with an explanation).

Developer note - verify recognition without a microphone:
```
DRIFT_TEST_MODELS_DIR=/path/to/models  DRIFT_TEST_WAV=/path/to/16k-mono.wav  mvn test -Dtest=VoskIntegrationTest
```
