package com.universeexe.verityvoice.common.config;

import net.minecraftforge.common.ForgeConfigSpec;

public final class VoiceCommonConfig {
    public static final ForgeConfigSpec SPEC;

    public static final ForgeConfigSpec.BooleanValue VOICE_INTENT_EVENTS_ENABLED;
    public static final ForgeConfigSpec.BooleanValue REQUIRE_NEARBY_VERITY;
    public static final ForgeConfigSpec.BooleanValue REQUIRE_ASSOCIATED_VERITY;
    public static final ForgeConfigSpec.BooleanValue ALLOW_COMMANDS_BEFORE_INTRODUCTION;
    public static final ForgeConfigSpec.DoubleValue MAXIMUM_ALLOWED_DISTANCE;
    public static final ForgeConfigSpec.IntValue INTENT_RATE_LIMIT_COUNT;
    public static final ForgeConfigSpec.IntValue INTENT_RATE_LIMIT_WINDOW_TICKS;
    public static final ForgeConfigSpec.BooleanValue REJECT_DEMON_VERITY;
    public static final ForgeConfigSpec.BooleanValue ENABLE_QUEST_COMPATIBILITY;
    public static final ForgeConfigSpec.BooleanValue ENABLE_DEBUG_COMMANDS;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        builder.comment("Server-side voice intent validation").push("voice");
        VOICE_INTENT_EVENTS_ENABLED = builder.define("voiceIntentEventsEnabled", true);
        REQUIRE_NEARBY_VERITY = builder.define("requireNearbyVerity", true);
        REQUIRE_ASSOCIATED_VERITY = builder.define("requireAssociatedVerity", true);
        ALLOW_COMMANDS_BEFORE_INTRODUCTION = builder.define("allowCommandsBeforeIntroduction", false);
        MAXIMUM_ALLOWED_DISTANCE = builder.defineInRange("maximumAllowedDistance", 32.0, 1.0, 64.0);
        INTENT_RATE_LIMIT_COUNT = builder.defineInRange("intentRateLimitCount", 5, 1, 50);
        INTENT_RATE_LIMIT_WINDOW_TICKS = builder.defineInRange("intentRateLimitWindowTicks", 100, 20, 1200);
        REJECT_DEMON_VERITY = builder.define("rejectDemonVerity", true);
        ENABLE_QUEST_COMPATIBILITY = builder.define("enableQuestCompatibility", true);
        ENABLE_DEBUG_COMMANDS = builder.define("enableDebugCommands", true);
        builder.pop();
        SPEC = builder.build();
    }

    private VoiceCommonConfig() {
    }
}
