package com.lightre.skybatuhan.manager;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.lightre.skybatuhan.base.ModConfig;
import net.fabricmc.loader.api.FabricLoader;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.nio.file.Path;

public class ConfigManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    // Directory: .minecraft/config/skybatuhan
    private static final Path CONFIG_DIR = FabricLoader.getInstance().getConfigDir().resolve("skybatuhan");
    private static final File CONFIG_FILE = CONFIG_DIR.resolve("config.json").toFile();

    public static ModConfig config = new ModConfig();

    public static void load() {
        if (!CONFIG_FILE.exists()) {
            save();
            return;
        }

        try (FileReader reader = new FileReader(CONFIG_FILE)) {
            config = GSON.fromJson(reader, ModConfig.class);

            if (config == null) {
                config = new ModConfig();
            }

            if (config.safety == null) config.safety = new ModConfig.SafetyCategory();
            if (config.farming == null) config.farming = new ModConfig.FarmingCategory();

            if (config.farming.farmingMovements == null)
                config.farming.farmingMovements = new ModConfig.FarmingMovements();

            if (config.farming.farmingMovements.firstMove == null)
                config.farming.farmingMovements.firstMove = new ModConfig.MoveSettings();

            if (config.farming.farmingMovements.secondMove == null)
                config.farming.farmingMovements.secondMove = new ModConfig.MoveSettings();

        } catch (Exception e) {
            System.err.println("[" + CONFIG_FILE.getName() + "] Ayarlar yuklenirken hata olustu! Varsayilanlar kullaniliyor.");
            e.printStackTrace();
            config = new ModConfig();
        }
    }

    public static void save() {
        try {
            if (!CONFIG_DIR.toFile().exists()) {
                CONFIG_DIR.toFile().mkdirs();
            }

            try (FileWriter writer = new FileWriter(CONFIG_FILE)) {
                GSON.toJson(config, writer);
            }
        } catch (Exception e) {
            System.err.println("[" + CONFIG_FILE.getName() + "] Ayarlar kaydedilirken hata olustu.");
            e.printStackTrace();
        }
    }
}