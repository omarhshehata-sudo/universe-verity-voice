package com.universeexe.verityvoice.client.hud;

import com.universeexe.verityvoice.client.VoiceListeningController;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class VoiceConsentScreen extends Screen {
    private final Screen parent;

    public VoiceConsentScreen(Screen parent) {
        super(Component.literal("Verity Voice Commands"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int cx = this.width / 2;
        int cy = this.height / 2;
        addRenderableWidget(Button.builder(Component.literal("ENABLE VOICE COMMANDS"), b -> {
            VoiceListeningController.INSTANCE.enableVoice(true);
            onClose();
        }).bounds(cx - 120, cy + 20, 240, 20).build());
        addRenderableWidget(Button.builder(Component.literal("NOT NOW"), b -> {
            VoiceListeningController.INSTANCE.enableVoice(false);
            onClose();
        }).bounds(cx - 120, cy + 46, 240, 20).build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, this.height / 2 - 50, 0xFFFFFF);
        graphics.drawCenteredString(this.font,
                "Verity can listen for spoken commands using your microphone.",
                this.width / 2, this.height / 2 - 30, 0xCCCCCC);
        graphics.drawCenteredString(this.font,
                "Recognition happens locally on your computer.",
                this.width / 2, this.height / 2 - 18, 0xCCCCCC);
        graphics.drawCenteredString(this.font,
                "Audio is not uploaded or saved.",
                this.width / 2, this.height / 2 - 6, 0xCCCCCC);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(parent);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
