# Vosk model placement (modpack)

Place the extracted offline model here in the packaged modpack:

```
overrides/config/universe_verity_voice/models/vosk-model-small-en-us-0.15/
```

At runtime this becomes:

```
config/universe_verity_voice/models/vosk-model-small-en-us-0.15/
```

## Required contents

Download **vosk-model-small-en-us-0.15** from the official Alphacephei / Vosk model list, extract it, and keep the original folder name.

The folder must contain at least one of: `am/`, `conf/`, `graph/`, `ivector/`.

**Do not delete license / attribution files** that ship with the model.

## Quick install (dev / local)

From the `universe-verity-voice` repo:

```bash
chmod +x scripts/download-vosk-model.sh
./scripts/download-vosk-model.sh /path/to/minecraft/instance
# or for Gradle runClient:
./scripts/download-vosk-model.sh ./run
```

Official zip: https://alphacephei.com/vosk/models/vosk-model-small-en-us-0.15.zip

## Notes

- The mod never downloads the model silently.
- HUD **MODEL_MISSING** = folder missing/incomplete (press V again after install — no restart needed).
- HUD **NATIVE_ERROR** = Vosk/JNA natives failed for this session (restart after fixing packaging).
- Use `/verityvoice model` in-game to print the exact absolute path.
