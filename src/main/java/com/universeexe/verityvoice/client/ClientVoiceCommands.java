package com.universeexe.verityvoice.client;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;

@OnlyIn(Dist.CLIENT)
public final class ClientVoiceCommands {
    private ClientVoiceCommands() {
    }

    public static void register(RegisterClientCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(Commands.literal("verityvoice")
                .then(Commands.literal("status")
                        .executes(ctx -> {
                            ClientVoiceCommandBridge.status();
                            return 1;
                        }))
                .then(Commands.literal("enable")
                        .executes(ctx -> {
                            ClientVoiceCommandBridge.setEnabled(true);
                            return 1;
                        }))
                .then(Commands.literal("disable")
                        .executes(ctx -> {
                            ClientVoiceCommandBridge.setEnabled(false);
                            return 1;
                        }))
                .then(Commands.literal("devices")
                        .executes(ctx -> {
                            ClientVoiceCommandBridge.run("devices");
                            return 1;
                        }))
                .then(Commands.literal("device")
                        .then(Commands.argument("name", StringArgumentType.greedyString())
                                .executes(ctx -> {
                                    ClientVoiceCommandBridge.setDevice(StringArgumentType.getString(ctx, "name"));
                                    return 1;
                                })))
                .then(Commands.literal("model")
                        .executes(ctx -> {
                            ClientVoiceCommandBridge.run("model");
                            return 1;
                        }))
                .then(Commands.literal("testmic")
                        .executes(ctx -> {
                            ClientVoiceCommandBridge.run("testmic");
                            return 1;
                        }))
                .then(Commands.literal("start")
                        .executes(ctx -> {
                            ClientVoiceCommandBridge.run("start");
                            return 1;
                        }))
                .then(Commands.literal("stop")
                        .executes(ctx -> {
                            ClientVoiceCommandBridge.run("stop");
                            return 1;
                        }))
                .then(Commands.literal("debug")
                        .then(Commands.argument("value", BoolArgumentType.bool())
                                .executes(ctx -> {
                                    ClientVoiceCommandBridge.setDebug(BoolArgumentType.getBool(ctx, "value"));
                                    return 1;
                                })))
                .then(Commands.literal("transcript")
                        .then(Commands.argument("value", BoolArgumentType.bool())
                                .executes(ctx -> {
                                    ClientVoiceCommandBridge.setTranscript(BoolArgumentType.getBool(ctx, "value"));
                                    return 1;
                                })))
        );
    }
}
