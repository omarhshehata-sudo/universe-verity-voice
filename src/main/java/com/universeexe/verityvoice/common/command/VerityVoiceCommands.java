package com.universeexe.verityvoice.common.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.universeexe.verityvoice.common.OfficialVerityVoiceBridge;
import com.universeexe.verityvoice.common.VoiceCommandDefinition;
import com.universeexe.verityvoice.common.VoiceCommandReloadListener;
import com.universeexe.verityvoice.common.VoiceIntents;
import com.universeexe.verityvoice.common.VerityVoiceServerValidator;
import com.universeexe.verityvoice.common.config.VoiceCommonConfig;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * Server-registered commands. Client-only mic/model controls live in ClientVoiceCommands.
 */
public final class VerityVoiceCommands {
    private static final AtomicLong SIM_SEQUENCE = new AtomicLong(1_000_000L);

    private static final SuggestionProvider<CommandSourceStack> INTENT_SUGGESTIONS = (ctx, builder) ->
            SharedSuggestionProvider.suggest(
                    VoiceIntents.all().stream()
                            .filter(id -> !id.equals(VoiceIntents.UNKNOWN))
                            .map(ResourceLocation::getPath)
                            .collect(Collectors.toList()),
                    builder
            );

    private VerityVoiceCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("verityvoice")
                .then(Commands.literal("status")
                        .executes(ctx -> status(ctx.getSource())))
                .then(Commands.literal("intents")
                        .executes(ctx -> listIntents(ctx.getSource())))
                .then(Commands.literal("reload")
                        .requires(s -> s.hasPermission(2))
                        .executes(ctx -> reload(ctx.getSource())))
                .then(Commands.literal("simulate")
                        .requires(s -> s.hasPermission(2) && VoiceCommonConfig.ENABLE_DEBUG_COMMANDS.get())
                        .then(Commands.argument("intent", StringArgumentType.word())
                                .suggests(INTENT_SUGGESTIONS)
                                .executes(ctx -> simulate(ctx.getSource(), StringArgumentType.getString(ctx, "intent")))))
                .then(Commands.literal("nearestverity")
                        .executes(ctx -> nearest(ctx.getSource())))
        );
    }

    private static int status(CommandSourceStack source) {
        if (source.getEntity() instanceof ServerPlayer player) {
            Optional<Entity> near = OfficialVerityVoiceBridge.findNearestNormalVerity(player, 32.0);
            Optional<Entity> associated = OfficialVerityVoiceBridge.findAssociatedVerity(player);
            source.sendSuccess(() -> Component.literal("[VerityVoice] Server intents enabled="
                    + VoiceCommonConfig.VOICE_INTENT_EVENTS_ENABLED.get()
                    + " introComplete=" + OfficialVerityVoiceBridge.isIntroductionComplete(player)
                    + " associated=" + associated.map(e -> String.valueOf(e.getId())).orElse("none")
                    + " nearby="
                    + near.map(e -> e.getId() + "@" + String.format(Locale.ROOT, "%.1f", player.distanceTo(e))).orElse("none")), false);
            return 1;
        }
        source.sendSuccess(() -> Component.literal("[VerityVoice] Server intents enabled="
                + VoiceCommonConfig.VOICE_INTENT_EVENTS_ENABLED.get()), false);
        return 1;
    }

    private static int listIntents(CommandSourceStack source) {
        var cmds = VoiceCommandReloadListener.INSTANCE.getCommands();
        source.sendSuccess(() -> Component.literal("[VerityVoice] " + cmds.size() + " command(s):"), false);
        for (VoiceCommandDefinition def : cmds.values()) {
            source.sendSuccess(() -> Component.literal(" - " + def.eventId() + " aliases=" + def.aliases().size()), false);
        }
        return cmds.size();
    }

    private static int reload(CommandSourceStack source) {
        source.getServer().execute(() -> source.getServer().getPlayerList().reloadResources());
        source.sendSuccess(() -> Component.literal("[VerityVoice] Reloading resources / command definitions"), false);
        return 1;
    }

    private static int simulate(CommandSourceStack source, String intentPath) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("Player only"));
            return 0;
        }
        ResourceLocation intentId = VoiceIntents.id(intentPath.toLowerCase(Locale.ROOT));
        Optional<Entity> verity = OfficialVerityVoiceBridge.findAssociatedVerity(player);
        if (verity.isEmpty()) {
            verity = OfficialVerityVoiceBridge.findNearestNormalVerity(player, VoiceCommonConfig.MAXIMUM_ALLOWED_DISTANCE.get());
        }
        if (verity.isEmpty()) {
            source.sendFailure(Component.literal("[VerityVoice] No nearby Verity to simulate against"));
            return 0;
        }
        var reason = VerityVoiceServerValidator.validateAndDispatch(
                player,
                intentId,
                verity.get().getId(),
                1.0f,
                SIM_SEQUENCE.getAndIncrement(),
                "simulate:" + intentPath
        );
        source.sendSuccess(() -> Component.literal("[VerityVoice] simulate " + intentId + " -> " + reason), false);
        return reason == VerityVoiceServerValidator.RejectReason.OK ? 1 : 0;
    }

    private static int nearest(CommandSourceStack source) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("Player only"));
            return 0;
        }
        Optional<Entity> near = OfficialVerityVoiceBridge.findNearestNormalVerity(player, 32.0);
        if (near.isEmpty()) {
            source.sendSuccess(() -> Component.literal("[VerityVoice] No normal Verity within 32 blocks"), false);
            return 0;
        }
        Entity e = near.get();
        source.sendSuccess(() -> Component.literal(String.format(
                Locale.ROOT,
                "[VerityVoice] Nearest Verity id=%d uuid=%s dist=%.2f ownerOk=%s",
                e.getId(),
                e.getUUID(),
                player.distanceTo(e),
                OfficialVerityVoiceBridge.belongsToPlayer(e, player)
        )), false);
        return 1;
    }
}
