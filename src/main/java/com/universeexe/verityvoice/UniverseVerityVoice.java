package com.universeexe.verityvoice;

import com.mojang.logging.LogUtils;
import com.universeexe.verityvoice.common.command.VerityVoiceCommands;
import com.universeexe.verityvoice.common.config.VoiceClientConfig;
import com.universeexe.verityvoice.common.config.VoiceCommonConfig;
import com.universeexe.verityvoice.common.event.VerityVoiceGameplayResponses;
import com.universeexe.verityvoice.common.network.VerityVoiceNetwork;
import com.universeexe.verityvoice.common.quest.VerityVoiceQuestCompat;
import com.universeexe.verityvoice.common.VoiceCommandReloadListener;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;
import org.slf4j.Logger;

@Mod(UniverseVerityVoice.MOD_ID)
public class UniverseVerityVoice {
    public static final String MOD_ID = "universe_verity_voice";
    public static final Logger LOGGER = LogUtils.getLogger();

    public UniverseVerityVoice() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, VoiceCommonConfig.SPEC);
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, VoiceClientConfig.SPEC);

        modBus.addListener(this::commonSetup);

        MinecraftForge.EVENT_BUS.addListener(this::onRegisterCommands);
        MinecraftForge.EVENT_BUS.addListener(this::onAddReloadListeners);
        MinecraftForge.EVENT_BUS.addListener(this::onServerStarting);
        MinecraftForge.EVENT_BUS.register(new VerityVoiceGameplayResponses());

        if (FMLEnvironment.dist.isClient()) {
            com.universeexe.verityvoice.client.VerityVoiceClient.init(modBus);
        }
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            VerityVoiceNetwork.register();
            VerityVoiceQuestCompat.bootstrap();
        });
    }

    private void onRegisterCommands(RegisterCommandsEvent event) {
        VerityVoiceCommands.register(event.getDispatcher());
    }

    private void onAddReloadListeners(AddReloadListenerEvent event) {
        event.addListener(VoiceCommandReloadListener.INSTANCE);
    }

    private void onServerStarting(ServerStartingEvent event) {
        LOGGER.info("Universe Verity Voice loaded (offline speech-to-intent foundation; no cloud STT)");
    }
}
