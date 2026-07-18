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
    private final VoskRecognitionService vosk = new VoskRecognitionService();
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

    public VoskRecognitionService vosk() {
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
        vosk.close();
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
                boolean utteranceEnd = vosk.acceptWaveForm(buffer, read);
                String partial = vosk.partialResultText();
                if (partial != null && !partial.isBlank()) {
                    VoiceRecognitionState.setPartialText(partial);
                }
                if (utteranceEnd) {
                    String text = vosk.finalResultText();
                    handleRecognized(text, false);
                }
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception ex) {
                VoiceRecognitionState.setLastError(ex.getMessage());
                UniverseVerityVoice.LOGGER.warn("[VerityVoice] Recognition worker error: {}", ex.toString());
                listening.set(false);
                captureService.stopCapture();
            }
        }
        captureService.closeQuietly();
        vosk.close();
    }

    private void handleCommand(Command cmd) {
        switch (cmd) {
            case START_LISTEN -> {
                VoiceCommandRegistry.INSTANCE.reloadFromServerDefinitions();
                if (!vosk.ensureLoaded(VoiceCommandRegistry.INSTANCE.grammarWords())) {
                    if (VoiceRecognitionState.consumeModelMissingNotify()) {
                        Minecraft.getInstance().execute(() -> {
                            var player = Minecraft.getInstance().player;
                            if (player != null) {
                                player.displayClientMessage(net.minecraft.network.chat.Component.literal(
                                        "[VerityVoice] Speech model missing. Expected: "
                                                + vosk.expectedModelPath().toAbsolutePath()
                                ), false);
                            }
                        });
                    }
                    return;
                }
                if (captureService.startCapture()) {
                    vosk.resetUtterance();
                    listening.set(true);
                    trailingUntilMs = 0;
                    VoiceRecognitionState.setRecognizerStatus(VoiceRecognitionState.RecognizerStatus.LISTENING);
                }
            }
            case STOP_LISTEN -> {
                trailingUntilMs = System.currentTimeMillis()
                        + VoiceClientConfig.PUSH_TO_TALK_TRAILING_MS.get();
                // Keep listening briefly for trailing audio; finishUtterance ends it.
                if (!listening.get()) {
                    captureService.stopCapture();
                }
            }
            case RELOAD_MODEL -> {
                vosk.close();
                VoiceRecognitionState.resetModelMissingNotify();
                VoiceCommandRegistry.INSTANCE.reloadFromServerDefinitions();
                vosk.ensureLoaded(VoiceCommandRegistry.INSTANCE.grammarWords());
            }
            case UNLOAD_MODEL -> {
                listening.set(false);
                captureService.stopCapture();
                vosk.close();
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
                vosk.close();
            }
        }
    }

    private void finishUtterance() {
        VoiceRecognitionState.setRecognizerStatus(VoiceRecognitionState.RecognizerStatus.PROCESSING);
        String text = vosk.flushFinal();
        handleRecognized(text, true);
        vosk.resetUtterance();
        VoiceRecognitionState.setRecognizerStatus(VoiceRecognitionState.RecognizerStatus.IDLE);
    }

    private void handleRecognized(String text, boolean finalFlush) {
        if (text == null || text.isBlank()) {
            if (finalFlush) {
                VoiceRecognitionState.setMicStatus(VoiceRecognitionState.MicStatus.NO_COMMAND);
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
        // Vosk small models often omit confidence; derive a conservative proxy.
        int words = normalized.split("\\s+").length;
        float base = Math.min(0.9f, 0.45f + 0.08f * words);
        if (raw != null && raw.equalsIgnoreCase(normalized)) {
            base += 0.05f;
        }
        return Math.min(1.0f, base);
    }
}
