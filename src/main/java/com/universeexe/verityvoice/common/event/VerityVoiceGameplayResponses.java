package com.universeexe.verityvoice.common.event;

import com.universeexe.verity.data.VerityPlayerData;
import com.universeexe.verity.entity.VerityEntity;
import com.universeexe.verity.registry.VeritySounds;
import com.universeexe.verityvoice.UniverseVerityVoice;
import com.universeexe.verityvoice.common.VoiceIntents;
import com.universeexe.verityvoice.common.config.VoiceCommonConfig;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.RegistryObject;

/**
 * Gameplay responses layered on the speech-to-intent foundation.
 * FOLLOW: play one of three voice lines (~33% each) + start rolling follow.
 * STAY: stop following.
 * HELLO: 50/50 greeting line, plus a one-time whisper follow-up per player.
 */
public final class VerityVoiceGameplayResponses {
    @SuppressWarnings("unchecked")
    private static final RegistryObject<SoundEvent>[] FOLLOW_LINES = new RegistryObject[]{
            VeritySounds.VOICE_OKAY_WHERE_ARE_WE_GOING,
            VeritySounds.VOICE_ALRIGHT_RIGHT_BEHIND_YOU,
            VeritySounds.VOICE_LEAD_THE_WAY
    };

    private static final String[] FOLLOW_LINE_IDS = {
            "okay_where_are_we_going",
            "alright_right_behind_you",
            "lead_the_way"
    };

    private static final int[] FOLLOW_LINE_DURATIONS = {
            VerityEntity.DUR_OKAY_WHERE_ARE_WE_GOING,
            VerityEntity.DUR_ALRIGHT_RIGHT_BEHIND_YOU,
            VerityEntity.DUR_LEAD_THE_WAY
    };

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
        } else if (VoiceIntents.HELLO.equals(event.getIntentId())) {
            handleHello(player, verity);
        }
    }

    private static void handleHello(ServerPlayer player, VerityEntity verity) {
        boolean firstWhisper = !VerityPlayerData.hasPlayedHelloWhisper(player);
        verity.playHelloVoiceResponse(firstWhisper);
        if (firstWhisper) {
            VerityPlayerData.setHelloWhisperPlayed(player, true);
        }

        if (VoiceCommonConfig.ENABLE_DEBUG_COMMANDS.get()) {
            UniverseVerityVoice.LOGGER.info(
                    "[VerityVoice] HELLO response for {} / verity={} (whisperFollowup={})",
                    player.getGameProfile().getName(),
                    verity.getId(),
                    firstWhisper
            );
            player.displayClientMessage(
                    net.minecraft.network.chat.Component.literal(
                            firstWhisper ? "[VOICE] Hello + first whisper follow-up" : "[VOICE] Hello reply"
                    ),
                    true
            );
        }
    }

    private static void handleFollow(ServerPlayer player, VerityEntity verity) {
        // Server-side equal pick among three lines — playVoiceLine syncs sound + talking anim duration.
        int pick = verity.getRandom().nextInt(FOLLOW_LINES.length);
        SoundEvent line = FOLLOW_LINES[pick].get();
        verity.playVoiceLine(line, FOLLOW_LINE_DURATIONS[pick]);
        verity.startFollowingOwner();

        if (VoiceCommonConfig.ENABLE_DEBUG_COMMANDS.get()) {
            UniverseVerityVoice.LOGGER.info(
                    "[VerityVoice] FOLLOW response: line={} + startFollowing for {} / verity={}",
                    FOLLOW_LINE_IDS[pick],
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
