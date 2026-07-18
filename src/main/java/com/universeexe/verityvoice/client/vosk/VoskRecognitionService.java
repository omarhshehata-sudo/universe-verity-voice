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
 */
@OnlyIn(Dist.CLIENT)
public final class VoskRecognitionService implements AutoCloseable {
    private Model model;
    private Recognizer recognizer;
    private String grammarFingerprint = "";
    private String loadError = "";
    /** Once natives fail, never retry — LibVosk clinit stays poisoned. */
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
            VoiceRecognitionState.setMicStatus(VoiceRecognitionState.MicStatus.ERROR);
            return false;
        }

        Path path = expectedModelPath();
        if (!looksLikeModel(path)) {
            VoiceRecognitionState.setModelStatus(VoiceRecognitionState.ModelStatus.MISSING);
            VoiceRecognitionState.setMicStatus(VoiceRecognitionState.MicStatus.MODEL_MISSING);
            loadError = "Missing Vosk model folder: " + path.toAbsolutePath();
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
            return true;
        } catch (Throwable ex) {
            // LinkageError / ExceptionInInitializerError / UnsatisfiedLinkError / model IO —
            // never escape to the client thread. VirtualMachineError is rethrown.
            if (ex instanceof VirtualMachineError) {
                throw (VirtualMachineError) ex;
            }
            String prefix = (ex instanceof LinkageError)
                    ? "Vosk native library unavailable"
                    : "Vosk model/native error";
            return failNative(prefix, ex);
        }
    }

    private boolean failNative(String prefix, Throwable err) {
        nativesPermanentlyFailed = true;
        loadError = prefix + ": " + (err.getMessage() == null ? err.getClass().getSimpleName() : err.getMessage());
        VoiceRecognitionState.setModelStatus(VoiceRecognitionState.ModelStatus.ERROR);
        VoiceRecognitionState.setMicStatus(VoiceRecognitionState.MicStatus.ERROR);
        VoiceRecognitionState.setLastError(loadError);
        UniverseVerityVoice.LOGGER.error(
                "[VerityVoice] {} — voice recognition disabled for this session (game continues).",
                loadError
        );
        UniverseVerityVoice.LOGGER.debug("[VerityVoice] Native/model failure detail", err);
        closeQuietly();
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
            VoiceRecognitionState.setLastError("Recognizer error: " + ex.getMessage());
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
