package com.universeexe.verityvoice.client;

import com.universeexe.verityvoice.UniverseVerityVoice;
import com.universeexe.verityvoice.client.audio.MicrophoneDeviceManager;
import com.universeexe.verityvoice.common.config.VoiceClientConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.nio.file.Path;

@OnlyIn(Dist.CLIENT)
public final class ClientVoiceCommandBridge {
    private ClientVoiceCommandBridge() {
    }

    public static void status(ServerPlayer ignored) {
        status();
    }

    public static void status() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        var worker = VoiceListeningController.INSTANCE.worker();
        player.displayClientMessage(Component.literal("[VerityVoice] enabled="
                + VoiceClientConfig.VOICE_COMMANDS_ENABLED.get()
                + " mode=" + VoiceClientConfig.LISTENING_MODE.get()
                + " mic=" + VoiceRecognitionState.micStatus()
                + " device=" + worker.capture().currentDeviceName()
                + " model=" + VoiceRecognitionState.modelStatus()
                + " workerListening=" + worker.isListening()
                + " nearby=" + VoiceRecognitionState.nearbyVerity()
                + " lastIntent=" + VoiceRecognitionState.matchedIntent()
                + " lastError=" + VoiceRecognitionState.lastError()), false);
    }

    public static void setEnabled(boolean enabled) {
        VoiceListeningController.INSTANCE.enableVoice(enabled);
        msg("voiceCommandsEnabled=" + enabled);
    }

    public static void setDevice(String name) {
        VoiceClientConfig.MICROPHONE_DEVICE.set(name);
        msg("microphoneDevice=" + name);
    }

    public static void setDebug(boolean value) {
        VoiceClientConfig.DEBUG_LOGGING.set(value);
        VoiceClientConfig.DEBUG_OVERLAY.set(value);
        msg("debug=" + value);
    }

    public static void setTranscript(boolean value) {
        VoiceClientConfig.SHOW_RECOGNIZED_TEXT.set(value);
        msg("transcript=" + value);
    }

    public static void run(String action) {
        var worker = VoiceListeningController.INSTANCE.worker();
        switch (action) {
            case "devices" -> {
                msg("Capture devices:");
                for (String name : MicrophoneDeviceManager.listCaptureDevices()) {
                    msg(" - " + name);
                }
            }
            case "model" -> {
                var vosk = worker.vosk();
                Path expected = vosk.expectedModelPath();
                msg("Exact model path (required folder):");
                msg("  " + expected.toAbsolutePath());
                msg("Relative: config/" + UniverseVerityVoice.MOD_ID + "/models/"
                        + VoiceClientConfig.MODEL_FOLDER_NAME.get() + "/");
                msg("status=" + VoiceRecognitionState.modelStatus()
                        + " mic=" + VoiceRecognitionState.micStatus()
                        + " nativesFailed=" + vosk.nativesPermanentlyFailed()
                        + " loaded=" + vosk.isLoaded()
                        + (vosk.loadError().isBlank() ? "" : " error=" + vosk.loadError()));
                msg("If MODEL_MISSING: download vosk-model-small-en-us-0.15, extract so am/conf/graph exist under that path, then press V or /verityvoice reload.");
            }
            case "testmic" -> worker.offer(VoiceRecognitionWorker.Command.TEST_MIC);
            case "start" -> worker.offer(VoiceRecognitionWorker.Command.START_LISTEN);
            case "stop" -> worker.offer(VoiceRecognitionWorker.Command.STOP_LISTEN);
            case "reload" -> {
                VoiceCommandRegistry.INSTANCE.reloadFromServerDefinitions();
                worker.offer(VoiceRecognitionWorker.Command.RELOAD_MODEL);
                msg("Client command registry / model reload requested");
            }
            default -> msg("Unknown client action: " + action);
        }
    }

    private static void msg(String text) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null) {
            player.displayClientMessage(Component.literal("[VerityVoice] " + text), false);
        }
    }
}
