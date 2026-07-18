package com.universeexe.verityvoice.common.event;

import com.universeexe.verity.entity.VerityEntity;
import com.universeexe.verity.registry.VeritySounds;
import com.universeexe.verityvoice.UniverseVerityVoice;
import com.universeexe.verityvoice.common.VoiceIntents;
import com.universeexe.verityvoice.common.config.VoiceCommonConfig;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * First gameplay responses layered on the speech-to-intent foundation.
 * FOLLOW: play voice line + start rolling follow. STAY: stop following.
 */
public final class VerityVoiceGameplayResponses {
    public VerityVoiceGameplayResponses() {
    }

    @SubscribeEvent
    public void onVoiceIntent(VerityVoiceIntentEvent event) {
        if (event.isCanceled()) {
            return;
        }
        Entity entity = event.getVerity();
        if (!(entity instanceof VerityEntity verity)) {
            return;
        }
        ServerPlayer player = event.getPlayer();

        if (VoiceIntents.FOLLOW.equals(event.getIntentId())) {
            handleFollow(player, verity);
        } else if (VoiceIntents.STAY.equals(event.getIntentId())) {
            handleStay(player, verity);
        }
    }

    private static void handleFollow(ServerPlayer player, VerityEntity verity) {
        verity.level().playSound(
                null,
                verity.getX(),
                verity.getY(),
                verity.getZ(),
                VeritySounds.VOICE_OKAY_WHERE_ARE_WE_GOING.get(),
                SoundSource.NEUTRAL,
                0.95f,
                1.0f
        );
        // ~2s line at 20 tps; roll anim from startFollowingOwner runs concurrently.
        verity.beginTalkingForTicks(40);
        verity.startFollowingOwner();

        if (VoiceCommonConfig.ENABLE_DEBUG_COMMANDS.get()) {
            UniverseVerityVoice.LOGGER.info(
                    "[VerityVoice] FOLLOW response: sound + startFollowing for {} / verity={}",
                    player.getGameProfile().getName(),
                    verity.getId()
            );
            player.displayClientMessage(
                    net.minecraft.network.chat.Component.literal("[VOICE] Verity is following you"),
                    true
            );
        }
    }

    private static void handleStay(ServerPlayer player, VerityEntity verity) {
        verity.stopFollowing();
        if (VoiceCommonConfig.ENABLE_DEBUG_COMMANDS.get()) {
            UniverseVerityVoice.LOGGER.info(
                    "[VerityVoice] STAY response: stopFollowing for {} / verity={}",
                    player.getGameProfile().getName(),
                    verity.getId()
            );
            player.displayClientMessage(
                    net.minecraft.network.chat.Component.literal("[VOICE] Verity stopped following"),
                    true
            );
        }
    }
}
