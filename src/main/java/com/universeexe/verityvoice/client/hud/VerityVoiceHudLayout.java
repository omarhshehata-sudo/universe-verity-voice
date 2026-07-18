package com.universeexe.verityvoice.client.hud;

import com.universeexe.verityvoice.common.config.VoiceClientConfig;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class VerityVoiceHudLayout {
    public enum Position {
        BOTTOM_CENTER,
        BOTTOM_RIGHT,
        BOTTOM_LEFT,
        CENTER_RIGHT,
        CENTER_LEFT;

        public static Position fromConfig(String raw) {
            if (raw == null || raw.isBlank()) {
                return BOTTOM_CENTER;
            }
            try {
                return Position.valueOf(raw.trim().toUpperCase());
            } catch (IllegalArgumentException ex) {
                return BOTTOM_CENTER;
            }
        }
    }

    public record PanelRect(int x, int y, int width, int height) {
    }

    private VerityVoiceHudLayout() {
    }

    public static int barsWidth() {
        return VerityVoiceHudStyle.BAR_COUNT * VerityVoiceHudStyle.BAR_WIDTH
                + (VerityVoiceHudStyle.BAR_COUNT - 1) * VerityVoiceHudStyle.BAR_GAP;
    }

    public static int contentWidth(int textWidth, boolean includeBars, boolean includeDots) {
        int trailing = 0;
        if (includeBars) {
            trailing = VerityVoiceHudStyle.TEXT_BARS_GAP + barsWidth();
        } else if (includeDots) {
            trailing = VerityVoiceHudStyle.TEXT_BARS_GAP + 18;
        }
        int width = VerityVoiceHudStyle.PAD_X
                + VerityVoiceHudStyle.ICON_W
                + VerityVoiceHudStyle.ICON_TEXT_GAP
                + textWidth
                + trailing
                + VerityVoiceHudStyle.PAD_X;
        return Math.max(VerityVoiceHudStyle.MIN_PANEL_WIDTH, Math.min(VerityVoiceHudStyle.MAX_PANEL_WIDTH, width));
    }

    public static PanelRect compute(Minecraft mc, int panelWidth, int panelHeight, float scale) {
        int screenW = mc.getWindow().getGuiScaledWidth();
        int screenH = mc.getWindow().getGuiScaledHeight();
        int w = Math.round(panelWidth * scale);
        int h = Math.round(panelHeight * scale);
        int xOff = VoiceClientConfig.INDICATOR_X_OFFSET.get();
        int yOff = VoiceClientConfig.INDICATOR_Y_OFFSET.get();
        Position pos = Position.fromConfig(VoiceClientConfig.INDICATOR_POSITION.get());

        int x;
        int y;
        switch (pos) {
            case BOTTOM_LEFT -> {
                x = 12 + xOff;
                y = screenH - VerityVoiceHudStyle.HOTBAR_CLEARANCE - h + yOff;
            }
            case BOTTOM_RIGHT -> {
                x = screenW - w - 12 + xOff;
                y = screenH - VerityVoiceHudStyle.HOTBAR_CLEARANCE - h + yOff;
            }
            case CENTER_LEFT -> {
                x = 12 + xOff;
                y = (screenH - h) / 2 + yOff;
            }
            case CENTER_RIGHT -> {
                x = screenW - w - 12 + xOff;
                y = (screenH - h) / 2 + yOff;
            }
            default -> {
                x = (screenW - w) / 2 + xOff;
                y = screenH - VerityVoiceHudStyle.HOTBAR_CLEARANCE - h + yOff;
            }
        }

        if (pos == Position.BOTTOM_CENTER && mc.options.showSubtitles().get()) {
            y -= VerityVoiceHudStyle.SUBTITLE_NUDGE;
        }

        x = Math.max(2, Math.min(screenW - w - 2, x));
        y = Math.max(2, Math.min(screenH - h - 2, y));
        return new PanelRect(x, y, w, h);
    }
}
