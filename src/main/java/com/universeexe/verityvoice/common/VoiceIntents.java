package com.universeexe.verityvoice.common;

import com.universeexe.verityvoice.UniverseVerityVoice;
import net.minecraft.resources.ResourceLocation;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Registered logical intent IDs. Clients may only submit these IDs.
 */
public final class VoiceIntents {
    public static final ResourceLocation MEET_VERITY = id("meet_verity");
    public static final ResourceLocation HELLO = id("hello");
    public static final ResourceLocation MAKE_SOUND = id("make_sound");
    public static final ResourceLocation Q3_ANSWER = id("q3_answer");
    public static final ResourceLocation ASK_FAVORITE_SONG = id("ask_favorite_song");
    public static final ResourceLocation FIND_NEAREST_VILLAGE = id("find_nearest_village");
    public static final ResourceLocation FIND_NEAREST_DIAMONDS = id("find_nearest_diamonds");
    public static final ResourceLocation FOLLOW = id("follow");
    public static final ResourceLocation STAY = id("stay");
    public static final ResourceLocation COME_HERE = id("come_here");
    public static final ResourceLocation ASK_IF_ANGRY = id("ask_if_angry");
    public static final ResourceLocation ASK_WHAT_HAPPENED = id("ask_what_happened");
    public static final ResourceLocation UNKNOWN = id("unknown");

    private static final Set<ResourceLocation> ALL;

    static {
        Set<ResourceLocation> set = new LinkedHashSet<>();
        set.add(MEET_VERITY);
        set.add(HELLO);
        set.add(MAKE_SOUND);
        set.add(Q3_ANSWER);
        set.add(ASK_FAVORITE_SONG);
        set.add(FIND_NEAREST_VILLAGE);
        set.add(FIND_NEAREST_DIAMONDS);
        set.add(FOLLOW);
        set.add(STAY);
        set.add(COME_HERE);
        set.add(ASK_IF_ANGRY);
        set.add(ASK_WHAT_HAPPENED);
        set.add(UNKNOWN);
        ALL = Collections.unmodifiableSet(set);
    }

    private VoiceIntents() {
    }

    public static ResourceLocation id(String path) {
        return new ResourceLocation(UniverseVerityVoice.MOD_ID, path);
    }

    public static Set<ResourceLocation> all() {
        return ALL;
    }

    public static boolean isRegistered(ResourceLocation intentId) {
        return intentId != null && ALL.contains(intentId);
    }
}
