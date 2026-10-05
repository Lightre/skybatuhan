package com.lightre.skybatuhan.manager;

import com.lightre.skybatuhan.base.ModConfig;
import io.github.notenoughupdates.moulconfig.managed.ManagedConfig;
import net.fabricmc.loader.api.FabricLoader;

import java.io.File;
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
            CONFIG_DIR.toFile().mkdirs();

            managed = ManagedConfig.create(CONFIG_FILE, ModConfig.class);
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

            config.fishing.minCastDelay = Math.max(config.fishing.minCastDelay, ModConfig.FishingCategory.MIN_CAST_DELAY_MS);
            config.fishing.maxCastDelay = Math.max(config.fishing.maxCastDelay, ModConfig.FishingCategory.MIN_CAST_DELAY_MS);

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
            System.err.println("[" + CONFIG_FILE.getName() + "] Ayarlar yuklenirken hata olustu!");
            e.printStackTrace();
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
            System.err.println("[" + CONFIG_FILE.getName() + "] Ayarlar kaydedilirken hata olustu.");
            e.printStackTrace();
        }
    }
}