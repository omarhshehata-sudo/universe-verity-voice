package com.universeexe.verityvoice.common.quest;

import com.universeexe.verityvoice.UniverseVerityVoice;
import com.universeexe.verityvoice.common.config.VoiceCommonConfig;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.fml.ModList;

/**
 * Soft-dependency stub for future FTB Quests criteria.
 * Does not load FTB classes unless the mod is present (and still does nothing in v1).
 */
public final class VerityVoiceQuestCompat {
    private static boolean ftbPresent;

    private VerityVoiceQuestCompat() {
    }

    public static void bootstrap() {
        ftbPresent = ModList.get().isLoaded("ftbquests");
        if (ftbPresent) {
            UniverseVerityVoice.LOGGER.info("[VerityVoice] FTB Quests detected — voice intent criterion stub ready (no quests registered yet)");
        }
    }

    public static void onIntentAccepted(ServerPlayer player, ResourceLocation intentId) {
        if (!VoiceCommonConfig.ENABLE_QUEST_COMPATIBILITY.get() || !ftbPresent) {
            return;
        }
        // Future: trigger custom criterion / team data. Intentionally a no-op foundation.
        VerityVoiceIntentCriterion.record(player, intentId);
    }
}
