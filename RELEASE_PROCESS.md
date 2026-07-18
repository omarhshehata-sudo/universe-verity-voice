# Release process (Universe Verity Voice)

Publish to the universe.exe GitHub organization / existing Verity ecosystem:

**Suggested repo:** https://github.com/omarhshehata-sudo/universe-verity-voice  
(or a release under the same org with a clear `universe_verity_voice` artifact name)

This mod **depends on** [universe-verity](https://github.com/omarhshehata-sudo/universe-verity) (`universe_verity` ≥ 1.0.14).

## Steps

1. Ensure `../universe-verity` is built (`./gradlew build`) so the local dependency JAR exists.
2. Bump `mod_version` in `gradle.properties`.
3. Build: `./gradlew clean build`
4. Confirm JAR: `build/libs/universe_verity_voice-<version>.jar`
5. Copy the JAR to the Desktop for local install testing.
6. Commit/push source to the voice mod repository.
7. Create a GitHub Release with the JAR attached:

```bash
gh release create "v<version>" \
  --repo omarhshehata-sudo/universe-verity-voice \
  --title "Universe Verity Voice <version>" \
  --notes "Offline Vosk speech-to-intent foundation for Forge 1.20.1. Requires universe_verity and the vosk-model-small-en-us-0.15 folder under config/universe_verity_voice/models/." \
  "build/libs/universe_verity_voice-<version>.jar"
```

Do not commit the large Vosk model into git. Ship it via modpack `overrides/config/...` with license files intact.
