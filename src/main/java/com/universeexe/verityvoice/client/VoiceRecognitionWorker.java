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
    /** Best mid-hold final from Vosk; intents dispatch only on flush (PTT release). */
    private volatile String pendingUtteranceText = "";

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
                    // Mid-hold finals update preview only — never fire intents here.
                    // Restricted grammar + short words like "hi" used to spam HELLO every segment.
                    String text = service.finalResultText();
                    if (text != null && !text.isBlank()) {
                        pendingUtteranceText = text;
                        String normalized = VoiceTextNormalizer.normalize(text);
                        VoiceRecognitionState.setFinalText(text);
                        VoiceRecognitionState.setNormalizedText(normalized);
                    }
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
                // ensureLoaded always re-checks the model path when unloaded — installing mid-session works.
                if (!service.ensureLoaded(VoiceCommandRegistry.INSTANCE.grammarWords())) {
                    Minecraft.getInstance().execute(() -> {
                        var player = Minecraft.getInstance().player;
                        if (player == null) {
                            return;
                        }
                        if (service.nativesPermanentlyFailed()) {
                            player.displayClientMessage(net.minecraft.network.chat.Component.literal(
                                    "[VerityVoice] NATIVE_ERROR — Vosk natives failed to load. "
                                            + "Check logs; restart after fixing. Detail: " + service.loadError()
                            ), false);
                            return;
                        }
                        if (VoiceRecognitionState.consumeModelMissingNotify()) {
                            player.displayClientMessage(net.minecraft.network.chat.Component.literal(
                                    "[VerityVoice] MODEL_MISSING — install vosk-model-small-en-us-0.15 at: "
                                            + service.expectedModelPath().toAbsolutePath()
                                            + " then press V again (or /verityvoice reload)."
                            ), false);
                        }
                    });
                    return;
                }
                if (captureService.startCapture()) {
                    service.resetUtterance();
                    pendingUtteranceText = "";
                    listening.set(true);
                    trailingUntilMs = 0;
                    VoiceRecognitionState.setMicStatus(VoiceRecognitionState.MicStatus.LISTENING);
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
                if (!service.nativesPermanentlyFailed()) {
                    service.clearRetryableFailure();
                }
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
        if ((text == null || text.isBlank()) && pendingUtteranceText != null && !pendingUtteranceText.isBlank()) {
            text = pendingUtteranceText;
        }
        pendingUtteranceText = "";
        handleRecognized(text, true);
        voskService().resetUtterance();
        VoiceRecognitionState.setRecognizerStatus(VoiceRecognitionState.RecognizerStatus.IDLE);
    }

    private void handleRecognized(String text, boolean finalFlush) {
        // Intents only on the end of a push-to-talk / listen segment.
        if (!finalFlush) {
            return;
        }
        if (text == null || text.isBlank()) {
            VoiceRecognitionState.setMicStatus(VoiceRecognitionState.MicStatus.NO_COMMAND);
            Minecraft.getInstance().execute(() ->
                    com.universeexe.verityvoice.client.hud.VerityVoiceHudController.INSTANCE.showNoCommand());
            return;
        }
        String normalized = VoiceTextNormalizer.normalize(text);
        VoiceRecognitionState.setFinalText(text);
        VoiceRecognitionState.setNormalizedText(normalized);

        long now = System.currentTimeMillis();
        // Stronger debounce — stops hello spam from similar mishears.
        if (normalized.equals(lastSubmittedNormalized) && now - lastSubmittedAtMs < 4000L) {
            return;
        }
        if (now - lastSubmittedAtMs < 1200L) {
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
        // Short clear phrases (hi / follow me) need enough confidence to pass gates.
        float base = Math.min(0.92f, 0.70f + 0.05f * Math.min(words, 5));
        if (raw != null && !raw.isBlank()) {
            base += 0.03f;
        }
        return Math.min(1.0f, base);
    }
}
