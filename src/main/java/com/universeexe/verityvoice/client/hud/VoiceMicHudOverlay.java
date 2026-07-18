package com.universeexe.verityvoice.client.hud;

import com.universeexe.verityvoice.client.VoiceRecognitionState;
import com.universeexe.verityvoice.common.config.VoiceClientConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;

@OnlyIn(Dist.CLIENT)
public final class VoiceMicHudOverlay {
    @SubscribeEvent
    public void onRender(RenderGuiOverlayEvent.Post event) {
        if (event.getOverlay() != VanillaGuiOverlay.HOTBAR.type()) {
            return;
        }
        if (!VoiceClientConfig.SHOW_MICROPHONE_INDICATOR.get() || !VoiceClientConfig.VOICE_COMMANDS_ENABLED.get()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) {
            return;
        }
        GuiGraphics g = event.getGuiGraphics();
        int x = event.getWindow().getGuiScaledWidth() - 90;
        int y = event.getWindow().getGuiScaledHeight() - 54;

        VoiceRecognitionState.MicStatus status = VoiceRecognitionState.micStatus();
        int color = switch (status) {
            case OFF -> 0xFF666666;
            case READY -> 0xFFFFFFFF;
            case LISTENING, RECOGNIZING -> pulseColor();
            case COMMAND_DETECTED -> 0xFF66FF66;
            case NO_COMMAND -> 0xFFFF6666;
            case ERROR, MODEL_MISSING -> 0xFFFFAA00;
        };
        String icon = switch (status) {
            case COMMAND_DETECTED -> "MIC OK";
            case NO_COMMAND -> "MIC --";
            case ERROR, MODEL_MISSING -> "MIC !";
            case LISTENING, RECOGNIZING -> "MIC...";
            default -> "MIC";
        };
        g.drawString(mc.font, icon, x, y, color, true);
        if (status == VoiceRecognitionState.MicStatus.LISTENING
                || status == VoiceRecognitionState.MicStatus.RECOGNIZING) {
            g.drawString(mc.font, "Listening...", x, y + 10, 0xFFDDDDDD, true);
        }
        if (VoiceClientConfig.SHOW_RECOGNIZED_TEXT.get() && !VoiceRecognitionState.normalizedText().isBlank()) {
            g.drawString(mc.font, trim(VoiceRecognitionState.normalizedText(), 28), x - 40, y - 12, 0xFFAAAAAA, true);
        }
    }

    private static int pulseColor() {
        float t = (System.currentTimeMillis() % 1000L) / 1000.0f;
        int c = 180 + (int) (75 * (0.5f + 0.5f * Math.sin(t * Math.PI * 2)));
        return 0xFF000000 | (c << 16) | (c << 8) | c;
    }

    private static String trim(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }
}
