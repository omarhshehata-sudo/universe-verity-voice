package com.universeexe.verityvoice.client.vosk;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.universeexe.verityvoice.UniverseVerityVoice;
import com.universeexe.verityvoice.client.VoiceRecognitionState;
import com.universeexe.verityvoice.common.config.VoiceClientConfig;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.fml.loading.FMLPaths;
import org.vosk.Model;
import org.vosk.Recognizer;

import javax.annotation.Nullable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Client-only Vosk wrapper. Never referenced from common/server code.
 * <p>
 * Native load (JNA + libvosk) is deferred until {@link #ensureLoaded} and every failure
 * path fails soft — missing model / UnsatisfiedLinkError / LinkageError must never take
 * down the JVM. Note: a true SIGSEGV inside native code cannot be caught in Java; we
 * therefore avoid loading natives during mod construction or title-screen setup.
 * <p>
 * {@link VoiceRecognitionState.ModelStatus#MISSING} is always retryable (install model, press V again).
 * True native linkage failures are session-permanent because LibVosk {@code <clinit>} stays poisoned.
 * Non-native model IO errors are retryable after fix + {@code /verityvoice reload}.
 */
@OnlyIn(Dist.CLIENT)
public final class VoskRecognitionService implements AutoCloseable {
    private Model model;
    private Recognizer recognizer;
    private String grammarFingerprint = "";
    private String loadError = "";
    /**
     * Only set for true native linkage / LibVosk clinit poisoning.
     * Missing model must NEVER set this — user can install the model mid-session.
     */
    private boolean nativesPermanentlyFailed;

    public Path expectedModelPath() {
        String folder = VoiceClientConfig.MODEL_FOLDER_NAME.get();
        return FMLPaths.CONFIGDIR.get()
                .resolve(UniverseVerityVoice.MOD_ID)
                .resolve("models")
                .resolve(folder);
    }

    public synchronized boolean ensureLoaded(@Nullable Collection<String> grammarWords) {
        if (nativesPermanentlyFailed) {
            VoiceRecognitionState.setModelStatus(VoiceRecognitionState.ModelStatus.ERROR);
            VoiceRecognitionState.setMicStatus(VoiceRecognitionState.MicStatus.NATIVE_ERROR);
            VoiceRecognitionState.setLastError(loadError.isBlank()
                    ? "Vosk native library unavailable (restart game after fixing natives)"
                    : loadError, false);
            return false;
        }

        Path path = expectedModelPath();
        if (!looksLikeModel(path)) {
            closeQuietly();
            loadError = "Missing Vosk model folder: " + path.toAbsolutePath()
                    + " (expected am/ conf/ graph/ or ivector/)";
            VoiceRecognitionState.setModelStatus(VoiceRecognitionState.ModelStatus.MISSING);
            VoiceRecognitionState.setMicStatus(VoiceRecognitionState.MicStatus.MODEL_MISSING);
            VoiceRecognitionState.setLastError(loadError, false);
            UniverseVerityVoice.LOGGER.warn("[VerityVoice] {}", loadError);
            return false;
        }
        try {
            if (model == null) {
                // First Model construction triggers LibVosk <clinit> / Native.register("vosk").
                model = new Model(path.toAbsolutePath().toString());
                VoiceRecognitionState.setModelStatus(VoiceRecognitionState.ModelStatus.LOADED);
                UniverseVerityVoice.LOGGER.info("[VerityVoice] Loaded Vosk model from {}", path.toAbsolutePath());
            }
            rebuildRecognizer(grammarWords);
            loadError = "";
            VoiceRecognitionState.setLastError("", false);
            VoiceRecognitionState.setModelStatus(VoiceRecognitionState.ModelStatus.LOADED);
            return true;
        } catch (Throwable ex) {
            // LinkageError / ExceptionInInitializerError / UnsatisfiedLinkError / model IO —
            // never escape to the client thread. VirtualMachineError is rethrown.
            if (ex instanceof VirtualMachineError) {
                throw (VirtualMachineError) ex;
            }
            if (isNativeLinkageFailure(ex)) {
                return failNative("Vosk native library unavailable", ex);
            }
            return failModel("Vosk model error (retryable)", ex);
        }
    }

    /**
     * Clear retryable failure state so the next listen can pick up a newly installed model.
     * Does not clear {@link #nativesPermanentlyFailed} (LibVosk clinit stays poisoned).
     */
    public synchronized void clearRetryableFailure() {
        if (nativesPermanentlyFailed) {
            return;
        }
        closeQuietly();
        loadError = "";
        VoiceRecognitionState.resetModelMissingNotify();
        if (VoiceRecognitionState.modelStatus() == VoiceRecognitionState.ModelStatus.MISSING
                || VoiceRecognitionState.modelStatus() == VoiceRecognitionState.ModelStatus.ERROR) {
            VoiceRecognitionState.setModelStatus(VoiceRecognitionState.ModelStatus.UNKNOWN);
        }
        if (VoiceRecognitionState.micStatus() == VoiceRecognitionState.MicStatus.MODEL_MISSING
                || VoiceRecognitionState.micStatus() == VoiceRecognitionState.MicStatus.NATIVE_ERROR
                || VoiceRecognitionState.micStatus() == VoiceRecognitionState.MicStatus.ERROR) {
            VoiceRecognitionState.setMicStatus(VoiceRecognitionState.MicStatus.OFF);
        }
    }

    private boolean failNative(String prefix, Throwable err) {
        nativesPermanentlyFailed = true;
        loadError = prefix + ": " + (err.getMessage() == null ? err.getClass().getSimpleName() : err.getMessage());
        VoiceRecognitionState.setModelStatus(VoiceRecognitionState.ModelStatus.ERROR);
        VoiceRecognitionState.setMicStatus(VoiceRecognitionState.MicStatus.NATIVE_ERROR);
        VoiceRecognitionState.setLastError(loadError, false);
        UniverseVerityVoice.LOGGER.error(
                "[VerityVoice] {} — native load failed for this session (game continues). Restart after fixing packaging/JNA.",
                loadError
        );
        UniverseVerityVoice.LOGGER.debug("[VerityVoice] Native failure detail", err);
        closeQuietly();
        return false;
    }

    private boolean failModel(String prefix, Throwable err) {
        loadError = prefix + ": " + (err.getMessage() == null ? err.getClass().getSimpleName() : err.getMessage());
        VoiceRecognitionState.setModelStatus(VoiceRecognitionState.ModelStatus.ERROR);
        VoiceRecognitionState.setMicStatus(VoiceRecognitionState.MicStatus.MODEL_MISSING);
        VoiceRecognitionState.setLastError(loadError, false);
        UniverseVerityVoice.LOGGER.error(
                "[VerityVoice] {} — fix/reinstall model then press V or /verityvoice reload (game continues).",
                loadError
        );
        UniverseVerityVoice.LOGGER.debug("[VerityVoice] Model failure detail", err);
        closeQuietly();
        return false;
    }

    private static boolean isNativeLinkageFailure(Throwable ex) {
        Throwable t = ex;
        while (t != null) {
            if (t instanceof UnsatisfiedLinkError || t instanceof ExceptionInInitializerError) {
                return true;
            }
            if (t instanceof NoClassDefFoundError) {
                String msg = t.getMessage() == null ? "" : t.getMessage();
                if (msg.contains("vosk") || msg.contains("LibVosk") || msg.contains("jna")) {
                    return true;
                }
            }
            if (t instanceof LinkageError && !(t instanceof ClassNotFoundException) && !(t instanceof NoClassDefFoundError)) {
                String msg = String.valueOf(t.getMessage()).toLowerCase();
                if (msg.contains("vosk") || msg.contains("jna") || msg.contains("native")) {
                    return true;
                }
            }
            t = t.getCause();
        }
        return false;
    }

    private static boolean looksLikeModel(Path path) {
        if (!Files.isDirectory(path)) {
            return false;
        }
        return Files.exists(path.resolve("am"))
                || Files.exists(path.resolve("conf"))
                || Files.exists(path.resolve("graph"))
                || Files.exists(path.resolve("ivector"));
    }

    private void rebuildRecognizer(@Nullable Collection<String> grammarWords) throws Exception {
        String fingerprint = VoiceClientConfig.USE_RESTRICTED_GRAMMAR.get() && grammarWords != null
                ? String.join("|", grammarWords)
                : "";
        if (recognizer != null && fingerprint.equals(grammarFingerprint)) {
            return;
        }
        if (recognizer != null) {
            recognizer.close();
            recognizer = null;
        }
        if (VoiceClientConfig.USE_RESTRICTED_GRAMMAR.get() && grammarWords != null && !grammarWords.isEmpty()) {
            String grammarJson = toGrammarJson(grammarWords);
            recognizer = new Recognizer(model, 16000.0f, grammarJson);
        } else {
            recognizer = new Recognizer(model, 16000.0f);
        }
        grammarFingerprint = fingerprint;
    }

    private static String toGrammarJson(Collection<String> words) {
        Set<String> unique = new LinkedHashSet<>();
        for (String word : words) {
            if (word != null && !word.isBlank()) {
                unique.add(word.trim().toLowerCase());
            }
        }
        unique.add("[unk]");
        StringBuilder sb = new StringBuilder("[");
        boolean first = true;
        for (String w : unique) {
            if (!first) {
                sb.append(',');
            }
            first = false;
            sb.append('"').append(w.replace("\"", "")).append('"');
        }
        sb.append(']');
        return sb.toString();
    }

    public synchronized boolean acceptWaveForm(byte[] pcm, int length) {
        if (recognizer == null || nativesPermanentlyFailed) {
            return false;
        }
        try {
            return recognizer.acceptWaveForm(pcm, length);
        } catch (Throwable ex) {
            if (ex instanceof VirtualMachineError) {
                throw (VirtualMachineError) ex;
            }
            VoiceRecognitionState.setLastError("Recognizer error: " + ex.getMessage(), false);
            return false;
        }
    }

    @Nullable
    public synchronized String partialResultText() {
        if (recognizer == null || nativesPermanentlyFailed) {
            return null;
        }
        try {
            return extractText(recognizer.getPartialResult(), "partial");
        } catch (Throwable ex) {
            return null;
        }
    }

    @Nullable
    public synchronized String finalResultText() {
        if (recognizer == null || nativesPermanentlyFailed) {
            return null;
        }
        try {
            return extractText(recognizer.getResult(), "text");
        } catch (Throwable ex) {
            return null;
        }
    }

    @Nullable
    public synchronized String flushFinal() {
        if (recognizer == null || nativesPermanentlyFailed) {
            return null;
        }
        try {
            return extractText(recognizer.getFinalResult(), "text");
        } catch (Throwable ex) {
            return null;
        }
    }

    public synchronized void resetUtterance() {
        if (recognizer == null || nativesPermanentlyFailed) {
            return;
        }
        try {
            recognizer.reset();
        } catch (Throwable ignored) {
        }
    }

    private static String extractText(String json, String field) {
        if (json == null || json.isBlank()) {
            return "";
        }
        try {
            JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
            if (obj.has(field) && !obj.get(field).isJsonNull()) {
                return obj.get(field).getAsString();
            }
            if (obj.has("text") && !obj.get("text").isJsonNull()) {
                return obj.get("text").getAsString();
            }
        } catch (Exception ex) {
            UniverseVerityVoice.LOGGER.debug("[VerityVoice] Malformed Vosk JSON: {}", json);
        }
        return "";
    }

    public String loadError() {
        return loadError;
    }

    public boolean isLoaded() {
        return model != null && recognizer != null && !nativesPermanentlyFailed;
    }

    public boolean nativesPermanentlyFailed() {
        return nativesPermanentlyFailed;
    }

    private void closeQuietly() {
        if (recognizer != null) {
            try {
                recognizer.close();
            } catch (Throwable ignored) {
            }
            recognizer = null;
        }
        if (model != null) {
            try {
                model.close();
            } catch (Throwable ignored) {
            }
            model = null;
        }
        grammarFingerprint = "";
    }

    @Override
    public synchronized void close() {
        closeQuietly();
    }
}
