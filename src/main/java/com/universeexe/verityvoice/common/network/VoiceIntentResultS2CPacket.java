package com.universeexe.verityvoice.common.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record VoiceIntentResultS2CPacket(boolean accepted, ResourceLocation intentId, String detail) {
    public static void encode(VoiceIntentResultS2CPacket packet, FriendlyByteBuf buf) {
        buf.writeBoolean(packet.accepted);
        buf.writeResourceLocation(packet.intentId);
        buf.writeUtf(packet.detail == null ? "" : packet.detail, 64);
    }

    public static VoiceIntentResultS2CPacket decode(FriendlyByteBuf buf) {
        return new VoiceIntentResultS2CPacket(buf.readBoolean(), buf.readResourceLocation(), buf.readUtf(64));
    }

    public static void handle(VoiceIntentResultS2CPacket packet, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                com.universeexe.verityvoice.client.VoiceRecognitionState.onServerResult(
                        packet.accepted, packet.intentId, packet.detail
                )));
        ctx.setPacketHandled(true);
    }
}
