package com.lightre.skybatuhan.hud.elements;

import com.lightre.skybatuhan.base.ModConfig;
import com.lightre.skybatuhan.hud.HudAnchor;
import com.lightre.skybatuhan.manager.ConfigManager;
import com.lightre.skybatuhan.manager.ModuleManager;
import com.lightre.skybatuhan.manager.SessionMonitor;

import java.util.List;

public class FarmElement extends FeatureStatusElement {
    public FarmElement() {
        super("farm", ModuleManager.getFarmFeature(), HudAnchor.TOP_LEFT, 4, 24);
    }

    @Override
    protected ModConfig.HudCategory.RunningBoxSettings settings() {
        return ConfigManager.config.hud.farm;
    }

    @Override
    protected boolean timerEnabled() {
        return ConfigManager.config.farming.safety.stopAfterEnabled;
    }

    @Override
    protected int timerMinutes() {
        return ConfigManager.config.farming.safety.stopAfterMinutes;
    }

    @Override
    protected boolean needsDisplay() {
        return SessionMonitor.isRecovering();
    }

    @Override
    protected void addExtraLines(List<String> lines) {
        String status = SessionMonitor.getStatusText();
        if (status != null) lines.add("§7Reconnect: §e" + status);
    }
}