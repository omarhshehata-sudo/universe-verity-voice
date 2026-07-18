package com.universeexe.verityvoice.common.event;

import com.universeexe.verity.entity.VerityEntity;
import com.universeexe.verity.registry.VeritySounds;
import com.universeexe.verityvoice.UniverseVerityVoice;
import com.universeexe.verityvoice.common.VoiceIntents;
import com.universeexe.verityvoice.common.config.VoiceCommonConfig;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * First gameplay responses layered on the speech-to-intent foundation.
 * FOLLOW: play one of two voice lines (50/50) + start rolling follow. STAY: stop following.
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
        // Server-side 50/50 pick — level.playSound(null, ...) broadcasts the same chosen event to all clients.
        boolean useBehindYou = verity.getRandom().nextBoolean();
        SoundEvent line = useBehindYou
                ? VeritySounds.VOICE_ALRIGHT_RIGHT_BEHIND_YOU.get()
                : VeritySounds.VOICE_OKAY_WHERE_ARE_WE_GOING.get();

        verity.level().playSound(
                null,
                verity.getX(),
                verity.getY(),
                verity.getZ(),
                line,
                SoundSource.NEUTRAL,
                0.95f,
                1.0f
        );
        // Both lines are ~2–3s; keep talking mouth for ~2.5s.
        verity.beginTalkingForTicks(50);
        verity.startFollowingOwner();

        if (VoiceCommonConfig.ENABLE_DEBUG_COMMANDS.get()) {
            UniverseVerityVoice.LOGGER.info(
                    "[VerityVoice] FOLLOW response: line={} + startFollowing for {} / verity={}",
                    useBehindYou ? "alright_right_behind_you" : "okay_where_are_we_going",
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
