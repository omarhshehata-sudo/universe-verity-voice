package com.universeexe.verityvoice.client.hud;

import com.universeexe.verityvoice.common.config.VoiceClientConfig;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class VerityVoiceHudAnimation {
    private float alpha;
    private float targetAlpha;
    private float slideY;
    private float targetSlideY;
    private float panelWidth;
    private float targetPanelWidth;
    private float displayedAudioLevel;
    private float pulsePhase;
    private float dotsPhase;
    private long lastFrameMs = System.currentTimeMillis();

    public void setVisible(boolean visible) {
        targetAlpha = visible ? 1.0f : 0.0f;
        boolean reduced = VoiceClientConfig.USE_REDUCED_MOTION.get();
        if (visible) {
            targetSlideY = reduced ? 0.0f : -3.0f;
            if (alpha <= 0.01f && !reduced) {
                slideY = 3.0f;
            }
        } else {
            targetSlideY = reduced ? 0.0f : 2.0f;
        }
    }

    public void setTargetPanelWidth(float width) {
        targetPanelWidth = width;
        if (panelWidth <= 1.0f || VoiceClientConfig.USE_REDUCED_MOTION.get()) {
            panelWidth = width;
        }
    }

    public void tickFrame(float audioTarget) {
        long now = System.currentTimeMillis();
        float dt = Math.min(0.05f, Math.max(0.001f, (now - lastFrameMs) / 1000.0f));
        lastFrameMs = now;

        boolean reduced = VoiceClientConfig.USE_REDUCED_MOTION.get();
        float fadeIn = Math.max(0.04f, VoiceClientConfig.INDICATOR_FADE_IN_MS.get() / 1000.0f);
        float fadeOut = Math.max(0.04f, VoiceClientConfig.INDICATOR_FADE_OUT_MS.get() / 1000.0f);
        float fadeSpeed = targetAlpha > alpha ? (1.0f / fadeIn) : (1.0f / fadeOut);
        alpha = approach(alpha, targetAlpha, fadeSpeed * dt);

        if (reduced) {
            slideY = 0.0f;
            panelWidth = targetPanelWidth;
            displayedAudioLevel = audioTarget;
            pulsePhase = 0.0f;
            dotsPhase = 0.0f;
            return;
        }

        slideY = approach(slideY, targetSlideY, 18.0f * dt);
        panelWidth = approach(panelWidth, targetPanelWidth, 140.0f * dt);
        displayedAudioLevel = lerp(displayedAudioLevel, audioTarget, 0.20f);
        pulsePhase = (pulsePhase + dt / 1.2f) % 1.0f;
        dotsPhase = (dotsPhase + dt * 1.4f) % 1.0f;
    }

    public float alpha() {
        return alpha;
    }

    public float slideY() {
        return slideY;
    }

    public float panelWidth() {
        return panelWidth;
    }

    public float displayedAudioLevel() {
        return displayedAudioLevel;
    }

    public float pulse01() {
        if (VoiceClientConfig.USE_REDUCED_MOTION.get()) {
            return 0.5f;
        }
        return 0.5f + 0.5f * (float) Math.sin(pulsePhase * Math.PI * 2.0);
    }

    public float dotsPhase() {
        return dotsPhase;
    }

    public boolean isFullyHidden() {
        return alpha <= 0.01f && targetAlpha <= 0.01f;
    }

    private static float approach(float current, float target, float maxDelta) {
        if (current < target) {
            return Math.min(target, current + maxDelta);
        }
        if (current > target) {
            return Math.max(target, current - maxDelta);
        }
        return target;
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * Math.max(0.0f, Math.min(1.0f, t));
    }
}
