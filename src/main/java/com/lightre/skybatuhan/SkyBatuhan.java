package com.lightre.skybatuhan;

import com.lightre.skybatuhan.hud.HudManager;
import com.lightre.skybatuhan.manager.*;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SkyBatuhan implements ClientModInitializer {
    public static final String MOD_ID = "skybatuhan";
    public static final Logger LOGGER = LoggerFactory.getLogger("SkyBatuhan");

    @Override
    public void onInitializeClient() {
        ConfigManager.load();
        FarmSessionHistory.load();
        PointConfigManager.load();
        ModuleManager.init();
        CommandManager.init();
        SessionMonitor.init();
        UpdateManager.init();
        HudManager.init();

        ClientTickEvents.END_CLIENT_TICK.register(ModuleManager::onTick);
        ClientLifecycleEvents.CLIENT_STOPPING.register(_ -> {
            FarmSessionHistory.finishOnGameClose();
            ConfigManager.save();
        });
        ClientTickEvents.END_CLIENT_TICK.register(_ -> FarmSessionHistory.onTick());
    }
}
