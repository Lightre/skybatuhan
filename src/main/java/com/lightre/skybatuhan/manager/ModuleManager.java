package com.lightre.skybatuhan.manager;

import com.lightre.skybatuhan.SkyBatuhan;
import com.lightre.skybatuhan.base.Feature;
import com.lightre.skybatuhan.features.AutoFarmFeature;
import com.lightre.skybatuhan.features.AutoFishFeature;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;
import io.github.notenoughupdates.moulconfig.common.IMinecraft;
import com.lightre.skybatuhan.base.ModConfig;

import java.util.ArrayList;
import java.util.List;


public class ModuleManager {
    private static final List<Feature> features = new ArrayList<>();
    private static final AutoFarmFeature farmFeature = new AutoFarmFeature();
    private static final AutoFishFeature fishFeature = new AutoFishFeature();

    private static final KeyMapping.Category MAIN_CATEGORY = KeyMapping.Category.register(
            Identifier.fromNamespaceAndPath(SkyBatuhan.MOD_ID, "main")
    );

    private static boolean lastFarmCfg = false;
    private static boolean lastFishCfg = false;
    private static KeyMapping addPointKey;
    private static KeyMapping setHomeKey;
    private static KeyMapping menuKey;
    private static boolean menuOpen = false;


    public static void init() {
        register();
        menuKey = registerKey("menu", GLFW.GLFW_KEY_RIGHT_SHIFT);
        addPointKey = registerKey("addpoint", GLFW.GLFW_KEY_UNKNOWN);
        setHomeKey = registerKey("sethome", GLFW.GLFW_KEY_UNKNOWN);
    }

    private static KeyMapping registerKey(String name, int defaultKey) {
        return KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.skybatuhan." + name,
                InputConstants.Type.KEYSYM,
                defaultKey,
                MAIN_CATEGORY
        ));
    }

    public static void onTick(Minecraft client) {
        if (client.player == null) return;
        while (menuKey.consumeClick()) openMenu();
        if (menuOpen && client.gui.screen() == null) {
            menuOpen = false;
            ConfigManager.save();
        }

        ModConfig cfg = ConfigManager.config;

        if (cfg.farming.autoFarmEnabled != lastFarmCfg) {
            lastFarmCfg = cfg.farming.autoFarmEnabled;
            farmFeature.setState(client, lastFarmCfg);
        } else if (farmFeature.isEnabled() != cfg.farming.autoFarmEnabled) {
            cfg.farming.autoFarmEnabled = farmFeature.isEnabled();
            lastFarmCfg = cfg.farming.autoFarmEnabled;
        }

        if (cfg.fishing.autoFishEnabled != lastFishCfg) {
            lastFishCfg = cfg.fishing.autoFishEnabled;
            fishFeature.setState(client, lastFishCfg);
        } else if (fishFeature.isEnabled() != cfg.fishing.autoFishEnabled) {
            cfg.fishing.autoFishEnabled = fishFeature.isEnabled();
            lastFishCfg = cfg.fishing.autoFishEnabled;
        }

        for (Feature f : features) {
            while (f.getKeyBinding().consumeClick()) f.toggle(client);
            if (f.isEnabled()) f.onTick(client);
        }

        while (addPointKey.consumeClick()) farmFeature.addWaypoint(client);
        while (setHomeKey.consumeClick()) farmFeature.setHomePoint(client);
    }

    private static void register() {
        KeyMapping kbFarm = registerKey("autofarm", GLFW.GLFW_KEY_CAPS_LOCK);
        farmFeature.setKeyBinding(kbFarm);
        features.add(farmFeature);

        KeyMapping kbFish = registerKey("autofish", GLFW.GLFW_KEY_UNKNOWN);
        fishFeature.setKeyBinding(kbFish);
        features.add(fishFeature);
    }

    public static AutoFarmFeature getFarmFeature() {
        return farmFeature;
    }
    public static AutoFishFeature getFishFeature() {
        return fishFeature;
    }

    public static void openMenu() {
        if (ConfigManager.getManaged() == null) {
            SkyBatuhan.LOGGER.warn("Managed config is null, menu not opened");
            return;
        }
        Minecraft.getInstance().schedule(() -> {
            IMinecraft.INSTANCE.openWrappedScreen(ConfigManager.getManaged().getEditor());
            menuOpen = true;
        });
    }
}