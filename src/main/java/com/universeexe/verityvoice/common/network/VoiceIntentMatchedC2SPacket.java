package com.universeexe.verityvoice.common.network;

import com.universeexe.verityvoice.UniverseVerityVoice;
import com.universeexe.verityvoice.common.VoiceIntentMatchedLimits;
import com.universeexe.verityvoice.common.VerityVoiceServerValidator;
import com.universeexe.verityvoice.common.VoiceIntents;
import com.universeexe.verityvoice.common.config.VoiceCommonConfig;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import javax.annotation.Nullable;
import java.util.function.Supplier;

/**
 * Client → server intent submission. Never carries audio bytes.
 */
public record VoiceIntentMatchedC2SPacket(
        int packetVersion,
        ResourceLocation intentId,
        int verityEntityId,
        float recognitionConfidence,
        long clientSequenceNumber,
        @Nullable String sanitizedPhrase
) {
    public static void encode(VoiceIntentMatchedC2SPacket packet, FriendlyByteBuf buf) {
        buf.writeVarInt(packet.packetVersion);
        buf.writeResourceLocation(packet.intentId);
        buf.writeVarInt(packet.verityEntityId);
        buf.writeFloat(packet.recognitionConfidence);
        buf.writeVarLong(packet.clientSequenceNumber);
        boolean hasPhrase = packet.sanitizedPhrase != null && !packet.sanitizedPhrase.isEmpty();
        buf.writeBoolean(hasPhrase);
        if (hasPhrase) {
            buf.writeUtf(packet.sanitizedPhrase, VoiceIntentMatchedLimits.MAX_PHRASE_LENGTH);
        }
    }

    public static VoiceIntentMatchedC2SPacket decode(FriendlyByteBuf buf) {
        int version = buf.readVarInt();
        ResourceLocation intentId = buf.readResourceLocation();
        int verityId = buf.readVarInt();
        float confidence = buf.readFloat();
        long sequence = buf.readVarLong();
        String phrase = null;
        if (buf.readBoolean()) {
            phrase = buf.readUtf(VoiceIntentMatchedLimits.MAX_PHRASE_LENGTH);
        }
        return new VoiceIntentMatchedC2SPacket(version, intentId, verityId, confidence, sequence, phrase);
    }

    public static void handle(VoiceIntentMatchedC2SPacket packet, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player == null) {
                return;
            }
            if (packet.packetVersion != VoiceIntentMatchedLimits.PACKET_VERSION) {
                if (VoiceCommonConfig.ENABLE_DEBUG_COMMANDS.get()) {
                    UniverseVerityVoice.LOGGER.debug("[VerityVoice] Rejected bad packet version from {}", player.getGameProfile().getName());
                }
                return;
            }
            ResourceLocation intentId = packet.intentId;
            if (intentId == null
                    || !UniverseVerityVoice.MOD_ID.equals(intentId.getNamespace())
                    || intentId.getPath().length() > VoiceIntentMatchedLimits.MAX_INTENT_PATH_LENGTH
                    || !VoiceIntents.isRegistered(intentId)) {
                return;
            }
            float confidence = Math.max(0.0f, Math.min(1.0f, packet.recognitionConfidence));
            String phrase = VerityVoiceServerValidator.sanitizePhrase(packet.sanitizedPhrase);
            VerityVoiceServerValidator.RejectReason reason = VerityVoiceServerValidator.validateAndDispatch(
                    player, intentId, packet.verityEntityId, confidence, packet.clientSequenceNumber, phrase
            );
            if (reason != VerityVoiceServerValidator.RejectReason.OK && VoiceCommonConfig.ENABLE_DEBUG_COMMANDS.get()) {
                UniverseVerityVoice.LOGGER.debug(
                        "[VerityVoice] Rejected intent {} from {}: {}",
                        intentId, player.getGameProfile().getName(), reason
                );
            }
        });
        ctx.setPacketHandled(true);
    }
}
