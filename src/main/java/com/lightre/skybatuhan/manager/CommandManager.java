package com.lightre.skybatuhan.manager;

import com.lightre.skybatuhan.SkyBatuhan;
import com.lightre.skybatuhan.base.Feature;
import com.lightre.skybatuhan.util.ModInfo;
import com.lightre.skybatuhan.util.FarmPoint;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;

import java.util.Locale;
import java.util.concurrent.CompletableFuture;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;

public class CommandManager {
    private static final String PREFIX = "§6[SkyBatuhan] ";

    public static void init() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, _) -> {
            LiteralArgumentBuilder<FabricClientCommandSource> sbh = literal("sbh").executes(CommandManager::mainGui);

            sbh.then(literal("reload").executes(CommandManager::reloadConfigs));
            sbh.then(registerFarmCommands());
            sbh.then(literal("status").executes(CommandManager::status));
            sbh.then(registerToggleCommands());

            dispatcher.register(sbh);
        });
    }

    private static LiteralArgumentBuilder<FabricClientCommandSource> registerFarmCommands() {
        return literal("farm").then(literal("list").executes(CommandManager::listFarm)).then(literal("remove").then(argument("id", IntegerArgumentType.integer(1)).suggests(CommandManager::suggestIds).executes(CommandManager::removeFarm))).then(literal("undo").executes(CommandManager::undoFarm)).then(literal("clear").executes(CommandManager::askClearFarm).then(literal("confirm").executes(CommandManager::clearFarm)));
    }

    // --- Handlers ---

    private static LiteralArgumentBuilder<FabricClientCommandSource> registerToggleCommands() {
        return literal("toggle").then(literal("farm").executes(_ -> toggle(ModuleManager.getFarmFeature()))).then(literal("fish").executes(_ -> toggle(ModuleManager.getFishFeature())));
    }

    private static int toggle(Feature feature) {
        // The feature prints its own "Auto Farm: ON/OFF" message
        feature.toggle(Minecraft.getInstance());
        return 1;
    }

    private static int status(CommandContext<FabricClientCommandSource> context) {
        var config = ConfigManager.config;
        var data = PointConfigManager.data;
        var farm = ModuleManager.getFarmFeature();
        var fish = ModuleManager.getFishFeature();

        sendMessage(context, PREFIX + "§fStatus §7(v" + ModInfo.getVersion() + ")");
        sendMessage(context, "§7Auto Farm: " + onOff(farm.isEnabled()) + timerInfo(farm, config.farming.safety.stopAfterEnabled, config.farming.safety.stopAfterMinutes));
        sendMessage(context, "§7Auto Fish: " + onOff(fish.isEnabled()) + timerInfo(fish, config.fishing.safety.stopAfterEnabled, config.fishing.safety.stopAfterMinutes));
        sendMessage(context, "§7Points: §f" + data.waypoints.size() + " §7turning, home " + (data.homePoint != null ? "§aset" : "§cnot set"));
        sendMessage(context, "§7Auto reconnect: " + onOff(config.reconnect.enabled) + (SessionMonitor.isRecovering() ? " §e(recovering now)" : ""));
        sendMessage(context, "§7Discord notifications: " + onOff(config.discord.enabled));
        return 1;
    }

    private static String onOff(boolean on) {
        return on ? "§aON" : "§cOFF";
    }

    private static String timerInfo(Feature feature, boolean timerOn, int limitMinutes) {
        if (!feature.isEnabled()) return "";

        long ranMinutes = feature.getRunTimeMs() / 60_000L;
        String text = " §7(running " + ranMinutes + " min";
        if (timerOn) text += ", stops in " + Math.max(0, limitMinutes - ranMinutes) + " min";
        return text + ")";
    }

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

    private static int listFarm(CommandContext<FabricClientCommandSource> context) {
        var data = PointConfigManager.data;

        if (data.waypoints.isEmpty() && data.homePoint == null) {
            sendMessage(context, PREFIX + "§7No points saved yet.");
            return 0;
        }

        sendMessage(context, PREFIX + "§fTurning points (" + data.waypoints.size() + "):");
        for (int i = 0; i < data.waypoints.size(); i++) {
            int id = i + 1;
            Component remove = Component.literal(" §c[remove]").withStyle(style -> style.withClickEvent(new ClickEvent.SuggestCommand("/sbh farm remove " + id)).withHoverEvent(new HoverEvent.ShowText(Component.literal("Click to type /sbh farm remove " + id))));

            context.getSource().sendFeedback(Component.literal("§e#" + id + " §7" + format(data.waypoints.get(i))).append(remove));
        }

        sendMessage(context, data.homePoint != null ? "§dHome §7" + format(data.homePoint) : "§dHome §7not set");
        return 1;
    }

    private static int removeFarm(CommandContext<FabricClientCommandSource> context) {
        int id = IntegerArgumentType.getInteger(context, "id");
        FarmPoint removed = ModuleManager.getFarmFeature().removeWaypoint(id);

        if (removed == null) {
            sendMessage(context, PREFIX + "§cThere is no point with ID " + id + ".");
            return 0;
        }

        sendMessage(context, PREFIX + "§eRemoved #" + id + " §7(" + format(removed) + ")§e. The points after it moved up by one.");
        listFarm(context);
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

    private static int askClearFarm(CommandContext<FabricClientCommandSource> context) {
        var data = PointConfigManager.data;

        if (data.waypoints.isEmpty() && data.homePoint == null) {
            sendMessage(context, PREFIX + "§7There is nothing to clear.");
            return 0;
        }

        Component confirm = Component.literal("§a[Confirm]").withStyle(style -> style.withClickEvent(new ClickEvent.SuggestCommand("/sbh farm clear confirm")).withHoverEvent(new HoverEvent.ShowText(Component.literal("Click to type /sbh farm clear confirm"))));

        context.getSource().sendFeedback(Component.literal(PREFIX + "§cThis deletes " + data.waypoints.size() + " turning point(s) and the home point. Type §f/sbh farm clear confirm§c or click ").append(confirm));
        return 1;
    }

    private static int clearFarm(CommandContext<FabricClientCommandSource> context) {
        ModuleManager.getFarmFeature().clearAll();
        sendMessage(context, PREFIX + "§cAll farm points cleared!");
        return 1;
    }

    private static CompletableFuture<Suggestions> suggestIds(CommandContext<FabricClientCommandSource> context, SuggestionsBuilder builder) {
        int count = PointConfigManager.data.waypoints.size();
        for (int i = 1; i <= count; i++) {
            builder.suggest(i);
        }
        return builder.buildFuture();
    }

    private static int mainGui(CommandContext<FabricClientCommandSource> context) {
        Minecraft client = Minecraft.getInstance();

        client.execute(ModuleManager::openMenu);

        return 1;
    }

    private static String format(FarmPoint point) {
        return String.format(Locale.ROOT, "%.1f, %.1f, %.1f", point.x, point.y, point.z);
    }

    public static void sendMessage(CommandContext<FabricClientCommandSource> context, String message) {
        context.getSource().sendFeedback(Component.literal(message));
    }
}