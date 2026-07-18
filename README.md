# Universe Verity Voice

Offline, local microphone speech-to-intent foundation for Verity in the universe.exe modpack.

- **Mod ID:** `universe_verity_voice`
- **Minecraft:** 1.20.1 Forge
- **Depends on:** `universe_verity` (entity bridge)
- **STT:** Vosk `0.3.45` (embedded) + `vosk-model-small-en-us-0.15` (external config folder)

## What this mod does

1. Opt-in microphone capture on the client  
2. Offline Vosk recognition (no cloud, no upload, no audio saved)  
3. Alias/intent matching from datapack JSON  
4. Secure C2S intent packets (intent ID only; optional debug phrase)  
5. Server validation + `VerityVoiceIntentEvent` for future quests/dialogue  

## What it does **not** do

Dialogue, TTS, diamond/village finding, anger, demon transform, quest screens, or Verity JE AI.

## Model install

Exact runtime path (required):

```
config/universe_verity_voice/models/vosk-model-small-en-us-0.15/
```

Modpack path:

```
overrides/config/universe_verity_voice/models/vosk-model-small-en-us-0.15/
```

Helper script (downloads Alphacephei zip + extracts):

```bash
chmod +x scripts/download-vosk-model.sh
./scripts/download-vosk-model.sh /path/to/minecraft/instance
```

In-game: `/verityvoice model` prints the absolute expected path.

- **MODEL_MISSING** — folder missing; install model, press V again (retryable, no restart).
- **NATIVE_ERROR** — libvosk/JNA failed this session; fix packaging and restart.

See `modpack/overrides/config/universe_verity_voice/models/README.md`.

## Quick commands

| Command | Side | Purpose |
|---------|------|---------|
| `/verityvoice enable` | client | Opt in |
| `/verityvoice disable` | client | Opt out / unload model |
| `/verityvoice model` | client | Expected model path |
| `/verityvoice testmic` | client | 3s level test (not saved) |
| `/verityvoice simulate <intent>` | server (op) | Fire intent without mic |
| `/verityvoice nearestverity` | server | Proximity debug |

Default push-to-talk key: **V** (category Universe.exe).

## Build

```bash
cd ../universe-verity && ./gradlew build
cd ../universe-verity-voice && ./gradlew clean build
```

## Privacy

- Audio never leaves the client  
- Audio is never written to disk  
- Recognized text is never executed as Minecraft commands  
- Server receives registered intent IDs only  
