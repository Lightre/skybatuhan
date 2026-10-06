package com.lightre.skybatuhan;

import com.lightre.skybatuhan.manager.ConfigManager;
import com.lightre.skybatuhan.manager.PointConfigManager;
import com.lightre.skybatuhan.manager.ModuleManager;
import com.lightre.skybatuhan.manager.CommandManager;
import com.lightre.skybatuhan.manager.SessionMonitor;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SkyBatuhan implements ClientModInitializer {
    public static final String MOD_ID = "skybatuhan";
    public static final Logger LOGGER = LoggerFactory.getLogger("SkyBatuhan");

    @Override
    public void onInitializeClient() {
        ConfigManager.load();
        PointConfigManager.load();
        ModuleManager.init();
        CommandManager.init();
        SessionMonitor.init();

        ClientTickEvents.END_CLIENT_TICK.register(ModuleManager::onTick);
    }
}