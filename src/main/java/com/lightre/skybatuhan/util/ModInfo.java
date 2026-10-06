package com.lightre.skybatuhan.util;

import net.fabricmc.loader.api.FabricLoader;

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
        return getModVersion("moulconfig");
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