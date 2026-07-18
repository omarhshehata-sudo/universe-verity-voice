package com.universeexe.verityvoice.common;

import net.minecraft.resources.ResourceLocation;

/**
 * Named logical intents produced by offline recognition + alias matching.
 */
public enum VerityVoiceIntent {
    MEET_VERITY(VoiceIntents.MEET_VERITY),
    HELLO_VERITY(VoiceIntents.HELLO),
    MAKE_SOUND(VoiceIntents.MAKE_SOUND),
    ASK_FAVORITE_SONG(VoiceIntents.ASK_FAVORITE_SONG),
    FIND_NEAREST_VILLAGE(VoiceIntents.FIND_NEAREST_VILLAGE),
    FIND_NEAREST_DIAMONDS(VoiceIntents.FIND_NEAREST_DIAMONDS),
    FOLLOW_PLAYER(VoiceIntents.FOLLOW),
    STAY_HERE(VoiceIntents.STAY),
    COME_HERE(VoiceIntents.COME_HERE),
    ASK_IF_ANGRY(VoiceIntents.ASK_IF_ANGRY),
    ASK_WHAT_HAPPENED(VoiceIntents.ASK_WHAT_HAPPENED),
    UNKNOWN(VoiceIntents.UNKNOWN);

    private final ResourceLocation id;

    VerityVoiceIntent(ResourceLocation id) {
        this.id = id;
    }

    public ResourceLocation id() {
        return id;
    }

    public static VerityVoiceIntent fromId(ResourceLocation id) {
        if (id == null) {
            return UNKNOWN;
        }
        for (VerityVoiceIntent value : values()) {
            if (value.id.equals(id)) {
                return value;
            }
        }
        return UNKNOWN;
    }
}
