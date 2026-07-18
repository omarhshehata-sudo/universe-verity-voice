package com.universeexe.verityvoice.client.audio;

import com.universeexe.verityvoice.UniverseVerityVoice;
import com.universeexe.verityvoice.client.VoiceRecognitionState;
import com.universeexe.verityvoice.common.config.VoiceClientConfig;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

@OnlyIn(Dist.CLIENT)
public final class MicrophoneCaptureService {
    private final AtomicReference<MicrophoneSource> source = new AtomicReference<>();
    private final AtomicBoolean capturing = new AtomicBoolean(false);
    private long lastOpenFailureMs;
    private String lastFailure = "";

    public synchronized boolean startCapture() {
        if (capturing.get() && source.get() != null && source.get().isOpen()) {
            return true;
        }
        long now = System.currentTimeMillis();
        if (now - lastOpenFailureMs < 15_000L) {
            VoiceRecognitionState.setLastError(lastFailure);
            return false;
        }
        try {
            MicrophoneSource mic = new TargetDataLineMicrophoneSource(VoiceClientConfig.MICROPHONE_DEVICE.get());
            if (!mic.open()) {
                throw new IllegalStateException("Microphone open returned false");
            }
            mic.start();
            source.set(mic);
            capturing.set(true);
            VoiceRecognitionState.setMicStatus(VoiceRecognitionState.MicStatus.LISTENING);
            VoiceRecognitionState.setLastError("");
            return true;
        } catch (Exception ex) {
            lastOpenFailureMs = now;
            lastFailure = summarize(ex);
            VoiceRecognitionState.setLastError(lastFailure);
            VoiceRecognitionState.setMicStatus(VoiceRecognitionState.MicStatus.ERROR);
            UniverseVerityVoice.LOGGER.warn("[VerityVoice] Microphone open failed: {}", lastFailure);
            closeQuietly();
            return false;
        }
    }

    public int read(byte[] buffer) {
        MicrophoneSource mic = source.get();
        if (!capturing.get() || mic == null) {
            return -1;
        }
        try {
            int read = mic.read(buffer, 0, buffer.length);
            if (read > 0) {
                VoiceRecognitionState.setAudioLevel(computeLevel(buffer, read));
            }
            return read;
        } catch (Exception ex) {
            VoiceRecognitionState.setLastError(summarize(ex));
            stopCapture();
            return -1;
        }
    }

    public synchronized void stopCapture() {
        capturing.set(false);
        MicrophoneSource mic = source.getAndSet(null);
        if (mic != null) {
            mic.stop();
            mic.close();
        }
        if (VoiceRecognitionState.micStatus() == VoiceRecognitionState.MicStatus.LISTENING
                || VoiceRecognitionState.micStatus() == VoiceRecognitionState.MicStatus.RECOGNIZING) {
            VoiceRecognitionState.setMicStatus(
                    VoiceClientConfig.VOICE_COMMANDS_ENABLED.get()
                            ? VoiceRecognitionState.MicStatus.READY
                            : VoiceRecognitionState.MicStatus.OFF
            );
        }
        VoiceRecognitionState.setAudioLevel(0.0f);
    }

    public void closeQuietly() {
        stopCapture();
    }

    public boolean isCapturing() {
        return capturing.get();
    }

    public String currentDeviceName() {
        MicrophoneSource mic = source.get();
        return mic == null ? VoiceClientConfig.MICROPHONE_DEVICE.get() : mic.deviceName();
    }

    public float testMicLevel(int millis, Consumer<Float> levelCallback) {
        boolean wasCapturing = capturing.get();
        if (!startCapture()) {
            return 0.0f;
        }
        byte[] buf = new byte[3200];
        long end = System.currentTimeMillis() + Math.max(500, millis);
        float peak = 0.0f;
        try {
            while (System.currentTimeMillis() < end) {
                int read = read(buf);
                if (read > 0) {
                    float level = computeLevel(buf, read);
                    peak = Math.max(peak, level);
                    if (levelCallback != null) {
                        levelCallback.accept(level);
                    }
                } else {
                    Thread.sleep(10L);
                }
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        } finally {
            if (!wasCapturing) {
                stopCapture();
            }
        }
        return peak;
    }

    private static float computeLevel(byte[] pcm, int length) {
        long sum = 0;
        int samples = length / 2;
        if (samples <= 0) {
            return 0.0f;
        }
        for (int i = 0; i + 1 < length; i += 2) {
            int sample = (pcm[i + 1] << 8) | (pcm[i] & 0xFF);
            sum += Math.abs(sample);
        }
        return Math.min(1.0f, (sum / (float) samples) / 8000.0f);
    }

    private static String summarize(Exception ex) {
        String msg = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
        if (msg.toLowerCase().contains("line unavailable") || msg.toLowerCase().contains("busy")) {
            return "Microphone busy or unavailable (another app/voice-chat mod may own it). Retry with /verityvoice testmic";
        }
        if (msg.toLowerCase().contains("permission") || msg.toLowerCase().contains("access")) {
            return "Microphone permission denied by the OS";
        }
        return "Microphone error: " + msg;
    }
}
