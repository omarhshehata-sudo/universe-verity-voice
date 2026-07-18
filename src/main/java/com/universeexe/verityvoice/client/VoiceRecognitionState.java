package com.universeexe.verityvoice.client;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class VoiceRecognitionState {
    public enum MicStatus {
        OFF,
        READY,
        LISTENING,
        RECOGNIZING,
        COMMAND_DETECTED,
        NO_COMMAND,
        ERROR,
        MODEL_MISSING
    }

    public enum ModelStatus {
        UNKNOWN,
        LOADED,
        MISSING,
        ERROR
    }

    public enum RecognizerStatus {
        IDLE,
        LISTENING,
        PROCESSING
    }

    public enum PacketStatus {
        NONE,
        SENT,
        REJECTED,
        ACCEPTED
    }

    private static volatile MicStatus micStatus = MicStatus.OFF;
    private static volatile ModelStatus modelStatus = ModelStatus.UNKNOWN;
    private static volatile RecognizerStatus recognizerStatus = RecognizerStatus.IDLE;
    private static volatile PacketStatus packetStatus = PacketStatus.NONE;
    private static volatile float audioLevel;
    private static volatile String partialText = "";
    private static volatile String finalText = "";
    private static volatile String normalizedText = "";
    private static volatile String matchedIntent = "";
    private static volatile float matchScore;
    private static volatile float speechConfidence;
    private static volatile String nearbyVerity = "none";
    private static volatile String lastError = "";
    private static volatile String lastDetail = "";
    private static volatile boolean modelMissingNotified;

    private VoiceRecognitionState() {
    }

    public static MicStatus micStatus() {
        return micStatus;
    }

    public static void setMicStatus(MicStatus status) {
        micStatus = status == null ? MicStatus.OFF : status;
    }

    public static ModelStatus modelStatus() {
        return modelStatus;
    }

    public static void setModelStatus(ModelStatus status) {
        modelStatus = status == null ? ModelStatus.UNKNOWN : status;
    }

    public static RecognizerStatus recognizerStatus() {
        return recognizerStatus;
    }

    public static void setRecognizerStatus(RecognizerStatus status) {
        recognizerStatus = status == null ? RecognizerStatus.IDLE : status;
    }

    public static PacketStatus packetStatus() {
        return packetStatus;
    }

    public static void setPacketStatus(PacketStatus status) {
        packetStatus = status == null ? PacketStatus.NONE : status;
    }

    public static float audioLevel() {
        return audioLevel;
    }

    public static void setAudioLevel(float level) {
        audioLevel = Math.max(0.0f, Math.min(1.0f, level));
    }

    public static String partialText() {
        return partialText;
    }

    public static void setPartialText(String text) {
        partialText = text == null ? "" : text;
    }

    public static String finalText() {
        return finalText;
    }

    public static void setFinalText(String text) {
        finalText = text == null ? "" : text;
    }

    public static String normalizedText() {
        return normalizedText;
    }

    public static void setNormalizedText(String text) {
        normalizedText = text == null ? "" : text;
    }

    public static String matchedIntent() {
        return matchedIntent;
    }

    public static void setMatchedIntent(String intent) {
        matchedIntent = intent == null ? "" : intent;
    }

    public static float matchScore() {
        return matchScore;
    }

    public static void setMatchScore(float score) {
        matchScore = score;
    }

    public static float speechConfidence() {
        return speechConfidence;
    }

    public static void setSpeechConfidence(float confidence) {
        speechConfidence = confidence;
    }

    public static String nearbyVerity() {
        return nearbyVerity;
    }

    public static void setNearbyVerity(String value) {
        nearbyVerity = value == null ? "none" : value;
    }

    public static String lastError() {
        return lastError;
    }

    public static void setLastError(String error) {
        lastError = error == null ? "" : error;
        if (error != null && !error.isBlank()) {
            micStatus = MicStatus.ERROR;
        }
    }

    public static String lastDetail() {
        return lastDetail;
    }

    public static boolean consumeModelMissingNotify() {
        if (modelStatus == ModelStatus.MISSING && !modelMissingNotified) {
            modelMissingNotified = true;
            return true;
        }
        return false;
    }

    public static void resetModelMissingNotify() {
        modelMissingNotified = false;
    }

    public static void onServerResult(boolean accepted, ResourceLocation intentId, String detail) {
        packetStatus = accepted ? PacketStatus.ACCEPTED : PacketStatus.REJECTED;
        matchedIntent = intentId == null ? "" : intentId.toString();
        lastDetail = detail == null ? "" : detail;
        if (accepted) {
            micStatus = MicStatus.COMMAND_DETECTED;
        }
    }
}
