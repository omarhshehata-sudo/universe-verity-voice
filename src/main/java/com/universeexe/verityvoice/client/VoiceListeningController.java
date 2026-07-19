package com.universeexe.verityvoice.client;

import com.universeexe.verityvoice.UniverseVerityVoice;
import com.universeexe.verityvoice.client.hud.VerityVoiceHudController;
import com.universeexe.verityvoice.client.hud.VerityVoiceHudState;
import com.universeexe.verityvoice.common.OfficialVerityVoiceBridge;
import com.universeexe.verityvoice.common.VoiceIntents;
import com.universeexe.verityvoice.common.config.VoiceClientConfig;
import com.universeexe.verityvoice.common.network.VerityVoiceNetwork;
import com.universeexe.verityvoice.common.network.VoiceIntentMatchedC2SPacket;
import com.universeexe.verityvoice.common.VoiceIntentMatchedLimits;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

@OnlyIn(Dist.CLIENT)
public final class VoiceListeningController {
    public static final VoiceListeningController INSTANCE = new VoiceListeningController();

    private static final double EXTENDED_PROXIMITY_RANGE = 48.0;

    private final VoiceRecognitionWorker worker = new VoiceRecognitionWorker();
    private final AtomicLong sequence = new AtomicLong(1);
    private boolean pushHeld;
    private long wakeWindowUntilMs;
    private boolean started;
    private long nearbyScanCooldown;
    private boolean listeningThisHold;
    private String lastSentIntentKey = "";
    private long lastSentIntentAtMs;

    private VoiceListeningController() {
        // Worker is constructed here, but Vosk/JNA natives stay unloaded until first listen.
        worker.setUtteranceHandler(this::onUtterance);
    }

    public VoiceRecognitionWorker worker() {
        return worker;
    }

    public void bootstrap() {
        if (!started) {
            worker.start();
            started = true;
        }
    }

    public void shutdown() {
        worker.shutdown();
        started = false;
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) {
            if (worker.isListening()) {
                worker.offer(VoiceRecognitionWorker.Command.STOP_LISTEN);
            }
            if (pushHeld) {
                pushHeld = false;
                listeningThisHold = false;
                VerityVoiceHudController.INSTANCE.onPushReleased(false);
            }
            return;
        }

        updateNearbyCache(player);

        if (shouldSuppress(mc)) {
            if (worker.isListening() && ListeningMode.fromConfig(VoiceClientConfig.LISTENING_MODE.get()) != ListeningMode.PUSH_TO_TALK) {
                worker.offer(VoiceRecognitionWorker.Command.STOP_LISTEN);
            }
            if (ListeningMode.fromConfig(VoiceClientConfig.LISTENING_MODE.get()) == ListeningMode.PUSH_TO_TALK
                    && pushHeld) {
                // Keep held tracking but do not capture while suppressed.
                if (worker.isListening()) {
                    worker.offer(VoiceRecognitionWorker.Command.STOP_LISTEN);
                    listeningThisHold = false;
                }
            }
            return;
        }

        ListeningMode mode = ListeningMode.fromConfig(VoiceClientConfig.LISTENING_MODE.get());
        Proximity proximity = evaluateProximity(player);

        switch (mode) {
            case PUSH_TO_TALK -> handlePushToTalk(proximity);
            case WAKE_WORD -> handleWakeWord(proximity.inRange());
            case NEARBY_CONTINUOUS -> handleNearbyContinuous(proximity.inRange());
        }
    }

    private void handlePushToTalk(Proximity proximity) {
        boolean down = VoiceKeyMappings.TALK_TO_VERITY.isDown();
        if (down && !pushHeld) {
            pushHeld = true;
            listeningThisHold = false;
            VerityVoiceHudController.INSTANCE.onPushPressed();
            VerityVoiceHudController.INSTANCE.setLastVerityDistance(proximity.distance());

            if (!VoiceClientConfig.VOICE_COMMANDS_ENABLED.get()) {
                VerityVoiceHudController.INSTANCE.showVoiceDisabled();
                return;
            }
            // Do NOT early-out on MODEL_MISSING — ensureLoaded retries each press so installing
            // the model mid-session works without restart. Native permanent fail is checked below.
            if (worker.vosk().nativesPermanentlyFailed()) {
                VerityVoiceHudController.INSTANCE.showNativeError();
                return;
            }
            if (proximity.status() == ProximityStatus.NONE) {
                VoiceRecognitionState.setNearbyVerity("none");
                VerityVoiceHudController.INSTANCE.showNoVerity();
                return;
            }
            if (proximity.status() == ProximityStatus.TOO_FAR) {
                VerityVoiceHudController.INSTANCE.showVerityTooFar();
                return;
            }

            VerityVoiceHudController.INSTANCE.setReady();
            worker.offer(VoiceRecognitionWorker.Command.START_LISTEN);
            listeningThisHold = true;
        } else if (!down && pushHeld) {
            pushHeld = false;
            VerityVoiceHudState hudState = VerityVoiceHudController.INSTANCE.state();
            boolean wasListening = listeningThisHold
                    && (worker.isListening() || worker.capture().isCapturing()
                    || hudState == VerityVoiceHudState.LISTENING
                    || hudState == VerityVoiceHudState.READY);
            if (listeningThisHold) {
                worker.offer(VoiceRecognitionWorker.Command.STOP_LISTEN);
            }
            VerityVoiceHudController.INSTANCE.onPushReleased(wasListening);
            listeningThisHold = false;
        } else if (down && pushHeld) {
            VerityVoiceHudController.INSTANCE.setLastVerityDistance(proximity.distance());
            VerityVoiceHudState hudState = VerityVoiceHudController.INSTANCE.logicalState();
            if (listeningThisHold
                    && (worker.isListening() || worker.capture().isCapturing())
                    && hudState != VerityVoiceHudState.LISTENING
                    && hudState != VerityVoiceHudState.READY) {
                VerityVoiceHudController.INSTANCE.setListening();
            }
        }
    }

    private void handleWakeWord(boolean verityNearby) {
        if (!VoiceClientConfig.VOICE_COMMANDS_ENABLED.get()) {
            return;
        }
        if (!verityNearby) {
            if (worker.isListening()) {
                worker.offer(VoiceRecognitionWorker.Command.STOP_LISTEN);
            }
            return;
        }
        long now = System.currentTimeMillis();
        if (!worker.isListening()) {
            worker.offer(VoiceRecognitionWorker.Command.START_LISTEN);
        }
        if (wakeWindowUntilMs > 0 && now > wakeWindowUntilMs) {
            wakeWindowUntilMs = 0;
        }
    }

    private void handleNearbyContinuous(boolean verityNearby) {
        if (!VoiceClientConfig.VOICE_COMMANDS_ENABLED.get()) {
            return;
        }
        if (!verityNearby) {
            if (worker.isListening()) {
                worker.offer(VoiceRecognitionWorker.Command.STOP_LISTEN);
            }
            return;
        }
        if (!worker.isListening()) {
            worker.offer(VoiceRecognitionWorker.Command.START_LISTEN);
        }
    }

    private boolean shouldSuppress(Minecraft mc) {
        if (VoiceClientConfig.SUPPRESS_WHILE_PAUSED.get() && (mc.isPaused() || mc.screen instanceof PauseScreen)) {
            return true;
        }
        if (VoiceClientConfig.SUPPRESS_WHILE_CHAT_OPEN.get() && mc.screen instanceof ChatScreen) {
            return true;
        }
        if (VoiceClientConfig.SUPPRESS_DURING_MENUS.get() && mc.screen != null
                && !(mc.screen instanceof ChatScreen)
                && !(mc.screen instanceof com.universeexe.verityvoice.client.hud.VoiceConsentScreen)) {
            if (mc.screen.isPauseScreen()
                    || mc.screen instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen) {
                return true;
            }
        }
        return false;
    }

    private void updateNearbyCache(LocalPlayer player) {
        long now = System.currentTimeMillis();
        if (now < nearbyScanCooldown) {
            return;
        }
        nearbyScanCooldown = now + 250L;
        Proximity proximity = evaluateProximity(player);
        VerityVoiceHudController.INSTANCE.setLastVerityDistance(proximity.distance());
        if (proximity.status() == ProximityStatus.IN_RANGE && proximity.entity().isPresent()) {
            Entity e = proximity.entity().get();
            VoiceRecognitionState.setNearbyVerity(e.getId() + " @ " + String.format("%.1f", proximity.distance()));
        } else if (proximity.status() == ProximityStatus.TOO_FAR) {
            VoiceRecognitionState.setNearbyVerity("too_far @ " + String.format("%.1f", proximity.distance()));
        } else {
            VoiceRecognitionState.setNearbyVerity("none");
        }
    }

    private Proximity evaluateProximity(LocalPlayer player) {
        double listenRange = VoiceClientConfig.NORMAL_LISTENING_DISTANCE.get();
        Optional<Entity> near = OfficialVerityVoiceBridge.findNearestNormalVerity(player, listenRange);
        if (near.isPresent()) {
            return new Proximity(ProximityStatus.IN_RANGE, player.distanceTo(near.get()), near);
        }
        Optional<Entity> extended = OfficialVerityVoiceBridge.findNearestNormalVerity(player, EXTENDED_PROXIMITY_RANGE);
        if (extended.isPresent()) {
            return new Proximity(ProximityStatus.TOO_FAR, player.distanceTo(extended.get()), extended);
        }
        return Proximity.NONE;
    }

    private void onUtterance(VoiceRecognitionWorker.UtteranceResult result) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) {
            return;
        }
        ListeningMode mode = ListeningMode.fromConfig(VoiceClientConfig.LISTENING_MODE.get());
        String normalized = result.normalized();

        if (mode == ListeningMode.WAKE_WORD) {
            String wake = VoiceClientConfig.WAKE_WORD.get();
            if (VoiceTextNormalizer.containsWakeWord(normalized, wake)
                    && VoiceTextNormalizer.stripWakeWord(normalized, wake).isBlank()) {
                wakeWindowUntilMs = System.currentTimeMillis()
                        + (long) (VoiceClientConfig.WAKE_WORD_COMMAND_WINDOW_SECONDS.get() * 1000.0);
                VoiceRecognitionState.setMicStatus(VoiceRecognitionState.MicStatus.LISTENING);
                return;
            }
            if (wakeWindowUntilMs > 0 && System.currentTimeMillis() <= wakeWindowUntilMs) {
                // ok — inside command window
            } else if (!VoiceTextNormalizer.containsWakeWord(normalized, wake)) {
                return;
            }
        }

        VoiceIntentMatcher.MatchResult match = VoiceIntentMatcher.match(normalized, mode);
        if (match == null || match.intentId().equals(VoiceIntents.UNKNOWN) || match.definition() == null) {
            VoiceRecognitionState.setMicStatus(VoiceRecognitionState.MicStatus.NO_COMMAND);
            VoiceRecognitionState.setMatchedIntent("");
            VerityVoiceHudController.INSTANCE.showNoCommand();
            return;
        }

        float combined = Math.min(match.score(), result.speechConfidence());
        // Reject weak / empty-sounding recognitions (common when grammar guesses "hi").
        float minConf = Math.max(
                match.definition().minimumConfidence(),
                VoiceClientConfig.MINIMUM_RECOGNITION_CONFIDENCE.get().floatValue()
        );
        if (combined < minConf) {
            VoiceRecognitionState.setMicStatus(VoiceRecognitionState.MicStatus.NO_COMMAND);
            VerityVoiceHudController.INSTANCE.showNoCommand();
            return;
        }
        VoiceRecognitionState.setMatchScore(match.score());
        VoiceRecognitionState.setMatchedIntent(match.intentId().toString());
        VoiceRecognitionState.setSpeechConfidence(result.speechConfidence());

        Optional<Entity> verity = OfficialVerityVoiceBridge.findNearestNormalVerity(
                player, match.definition().maximumDistance()
        );
        if (verity.isEmpty()) {
            VoiceRecognitionState.setLastError("");
            VoiceRecognitionState.setPacketStatus(VoiceRecognitionState.PacketStatus.REJECTED);
            Optional<Entity> far = OfficialVerityVoiceBridge.findNearestNormalVerity(player, EXTENDED_PROXIMITY_RANGE);
            if (far.isPresent()) {
                VerityVoiceHudController.INSTANCE.showVerityTooFar();
            } else {
                VerityVoiceHudController.INSTANCE.showNoVerity();
            }
            return;
        }

        // Always send a short sanitized phrase for trust/insult/thanks matching (never raw audio).
        String phrase = normalized.length() > VoiceIntentMatchedLimits.MAX_PHRASE_LENGTH
                ? normalized.substring(0, VoiceIntentMatchedLimits.MAX_PHRASE_LENGTH)
                : normalized;

        String intentKey = VoiceIntents.HELLO.equals(match.intentId())
                ? match.intentId().toString()
                : match.intentId() + "|" + phrase;
        long nowMs = System.currentTimeMillis();
        long debounceMs = VoiceIntents.HELLO.equals(match.intentId()) ? 5000L : 2500L;
        if (VoiceIntents.HELLO.equals(match.intentId())) {
            if (nowMs - lastSentIntentAtMs < debounceMs) {
                VoiceRecognitionState.setMicStatus(VoiceRecognitionState.MicStatus.NO_COMMAND);
                VerityVoiceHudController.INSTANCE.showNoCommand();
                return;
            }
        } else if (intentKey.equals(lastSentIntentKey) && nowMs - lastSentIntentAtMs < debounceMs) {
            VoiceRecognitionState.setMicStatus(VoiceRecognitionState.MicStatus.NO_COMMAND);
            VerityVoiceHudController.INSTANCE.showNoCommand();
            return;
        }
        lastSentIntentKey = intentKey;
        lastSentIntentAtMs = nowMs;

        VoiceIntentMatchedC2SPacket packet = new VoiceIntentMatchedC2SPacket(
                VoiceIntentMatchedLimits.PACKET_VERSION,
                match.intentId(),
                verity.get().getId(),
                combined,
                sequence.getAndIncrement(),
                phrase
        );
        VerityVoiceNetwork.CHANNEL.sendToServer(packet);
        VoiceRecognitionState.setPacketStatus(VoiceRecognitionState.PacketStatus.SENT);
        // Stay in PROCESSING until the server accepts — never show success early.
        VoiceRecognitionState.setMicStatus(VoiceRecognitionState.MicStatus.RECOGNIZING);
        VerityVoiceHudController.INSTANCE.setProcessing();

        if (VoiceClientConfig.SHOW_DETECTED_INTENT.get() || VoiceClientConfig.DEBUG_LOGGING.get()) {
            player.displayClientMessage(net.minecraft.network.chat.Component.literal(
                    "[VOICE DEBUG] Intent: " + match.intentId().getPath()
            ), true);
        }
        if (VoiceClientConfig.DEBUG_LOGGING.get()) {
            UniverseVerityVoice.LOGGER.info("[VerityVoice] Matched {} score={} phrase='{}'",
                    match.intentId(), match.score(), normalized);
        }

        if (mode == ListeningMode.WAKE_WORD) {
            wakeWindowUntilMs = 0;
        }
    }

    @SubscribeEvent
    public void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        pushHeld = false;
        listeningThisHold = false;
        wakeWindowUntilMs = 0;
        worker.offer(VoiceRecognitionWorker.Command.STOP_LISTEN);
        VoiceRecognitionState.setMicStatus(VoiceRecognitionState.MicStatus.OFF);
        VoiceRecognitionState.setPacketStatus(VoiceRecognitionState.PacketStatus.NONE);
        VerityVoiceHudController.INSTANCE.reset();
    }

    public void enableVoice(boolean enabled) {
        VoiceClientConfig.VOICE_COMMANDS_ENABLED.set(enabled);
        VoiceClientConfig.CONSENT_ANSWERED.set(true);
        if (enabled) {
            VoiceRecognitionState.setMicStatus(VoiceRecognitionState.MicStatus.READY);
            worker.offer(VoiceRecognitionWorker.Command.RELOAD_MODEL);
        } else {
            worker.offer(VoiceRecognitionWorker.Command.UNLOAD_MODEL);
        }
    }

    private enum ProximityStatus {
        NONE,
        IN_RANGE,
        TOO_FAR
    }

    private record Proximity(ProximityStatus status, double distance, Optional<Entity> entity) {
        static final Proximity NONE = new Proximity(ProximityStatus.NONE, -1.0, Optional.empty());

        boolean inRange() {
            return status == ProximityStatus.IN_RANGE;
        }
    }
}
