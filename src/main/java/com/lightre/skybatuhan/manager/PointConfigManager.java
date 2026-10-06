package com.lightre.skybatuhan.manager;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.lightre.skybatuhan.SkyBatuhan;
import com.lightre.skybatuhan.util.FarmData;
import net.fabricmc.loader.api.FabricLoader;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.nio.file.Path;
import java.util.ArrayList;

public class PointConfigManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_DIR = FabricLoader.getInstance().getConfigDir().resolve("skybatuhan");
    private static final File POINTS_FILE = CONFIG_DIR.resolve("points.json").toFile();

    public static FarmData data = new FarmData();

    public static void load() {
        if (!POINTS_FILE.exists()) {
            save();
            return;
        }
        try (FileReader reader = new FileReader(POINTS_FILE)) {
            data = GSON.fromJson(reader, FarmData.class);
            if (data == null) data = new FarmData();
            if (data.waypoints == null) data.waypoints = new ArrayList<>();
        } catch (Exception e) {
            SkyBatuhan.LOGGER.error("Could not load points.json", e);
        }
    }

    public static void save() {
        try {
            if (!CONFIG_DIR.toFile().exists()) CONFIG_DIR.toFile().mkdirs();
            try (FileWriter writer = new FileWriter(POINTS_FILE)) {
                GSON.toJson(data, writer);
            }
        } catch (Exception e) {
            SkyBatuhan.LOGGER.error("Could not save points.json", e);
        }
    }
}