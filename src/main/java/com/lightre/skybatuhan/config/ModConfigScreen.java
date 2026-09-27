package com.lightre.skybatuhan.config;

import net.minecraft.client.Minecraft;
import com.lightre.skybatuhan.base.ModConfig;
import com.lightre.skybatuhan.manager.ConfigManager;
import com.lightre.skybatuhan.manager.ModuleManager;
import dev.isxander.yacl3.api.*;
import dev.isxander.yacl3.api.controller.BooleanControllerBuilder;
import dev.isxander.yacl3.api.controller.DoubleSliderControllerBuilder;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;
import java.util.function.Supplier;

@SuppressWarnings("deprecation")
public class ModConfigScreen {

    public static Screen create(Screen parent) {
        return YetAnotherConfigLib.createBuilder()
                .title(Component.literal("SkyBatuhan Config"))
                .category(generalCategory())
                .category(farmingCategory())
                .category(fishingCategory())
                .save(ConfigManager::save)
                .build()
                .generateScreen(parent);
    }

    private static ConfigCategory generalCategory() {
        ModConfig config = ConfigManager.config;
        return ConfigCategory.createBuilder().name(Component.literal("General")).build();
    }

    private static ConfigCategory farmingCategory() {
        ModConfig config = ConfigManager.config;

        return ConfigCategory.createBuilder()
                .name(Component.literal("Farming"))
                .group(OptionGroup.createBuilder()
                        .name(Component.literal("Auto Farm Status"))
                        .option(booleanOption("Auto Farm Enabled",
                                () -> ConfigManager.config.featureStates.getOrDefault("Auto Farm", false),
                                val -> ModuleManager.getFarmFeature().setState(Minecraft.getInstance(), val)))
                        .build())
                .group(OptionGroup.createBuilder()
                        .name(Component.literal("General Settings"))
                        .option(booleanOption("Attack Enabled", () -> config.farming.general.attackEnabled, val -> config.farming.general.attackEnabled = val))
                        .option(doubleOption("Point Range", () -> config.farming.general.pointRange, val -> config.farming.general.pointRange = val, 0.1, 3.0, 0.1))
                        .build())
                .group(movementGroup("First Movement", config.farming.farmingMovements.firstMove))
                .group(movementGroup("Second Movement", config.farming.farmingMovements.secondMove))
                .group(OptionGroup.createBuilder()
                        .name(Component.literal("Security Settings"))
                        .option(doubleOption("Threshold", () -> (double) config.safety.threshold, val -> config.safety.threshold = val.floatValue(), 0.0, 1.0, 0.1))
                        .option(doubleOption("Timeout (ms)", () -> (double) config.safety.timeoutMs, val -> config.safety.timeoutMs = val.longValue(), 0, 10000, 10))
                        .build())
                .build();
    }

    private static ConfigCategory fishingCategory() {
        ModConfig config = ConfigManager.config;

        return ConfigCategory.createBuilder()
                .name(Component.literal("Fishing"))
                .group(OptionGroup.createBuilder()
                        .name(Component.literal("Auto Fish Status"))
                        .option(booleanOption("Auto Fish Enabled",
                                () -> config.fishing.autoFishEnabled,
                                val -> ModuleManager.getFishFeature().setState(Minecraft.getInstance(), val)))
                        .option(Option.<String>createBuilder()
                                .name(Component.literal("Fishing Mode"))
                                .binding(config.fishing.fishMode, () -> config.fishing.fishMode, val -> config.fishing.fishMode = val)
                                .controller(opt -> dev.isxander.yacl3.api.controller.CyclingListControllerBuilder.create(opt)
                                        .values(java.util.List.of("Vanilla", "Skyblock"))
                                        .valueFormatter(Component::literal))
                                .build())
                        .build())
                .group(OptionGroup.createBuilder()
                        .name(Component.literal("Safety & AFK Settings"))
                        .option(doubleOption("AFK Timeout (Seconds)", () -> config.fishing.afkTimeoutSeconds, val -> config.fishing.afkTimeoutSeconds = val, 5.0, 180.0, 1.0))
                        .build())
                .group(OptionGroup.createBuilder()
                        .name(Component.literal("Reel-In Delay Settings (ms)"))
                        .option(doubleOption("Min Reel Delay", () -> config.fishing.minReelDelay, val -> config.fishing.minReelDelay = val, 10, 2000, 10))
                        .option(doubleOption("Max Reel Delay", () -> config.fishing.maxReelDelay, val -> config.fishing.maxReelDelay = val, 10, 2000, 10))
                        .build())
                .group(OptionGroup.createBuilder()
                        .name(Component.literal("Recast Delay Settings (ms)"))
                        .option(doubleOption("Min Cast Delay", () -> config.fishing.minCastDelay, val -> config.fishing.minCastDelay = val, 10, 2000, 10))
                        .option(doubleOption("Max Cast Delay", () -> config.fishing.maxCastDelay, val -> config.fishing.maxCastDelay = val, 10, 2000, 10))
                        .build())
                .build();
    }

    private static OptionGroup movementGroup(String groupName, ModConfig.MoveSettings movement) {
        return OptionGroup.createBuilder()
                .name(Component.literal(groupName))
                .option(booleanOption("Forward", () -> movement.forward, val -> movement.forward = val))
                .option(booleanOption("Left", () -> movement.left, val -> movement.left = val))
                .option(booleanOption("Back", () -> movement.back, val -> movement.back = val))
                .option(booleanOption("Right", () -> movement.right, val -> movement.right = val))
                .build();
    }

    private static Option<Boolean> booleanOption(String name, Supplier<Boolean> getter, Consumer<Boolean> setter) {
        return Option.<Boolean>createBuilder()
                .name(Component.literal(name))
                .binding(getter.get(), getter, setter)
                .controller(opt -> BooleanControllerBuilder.create(opt).coloured(true))
                .build();
    }

    private static Option<Double> doubleOption(String name, Supplier<Double> getter, Consumer<Double> setter, double min, double max, double step) {
        return Option.<Double>createBuilder()
                .name(Component.literal(name))
                .binding(getter.get(), getter, setter)
                .controller(opt -> DoubleSliderControllerBuilder.create(opt).range(min, max).step(step))
                .build();
    }
}