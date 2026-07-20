package com.universeexe.verityvoice.common.event;

import com.universeexe.verity.entity.VerityEntity;
import com.universeexe.verity.quest.VerityQuestManager;
import com.universeexe.verity.registry.VeritySounds;
import com.universeexe.verity.trust.TrustReason;
import com.universeexe.verity.trust.VerityTrustManager;
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
 * HELLO: Quest 2 first-greeting conversation (single sequential path via VerityQuestManager).
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

        String phrase = event.getSanitizedPhrase() == null ? "" : event.getSanitizedPhrase();
        if (!phrase.isBlank() && VerityTrustManager.isInsultPhrase(phrase)) {
            VerityTrustManager.addTrustDefault(player, verity, TrustReason.INSULTED_VERITY);
        }

        if (!phrase.isBlank() && VerityQuestManager.tryHandleQ3ResponsePhrase(player, verity, phrase)) {
            return;
        }

        if (VoiceIntents.FOLLOW.equals(event.getIntentId())) {
            VerityTrustManager.noteVoiceCommand(player, "follow");
            handleFollow(player, verity);
        } else if (VoiceIntents.STAY.equals(event.getIntentId())) {
            handleStay(player, verity);
        } else if (VoiceIntents.HELLO.equals(event.getIntentId())) {
            handleHello(player, verity, phrase);
        } else if (VoiceIntents.MAKE_SOUND.equals(event.getIntentId())) {
            handleMakeSound(player, verity, phrase);
        } else if (VoiceIntents.Q3_ANSWER.equals(event.getIntentId())) {
            handleQ3Answer(player, verity, phrase);
        } else if (VoiceIntents.FIND_NEAREST_DIAMONDS.equals(event.getIntentId())
                || VoiceIntents.FIND_NEAREST_VILLAGE.equals(event.getIntentId())) {
            VerityTrustManager.noteVoiceCommand(player, event.getIntentId().getPath());
            VerityTrustManager.markHelpCompleted(player, event.getIntentId().toString());
        } else if (!phrase.isBlank() && VerityTrustManager.isThanksPhrase(phrase)) {
            VerityTrustManager.addTrustDefault(player, verity, TrustReason.THANKED_AFTER_HELP);
        }
    }

    private static void handleMakeSound(ServerPlayer player, VerityEntity verity, String phrase) {
        VerityQuestManager.handleMakeSoundIntent(player, verity, phrase);

        if (VoiceCommonConfig.ENABLE_DEBUG_COMMANDS.get()) {
            UniverseVerityVoice.LOGGER.info(
                    "[VerityVoice] MAKE_SOUND quest response for {} / verity={}",
                    player.getGameProfile().getName(),
                    verity.getId()
            );
            player.displayClientMessage(
                    net.minecraft.network.chat.Component.literal("[VOICE] Make a sound → Verity quest dialogue"),
                    true
            );
        }
    }

    private static void handleQ3Answer(ServerPlayer player, VerityEntity verity, String phrase) {
        VerityQuestManager.tryHandleQ3ResponsePhrase(player, verity, phrase);
    }

    private static void handleHello(ServerPlayer player, VerityEntity verity, String phrase) {
        VerityQuestManager.handleHelloIntent(player, verity, phrase);

        if (VoiceCommonConfig.ENABLE_DEBUG_COMMANDS.get()) {
            UniverseVerityVoice.LOGGER.info(
                    "[VerityVoice] HELLO quest response for {} / verity={} phrase='{}'",
                    player.getGameProfile().getName(),
                    verity.getId(),
                    phrase
            );
            player.displayClientMessage(
                    net.minecraft.network.chat.Component.literal("[VOICE] Hello → Verity quest dialogue"),
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
