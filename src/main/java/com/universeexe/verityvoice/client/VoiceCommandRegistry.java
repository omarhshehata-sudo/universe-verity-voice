package com.universeexe.verityvoice.client;

import com.universeexe.verityvoice.common.VoiceCommandDefinition;
import com.universeexe.verityvoice.common.VoiceCommandReloadListener;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@OnlyIn(Dist.CLIENT)
public final class VoiceCommandRegistry {
    public static final VoiceCommandRegistry INSTANCE = new VoiceCommandRegistry();

    private volatile List<VoiceCommandDefinition> commands = List.of();

    private VoiceCommandRegistry() {
    }

    public void reloadFromServerDefinitions() {
        Map<?, VoiceCommandDefinition> map = VoiceCommandReloadListener.INSTANCE.snapshot();
        List<VoiceCommandDefinition> list = new ArrayList<>(map.values());
        list.sort((a, b) -> Integer.compare(b.priority(), a.priority()));
        commands = List.copyOf(list);
    }

    public List<VoiceCommandDefinition> commands() {
        return commands;
    }

    public Collection<String> grammarWords() {
        Set<String> words = new LinkedHashSet<>();
        words.add("verity");
        words.add("verety");
        words.add("veritye");
        words.add("hello");
        words.add("hi");
        words.add("hey");
        words.add("make");
        words.add("sound");
        words.add("noise");
        words.add("favorite");
        words.add("favourite");
        words.add("song");
        words.add("music");
        words.add("nearest");
        words.add("closest");
        words.add("village");
        words.add("diamonds");
        words.add("diamond");
        words.add("follow");
        words.add("stay");
        words.add("wait");
        words.add("come");
        words.add("here");
        words.add("angry");
        words.add("mad");
        words.add("what");
        words.add("where");
        words.add("is");
        words.add("are");
        words.add("your");
        words.add("the");
        words.add("me");
        words.add("you");
        words.add("do");
        words.add("a");
        words.add("can");
        words.add("find");
        words.add("like");
        words.add("over");
        words.add("back");
        words.add("get");
        words.add("did");
        words.add("i");
        words.add("upset");
        words.add("happened");
        words.add("tell");
        words.add("to");
        words.add("with");
        words.add("not");
        words.add("move");
        words.add("meet");
        words.add("nice");
        for (VoiceCommandDefinition def : commands) {
            for (String alias : def.aliases()) {
                for (String token : alias.split("\\s+")) {
                    if (!token.isBlank()) {
                        words.add(token);
                    }
                }
            }
        }
        return words;
    }
}
