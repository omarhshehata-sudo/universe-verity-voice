package com.universeexe.verityvoice.client;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.Locale;

@OnlyIn(Dist.CLIENT)
public enum ListeningMode {
    PUSH_TO_TALK,
    WAKE_WORD,
    NEARBY_CONTINUOUS;

    public static ListeningMode fromConfig(String raw) {
        if (raw == null) {
            return PUSH_TO_TALK;
        }
        try {
            return ListeningMode.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return PUSH_TO_TALK;
        }
    }
}
