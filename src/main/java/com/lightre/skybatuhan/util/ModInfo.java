package com.lightre.skybatuhan.util;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.metadata.CustomValue;

public final class ModInfo {
    private ModInfo() {}

    public static String getVersion() {
        return getModVersion("skybatuhan");
    }

    public static String getMinecraftVersion() {
        return getModVersion("minecraft");
    }

    public static String getLoaderVersion() {
        return getModVersion("fabricloader");
    }

    public static String getFabricApiVersion() {
        return getModVersion("fabric-api");
    }

    public static String getMoulConfigVersion() {
        return FabricLoader.getInstance()
                .getModContainer("skybatuhan")
                .map(c -> c.getMetadata().getCustomValue("skybatuhan:moulconfig_version"))
                .map(CustomValue::getAsString)
                .orElse("?");
    }

    public static String getJavaVersion() {
        return String.valueOf(Runtime.version().feature());
    }

    public static String getModVersion(String modId) {
        return FabricLoader.getInstance()
                .getModContainer(modId)
                .map(c -> c.getMetadata().getVersion().getFriendlyString())
                .orElse("?");
    }
}