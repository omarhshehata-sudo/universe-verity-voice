package com.universeexe.verityvoice.client;

import com.universeexe.verityvoice.common.VoiceCommandDefinition;
import com.universeexe.verityvoice.common.VoiceIntents;
import com.universeexe.verityvoice.common.config.VoiceClientConfig;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@OnlyIn(Dist.CLIENT)
public final class VoiceIntentMatcher {
    public record MatchResult(ResourceLocation intentId, float score, VoiceCommandDefinition definition) {
    }

    private VoiceIntentMatcher() {
    }

    @Nullable
    public static MatchResult match(String normalized, ListeningMode mode) {
        if (normalized == null || normalized.isBlank()) {
            return null;
        }
        String wake = VoiceClientConfig.WAKE_WORD.get();
        boolean hasWake = VoiceTextNormalizer.containsWakeWord(normalized, wake);
        String withoutWake = VoiceTextNormalizer.stripWakeWord(normalized, wake);

        if (mode == ListeningMode.WAKE_WORD || mode == ListeningMode.NEARBY_CONTINUOUS) {
            if (!hasWake) {
                return null;
            }
        }

        List<VoiceCommandDefinition> commands = VoiceCommandRegistry.INSTANCE.commands();
        MatchResult best = null;
        MatchResult second = null;

        // Explicit FOLLOW rule: any utterance containing both "follow" and "me".
        MatchResult followMe = matchFollowMe(normalized, withoutWake, hasWake, mode, commands);
        if (followMe != null) {
            return followMe;
        }

        for (VoiceCommandDefinition def : commands) {
            if (!def.enabled()) {
                continue;
            }
            if ((mode == ListeningMode.WAKE_WORD || mode == ListeningMode.NEARBY_CONTINUOUS)
                    && def.wakeWordRequired() && !hasWake) {
                continue;
            }
            float score = scoreDefinition(normalized, withoutWake, def, mode, hasWake);
            if (score <= 0.0f) {
                continue;
            }
            MatchResult current = new MatchResult(def.eventId(), score, def);
            if (best == null || current.score() > best.score()) {
                second = best;
                best = current;
            } else if (second == null || current.score() > second.score()) {
                second = current;
            }
        }

        if (best == null) {
            return null;
        }
        // Ambiguous: similar top scores → UNKNOWN / no match.
        if (second != null
                && best.definition() != null
                && Math.abs(best.score() - second.score()) < 0.08f
                && best.score() < 0.95f
                && !best.intentId().equals(VoiceIntents.FOLLOW)) {
            return new MatchResult(VoiceIntents.UNKNOWN, best.score(), null);
        }
        if (best.definition() == null) {
            return null;
        }
        float min = Math.max(best.definition().minimumConfidence(), VoiceClientConfig.MINIMUM_RECOGNITION_CONFIDENCE.get().floatValue());
        if (best.score() < min) {
            return null;
        }
        return best;
    }

    @Nullable
    private static MatchResult matchFollowMe(
            String full,
            String withoutWake,
            boolean hasWake,
            ListeningMode mode,
            List<VoiceCommandDefinition> commands
    ) {
        Set<String> tokens = tokens(full);
        if (!(tokens.contains("follow") && tokens.contains("me"))) {
            return null;
        }
        if ((mode == ListeningMode.WAKE_WORD || mode == ListeningMode.NEARBY_CONTINUOUS) && !hasWake) {
            return null;
        }
        VoiceCommandDefinition followDef = null;
        for (VoiceCommandDefinition def : commands) {
            if (VoiceIntents.FOLLOW.equals(def.eventId()) && def.enabled()) {
                followDef = def;
                break;
            }
        }
        if (followDef == null) {
            return null;
        }
        float score = hasWake || mode == ListeningMode.PUSH_TO_TALK ? 0.95f : 0.0f;
        if (score <= 0.0f) {
            return null;
        }
        return new MatchResult(VoiceIntents.FOLLOW, score, followDef);
    }

    private static float scoreDefinition(
            String full,
            String withoutWake,
            VoiceCommandDefinition def,
            ListeningMode mode,
            boolean hasWake
    ) {
        float best = 0.0f;
        for (String alias : def.aliases()) {
            String a = alias.toLowerCase(Locale.ROOT).trim();
            if (a.isEmpty()) {
                continue;
            }
            if (full.equals(a) || withoutWake.equals(a)) {
                best = Math.max(best, hasWake || mode == ListeningMode.PUSH_TO_TALK ? 1.0f : 0.92f);
                continue;
            }
            // Exact after wake strip already handled; try token containment conservatively.
            float tokenScore = tokenScore(full, withoutWake, a, mode, hasWake);
            best = Math.max(best, tokenScore);
        }
        return best;
    }

    private static float tokenScore(String full, String withoutWake, String alias, ListeningMode mode, boolean hasWake) {
        Set<String> aliasTokens = tokens(alias);
        if (aliasTokens.isEmpty()) {
            return 0.0f;
        }
        Set<String> textTokens = tokens(full);
        Set<String> textNoWake = tokens(withoutWake);

        // Require almost all alias tokens to appear — prevents "I found a village yesterday".
        int hitFull = countHits(aliasTokens, textTokens);
        int hitNoWake = countHits(aliasTokens, textNoWake);
        int hit = Math.max(hitFull, hitNoWake);
        float coverage = hit / (float) aliasTokens.size();
        if (coverage < 0.85f) {
            return 0.0f;
        }
        // Extra content penalty: casual speech usually has many extra tokens.
        int extra = Math.max(0, textNoWake.size() - aliasTokens.size());
        if (extra > 2) {
            return 0.0f;
        }
        float score = 0.70f + 0.20f * coverage - 0.05f * extra;
        if (hasWake) {
            score += 0.05f;
        } else if (mode != ListeningMode.PUSH_TO_TALK) {
            return 0.0f;
        }
        // Fuzzy: allow one single-character edit on a single token only when coverage high.
        if (coverage >= 0.99f && extra <= 1) {
            score = Math.max(score, 0.88f);
        }
        return Math.min(0.95f, score);
    }

    private static int countHits(Set<String> aliasTokens, Set<String> textTokens) {
        int hit = 0;
        for (String token : aliasTokens) {
            if (textTokens.contains(token) || fuzzyTokenPresent(token, textTokens)) {
                hit++;
            }
        }
        return hit;
    }

    private static boolean fuzzyTokenPresent(String token, Set<String> textTokens) {
        if (token.length() < 4) {
            return false;
        }
        for (String t : textTokens) {
            if (Math.abs(t.length() - token.length()) <= 1 && levenshtein(t, token) <= 1) {
                return true;
            }
        }
        return false;
    }

    private static Set<String> tokens(String text) {
        Set<String> set = new HashSet<>();
        if (text == null || text.isBlank()) {
            return set;
        }
        set.addAll(Arrays.asList(text.split("\\s+")));
        set.remove("");
        return set;
    }

    private static int levenshtein(String a, String b) {
        int[] prev = new int[b.length() + 1];
        int[] cur = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) {
            prev[j] = j;
        }
        for (int i = 1; i <= a.length(); i++) {
            cur[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                cur[j] = Math.min(Math.min(cur[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
            }
            int[] tmp = prev;
            prev = cur;
            cur = tmp;
        }
        return prev[b.length()];
    }
}
