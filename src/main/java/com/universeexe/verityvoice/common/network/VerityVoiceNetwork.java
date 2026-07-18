package com.universeexe.verityvoice.common.network;

import com.universeexe.verityvoice.UniverseVerityVoice;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.Optional;

public final class VerityVoiceNetwork {
    private static final String PROTOCOL = "1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(UniverseVerityVoice.MOD_ID, "main"),
            () -> PROTOCOL,
            PROTOCOL::equals,
            PROTOCOL::equals
    );

    private static int nextId;

    private VerityVoiceNetwork() {
    }

    public static void register() {
        CHANNEL.registerMessage(
                nextId++,
                VoiceIntentMatchedC2SPacket.class,
                VoiceIntentMatchedC2SPacket::encode,
                VoiceIntentMatchedC2SPacket::decode,
                VoiceIntentMatchedC2SPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER)
        );
        CHANNEL.registerMessage(
                nextId++,
                VoiceIntentResultS2CPacket.class,
                VoiceIntentResultS2CPacket::encode,
                VoiceIntentResultS2CPacket::decode,
                VoiceIntentResultS2CPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT)
        );
    }
}
