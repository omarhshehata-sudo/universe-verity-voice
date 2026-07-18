package com.universeexe.verityvoice.client.hud;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * Centralized muted cream / dark analog palette for the push-to-talk indicator.
 */
@OnlyIn(Dist.CLIENT)
public final class VerityVoiceHudStyle {
    public static final int TEXT_CREAM = 0xFFD7D0BC;
    public static final int TEXT_CREAM_BRIGHT = 0xFFE0D8C5;
    public static final int TEXT_DIM = 0xFF9A9484;
    public static final int BG_DARK = 0xFF11110F;
    public static final int BG_PANEL = 0xFF181815;
    public static final int BORDER_IDLE = 0xFF4A4840;
    public static final int ACCENT_LISTENING = 0xFFC9B896;
    public static final int ACCENT_AMBER = 0xFFB89A6A;
    public static final int ACCENT_SUCCESS = 0xFF7A9A72;
    public static final int ACCENT_ERROR = 0xFF8A5A52;
    public static final int ACCENT_MUTED = 0xFF6E6A60;
    public static final int SHADOW = 0x66000000;
    public static final int DEBUG_TEXT = 0xFFA8B89A;

    public static final int MIN_PANEL_WIDTH = 92;
    public static final int MAX_PANEL_WIDTH = 180;
    public static final int PANEL_HEIGHT = 24;
    public static final int PAD_X = 6;
    public static final int PAD_Y = 4;
    public static final int ICON_TEXT_GAP = 4;
    public static final int TEXT_BARS_GAP = 5;
    public static final int ICON_W = 10;
    public static final int ICON_H = 14;
    public static final int BAR_COUNT = 4;
    public static final int BAR_WIDTH = 2;
    public static final int BAR_GAP = 2;
    public static final int BAR_MIN_H = 2;
    public static final int BAR_MAX_H = 10;
    public static final int HOTBAR_CLEARANCE = 44;
    public static final int SUBTITLE_NUDGE = 14;

    private VerityVoiceHudStyle() {
    }

    public static int withAlpha(int argb, float alpha01) {
        float a = Math.max(0.0f, Math.min(1.0f, alpha01));
        int srcA = (argb >>> 24) & 0xFF;
        int outA = Math.max(0, Math.min(255, Math.round(srcA * a)));
        return (outA << 24) | (argb & 0x00FFFFFF);
    }

    public static int accentFor(VerityVoiceHudState state) {
        return switch (state) {
            case LISTENING, READY -> ACCENT_LISTENING;
            case PROCESSING -> ACCENT_AMBER;
            case COMMAND_RECOGNIZED -> ACCENT_SUCCESS;
            case NO_COMMAND -> ACCENT_MUTED;
            case VERITY_TOO_FAR, MICROPHONE_ERROR, MODEL_MISSING, NATIVE_ERROR, VOICE_DISABLED -> ACCENT_ERROR;
            case NO_VERITY -> ACCENT_AMBER;
            case HIDDEN -> BORDER_IDLE;
        };
    }

    public static int textFor(VerityVoiceHudState state) {
        return switch (state) {
            case PROCESSING, VOICE_DISABLED -> TEXT_DIM;
            case COMMAND_RECOGNIZED -> TEXT_CREAM_BRIGHT;
            default -> TEXT_CREAM;
        };
    }
}
