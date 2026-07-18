package com.universeexe.verityvoice.common.quest;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/**
 * Quest-compatible trigger record. Future FTB Quests (or other quest mods) can poll/subscribe.
 * This class must not import FTB APIs directly.
 */
public final class VerityVoiceIntentCriterion {
    private VerityVoiceIntentCriterion() {
    }

    /**
     * Records that {@code player} successfully used {@code intentId}.
     * Foundation only — no quest completion side effects yet.
     */
    public static void record(ServerPlayer player, ResourceLocation intentId) {
        if (player == null || intentId == null) {
            return;
        }
        // Placeholder for future custom trigger wiring.
    }
}
