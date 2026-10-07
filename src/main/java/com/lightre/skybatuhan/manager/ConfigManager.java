package com.lightre.skybatuhan.manager;

import com.lightre.skybatuhan.SkyBatuhan;
import com.lightre.skybatuhan.base.ModConfig;
import com.lightre.skybatuhan.base.ConfigEditorInfoValue;
import com.lightre.skybatuhan.base.enums.FishingOptions.ActionSlot;
import com.lightre.skybatuhan.base.enums.FishingOptions.FishMode;
import io.github.notenoughupdates.moulconfig.managed.ManagedConfig;
import io.github.notenoughupdates.moulconfig.common.text.StructuredText;
import io.github.notenoughupdates.moulconfig.gui.editors.GuiOptionEditorInfoText;
import io.github.notenoughupdates.moulconfig.managed.ManagedConfigBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

public class ConfigManager {
    // Directory: .minecraft/config/skybatuhan
    private static final Path CONFIG_DIR = FabricLoader.getInstance().getConfigDir().resolve("skybatuhan");
    private static final File CONFIG_FILE = CONFIG_DIR.resolve("config.json").toFile();

    public static ModConfig config = new ModConfig();
    private static ManagedConfig<ModConfig> managed;

    private static boolean firstLoadDone = false;

    public static ManagedConfig<ModConfig> getManaged() {
        return managed;
    }

    public static void load() {
        boolean existed = CONFIG_FILE.exists();

        try {
            Files.createDirectories(CONFIG_DIR);

            ManagedConfigBuilder<ModConfig> builder = new ManagedConfigBuilder<>(CONFIG_FILE, ModConfig.class);
            builder.customProcessor(ConfigEditorInfoValue.class, (option, annotation) ->
                    new GuiOptionEditorInfoText(option, StructuredText.of(String.valueOf(option.get()))));
            managed = new ManagedConfig<>(builder);
            config = managed.getInstance();

            if (config == null) {
                config = new ModConfig();
            }

            if (config.farming == null) config.farming = new ModConfig.FarmingCategory();
            if (config.fishing == null) config.fishing = new ModConfig.FishingCategory();

            if (config.farming.safety == null) config.farming.safety = new ModConfig.SafetyCategory();
            if (config.farming.general == null) config.farming.general = new ModConfig.GeneralSettings();

            if (config.farming.farmingMovements == null)
                config.farming.farmingMovements = new ModConfig.FarmingMovements();

            if (config.farming.farmingMovements.firstMove == null)
                config.farming.farmingMovements.firstMove = new ModConfig.MoveSettings();

            if (config.farming.farmingMovements.secondMove == null)
                config.farming.farmingMovements.secondMove = new ModConfig.MoveSettings();

            if (config.fishing.fishMode == null) config.fishing.fishMode = FishMode.VANILLA;
            if (config.fishing.actionSlot == null) config.fishing.actionSlot = ActionSlot.SLOT_3;

            config.fishing.minCastDelay = Math.max(config.fishing.minCastDelay, ModConfig.FishingCategory.MIN_CAST_DELAY_MS);
            config.fishing.maxCastDelay = Math.max(config.fishing.maxCastDelay, ModConfig.FishingCategory.MIN_CAST_DELAY_MS);
            config.farming.safety.timeoutMs = Math.max(500, config.farming.safety.timeoutMs);
            config.farming.safety.threshold = Math.max(0.1, config.farming.safety.threshold);
            config.farming.general.pointRange = Math.max(0.1, config.farming.general.pointRange);

            if (config.disconnect == null) config.disconnect = new ModConfig.DisconnectCategory();
            if (config.disconnect.reconnect == null) config.disconnect.reconnect = new ModConfig.ReconnectCategory();

            if (!firstLoadDone) {
                // Features must never start by themselves when the game launches
                config.farming.autoFarmEnabled = false;
                config.fishing.autoFishEnabled = false;
                firstLoadDone = true;
            } else {
                // Reload: keep the real on/off state, otherwise the menu sync would switch the features off
                config.farming.autoFarmEnabled = ModuleManager.getFarmFeature().isEnabled();
                config.fishing.autoFishEnabled = ModuleManager.getFishFeature().isEnabled();
            }

            if (!existed) save();

        } catch (Exception e) {
            SkyBatuhan.LOGGER.error("Could not load config.json, using defaults", e);
            if (managed == null) {
                config = new ModConfig();
            }
        }
    }

    public static void save() {
        try {
            if (managed != null) {
                managed.saveToFile();
            }
        } catch (Exception e) {
            SkyBatuhan.LOGGER.error("Could not save config.json", e);
        }
    }
}