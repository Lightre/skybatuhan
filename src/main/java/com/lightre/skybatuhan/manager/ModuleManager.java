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

import java.util.ArrayList;
import java.util.List;

public class ModuleManager {
    private static final List<Feature> features = new ArrayList<>();
    private static final AutoFarmFeature farmFeature = new AutoFarmFeature();
    private static final AutoFishFeature fishFeature = new AutoFishFeature();

    private static final KeyMapping.Category MAIN_CATEGORY = KeyMapping.Category.register(
            Identifier.fromNamespaceAndPath(SkyBatuhan.MOD_ID, "main")
    );

    private static KeyMapping addPointKey;
    private static KeyMapping setHomeKey;

    public static void init() {
        register();

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
}