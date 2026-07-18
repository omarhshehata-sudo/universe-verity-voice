package com.universeexe.verityvoice.common.event;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.eventbus.api.Cancelable;
import net.minecraftforge.eventbus.api.Event;

/**
 * Fired on the Forge EVENT_BUS after server validation accepts a voice intent.
 * Future quest/dialogue/navigation systems should subscribe here.
 */
@Cancelable
public class VerityVoiceIntentEvent extends Event {
    private final ServerPlayer player;
    private final Entity verity;
    private final ResourceLocation intentId;
    private final float confidence;
    private final long sequenceNumber;
    private final String sanitizedPhrase;
    private InteractionResult result = InteractionResult.PASS;

    public VerityVoiceIntentEvent(
            ServerPlayer player,
            Entity verity,
            ResourceLocation intentId,
            float confidence,
            long sequenceNumber,
            String sanitizedPhrase
    ) {
        this.player = player;
        this.verity = verity;
        this.intentId = intentId;
        this.confidence = confidence;
        this.sequenceNumber = sequenceNumber;
        this.sanitizedPhrase = sanitizedPhrase == null ? "" : sanitizedPhrase;
    }

    public ServerPlayer getPlayer() {
        return player;
    }

    public Entity getVerity() {
        return verity;
    }

    public ResourceLocation getIntentId() {
        return intentId;
    }

    public float getConfidence() {
        return confidence;
    }

    public long getSequenceNumber() {
        return sequenceNumber;
    }

    public String getSanitizedPhrase() {
        return sanitizedPhrase;
    }

    public InteractionResult getInteractionResult() {
        return result;
    }

    public void setInteractionResult(InteractionResult result) {
        this.result = result == null ? InteractionResult.PASS : result;
    }
}
