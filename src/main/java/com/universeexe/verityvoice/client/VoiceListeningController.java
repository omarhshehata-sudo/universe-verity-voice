package com.universeexe.verityvoice.client;

import com.universeexe.verityvoice.UniverseVerityVoice;
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

    private final VoiceRecognitionWorker worker = new VoiceRecognitionWorker();
    private final AtomicLong sequence = new AtomicLong(1);
    private boolean pushHeld;
    private long wakeWindowUntilMs;
    private boolean started;
    private long nearbyScanCooldown;

    private VoiceListeningController() {
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
            pushHeld = false;
            return;
        }

        updateNearbyCache(player);

        if (!VoiceClientConfig.VOICE_COMMANDS_ENABLED.get()) {
            return;
        }
        if (shouldSuppress(mc)) {
            if (worker.isListening() && ListeningMode.fromConfig(VoiceClientConfig.LISTENING_MODE.get()) != ListeningMode.PUSH_TO_TALK) {
                worker.offer(VoiceRecognitionWorker.Command.STOP_LISTEN);
            }
            return;
        }

        ListeningMode mode = ListeningMode.fromConfig(VoiceClientConfig.LISTENING_MODE.get());
        boolean verityNearby = isVerityInRange(player);

        switch (mode) {
            case PUSH_TO_TALK -> handlePushToTalk(verityNearby);
            case WAKE_WORD -> handleWakeWord(verityNearby);
            case NEARBY_CONTINUOUS -> handleNearbyContinuous(verityNearby);
        }
    }

    private void handlePushToTalk(boolean verityNearby) {
        boolean down = VoiceKeyMappings.TALK_TO_VERITY.isDown();
        if (down && !pushHeld) {
            pushHeld = true;
            if (verityNearby) {
                worker.offer(VoiceRecognitionWorker.Command.START_LISTEN);
            } else {
                VoiceRecognitionState.setLastError("No Verity nearby");
            }
        } else if (!down && pushHeld) {
            pushHeld = false;
            worker.offer(VoiceRecognitionWorker.Command.STOP_LISTEN);
        }
    }

    private void handleWakeWord(boolean verityNearby) {
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
            // Allow gameplay overlays; block generic menus.
            if (mc.screen.isPauseScreen()) {
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
        double range = VoiceClientConfig.NORMAL_LISTENING_DISTANCE.get();
        Optional<Entity> near = OfficialVerityVoiceBridge.findNearestNormalVerity(player, range);
        if (near.isPresent()) {
            Entity e = near.get();
            VoiceRecognitionState.setNearbyVerity(e.getId() + " @ " + String.format("%.1f", player.distanceTo(e)));
        } else {
            VoiceRecognitionState.setNearbyVerity("none");
        }
    }

    private boolean isVerityInRange(LocalPlayer player) {
        double range = VoiceClientConfig.NORMAL_LISTENING_DISTANCE.get();
        return OfficialVerityVoiceBridge.findNearestNormalVerity(player, range).isPresent();
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
            return;
        }

        float combined = Math.min(match.score(), result.speechConfidence());
        VoiceRecognitionState.setMatchScore(match.score());
        VoiceRecognitionState.setMatchedIntent(match.intentId().toString());
        VoiceRecognitionState.setSpeechConfidence(result.speechConfidence());

        Optional<Entity> verity = OfficialVerityVoiceBridge.findNearestNormalVerity(
                player, match.definition().maximumDistance()
        );
        if (verity.isEmpty()) {
            VoiceRecognitionState.setLastError("No Verity in range for intent");
            VoiceRecognitionState.setPacketStatus(VoiceRecognitionState.PacketStatus.REJECTED);
            return;
        }

        String phrase = null;
        if (VoiceClientConfig.SEND_RECOGNIZED_TEXT_TO_SERVER_FOR_DEBUG.get()) {
            phrase = normalized.length() > VoiceIntentMatchedLimits.MAX_PHRASE_LENGTH
                    ? normalized.substring(0, VoiceIntentMatchedLimits.MAX_PHRASE_LENGTH)
                    : normalized;
        }

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
        VoiceRecognitionState.setMicStatus(VoiceRecognitionState.MicStatus.COMMAND_DETECTED);

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
        wakeWindowUntilMs = 0;
        worker.offer(VoiceRecognitionWorker.Command.STOP_LISTEN);
        VoiceRecognitionState.setMicStatus(VoiceRecognitionState.MicStatus.OFF);
        VoiceRecognitionState.setPacketStatus(VoiceRecognitionState.PacketStatus.NONE);
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
}
