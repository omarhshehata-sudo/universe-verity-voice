package com.universeexe.verityvoice.client.hud;

import com.universeexe.verityvoice.UniverseVerityVoice;
import com.universeexe.verityvoice.client.VoiceListeningController;
import com.universeexe.verityvoice.client.VoiceRecognitionState;
import com.universeexe.verityvoice.common.config.VoiceClientConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;

import javax.annotation.Nullable;

/**
 * Owns HUD display state. Does not open/close the microphone.
 */
@OnlyIn(Dist.CLIENT)
public final class VerityVoiceHudController {
    public static final VerityVoiceHudController INSTANCE = new VerityVoiceHudController();

    private static final long PROCESSING_TIMEOUT_MS = 3000L;
    private static final long READY_MAX_MS = 220L;
    private static final long MIC_ERROR_COOLDOWN_MS = 10_000L;
    private static final long HELD_FADE_AFTER_RELEASE_MS = 700L;

    private final VerityVoiceHudAnimation animation = new VerityVoiceHudAnimation();

    private VerityVoiceHudState state = VerityVoiceHudState.HIDDEN;
    private VerityVoiceHudState logicalState = VerityVoiceHudState.HIDDEN;
    private long stateEnteredAtMs = System.currentTimeMillis();
    private long resultUntilMs;
    private long processingUntilMs;
    private long readyUntilMs;
    private long micErrorCooldownUntilMs;
    private long heldReleaseUntilMs;
    private boolean pushHeld;
    private boolean awaitingCapture;
    private boolean previewMode;
    private boolean hudDebug;
    @Nullable
    private ResourceLocation lastAcceptedIntent;
    private String lastAcceptedIntentName = "";
    private double lastVerityDistance = -1.0;

    private VerityVoiceHudController() {
    }

    public VerityVoiceHudAnimation animation() {
        return animation;
    }

    public VerityVoiceHudState state() {
        return state;
    }

    public VerityVoiceHudState logicalState() {
        return logicalState;
    }

    public long stateAgeMs() {
        return Math.max(0L, System.currentTimeMillis() - stateEnteredAtMs);
    }

    public boolean hudDebug() {
        return hudDebug;
    }

    public void setHudDebug(boolean value) {
        hudDebug = value;
    }

    @Nullable
    public ResourceLocation lastAcceptedIntent() {
        return lastAcceptedIntent;
    }

    public String lastAcceptedIntentName() {
        return lastAcceptedIntentName;
    }

    public double lastVerityDistance() {
        return lastVerityDistance;
    }

    public void setLastVerityDistance(double distance) {
        lastVerityDistance = distance;
    }

    public boolean isPushHeld() {
        return pushHeld;
    }

    public boolean isPreviewMode() {
        return previewMode;
    }

    public void setReady() {
        if (!indicatorEnabled()) {
            return;
        }
        awaitingCapture = true;
        enter(VerityVoiceHudState.READY, READY_MAX_MS);
        readyUntilMs = System.currentTimeMillis() + READY_MAX_MS;
    }

    public void setListening() {
        if (!indicatorEnabled()) {
            return;
        }
        awaitingCapture = false;
        enter(VerityVoiceHudState.LISTENING, 0L);
    }

    public void setProcessing() {
        if (!indicatorEnabled() || !VoiceClientConfig.SHOW_PROCESSING_STATE.get()) {
            hide();
            return;
        }
        awaitingCapture = false;
        enter(VerityVoiceHudState.PROCESSING, 0L);
        processingUntilMs = System.currentTimeMillis() + PROCESSING_TIMEOUT_MS;
    }

    public void showCommandRecognized(@Nullable ResourceLocation intentId) {
        if (!indicatorEnabled() || !VoiceClientConfig.SHOW_SUCCESS_STATE.get()) {
            hide();
            return;
        }
        lastAcceptedIntent = intentId;
        lastAcceptedIntentName = intentId == null ? "" : intentId.getPath();
        enter(VerityVoiceHudState.COMMAND_RECOGNIZED, VoiceClientConfig.RESULT_DISPLAY_MS.get());
    }

    public void showNoCommand() {
        if (!indicatorEnabled() || !VoiceClientConfig.SHOW_NO_COMMAND_STATE.get()) {
            hide();
            return;
        }
        enter(VerityVoiceHudState.NO_COMMAND, VoiceClientConfig.RESULT_DISPLAY_MS.get());
    }

    public void showVerityTooFar() {
        if (!indicatorEnabled() || !VoiceClientConfig.SHOW_UNAVAILABLE_STATE.get()) {
            return;
        }
        enter(VerityVoiceHudState.VERITY_TOO_FAR, 0L);
        heldReleaseUntilMs = 0L;
    }

    public void showNoVerity() {
        if (!indicatorEnabled() || !VoiceClientConfig.SHOW_UNAVAILABLE_STATE.get()) {
            return;
        }
        enter(VerityVoiceHudState.NO_VERITY, 0L);
        heldReleaseUntilMs = 0L;
    }

    public void showMicrophoneError() {
        if (!indicatorEnabled() || !VoiceClientConfig.SHOW_UNAVAILABLE_STATE.get()) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now < micErrorCooldownUntilMs && logicalState == VerityVoiceHudState.MICROPHONE_ERROR) {
            return;
        }
        if (now < micErrorCooldownUntilMs && state == VerityVoiceHudState.MICROPHONE_ERROR) {
            return;
        }
        micErrorCooldownUntilMs = now + MIC_ERROR_COOLDOWN_MS;
        enter(VerityVoiceHudState.MICROPHONE_ERROR, 2000L);
    }

    public void showModelMissing() {
        if (!indicatorEnabled() || !VoiceClientConfig.SHOW_UNAVAILABLE_STATE.get()) {
            return;
        }
        enter(VerityVoiceHudState.MODEL_MISSING, 2200L);
    }

    public void showVoiceDisabled() {
        if (!indicatorEnabled()) {
            return;
        }
        enter(VerityVoiceHudState.VOICE_DISABLED, 1400L);
    }

    public static boolean indicatorEnabled() {
        return VoiceClientConfig.SHOW_PUSH_TO_TALK_INDICATOR.get()
                && VoiceClientConfig.SHOW_MICROPHONE_INDICATOR.get();
    }

    public void hide() {
        awaitingCapture = false;
        previewMode = false;
        resultUntilMs = 0L;
        processingUntilMs = 0L;
        readyUntilMs = 0L;
        heldReleaseUntilMs = 0L;
        enter(VerityVoiceHudState.HIDDEN, 0L);
    }

    public void reset() {
        hide();
        lastAcceptedIntent = null;
        lastAcceptedIntentName = "";
        animation.setVisible(false);
    }

    public void preview(VerityVoiceHudState previewState) {
        previewMode = true;
        if (previewState == null || previewState == VerityVoiceHudState.HIDDEN) {
            hide();
            return;
        }
        if (previewState == VerityVoiceHudState.COMMAND_RECOGNIZED) {
            showCommandRecognized(com.universeexe.verityvoice.common.VoiceIntents.HELLO);
            previewMode = true;
            return;
        }
        enter(previewState, previewState.isResultState() ? VoiceClientConfig.RESULT_DISPLAY_MS.get() : 0L);
        if (previewState == VerityVoiceHudState.PROCESSING) {
            processingUntilMs = System.currentTimeMillis() + PROCESSING_TIMEOUT_MS;
        }
    }

    public void onPushPressed() {
        pushHeld = true;
        heldReleaseUntilMs = 0L;
    }

    public void onPushReleased(boolean wasListening) {
        pushHeld = false;
        if (logicalState.isHeldProblemState()) {
            heldReleaseUntilMs = System.currentTimeMillis() + HELD_FADE_AFTER_RELEASE_MS;
            return;
        }
        if (wasListening || logicalState == VerityVoiceHudState.LISTENING
                || logicalState == VerityVoiceHudState.READY) {
            setProcessing();
        }
    }

    public void onServerResult(boolean accepted, @Nullable ResourceLocation intentId, String detail) {
        if (previewMode) {
            return;
        }
        if (accepted) {
            showCommandRecognized(intentId);
            return;
        }
        String d = detail == null ? "" : detail.toUpperCase();
        if (d.contains("TOO_FAR")) {
            showVerityTooFar();
            heldReleaseUntilMs = System.currentTimeMillis() + HELD_FADE_AFTER_RELEASE_MS;
        } else if (d.contains("BAD_ENTITY") || d.contains("NOT_NORMAL") || d.contains("DEMON")) {
            showNoVerity();
            heldReleaseUntilMs = System.currentTimeMillis() + HELD_FADE_AFTER_RELEASE_MS;
        } else {
            showNoCommand();
        }
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        long now = System.currentTimeMillis();

        if (!indicatorEnabled()) {
            if (logicalState != VerityVoiceHudState.HIDDEN) {
                hide();
            }
            animation.setVisible(false);
            return;
        }

        if (!previewMode) {
            syncFromCapture(now);
        }

        if (resultUntilMs > 0L && now >= resultUntilMs && logicalState.isResultState()) {
            hide();
        }
        if (processingUntilMs > 0L && logicalState == VerityVoiceHudState.PROCESSING && now >= processingUntilMs) {
            if (VoiceClientConfig.DEBUG_LOGGING.get() || hudDebug) {
                UniverseVerityVoice.LOGGER.debug("[VerityVoice] HUD processing timeout");
            }
            hide();
        }
        if (readyUntilMs > 0L && logicalState == VerityVoiceHudState.READY && now >= readyUntilMs && awaitingCapture) {
            // Capture still opening; keep READY until listening or failure.
            readyUntilMs = 0L;
        }
        if (heldReleaseUntilMs > 0L && !pushHeld && logicalState.isHeldProblemState() && now >= heldReleaseUntilMs) {
            hide();
        }

        boolean suppress = shouldSuppressVisibility(mc);
        boolean wantVisible = !suppress && logicalState.isVisibleCandidate() && indicatorEnabled();
        animation.setVisible(wantVisible);
        if (suppress && !previewMode && logicalState.isVisibleCandidate()
                && !logicalState.isResultState()) {
            // Soft-hide active listening cues when menus open; results may finish fading.
            if (logicalState == VerityVoiceHudState.LISTENING
                    || logicalState == VerityVoiceHudState.READY
                    || logicalState == VerityVoiceHudState.PROCESSING) {
                hide();
            }
        }

        if (animation.isFullyHidden() && logicalState == VerityVoiceHudState.HIDDEN) {
            previewMode = false;
        }
    }

    @SubscribeEvent
    public void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        reset();
        pushHeld = false;
    }

    private void syncFromCapture(long now) {
        if (previewMode) {
            return;
        }
        VoiceRecognitionState.MicStatus mic = VoiceRecognitionState.micStatus();
        boolean capturing = VoiceListeningController.INSTANCE.worker().isListening()
                || VoiceListeningController.INSTANCE.worker().capture().isCapturing();

        if (awaitingCapture || logicalState == VerityVoiceHudState.READY) {
            if (capturing || mic == VoiceRecognitionState.MicStatus.LISTENING) {
                setListening();
            } else if (mic == VoiceRecognitionState.MicStatus.ERROR) {
                showMicrophoneError();
                awaitingCapture = false;
            } else if (mic == VoiceRecognitionState.MicStatus.MODEL_MISSING
                    || VoiceRecognitionState.modelStatus() == VoiceRecognitionState.ModelStatus.MISSING) {
                showModelMissing();
                awaitingCapture = false;
            } else if (now - stateEnteredAtMs > 800L && awaitingCapture && !capturing) {
                // Start request failed quietly.
                if (VoiceRecognitionState.modelStatus() == VoiceRecognitionState.ModelStatus.MISSING) {
                    showModelMissing();
                } else if (!VoiceRecognitionState.lastError().isBlank()) {
                    showMicrophoneError();
                }
                awaitingCapture = false;
            }
        }

        if (logicalState == VerityVoiceHudState.LISTENING && !pushHeld && !capturing
                && VoiceRecognitionState.recognizerStatus() == VoiceRecognitionState.RecognizerStatus.PROCESSING) {
            setProcessing();
        }
    }

    private void enter(VerityVoiceHudState next, long resultDurationMs) {
        if (next == null) {
            next = VerityVoiceHudState.HIDDEN;
        }
        if (next != VerityVoiceHudState.COMMAND_RECOGNIZED && next != logicalState) {
            // keep last accepted for debug
        }
        logicalState = next;
        state = next;
        stateEnteredAtMs = System.currentTimeMillis();
        if (next == VerityVoiceHudState.HIDDEN) {
            resultUntilMs = 0L;
            processingUntilMs = 0L;
            awaitingCapture = false;
        } else if (resultDurationMs > 0L) {
            resultUntilMs = stateEnteredAtMs + resultDurationMs;
        } else if (!next.isResultState()) {
            resultUntilMs = 0L;
        }
        animation.setVisible(next.isVisibleCandidate());
    }

    public static boolean shouldSuppressVisibility(Minecraft mc) {
        if (mc == null) {
            return true;
        }
        if (mc.player == null || mc.level == null) {
            return true;
        }
        if (!mc.player.isAlive()) {
            return true;
        }
        if (mc.options.hideGui) {
            return true;
        }
        if (mc.screen != null) {
            String name = mc.screen.getClass().getName().toLowerCase();
            if (name.contains("fancymenu") || name.contains("title") || name.contains("startup")) {
                return true;
            }
            if (VoiceClientConfig.SUPPRESS_DURING_MENUS.get() && mc.screen.isPauseScreen()) {
                return true;
            }
            if (VoiceClientConfig.SUPPRESS_WHILE_CHAT_OPEN.get()
                    && mc.screen instanceof net.minecraft.client.gui.screens.ChatScreen) {
                return true;
            }
            if (VoiceClientConfig.SUPPRESS_DURING_MENUS.get()
                    && !(mc.screen instanceof com.universeexe.verityvoice.client.hud.VoiceConsentScreen)
                    && !(mc.screen instanceof net.minecraft.client.gui.screens.ChatScreen)
                    && mc.screen.isPauseScreen()) {
                return true;
            }
            // Inventory / quest books / generic fullscreen menus
            if (VoiceClientConfig.SUPPRESS_DURING_MENUS.get()
                    && mc.screen instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen) {
                return true;
            }
        }
        if (ModList.get().isLoaded("fancymenu") && mc.level == null) {
            return true;
        }
        if (mc.options.keyPlayerList.isDown()) {
            return true;
        }
        if (VoiceClientConfig.SUPPRESS_WHILE_PAUSED.get() && mc.isPaused()) {
            return true;
        }
        return false;
    }
}
