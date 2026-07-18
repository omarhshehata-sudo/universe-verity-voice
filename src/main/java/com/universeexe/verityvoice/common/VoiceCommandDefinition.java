package com.universeexe.verityvoice.common;

import net.minecraft.resources.ResourceLocation;

import java.util.List;

public record VoiceCommandDefinition(
        String localId,
        ResourceLocation eventId,
        boolean enabled,
        boolean wakeWordRequired,
        boolean allowedDuringIntroduction,
        double maximumDistance,
        float minimumConfidence,
        int cooldownTicks,
        int priority,
        List<String> aliases
) {
    public VoiceCommandDefinition {
        aliases = List.copyOf(aliases);
    }
}
