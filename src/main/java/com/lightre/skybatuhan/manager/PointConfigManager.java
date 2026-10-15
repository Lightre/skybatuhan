package com.lightre.skybatuhan.manager;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.lightre.skybatuhan.SkyBatuhan;
import com.lightre.skybatuhan.util.FarmData;
import com.lightre.skybatuhan.util.SafeFile;
import net.fabricmc.loader.api.FabricLoader;

import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;

public class PointConfigManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path POINTS_FILE = FabricLoader.getInstance().getConfigDir().resolve("skybatuhan").resolve("points.json");

    public static FarmData data = new FarmData();

    public static void load() {
        if (!Files.exists(POINTS_FILE)) {
            save();
            return;
        }

        try (Reader reader = Files.newBufferedReader(POINTS_FILE)) {
            FarmData loaded = GSON.fromJson(reader, FarmData.class);
            if (loaded == null) throw new IllegalStateException("points.json is empty");
            if (loaded.waypoints == null) loaded.waypoints = new ArrayList<>();
            data = loaded;
        } catch (Exception e) {
            var backup = SafeFile.backupBroken(POINTS_FILE);
            SkyBatuhan.LOGGER.error("Could not read points.json, the old file was moved to {}", backup, e);
            save();
        }
    }

    public static void save() {
        try {
            SafeFile.writeAtomically(POINTS_FILE, GSON.toJson(data));
        } catch (Exception e) {
            SkyBatuhan.LOGGER.error("Could not save points.json", e);
        }
    }
}