package com.universeexe.verityvoice.common;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import com.universeexe.verityvoice.UniverseVerityVoice;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Loads data-driven voice command definitions from datapacks.
 * Path: data/&lt;namespace&gt;/verity_voice_commands/*.json
 */
public final class VoiceCommandReloadListener extends SimplePreparableReloadListener<Map<ResourceLocation, VoiceCommandDefinition>> {
    public static final VoiceCommandReloadListener INSTANCE = new VoiceCommandReloadListener();
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().setLenient().create();
    private static final String FOLDER = "verity_voice_commands";

    private volatile Map<ResourceLocation, VoiceCommandDefinition> commands = Map.of();

    private VoiceCommandReloadListener() {
    }

    public Map<ResourceLocation, VoiceCommandDefinition> getCommands() {
        return commands;
    }

    public Optional<VoiceCommandDefinition> get(ResourceLocation eventId) {
        return Optional.ofNullable(commands.get(eventId));
    }

    public boolean isKnownIntent(ResourceLocation intentId) {
        return VoiceIntents.isRegistered(intentId) && (intentId.equals(VoiceIntents.UNKNOWN) || commands.containsKey(intentId));
    }

    @Override
    protected Map<ResourceLocation, VoiceCommandDefinition> prepare(ResourceManager resourceManager, ProfilerFiller profiler) {
        Map<String, VoiceCommandDefinition> byLocalId = new LinkedHashMap<>();
        Map<ResourceLocation, VoiceCommandDefinition> byEventId = new LinkedHashMap<>();

        resourceManager.listResources(FOLDER, path -> path.getPath().endsWith(".json")).forEach((location, resource) -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(resource.open(), StandardCharsets.UTF_8))) {
                JsonObject root = GSON.fromJson(reader, JsonObject.class);
                VoiceCommandDefinition parsed = parse(location, root);
                if (parsed == null) {
                    return;
                }
                if (byLocalId.containsKey(parsed.localId())) {
                    LOGGER.error("[VerityVoice] Duplicate command id '{}' from {}", parsed.localId(), location);
                    return;
                }
                if (byEventId.containsKey(parsed.eventId())) {
                    LOGGER.error("[VerityVoice] Duplicate event_id '{}' from {}", parsed.eventId(), location);
                    return;
                }
                byLocalId.put(parsed.localId(), parsed);
                byEventId.put(parsed.eventId(), parsed);
            } catch (Exception ex) {
                LOGGER.error("[VerityVoice] Failed to load voice command {}: {}", location, ex.toString());
            }
        });

        return Collections.unmodifiableMap(byEventId);
    }

    @Override
    protected void apply(Map<ResourceLocation, VoiceCommandDefinition> object, ResourceManager resourceManager, ProfilerFiller profiler) {
        this.commands = object;
        LOGGER.info("[VerityVoice] Loaded {} voice command definition(s)", object.size());
    }

    @Nullable
    private static VoiceCommandDefinition parse(ResourceLocation source, JsonObject root) {
        if (root == null) {
            LOGGER.error("[VerityVoice] Empty command file {}", source);
            return null;
        }
        if (!root.has("id") || root.get("id").getAsString().isBlank()) {
            LOGGER.error("[VerityVoice] Missing ID in {}", source);
            return null;
        }
        String localId = root.get("id").getAsString().trim().toLowerCase(Locale.ROOT);
        String eventRaw = root.has("event_id") ? root.get("event_id").getAsString().trim() : UniverseVerityVoice.MOD_ID + ":" + localId;
        ResourceLocation eventId = ResourceLocation.tryParse(eventRaw);
        if (eventId == null || !VoiceIntents.isRegistered(eventId)) {
            LOGGER.error("[VerityVoice] Unknown event_id '{}' in {}", eventRaw, source);
            return null;
        }

        boolean enabled = !root.has("enabled") || root.get("enabled").getAsBoolean();
        boolean wakeWordRequired = !root.has("wake_word_required") || root.get("wake_word_required").getAsBoolean();
        boolean allowedDuringIntroduction = root.has("allowed_during_introduction") && root.get("allowed_during_introduction").getAsBoolean();

        double maximumDistance = root.has("maximum_distance") ? root.get("maximum_distance").getAsDouble() : 12.0;
        if (maximumDistance <= 0.0 || maximumDistance > 32.0) {
            LOGGER.error("[VerityVoice] Invalid distance {} in {}", maximumDistance, source);
            return null;
        }

        float minimumConfidence = root.has("minimum_confidence") ? root.get("minimum_confidence").getAsFloat() : 0.60f;
        if (minimumConfidence < 0.0f || minimumConfidence > 1.0f) {
            LOGGER.error("[VerityVoice] Invalid confidence {} in {}", minimumConfidence, source);
            return null;
        }

        int cooldownTicks = root.has("cooldown_ticks") ? root.get("cooldown_ticks").getAsInt() : 40;
        int priority = root.has("priority") ? root.get("priority").getAsInt() : 50;

        List<String> aliases = new ArrayList<>();
        if (root.has("aliases") && root.get("aliases").isJsonArray()) {
            JsonArray arr = root.getAsJsonArray("aliases");
            for (JsonElement el : arr) {
                String alias = el.getAsString();
                if (alias == null || alias.isBlank()) {
                    LOGGER.error("[VerityVoice] Empty alias in {}", source);
                    continue;
                }
                aliases.add(alias.trim().toLowerCase(Locale.ROOT));
            }
        }
        if (aliases.isEmpty()) {
            LOGGER.error("[VerityVoice] No aliases in {}", source);
            return null;
        }

        return new VoiceCommandDefinition(
                localId,
                eventId,
                enabled,
                wakeWordRequired,
                allowedDuringIntroduction,
                maximumDistance,
                minimumConfidence,
                Math.max(0, cooldownTicks),
                priority,
                aliases
        );
    }

    /** Snapshot for client-side matcher after sync or for commands listing. */
    public Map<ResourceLocation, VoiceCommandDefinition> snapshot() {
        return new HashMap<>(commands);
    }
}
