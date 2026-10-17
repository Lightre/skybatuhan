package com.lightre.skybatuhan.hud.elements;

import com.lightre.skybatuhan.base.ModConfig;
import com.lightre.skybatuhan.hud.HudAnchor;
import com.lightre.skybatuhan.manager.ConfigManager;
import com.lightre.skybatuhan.manager.ModuleManager;

public class FishElement extends FeatureStatusElement {
    public FishElement() {
        super("fish", ModuleManager.getFishFeature(), HudAnchor.TOP_LEFT, 4, 74);
    }

    @Override
    protected ModConfig.HudCategory.RunningBoxSettings settings() {
        return ConfigManager.config.hud.fish;
    }

    @Override
    protected boolean timerEnabled() {
        return ConfigManager.config.fishing.safety.stopAfterEnabled;
    }

    @Override
    protected int timerMinutes() {
        return ConfigManager.config.fishing.safety.stopAfterMinutes;
    }
}