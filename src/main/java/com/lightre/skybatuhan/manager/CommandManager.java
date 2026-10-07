package com.lightre.skybatuhan.manager;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;
// import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import com.lightre.skybatuhan.SkyBatuhan;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;

public class CommandManager {

    private static final String PREFIX = "§6[SkyBatuhan] ";

    public static void init() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, _) -> {
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
        ModuleManager.getFarmFeature().clearAll();
        sendMessage(context, PREFIX + "§cAll farm points cleared!");
        return 1;
    }

    private static int undoFarm(CommandContext<FabricClientCommandSource> context) {
        boolean removed = ModuleManager.getFarmFeature().undoLastPoint();

        if (removed) {
            sendMessage(context, PREFIX + "§eLast point undone.");
        } else {
            sendMessage(context, PREFIX + "§7There are no points to undo.");
        }
        return removed ? 1 : 0;
    }

    private static int mainGui(CommandContext<FabricClientCommandSource> context) {
        Minecraft client = Minecraft.getInstance();

        client.execute(ModuleManager::openMenu);

        return 1;
    }

    public static void sendMessage(CommandContext<FabricClientCommandSource> context, String message) {
        context.getSource().sendFeedback(Component.literal(message));
    }
}
