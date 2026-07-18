package com.universeexe.verityvoice.common;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

/**
 * Validated context passed into {@link com.universeexe.verityvoice.common.event.VerityVoiceIntentEvent}.
 */
public record VerityVoiceIntentContext(
        ServerPlayer player,
        Entity verity,
        ResourceLocation intentId,
        float confidence,
        long sequenceNumber,
        String sanitizedPhrase
) {
}
