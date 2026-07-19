package com.universeexe.verityvoice.common;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class VerityVoiceCooldownManager {
    public static final VerityVoiceCooldownManager INSTANCE = new VerityVoiceCooldownManager();

    private final Map<UUID, Map<ResourceLocation, Long>> intentReadyAtTick = new ConcurrentHashMap<>();
    private final Map<UUID, RateWindow> rateWindows = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastSequence = new ConcurrentHashMap<>();
    private final Map<UUID, String> lastIntentKey = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastIntentTick = new ConcurrentHashMap<>();
    private final Map<UUID, Long> helloReadyAtTick = new ConcurrentHashMap<>();

    /** HELLO debounce window after accept — any phrase (5 s). */
    public static final int HELLO_DEBOUNCE_TICKS = 100;

    private VerityVoiceCooldownManager() {
    }

    public boolean isOnCooldown(ServerPlayer player, ResourceLocation intentId, long gameTime) {
        Map<ResourceLocation, Long> map = intentReadyAtTick.get(player.getUUID());
        if (map == null) {
            return false;
        }
        Long ready = map.get(intentId);
        return ready != null && gameTime < ready;
    }

    public void applyCooldown(ServerPlayer player, ResourceLocation intentId, int cooldownTicks, long gameTime) {
        intentReadyAtTick
                .computeIfAbsent(player.getUUID(), id -> new ConcurrentHashMap<>())
                .put(intentId, gameTime + Math.max(0, cooldownTicks));
    }

    public boolean acceptSequence(ServerPlayer player, long sequence) {
        Long prev = lastSequence.get(player.getUUID());
        if (prev != null && sequence <= prev) {
            return false;
        }
        lastSequence.put(player.getUUID(), sequence);
        return true;
    }

    /**
     * Rejects the same intent+phrase fired twice within {@code debounceTicks} (default 40 = 2s).
     * HELLO uses intent-only debounce ({@link #HELLO_DEBOUNCE_TICKS}) so "hi" and "hello" cannot stack.
     */
    public boolean acceptIntent(ServerPlayer player, ResourceLocation intentId, String phrase, long gameTime) {
        UUID id = player.getUUID();
        if (VoiceIntents.HELLO.equals(intentId)) {
            Long helloReady = helloReadyAtTick.get(id);
            if (helloReady != null && gameTime < helloReady) {
                return false;
            }
            helloReadyAtTick.put(id, gameTime + HELLO_DEBOUNCE_TICKS);
            return true;
        }
        String key = intentId + "|" + (phrase == null ? "" : phrase.trim().toLowerCase());
        String prevKey = lastIntentKey.get(id);
        Long prevTick = lastIntentTick.get(id);
        if (prevKey != null && prevKey.equals(key) && prevTick != null && gameTime - prevTick < 40L) {
            return false;
        }
        lastIntentKey.put(id, key);
        lastIntentTick.put(id, gameTime);
        return true;
    }

    public boolean isHelloDebounced(ServerPlayer player, long gameTime) {
        Long ready = helloReadyAtTick.get(player.getUUID());
        return ready != null && gameTime < ready;
    }

    public void markHelloAccepted(ServerPlayer player, long gameTime) {
        helloReadyAtTick.put(player.getUUID(), gameTime + HELLO_DEBOUNCE_TICKS);
    }

    public boolean allowRate(ServerPlayer player, long gameTime, int maxCount, int windowTicks) {
        RateWindow window = rateWindows.computeIfAbsent(player.getUUID(), id -> new RateWindow());
        if (gameTime - window.windowStart >= windowTicks) {
            window.windowStart = gameTime;
            window.count = 0;
        }
        if (window.count >= maxCount) {
            return false;
        }
        window.count++;
        return true;
    }

    public void clear(ServerPlayer player) {
        UUID id = player.getUUID();
        intentReadyAtTick.remove(id);
        rateWindows.remove(id);
        lastSequence.remove(id);
        lastIntentKey.remove(id);
        lastIntentTick.remove(id);
        helloReadyAtTick.remove(id);
    }

    private static final class RateWindow {
        long windowStart;
        int count;
    }
}
