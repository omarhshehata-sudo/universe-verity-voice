package com.universeexe.verityvoice.common;

import com.universeexe.verityvoice.UniverseVerityVoice;
import com.universeexe.verityvoice.common.config.VoiceCommonConfig;
import com.universeexe.verityvoice.common.event.VerityVoiceIntentEvent;
import com.universeexe.verityvoice.common.network.VoiceIntentResultS2CPacket;
import com.universeexe.verityvoice.common.quest.VerityVoiceQuestCompat;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.network.PacketDistributor;

import javax.annotation.Nullable;
import java.util.Optional;

public final class VerityVoiceServerValidator {
    private VerityVoiceServerValidator() {
    }

    public enum RejectReason {
        OK,
        DISABLED,
        DEAD,
        UNKNOWN_INTENT,
        BAD_ENTITY,
        NOT_NORMAL_VERITY,
        DEMON,
        TOO_FAR,
        WRONG_OWNER,
        INTRO_BLOCKED,
        COOLDOWN,
        RATE_LIMIT,
        DUPLICATE_SEQUENCE,
        LOW_CONFIDENCE,
        WRONG_DIMENSION
    }

    public static RejectReason validateAndDispatch(
            ServerPlayer player,
            ResourceLocation intentId,
            int verityEntityId,
            float confidence,
            long sequence,
            @Nullable String sanitizedPhrase
    ) {
        if (!VoiceCommonConfig.VOICE_INTENT_EVENTS_ENABLED.get()) {
            return reject(player, intentId, RejectReason.DISABLED);
        }
        if (player == null || !player.isAlive()) {
            return RejectReason.DEAD;
        }
        if (!VoiceIntents.isRegistered(intentId) || intentId.equals(VoiceIntents.UNKNOWN)) {
            return reject(player, intentId, RejectReason.UNKNOWN_INTENT);
        }

        Optional<VoiceCommandDefinition> defOpt = VoiceCommandReloadListener.INSTANCE.get(intentId);
        if (defOpt.isEmpty() || !defOpt.get().enabled()) {
            return reject(player, intentId, RejectReason.UNKNOWN_INTENT);
        }
        VoiceCommandDefinition def = defOpt.get();

        if (confidence < def.minimumConfidence()) {
            return reject(player, intentId, RejectReason.LOW_CONFIDENCE);
        }

        long gameTime = player.level().getGameTime();
        if (!VerityVoiceCooldownManager.INSTANCE.acceptSequence(player, sequence)) {
            return reject(player, intentId, RejectReason.DUPLICATE_SEQUENCE);
        }
        if (!VerityVoiceCooldownManager.INSTANCE.allowRate(
                player,
                gameTime,
                VoiceCommonConfig.INTENT_RATE_LIMIT_COUNT.get(),
                VoiceCommonConfig.INTENT_RATE_LIMIT_WINDOW_TICKS.get()
        )) {
            return reject(player, intentId, RejectReason.RATE_LIMIT);
        }
        if (VerityVoiceCooldownManager.INSTANCE.isOnCooldown(player, intentId, gameTime)) {
            return reject(player, intentId, RejectReason.COOLDOWN);
        }

        Entity verity = player.level().getEntity(verityEntityId);
        if (verity == null || !verity.isAlive()) {
            return reject(player, intentId, RejectReason.BAD_ENTITY);
        }
        if (verity.level() != player.level()) {
            return reject(player, intentId, RejectReason.WRONG_DIMENSION);
        }
        if (VoiceCommonConfig.REJECT_DEMON_VERITY.get() && OfficialVerityVoiceBridge.isDemonVerity(verity)) {
            return reject(player, intentId, RejectReason.DEMON);
        }
        if (!OfficialVerityVoiceBridge.isVerityAvailableForConversation(verity)) {
            return reject(player, intentId, RejectReason.NOT_NORMAL_VERITY);
        }

        if (VoiceCommonConfig.REQUIRE_NEARBY_VERITY.get()) {
            double maxDist = Math.min(def.maximumDistance(), VoiceCommonConfig.MAXIMUM_ALLOWED_DISTANCE.get());
            if (player.distanceTo(verity) > maxDist) {
                return reject(player, intentId, RejectReason.TOO_FAR);
            }
        }

        if (VoiceCommonConfig.REQUIRE_ASSOCIATED_VERITY.get()
                && !OfficialVerityVoiceBridge.belongsToPlayer(verity, player)) {
            return reject(player, intentId, RejectReason.WRONG_OWNER);
        }

        boolean introDone = OfficialVerityVoiceBridge.isIntroductionComplete(player);
        if (!introDone && !def.allowedDuringIntroduction()
                && !VoiceCommonConfig.ALLOW_COMMANDS_BEFORE_INTRODUCTION.get()) {
            return reject(player, intentId, RejectReason.INTRO_BLOCKED);
        }

        String phrase = sanitizedPhrase == null ? "" : sanitizePhrase(sanitizedPhrase);
        VerityVoiceIntentEvent event = new VerityVoiceIntentEvent(
                player, verity, intentId, confidence, sequence, phrase
        );
        MinecraftForge.EVENT_BUS.post(event);
        if (event.isCanceled()) {
            ack(player, false, intentId, "canceled");
            return RejectReason.OK;
        }

        VerityVoiceCooldownManager.INSTANCE.applyCooldown(player, intentId, def.cooldownTicks(), gameTime);
        VerityVoiceQuestCompat.onIntentAccepted(player, intentId);

        if (VoiceCommonConfig.ENABLE_DEBUG_COMMANDS.get()) {
            UniverseVerityVoice.LOGGER.info(
                    "[VerityVoice] Accepted intent {} from {} (verity={}, conf={})",
                    intentId, player.getGameProfile().getName(), verity.getId(), confidence
            );
        }
        ack(player, true, intentId, "accepted");
        return RejectReason.OK;
    }

    private static RejectReason reject(ServerPlayer player, ResourceLocation intentId, RejectReason reason) {
        if (player != null && intentId != null) {
            ack(player, false, intentId, reason.name());
        }
        return reason;
    }

    private static void ack(ServerPlayer player, boolean accepted, ResourceLocation intentId, String detail) {
        com.universeexe.verityvoice.common.network.VerityVoiceNetwork.CHANNEL.send(
                PacketDistributor.PLAYER.with(() -> player),
                new VoiceIntentResultS2CPacket(accepted, intentId, detail)
        );
    }

    public static String sanitizePhrase(String raw) {
        if (raw == null) {
            return "";
        }
        String cleaned = raw.replaceAll("[\\r\\n\\t]", " ").trim();
        if (cleaned.length() > VoiceIntentMatchedLimits.MAX_PHRASE_LENGTH) {
            cleaned = cleaned.substring(0, VoiceIntentMatchedLimits.MAX_PHRASE_LENGTH);
        }
        // Never treat as a command — strip leading slash noise.
        if (cleaned.startsWith("/")) {
            cleaned = cleaned.substring(1).trim();
        }
        return cleaned;
    }
}
