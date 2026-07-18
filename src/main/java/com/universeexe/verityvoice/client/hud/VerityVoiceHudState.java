package com.universeexe.verityvoice.client.hud;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import javax.annotation.Nullable;

/**
 * Logical push-to-talk indicator states. Separate from microphone capture state.
 */
@OnlyIn(Dist.CLIENT)
public enum VerityVoiceHudState {
    HIDDEN,
    READY,
    LISTENING,
    PROCESSING,
    COMMAND_RECOGNIZED,
    NO_COMMAND,
    VERITY_TOO_FAR,
    NO_VERITY,
    MICROPHONE_ERROR,
    MODEL_MISSING,
    VOICE_DISABLED;

    public String langKey() {
        return "gui.universe_verity_voice." + name().toLowerCase();
    }

    public String compactLangKey() {
        return langKey() + ".compact";
    }

    public boolean isResultState() {
        return this == COMMAND_RECOGNIZED
                || this == NO_COMMAND
                || this == MICROPHONE_ERROR
                || this == MODEL_MISSING
                || this == VOICE_DISABLED;
    }

    public boolean isHeldProblemState() {
        return this == VERITY_TOO_FAR || this == NO_VERITY;
    }

    public boolean showsAudioBars() {
        return this == LISTENING;
    }

    public boolean showsProcessingDots() {
        return this == PROCESSING;
    }

    public boolean isVisibleCandidate() {
        return this != HIDDEN;
    }

    @Nullable
    public static VerityVoiceHudState fromDebugName(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String key = raw.trim().toLowerCase().replace('-', '_');
        return switch (key) {
            case "hidden" -> HIDDEN;
            case "ready" -> READY;
            case "listening" -> LISTENING;
            case "processing" -> PROCESSING;
            case "recognized", "command_recognized", "success" -> COMMAND_RECOGNIZED;
            case "no_command", "nocommand" -> NO_COMMAND;
            case "too_far", "verity_too_far" -> VERITY_TOO_FAR;
            case "no_verity", "noverty" -> NO_VERITY;
            case "microphone_error", "mic_error", "mic" -> MICROPHONE_ERROR;
            case "model_missing", "model" -> MODEL_MISSING;
            case "voice_disabled", "disabled" -> VOICE_DISABLED;
            default -> {
                try {
                    yield VerityVoiceHudState.valueOf(key.toUpperCase());
                } catch (IllegalArgumentException ex) {
                    yield null;
                }
            }
        };
    }

    public static String intentDisplayKey(ResourceLocation intentId) {
        if (intentId == null) {
            return "intent.universe_verity_voice.unknown";
        }
        return "intent." + intentId.getNamespace() + "." + intentId.getPath();
    }
}
