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

**Do not delete license / attribution files** that ship with the model.

## Notes

- The mod never downloads the model silently.
- If the folder is missing, speech recognition disables with one clear message; the rest of the game remains playable.
- Use `/verityvoice model` in-game to print the expected absolute path.
