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
public final class VoiceDebugOverlay {
    @SubscribeEvent
    public void onRender(RenderGuiOverlayEvent.Post event) {
        if (!VoiceClientConfig.DEBUG_OVERLAY.get()) {
            return;
        }
        if (event.getOverlay() != VanillaGuiOverlay.CHAT_PANEL.type()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        GuiGraphics g = event.getGuiGraphics();
        int x = 4;
        int y = 4;
        int color = 0xFFA0FFA0;
        y = line(g, mc, x, y, color, "Microphone: " + (VoiceRecognitionState.micStatus() == VoiceRecognitionState.MicStatus.LISTENING
                || VoiceRecognitionState.micStatus() == VoiceRecognitionState.MicStatus.RECOGNIZING ? "OPEN" : "CLOSED")
                + " (" + VoiceRecognitionState.micStatus() + ")");
        y = line(g, mc, x, y, color, "Model: " + VoiceRecognitionState.modelStatus());
        y = line(g, mc, x, y, color, "Recognizer: " + VoiceRecognitionState.recognizerStatus());
        y = line(g, mc, x, y, color, String.format("Audio level: %.2f", VoiceRecognitionState.audioLevel()));
        y = line(g, mc, x, y, color, "Partial: " + VoiceRecognitionState.partialText());
        y = line(g, mc, x, y, color, "Final: " + VoiceRecognitionState.finalText());
        y = line(g, mc, x, y, color, "Normalized: " + VoiceRecognitionState.normalizedText());
        y = line(g, mc, x, y, color, "Matched intent: " + VoiceRecognitionState.matchedIntent());
        y = line(g, mc, x, y, color, String.format("Match score: %.2f", VoiceRecognitionState.matchScore()));
        y = line(g, mc, x, y, color, String.format("Speech confidence: %.2f", VoiceRecognitionState.speechConfidence()));
        y = line(g, mc, x, y, color, "Nearby Verity: " + VoiceRecognitionState.nearbyVerity());
        line(g, mc, x, y, color, "Packet: " + VoiceRecognitionState.packetStatus()
                + (VoiceRecognitionState.lastDetail().isBlank() ? "" : " (" + VoiceRecognitionState.lastDetail() + ")"));
    }

    private static int line(GuiGraphics g, Minecraft mc, int x, int y, int color, String text) {
        g.drawString(mc.font, text, x, y, color, true);
        return y + 10;
    }
}
