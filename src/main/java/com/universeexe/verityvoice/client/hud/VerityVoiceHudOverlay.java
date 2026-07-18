package com.universeexe.verityvoice.client.hud;

import com.universeexe.verityvoice.client.VoiceListeningController;
import com.universeexe.verityvoice.client.VoiceRecognitionState;
import com.universeexe.verityvoice.common.config.VoiceClientConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;

@OnlyIn(Dist.CLIENT)
public final class VerityVoiceHudOverlay {
    private boolean renderedThisFrame;

    @SubscribeEvent
    public void onRenderPre(RenderGuiOverlayEvent.Pre event) {
        if (event.getOverlay() == VanillaGuiOverlay.HOTBAR.type()) {
            renderedThisFrame = false;
        }
    }

    @SubscribeEvent
    public void onRender(RenderGuiOverlayEvent.Post event) {
        if (event.getOverlay() != VanillaGuiOverlay.HOTBAR.type()) {
            return;
        }
        if (renderedThisFrame) {
            return;
        }
        renderedThisFrame = true;

        if (!VerityVoiceHudController.indicatorEnabled()) {
            return;
        }

        try {
            renderIndicator(event);
        } catch (Throwable ex) {
            if (ex instanceof VirtualMachineError) {
                throw (VirtualMachineError) ex;
            }
            // Never let HUD drawing take down the render thread.
        }
    }

    private void renderIndicator(RenderGuiOverlayEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        VerityVoiceHudController controller = VerityVoiceHudController.INSTANCE;
        if (VerityVoiceHudController.shouldSuppressVisibility(mc) && !controller.isPreviewMode()) {
            controller.animation().setVisible(false);
        }

        float audioTarget = 0.0f;
        if (controller.state() == VerityVoiceHudState.LISTENING) {
            audioTarget = VoiceRecognitionState.audioLevel();
        }
        controller.animation().tickFrame(audioTarget);

        VerityVoiceHudAnimation anim = controller.animation();
        float alpha = anim.alpha() * VoiceClientConfig.INDICATOR_OPACITY.get().floatValue();
        if (alpha <= 0.01f && !controller.hudDebug()) {
            return;
        }

        VerityVoiceHudState state = controller.state();
        if (state == VerityVoiceHudState.HIDDEN && !controller.hudDebug()) {
            return;
        }

        Font font = mc.font;
        Component label = resolveLabel(state, controller);
        int textWidth = font.width(label);
        boolean bars = state.showsAudioBars() && VoiceClientConfig.SHOW_AUDIO_LEVEL_BARS.get();
        boolean dots = state.showsProcessingDots();
        int desiredWidth = VerityVoiceHudLayout.contentWidth(textWidth, bars, dots);
        // Prefer compact translation if full text overflows.
        if (desiredWidth >= VerityVoiceHudStyle.MAX_PANEL_WIDTH) {
            Component compact = Component.translatable(state.compactLangKey());
            if (!compact.getString().equals(state.compactLangKey())) {
                label = compact;
                textWidth = font.width(label);
                desiredWidth = VerityVoiceHudLayout.contentWidth(textWidth, bars, dots);
            }
        }
        anim.setTargetPanelWidth(desiredWidth);

        float scale = VoiceClientConfig.INDICATOR_SCALE.get().floatValue();
        int drawWidth = Math.max(VerityVoiceHudStyle.MIN_PANEL_WIDTH, Math.round(anim.panelWidth()));
        VerityVoiceHudLayout.PanelRect rect = VerityVoiceHudLayout.compute(
                mc, drawWidth, VerityVoiceHudStyle.PANEL_HEIGHT, scale
        );

        GuiGraphics g = event.getGuiGraphics();
        g.pose().pushPose();
        try {
            float drawAlpha = Math.max(0.0f, Math.min(1.0f, alpha));
            int x = rect.x();
            int y = rect.y() + Math.round(anim.slideY());
            int w = rect.width();
            int h = rect.height();

            if (state != VerityVoiceHudState.HIDDEN && drawAlpha > 0.01f) {
                drawCapsule(g, x, y, w, h, drawAlpha, VerityVoiceHudStyle.accentFor(state), anim.pulse01(), state);
                int contentX = x + VerityVoiceHudStyle.PAD_X;
                int iconY = y + (h - VerityVoiceHudStyle.ICON_H) / 2;
                drawIcon(g, contentX, iconY, state, drawAlpha, anim.pulse01());
                int textX = contentX + VerityVoiceHudStyle.ICON_W + VerityVoiceHudStyle.ICON_TEXT_GAP;
                int textY = y + (h - 8) / 2;
                int textColor = VerityVoiceHudStyle.withAlpha(VerityVoiceHudStyle.textFor(state), drawAlpha);
                g.drawString(font, label, textX, textY, textColor, false);

                int trailingX = textX + textWidth + VerityVoiceHudStyle.TEXT_BARS_GAP;
                if (bars) {
                    drawAudioBars(g, trailingX, y, h, anim.displayedAudioLevel(), drawAlpha, anim.pulse01());
                } else if (dots) {
                    drawProcessingDots(g, trailingX, y, h, anim.dotsPhase(), drawAlpha);
                }
            }

            if (controller.hudDebug()) {
                drawDebug(g, mc, controller, anim, rect);
            }
        } finally {
            g.pose().popPose();
        }
    }

    private static Component resolveLabel(VerityVoiceHudState state, VerityVoiceHudController controller) {
        if (state == VerityVoiceHudState.COMMAND_RECOGNIZED && VoiceClientConfig.SHOW_DETECTED_INTENT.get()) {
            ResourceLocation intent = controller.lastAcceptedIntent();
            if (intent != null) {
                Component intentName = Component.translatable(VerityVoiceHudState.intentDisplayKey(intent));
                return Component.translatable("gui.universe_verity_voice.command_recognized.intent", intentName);
            }
        }
        if (state == VerityVoiceHudState.MODEL_MISSING && VoiceClientConfig.DEBUG_LOGGING.get()) {
            return Component.translatable("gui.universe_verity_voice.model_missing");
        }
        return Component.translatable(state.langKey());
    }

    private static void drawCapsule(
            GuiGraphics g, int x, int y, int w, int h, float alpha, int accent, float pulse, VerityVoiceHudState state
    ) {
        int bg = VerityVoiceHudStyle.withAlpha(VerityVoiceHudStyle.BG_PANEL, alpha);
        int border = VerityVoiceHudStyle.withAlpha(VerityVoiceHudStyle.BORDER_IDLE, alpha);
        int shadow = VerityVoiceHudStyle.withAlpha(VerityVoiceHudStyle.SHADOW, alpha * 0.85f);

        g.fill(x + 1, y + h, x + w - 1, y + h + 2, shadow);
        g.fill(x + 1, y, x + w - 1, y + h, bg);
        g.fill(x, y + 1, x + 1, y + h - 1, bg);
        g.fill(x + w - 1, y + 1, x + w, y + h - 1, bg);

        g.fill(x + 1, y, x + w - 1, y + 1, border);
        g.fill(x + 1, y + h - 1, x + w - 1, y + h, border);
        g.fill(x, y + 1, x + 1, y + h - 1, border);
        g.fill(x + w - 1, y + 1, x + w, y + h - 1, border);

        float accentA = alpha;
        if (state == VerityVoiceHudState.LISTENING && !VoiceClientConfig.USE_REDUCED_MOTION.get()) {
            accentA *= 0.55f + 0.45f * pulse;
        }
        int accentColor = VerityVoiceHudStyle.withAlpha(accent, accentA);
        g.fill(x + 3, y + h - 2, x + w - 3, y + h - 1, accentColor);
    }

    private static void drawIcon(GuiGraphics g, int x, int y, VerityVoiceHudState state, float alpha, float pulse) {
        int cream = VerityVoiceHudStyle.withAlpha(VerityVoiceHudStyle.TEXT_CREAM, alpha);
        int dim = VerityVoiceHudStyle.withAlpha(VerityVoiceHudStyle.TEXT_DIM, alpha);
        int success = VerityVoiceHudStyle.withAlpha(VerityVoiceHudStyle.ACCENT_SUCCESS, alpha);
        int error = VerityVoiceHudStyle.withAlpha(VerityVoiceHudStyle.ACCENT_ERROR, alpha);
        int muted = VerityVoiceHudStyle.withAlpha(VerityVoiceHudStyle.ACCENT_MUTED, alpha);

        switch (state) {
            case COMMAND_RECOGNIZED -> drawCheck(g, x, y, success);
            case NO_COMMAND -> drawQuestion(g, x, y, muted);
            case VERITY_TOO_FAR, NO_VERITY -> drawDistance(g, x, y, error);
            case MICROPHONE_ERROR -> {
                drawMic(g, x, y, cream, 1.0f);
                drawSlash(g, x, y, error);
            }
            case VOICE_DISABLED -> {
                drawMic(g, x, y, dim, 1.0f);
                drawSlash(g, x, y, muted);
            }
            case MODEL_MISSING -> drawMic(g, x, y, error, 1.0f);
            case PROCESSING -> drawMic(g, x, y, dim, 1.0f);
            case LISTENING -> {
                float glow = VoiceClientConfig.USE_REDUCED_MOTION.get() ? 1.0f : 0.85f + 0.15f * pulse;
                drawMic(g, x, y, cream, glow);
            }
            default -> drawMic(g, x, y, cream, 1.0f);
        }
    }

    private static void drawMic(GuiGraphics g, int x, int y, int color, float brightness) {
        int c = VerityVoiceHudStyle.withAlpha(color, brightness);
        // Capsule body
        g.fill(x + 3, y + 1, x + 7, y + 8, c);
        g.fill(x + 2, y + 2, x + 3, y + 7, c);
        g.fill(x + 7, y + 2, x + 8, y + 7, c);
        // Stand / base
        g.fill(x + 1, y + 8, x + 2, y + 10, c);
        g.fill(x + 8, y + 8, x + 9, y + 10, c);
        g.fill(x + 2, y + 10, x + 8, y + 11, c);
        g.fill(x + 4, y + 11, x + 6, y + 13, c);
        g.fill(x + 3, y + 13, x + 7, y + 14, c);
    }

    private static void drawSlash(GuiGraphics g, int x, int y, int color) {
        for (int i = 0; i < 12; i++) {
            int px = x + i;
            int py = y + 12 - i;
            g.fill(px, py, px + 1, py + 1, color);
            g.fill(px, py - 1, px + 1, py, color);
        }
    }

    private static void drawCheck(GuiGraphics g, int x, int y, int color) {
        g.fill(x + 2, y + 7, x + 3, y + 9, color);
        g.fill(x + 3, y + 8, x + 4, y + 10, color);
        g.fill(x + 4, y + 9, x + 5, y + 11, color);
        g.fill(x + 5, y + 8, x + 6, y + 10, color);
        g.fill(x + 6, y + 6, x + 7, y + 9, color);
        g.fill(x + 7, y + 4, x + 8, y + 7, color);
        g.fill(x + 8, y + 3, x + 9, y + 5, color);
    }

    private static void drawQuestion(GuiGraphics g, int x, int y, int color) {
        g.fill(x + 3, y + 2, x + 7, y + 3, color);
        g.fill(x + 2, y + 3, x + 3, y + 5, color);
        g.fill(x + 7, y + 3, x + 8, y + 6, color);
        g.fill(x + 5, y + 6, x + 7, y + 7, color);
        g.fill(x + 4, y + 7, x + 6, y + 8, color);
        g.fill(x + 4, y + 9, x + 6, y + 10, color);
        g.fill(x + 4, y + 12, x + 6, y + 13, color);
    }

    private static void drawDistance(GuiGraphics g, int x, int y, int color) {
        g.fill(x + 1, y + 11, x + 3, y + 13, color);
        g.fill(x + 4, y + 8, x + 6, y + 13, color);
        g.fill(x + 7, y + 4, x + 9, y + 13, color);
    }

    private static void drawAudioBars(GuiGraphics g, int x, int y, int h, float level, float alpha, float pulse) {
        float clamped = Math.max(0.0f, Math.min(1.0f, level));
        int baseColor = VerityVoiceHudStyle.withAlpha(VerityVoiceHudStyle.ACCENT_LISTENING, alpha);
        int barBottom = y + h - VerityVoiceHudStyle.PAD_Y;
        for (int i = 0; i < VerityVoiceHudStyle.BAR_COUNT; i++) {
            float bias = 0.72f + 0.09f * i;
            float barLevel = Math.max(0.0f, Math.min(1.0f, clamped * bias));
            int barH = VerityVoiceHudStyle.BAR_MIN_H
                    + Math.round(barLevel * (VerityVoiceHudStyle.BAR_MAX_H - VerityVoiceHudStyle.BAR_MIN_H));
            if (clamped < 0.04f) {
                barH = VerityVoiceHudStyle.BAR_MIN_H;
            }
            int bx = x + i * (VerityVoiceHudStyle.BAR_WIDTH + VerityVoiceHudStyle.BAR_GAP);
            int by = barBottom - barH;
            float a = alpha * (0.75f + 0.25f * pulse);
            g.fill(bx, by, bx + VerityVoiceHudStyle.BAR_WIDTH, barBottom,
                    VerityVoiceHudStyle.withAlpha(baseColor, a));
        }
    }

    private static void drawProcessingDots(GuiGraphics g, int x, int y, int h, float phase, float alpha) {
        int cy = y + h / 2;
        int color = VerityVoiceHudStyle.withAlpha(VerityVoiceHudStyle.TEXT_DIM, alpha);
        for (int i = 0; i < 3; i++) {
            float local = (phase + i * 0.22f) % 1.0f;
            int lift = VoiceClientConfig.USE_REDUCED_MOTION.get() ? 0 : (local < 0.5f ? 1 : 0);
            int dx = x + i * 5;
            g.fill(dx, cy - 1 - lift, dx + 2, cy + 1 - lift, color);
        }
    }

    private static void drawDebug(
            GuiGraphics g,
            Minecraft mc,
            VerityVoiceHudController controller,
            VerityVoiceHudAnimation anim,
            VerityVoiceHudLayout.PanelRect rect
    ) {
        int x = 4;
        int y = 40;
        int color = VerityVoiceHudStyle.DEBUG_TEXT;
        y = dbg(g, mc, x, y, color, "HUD state: " + controller.state());
        y = dbg(g, mc, x, y, color, "State age ms: " + controller.stateAgeMs());
        y = dbg(g, mc, x, y, color, String.format("Alpha: %.2f", anim.alpha()));
        y = dbg(g, mc, x, y, color, "Pos: " + VoiceClientConfig.INDICATOR_POSITION.get()
                + " @" + rect.x() + "," + rect.y());
        y = dbg(g, mc, x, y, color, String.format("Scale: %.2f width=%.1f",
                VoiceClientConfig.INDICATOR_SCALE.get(), anim.panelWidth()));
        y = dbg(g, mc, x, y, color, String.format("Audio: %.2f (disp %.2f)",
                VoiceRecognitionState.audioLevel(), anim.displayedAudioLevel()));
        y = dbg(g, mc, x, y, color, String.format("Verity dist: %.2f", controller.lastVerityDistance()));
        y = dbg(g, mc, x, y, color, "Listening: " + VoiceListeningController.INSTANCE.worker().isListening()
                + " capturing=" + VoiceListeningController.INSTANCE.worker().capture().isCapturing());
        y = dbg(g, mc, x, y, color, "Recognizer: " + VoiceRecognitionState.recognizerStatus()
                + " packet=" + VoiceRecognitionState.packetStatus());
        dbg(g, mc, x, y, color, "Last accepted: " + (controller.lastAcceptedIntent() == null
                ? "-" : controller.lastAcceptedIntent()));
    }

    private static int dbg(GuiGraphics g, Minecraft mc, int x, int y, int color, String text) {
        g.drawString(mc.font, text, x, y, color, true);
        return y + 10;
    }
}
