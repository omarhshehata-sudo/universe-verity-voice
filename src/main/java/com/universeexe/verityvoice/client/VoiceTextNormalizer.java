package com.universeexe.verityvoice.client;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.Locale;
import java.util.regex.Pattern;

@OnlyIn(Dist.CLIENT)
public final class VoiceTextNormalizer {
    private static final Pattern MULTI_SPACE = Pattern.compile("\\s+");
    private static final Pattern PUNCT = Pattern.compile("[\\p{Punct}&&[^']]+");

    private VoiceTextNormalizer() {
    }

    public static String normalize(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        String text = raw.toLowerCase(Locale.ROOT).trim();
        text = text.replace('’', '\'').replace('‘', '\'').replace('“', '"').replace('”', '"');
        text = text.replace("what's", "what is")
                .replace("whats", "what is")
                .replace("where's", "where is")
                .replace("wheres", "where is")
                .replace("favourite", "favorite")
                .replace("veritye", "verity")
                .replace("verety", "verity")
                .replace("ver it e", "verity")
                .replace("hullo", "hello")
                .replace("hallo", "hello")
                .replace("helo", "hello")
                .replace("hellow", "hello");
        text = PUNCT.matcher(text).replaceAll(" ");
        text = MULTI_SPACE.matcher(text).replaceAll(" ").trim();

        // Strip safe leading fillers only.
        while (true) {
            String next = text;
            if (next.startsWith("um ")) next = next.substring(3);
            else if (next.startsWith("uh ")) next = next.substring(3);
            else if (next.startsWith("please ")) next = next.substring(7);
            else break;
            text = next.trim();
        }
        return text;
    }

    public static String stripWakeWord(String normalized, String wakeWord) {
        if (normalized == null || normalized.isEmpty()) {
            return "";
        }
        String wake = wakeWord == null ? "verity" : wakeWord.toLowerCase(Locale.ROOT);
        String text = normalized;
        if (text.startsWith(wake + " ")) {
            text = text.substring(wake.length()).trim();
        } else if (text.equals(wake)) {
            text = "";
        }
        return text;
    }

    public static boolean containsWakeWord(String normalized, String wakeWord) {
        if (normalized == null || normalized.isEmpty()) {
            return false;
        }
        String wake = wakeWord == null ? "verity" : wakeWord.toLowerCase(Locale.ROOT);
        String[] aliases = {wake, "verety", "veritye"};
        for (String alias : aliases) {
            if (normalized.equals(alias) || normalized.startsWith(alias + " ") || normalized.endsWith(" " + alias)
                    || normalized.contains(" " + alias + " ")) {
                return true;
            }
        }
        return false;
    }
}
