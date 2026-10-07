package com.lightre.skybatuhan.manager;

import com.lightre.skybatuhan.SkyBatuhan;
import com.lightre.skybatuhan.base.Feature;
import com.lightre.skybatuhan.features.AutoFarmFeature;
import com.lightre.skybatuhan.features.AutoFishFeature;
import com.mojang.blaze3d.platform.InputConstants;
import io.github.notenoughupdates.moulconfig.common.IMinecraft;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

public class ModuleManager {

    private static final KeyMapping.Category MAIN_CATEGORY = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(SkyBatuhan.MOD_ID, "main"));

    private static final AutoFarmFeature farmFeature = new AutoFarmFeature();
    private static final AutoFishFeature fishFeature = new AutoFishFeature();

    private static final List<Feature> features = new ArrayList<>();
    private static final List<ConfigBinding> bindings = new ArrayList<>();
    private static final Set<KeyMapping> heldKeys = new HashSet<>();

    private static KeyMapping menuKey;
    private static KeyMapping addPointKey;
    private static KeyMapping setHomeKey;
    private static boolean menuOpen = false;

    public static void init() {
        registerFeature(farmFeature, "autofarm", GLFW.GLFW_KEY_CAPS_LOCK);
        registerFeature(fishFeature, "autofish", GLFW.GLFW_KEY_UNKNOWN);

        bindings.add(new ConfigBinding(() -> ConfigManager.config.farming.autoFarmEnabled, value -> ConfigManager.config.farming.autoFarmEnabled = value, farmFeature::isEnabled, farmFeature::setState));
        bindings.add(new ConfigBinding(() -> ConfigManager.config.fishing.autoFishEnabled, value -> ConfigManager.config.fishing.autoFishEnabled = value, fishFeature::isEnabled, fishFeature::setState));

        menuKey = registerKey("menu", GLFW.GLFW_KEY_RIGHT_SHIFT);
        addPointKey = registerKey("addpoint", GLFW.GLFW_KEY_UNKNOWN);
        setHomeKey = registerKey("sethome", GLFW.GLFW_KEY_UNKNOWN);
    }

    public static void onTick(Minecraft client) {
        if (client.player == null) return;

        handleMenu(client);
        bindings.forEach(binding -> binding.sync(client));

        for (Feature feature : features) {
            if (wasPressed(feature.getKeyBinding())) feature.toggle(client);
            if (feature.isEnabled()) feature.onTick(client);
        }

        if (wasPressed(addPointKey)) farmFeature.addWaypoint(client);
        if (wasPressed(setHomeKey)) farmFeature.setHomePoint(client);
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

    private static void handleMenu(Minecraft client) {
        if (wasPressed(menuKey)) openMenu();

        if (menuOpen && client.gui.screen() == null) {
            menuOpen = false;
            ConfigManager.save();
        }
    }

    private static void registerFeature(Feature feature, String name, int defaultKey) {
        feature.setKeyBinding(registerKey(name, defaultKey));
        features.add(feature);
    }

    private static KeyMapping registerKey(String name, int defaultKey) {
        return KeyMappingHelper.registerKeyMapping(new KeyMapping("key.skybatuhan." + name, InputConstants.Type.KEYSYM, defaultKey, MAIN_CATEGORY));
    }

    /**
     * Returns true only on the tick the key goes from released to pressed,
     * so holding the key does not retrigger the action.
     */
    private static boolean wasPressed(KeyMapping key) {
        discardQueuedClicks(key);

        if (!key.isDown()) {
            heldKeys.remove(key);
            return false;
        }
        return heldKeys.add(key);
    }

    private static void discardQueuedClicks(KeyMapping key) {
        // noinspection StatementWithEmptyBody
        while (key.consumeClick()) ;
    }

    /**
     * Keeps a feature's runtime state and its config flag in sync.
     * Config changes drive the feature; feature changes (e.g., via keybind) are written back to config.
     */
    private static final class ConfigBinding {
        private final BooleanSupplier configValue;
        private final Consumer<Boolean> configWriter;
        private final BooleanSupplier featureEnabled;
        private final BiConsumer<Minecraft, Boolean> featureSetter;
        private boolean lastConfigValue = false;

        private ConfigBinding(BooleanSupplier configValue, Consumer<Boolean> configWriter, BooleanSupplier featureEnabled, BiConsumer<Minecraft, Boolean> featureSetter) {
            this.configValue = configValue;
            this.configWriter = configWriter;
            this.featureEnabled = featureEnabled;
            this.featureSetter = featureSetter;
        }

        private void sync(Minecraft client) {
            boolean config = configValue.getAsBoolean();

            if (config != lastConfigValue) {
                lastConfigValue = config;
                featureSetter.accept(client, config);
            } else if (featureEnabled.getAsBoolean() != config) {
                boolean enabled = featureEnabled.getAsBoolean();
                configWriter.accept(enabled);
                lastConfigValue = enabled;
            }
        }
    }
}