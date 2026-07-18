package com.universeexe.verityvoice.common.config;

import net.minecraftforge.common.ForgeConfigSpec;

public final class VoiceClientConfig {
    public static final ForgeConfigSpec SPEC;

    public static final ForgeConfigSpec.BooleanValue VOICE_COMMANDS_ENABLED;
    public static final ForgeConfigSpec.BooleanValue CONSENT_ANSWERED;
    public static final ForgeConfigSpec.ConfigValue<String> LISTENING_MODE;
    public static final ForgeConfigSpec.ConfigValue<String> MICROPHONE_DEVICE;
    public static final ForgeConfigSpec.IntValue PUSH_TO_TALK_TRAILING_MS;
    public static final ForgeConfigSpec.ConfigValue<String> WAKE_WORD;
    public static final ForgeConfigSpec.DoubleValue WAKE_WORD_COMMAND_WINDOW_SECONDS;
    public static final ForgeConfigSpec.DoubleValue NORMAL_LISTENING_DISTANCE;
    public static final ForgeConfigSpec.BooleanValue SHOW_MICROPHONE_INDICATOR;
    public static final ForgeConfigSpec.BooleanValue SHOW_PUSH_TO_TALK_INDICATOR;
    public static final ForgeConfigSpec.ConfigValue<String> INDICATOR_POSITION;
    public static final ForgeConfigSpec.IntValue INDICATOR_X_OFFSET;
    public static final ForgeConfigSpec.IntValue INDICATOR_Y_OFFSET;
    public static final ForgeConfigSpec.DoubleValue INDICATOR_OPACITY;
    public static final ForgeConfigSpec.BooleanValue SHOW_AUDIO_LEVEL_BARS;
    public static final ForgeConfigSpec.BooleanValue SHOW_PROCESSING_STATE;
    public static final ForgeConfigSpec.BooleanValue SHOW_SUCCESS_STATE;
    public static final ForgeConfigSpec.BooleanValue SHOW_NO_COMMAND_STATE;
    public static final ForgeConfigSpec.BooleanValue SHOW_UNAVAILABLE_STATE;
    public static final ForgeConfigSpec.DoubleValue INDICATOR_SCALE;
    public static final ForgeConfigSpec.IntValue INDICATOR_FADE_IN_MS;
    public static final ForgeConfigSpec.IntValue INDICATOR_FADE_OUT_MS;
    public static final ForgeConfigSpec.IntValue RESULT_DISPLAY_MS;
    public static final ForgeConfigSpec.BooleanValue USE_REDUCED_MOTION;
    public static final ForgeConfigSpec.BooleanValue PLAY_INDICATOR_SOUNDS;
    public static final ForgeConfigSpec.BooleanValue ENABLE_VERITY_HUD_ANOMALIES;
    public static final ForgeConfigSpec.BooleanValue SHOW_RECOGNIZED_TEXT;
    public static final ForgeConfigSpec.BooleanValue SHOW_DETECTED_INTENT;
    public static final ForgeConfigSpec.BooleanValue SEND_RECOGNIZED_TEXT_TO_SERVER_FOR_DEBUG;
    public static final ForgeConfigSpec.DoubleValue MINIMUM_RECOGNITION_CONFIDENCE;
    public static final ForgeConfigSpec.BooleanValue SUPPRESS_WHILE_PAUSED;
    public static final ForgeConfigSpec.BooleanValue SUPPRESS_WHILE_CHAT_OPEN;
    public static final ForgeConfigSpec.BooleanValue SUPPRESS_WHILE_TYPING;
    public static final ForgeConfigSpec.BooleanValue SUPPRESS_DURING_MENUS;
    public static final ForgeConfigSpec.BooleanValue USE_RESTRICTED_GRAMMAR;
    public static final ForgeConfigSpec.BooleanValue DEBUG_LOGGING;
    public static final ForgeConfigSpec.BooleanValue DEBUG_OVERLAY;
    public static final ForgeConfigSpec.ConfigValue<String> MODEL_FOLDER_NAME;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        builder.comment("Client-only offline speech recognition").push("voice");
        VOICE_COMMANDS_ENABLED = builder.define("voiceCommandsEnabled", false);
        CONSENT_ANSWERED = builder.define("consentAnswered", false);
        LISTENING_MODE = builder.define("listeningMode", "PUSH_TO_TALK");
        MICROPHONE_DEVICE = builder.define("microphoneDevice", "DEFAULT");
        PUSH_TO_TALK_TRAILING_MS = builder.defineInRange("pushToTalkTrailingMilliseconds", 550, 100, 2000);
        WAKE_WORD = builder.define("wakeWord", "verity");
        WAKE_WORD_COMMAND_WINDOW_SECONDS = builder.defineInRange("wakeWordCommandWindowSeconds", 4.0, 1.0, 15.0);
        NORMAL_LISTENING_DISTANCE = builder.defineInRange("normalListeningDistance", 12.0, 1.0, 32.0);
        SHOW_MICROPHONE_INDICATOR = builder.comment("Legacy alias kept for older configs.")
                .define("showMicrophoneIndicator", true);
        SHOW_PUSH_TO_TALK_INDICATOR = builder
                .comment("Primary toggle for the push-to-talk HUD capsule.")
                .define("showPushToTalkIndicator", true);
        INDICATOR_POSITION = builder.define("indicatorPosition", "BOTTOM_CENTER");
        INDICATOR_X_OFFSET = builder.defineInRange("indicatorXOffset", 0, -500, 500);
        INDICATOR_Y_OFFSET = builder.defineInRange("indicatorYOffset", 0, -500, 500);
        INDICATOR_OPACITY = builder.defineInRange("indicatorOpacity", 0.65, 0.25, 1.0);
        SHOW_AUDIO_LEVEL_BARS = builder.define("showAudioLevelBars", true);
        SHOW_PROCESSING_STATE = builder.define("showProcessingState", true);
        SHOW_SUCCESS_STATE = builder.define("showSuccessState", true);
        SHOW_NO_COMMAND_STATE = builder.define("showNoCommandState", true);
        SHOW_UNAVAILABLE_STATE = builder.define("showUnavailableState", true);
        INDICATOR_SCALE = builder.defineInRange("indicatorScale", 1.0, 0.75, 1.5);
        INDICATOR_FADE_IN_MS = builder.defineInRange("indicatorFadeInMilliseconds", 130, 40, 600);
        INDICATOR_FADE_OUT_MS = builder.defineInRange("indicatorFadeOutMilliseconds", 220, 60, 800);
        RESULT_DISPLAY_MS = builder.defineInRange("resultDisplayMilliseconds", 1000, 300, 3000);
        USE_REDUCED_MOTION = builder.define("useReducedMotion", false);
        PLAY_INDICATOR_SOUNDS = builder.define("playIndicatorSounds", false);
        ENABLE_VERITY_HUD_ANOMALIES = builder.define("enableVerityHudAnomalies", false);
        SHOW_RECOGNIZED_TEXT = builder.define("showRecognizedText", false);
        SHOW_DETECTED_INTENT = builder.define("showDetectedIntent", false);
        SEND_RECOGNIZED_TEXT_TO_SERVER_FOR_DEBUG = builder.define("sendRecognizedTextToServerForDebug", false);
        MINIMUM_RECOGNITION_CONFIDENCE = builder.defineInRange("minimumRecognitionConfidence", 0.60, 0.0, 1.0);
        SUPPRESS_WHILE_PAUSED = builder.define("suppressRecognitionWhilePaused", true);
        SUPPRESS_WHILE_CHAT_OPEN = builder.define("suppressRecognitionWhileChatOpen", true);
        SUPPRESS_WHILE_TYPING = builder.define("suppressRecognitionWhileTyping", true);
        SUPPRESS_DURING_MENUS = builder.define("suppressRecognitionDuringMenus", true);
        USE_RESTRICTED_GRAMMAR = builder.comment(
                        "Tiny command grammars make Vosk invent short words like 'hi' from noise. "
                                + "Default false = free English recognition (recommended).")
                .define("useRestrictedGrammar", false);
        DEBUG_LOGGING = builder.define("debugLogging", false);
        DEBUG_OVERLAY = builder.define("debugOverlay", false);
        MODEL_FOLDER_NAME = builder.define("modelFolderName", "vosk-model-small-en-us-0.15");
        builder.pop();
        SPEC = builder.build();
    }

    private VoiceClientConfig() {
    }
}
