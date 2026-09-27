package com.lightre.skybatuhan.manager;

import com.lightre.skybatuhan.config.ModConfigScreen;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import com.lightre.skybatuhan.SkyBatuhan;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;

public class CommandManager {

    private static final String PREFIX = "§6[SkyBatuhan] ";

    public static void init() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            LiteralArgumentBuilder<FabricClientCommandSource> sbh = literal("sbh").executes(CommandManager::mainGui);

            sbh.then(literal("reload").executes(CommandManager::reloadConfigs));
            sbh.then(registerFarmCommands());

            dispatcher.register(sbh);
        });
    }

    private static LiteralArgumentBuilder<FabricClientCommandSource> registerFarmCommands() {
        return literal("farm").then(literal("clear").executes(CommandManager::clearFarm)).then(literal("undo").executes(CommandManager::undoFarm));
    }

    // --- Handlers ---

    private static int reloadConfigs(CommandContext<FabricClientCommandSource> context) {
        try {
            ConfigManager.load();
            PointConfigManager.load();
            sendMessage(context, PREFIX + "§aConfigurations reloaded!");
        } catch (Exception e) {
            sendMessage(context, PREFIX + "§cConfig reload failed!");
            SkyBatuhan.LOGGER.error("Config reload failed!", e);
        }
        return 1;
    }

    private static int clearFarm(CommandContext<FabricClientCommandSource> context) {
        var farm = ModuleManager.getFarmFeature();
        farm.clearAll(Minecraft.getInstance());
        sendMessage(context, PREFIX + "§cAll farm points cleared!");
        return 1;
    }

    private static int undoFarm(CommandContext<FabricClientCommandSource> context) {
        var farm = ModuleManager.getFarmFeature();
        farm.undoLastPoint(Minecraft.getInstance());
        sendMessage(context, PREFIX + "§eLast point undone.");
        return 1;
    }

    private static int mainGui(CommandContext<FabricClientCommandSource> context) {
        Minecraft client = Minecraft.getInstance();

        client.execute(() -> {
            client.setScreenAndShow(ModConfigScreen.create(client.gui.screen()));
        });

        return 1;
    }

    public static void sendMessage(CommandContext<FabricClientCommandSource> context, String message) {
        context.getSource().sendFeedback(Component.literal(message));
    }
}
