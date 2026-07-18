package com.universeexe.verityvoice.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import org.lwjgl.glfw.GLFW;

@OnlyIn(Dist.CLIENT)
public final class VoiceKeyMappings {
    public static final String CATEGORY = "key.categories.universe_exe";

    public static final KeyMapping TALK_TO_VERITY = new KeyMapping(
            "key.universe_verity_voice.talk_to_verity",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_V,
            CATEGORY
    );

    private VoiceKeyMappings() {
    }

    public static void register(RegisterKeyMappingsEvent event) {
        event.register(TALK_TO_VERITY);
    }
}
