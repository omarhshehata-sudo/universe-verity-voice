package com.universeexe.verityvoice.client;

import com.universeexe.verityvoice.UniverseVerityVoice;
import com.universeexe.verityvoice.client.audio.MicrophoneCaptureService;
import com.universeexe.verityvoice.client.vosk.VoskRecognitionService;
import com.universeexe.verityvoice.common.config.VoiceClientConfig;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

@OnlyIn(Dist.CLIENT)
public final class VoiceRecognitionWorker {
    public enum Command {
        START_LISTEN,
        STOP_LISTEN,
        RELOAD_MODEL,
        UNLOAD_MODEL,
        SHUTDOWN,
        TEST_MIC
    }

    private final MicrophoneCaptureService captureService = new MicrophoneCaptureService();
    /** Lazily created — constructing this must not run during mod/client class init. */
    private VoskRecognitionService vosk;
    private final BlockingQueue<Command> commands = new ArrayBlockingQueue<>(32);
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicBoolean listening = new AtomicBoolean(false);
    private final AtomicReference<Consumer<UtteranceResult>> utteranceHandler = new AtomicReference<>();
    private Thread thread;
    private long trailingUntilMs;
    private String lastSubmittedNormalized = "";
    private long lastSubmittedAtMs;

    public record UtteranceResult(String raw, String normalized, float speechConfidence) {
    }

    public synchronized void start() {
        if (running.get()) {
            return;
        }
        running.set(true);
        thread = new Thread(this::loop, "Universe-Verity-Voice-Recognition");
        thread.setDaemon(true);
        thread.setUncaughtExceptionHandler((t, e) -> {
            UniverseVerityVoice.LOGGER.error(
                    "[VerityVoice] Recognition thread died ({}) — voice disabled, game continues: {}",
                    t.getName(), e.toString()
            );
            listening.set(false);
            running.set(false);
            VoiceRecognitionState.setMicStatus(VoiceRecognitionState.MicStatus.ERROR);
            VoiceRecognitionState.setLastError("Recognition thread crashed: " + e.getClass().getSimpleName());
            try {
                captureService.closeQuietly();
            } catch (Throwable ignored) {
            }
        });
        thread.start();
    }

    public void setUtteranceHandler(Consumer<UtteranceResult> handler) {
        utteranceHandler.set(handler);
    }

    public void offer(Command command) {
        if (!commands.offer(command)) {
            UniverseVerityVoice.LOGGER.debug("[VerityVoice] Recognition command queue full, dropping {}", command);
        }
    }

    public MicrophoneCaptureService capture() {
        return captureService;
    }

    public synchronized VoskRecognitionService vosk() {
        return voskService();
    }

    private synchronized VoskRecognitionService voskService() {
        if (vosk == null) {
            vosk = new VoskRecognitionService();
        }
        return vosk;
    }

    public boolean isListening() {
        return listening.get();
    }

    public synchronized void shutdown() {
        offer(Command.SHUTDOWN);
        running.set(false);
        listening.set(false);
        captureService.closeQuietly();
        if (vosk != null) {
            vosk.close();
        }
        if (thread != null) {
            thread.interrupt();
        }
    }

    private void loop() {
        byte[] buffer = new byte[4096];
        while (running.get()) {
            try {
                Command cmd = commands.poll();
                if (cmd != null) {
                    handleCommand(cmd);
                }

                if (!listening.get()) {
                    Thread.sleep(20L);
                    continue;
                }

                if (trailingUntilMs > 0 && System.currentTimeMillis() > trailingUntilMs) {
                    finishUtterance();
                    listening.set(false);
                    captureService.stopCapture();
                    trailingUntilMs = 0;
                    continue;
                }

                int read = captureService.read(buffer);
                if (read <= 0) {
                    Thread.sleep(10L);
                    continue;
                }
                VoiceRecognitionState.setRecognizerStatus(VoiceRecognitionState.RecognizerStatus.LISTENING);
                VoskRecognitionService service = voskService();
                boolean utteranceEnd = service.acceptWaveForm(buffer, read);
                String partial = service.partialResultText();
                if (partial != null && !partial.isBlank()) {
                    VoiceRecognitionState.setPartialText(partial);
                }
                if (utteranceEnd) {
                    String text = service.finalResultText();
                    handleRecognized(text, false);
                }
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                break;
            } catch (Throwable ex) {
                if (ex instanceof VirtualMachineError) {
                    throw (VirtualMachineError) ex;
                }
                VoiceRecognitionState.setLastError(ex.getMessage() == null
                        ? ex.getClass().getSimpleName()
                        : ex.getMessage());
                UniverseVerityVoice.LOGGER.warn("[VerityVoice] Recognition worker error: {}", ex.toString());
                listening.set(false);
                try {
                    captureService.stopCapture();
                } catch (Throwable ignored) {
                }
            }
        }
        try {
            captureService.closeQuietly();
        } catch (Throwable ignored) {
        }
        try {
            if (vosk != null) {
                vosk.close();
            }
        } catch (Throwable ignored) {
        }
    }

    private void handleCommand(Command cmd) {
        switch (cmd) {
            case START_LISTEN -> {
                VoiceCommandRegistry.INSTANCE.reloadFromServerDefinitions();
                VoskRecognitionService service = voskService();
                if (!service.ensureLoaded(VoiceCommandRegistry.INSTANCE.grammarWords())) {
                    if (VoiceRecognitionState.consumeModelMissingNotify()) {
                        Minecraft.getInstance().execute(() -> {
                            var player = Minecraft.getInstance().player;
                            if (player != null) {
                                player.displayClientMessage(net.minecraft.network.chat.Component.literal(
                                        "[VerityVoice] Speech model missing. Expected: "
                                                + service.expectedModelPath().toAbsolutePath()
                                ), false);
                            }
                        });
                    }
                    return;
                }
                if (captureService.startCapture()) {
                    service.resetUtterance();
                    listening.set(true);
                    trailingUntilMs = 0;
                    VoiceRecognitionState.setRecognizerStatus(VoiceRecognitionState.RecognizerStatus.LISTENING);
                }
            }
            case STOP_LISTEN -> {
                trailingUntilMs = System.currentTimeMillis()
                        + VoiceClientConfig.PUSH_TO_TALK_TRAILING_MS.get();
                if (!listening.get()) {
                    captureService.stopCapture();
                }
            }
            case RELOAD_MODEL -> {
                // Only attempt native/model load when explicitly requested (or first listen).
                VoskRecognitionService service = voskService();
                service.close();
                VoiceRecognitionState.resetModelMissingNotify();
                VoiceCommandRegistry.INSTANCE.reloadFromServerDefinitions();
                service.ensureLoaded(VoiceCommandRegistry.INSTANCE.grammarWords());
            }
            case UNLOAD_MODEL -> {
                listening.set(false);
                captureService.stopCapture();
                if (vosk != null) {
                    vosk.close();
                }
                VoiceRecognitionState.setModelStatus(VoiceRecognitionState.ModelStatus.UNKNOWN);
                VoiceRecognitionState.setMicStatus(VoiceRecognitionState.MicStatus.OFF);
            }
            case TEST_MIC -> {
                float peak = captureService.testMicLevel(3000, level -> VoiceRecognitionState.setAudioLevel(level));
                Minecraft.getInstance().execute(() -> {
                    var player = Minecraft.getInstance().player;
                    if (player != null) {
                        player.displayClientMessage(net.minecraft.network.chat.Component.literal(
                                String.format("[VerityVoice] Mic test peak level=%.2f (audio not saved)", peak)
                        ), false);
                    }
                });
            }
            case SHUTDOWN -> {
                running.set(false);
                listening.set(false);
                captureService.closeQuietly();
                if (vosk != null) {
                    vosk.close();
                }
            }
        }
    }

    private void finishUtterance() {
        VoiceRecognitionState.setRecognizerStatus(VoiceRecognitionState.RecognizerStatus.PROCESSING);
        String text = voskService().flushFinal();
        handleRecognized(text, true);
        voskService().resetUtterance();
        VoiceRecognitionState.setRecognizerStatus(VoiceRecognitionState.RecognizerStatus.IDLE);
    }

    private void handleRecognized(String text, boolean finalFlush) {
        if (text == null || text.isBlank()) {
            if (finalFlush) {
                VoiceRecognitionState.setMicStatus(VoiceRecognitionState.MicStatus.NO_COMMAND);
                Minecraft.getInstance().execute(() ->
                        com.universeexe.verityvoice.client.hud.VerityVoiceHudController.INSTANCE.showNoCommand());
            }
            return;
        }
        String normalized = VoiceTextNormalizer.normalize(text);
        VoiceRecognitionState.setFinalText(text);
        VoiceRecognitionState.setNormalizedText(normalized);

        long now = System.currentTimeMillis();
        if (normalized.equals(lastSubmittedNormalized) && now - lastSubmittedAtMs < 1500L) {
            return;
        }

        float speechConf = estimateSpeechConfidence(text, normalized);
        VoiceRecognitionState.setSpeechConfidence(speechConf);
        Consumer<UtteranceResult> handler = utteranceHandler.get();
        if (handler != null) {
            lastSubmittedNormalized = normalized;
            lastSubmittedAtMs = now;
            Minecraft.getInstance().execute(() -> handler.accept(new UtteranceResult(text, normalized, speechConf)));
        }
    }

    private static float estimateSpeechConfidence(String raw, String normalized) {
        if (normalized.isBlank()) {
            return 0.0f;
        }
        int words = normalized.split("\\s+").length;
        float base = Math.min(0.9f, 0.45f + 0.08f * words);
        if (raw != null && raw.equalsIgnoreCase(normalized)) {
            base += 0.05f;
        }
        return Math.min(1.0f, base);
    }
}
