package com.universeexe.verityvoice.client;

import com.universeexe.verityvoice.client.hud.VoiceConsentScreen;
import com.universeexe.verityvoice.client.hud.VoiceDebugOverlay;
import com.universeexe.verityvoice.client.hud.VoiceMicHudOverlay;
import com.universeexe.verityvoice.common.config.VoiceClientConfig;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

@OnlyIn(Dist.CLIENT)
public final class VerityVoiceClient {
    private static boolean consentQueued;

    private VerityVoiceClient() {
    }

    public static void init(IEventBus modBus) {
        modBus.addListener(VerityVoiceClient::clientSetup);
        modBus.addListener(VoiceKeyMappings::register);
        MinecraftForge.EVENT_BUS.register(VoiceListeningController.INSTANCE);
        MinecraftForge.EVENT_BUS.register(new VoiceMicHudOverlay());
        MinecraftForge.EVENT_BUS.register(new VoiceDebugOverlay());
        MinecraftForge.EVENT_BUS.register(new VerityVoiceClient());
        MinecraftForge.EVENT_BUS.addListener(VerityVoiceClient::onRegisterClientCommands);
    }

    private static void clientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            VoiceListeningController.INSTANCE.bootstrap();
            VoiceCommandRegistry.INSTANCE.reloadFromServerDefinitions();
        });
    }

    private static void onRegisterClientCommands(RegisterClientCommandsEvent event) {
        ClientVoiceCommands.register(event);
    }

    @SubscribeEvent
    public void onLoggingIn(ClientPlayerNetworkEvent.LoggingIn event) {
        consentQueued = !VoiceClientConfig.CONSENT_ANSWERED.get();
        VoiceCommandRegistry.INSTANCE.reloadFromServerDefinitions();
        if (VoiceClientConfig.VOICE_COMMANDS_ENABLED.get()) {
            VoiceRecognitionState.setMicStatus(VoiceRecognitionState.MicStatus.READY);
            VoiceListeningController.INSTANCE.worker().offer(VoiceRecognitionWorker.Command.RELOAD_MODEL);
        }
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !consentQueued) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.screen != null) {
            return;
        }
        consentQueued = false;
        mc.setScreen(new VoiceConsentScreen(null));
    }

    @SubscribeEvent
    public void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        consentQueued = false;
    }
}
