package com.lightre.skybatuhan.hud.elements;

import com.lightre.skybatuhan.base.Feature;
import com.lightre.skybatuhan.base.ModConfig;
import com.lightre.skybatuhan.hud.HudAnchor;
import com.lightre.skybatuhan.hud.HudElement;
import com.lightre.skybatuhan.hud.HudFormat;

import java.util.ArrayList;
import java.util.List;

/**
 * Status box of a feature: on/off, run time and the time left on its stop timer.
 */
public abstract class FeatureStatusElement extends HudElement {
    private final Feature feature;

    protected FeatureStatusElement(String id, Feature feature, HudAnchor anchor, int offsetX, int offsetY) {
        super(id, feature.getName(), anchor, offsetX, offsetY);
        this.feature = feature;
    }

    protected abstract ModConfig.HudCategory.RunningBoxSettings settings();

    protected abstract boolean timerEnabled();

    protected abstract int timerMinutes();

    /**
     * Extra lines under the common ones.
     */
    protected void addExtraLines(List<String> lines) {
    }

    /**
     * True when the box should show even though the feature is off (e.g. while reconnecting).
     */
    protected boolean needsDisplay() {
        return false;
    }

    @Override
    public boolean isEnabled() {
        return settings().enabled;
    }

    @Override
    public boolean isActive() {
        return !settings().onlyWhileRunning || feature.isEnabled() || needsDisplay();
    }

    @Override
    public List<String> buildLines() {
        List<String> lines = new ArrayList<>();
        boolean on = feature.isEnabled();

        lines.add("§6" + feature.getName() + ": " + (on ? "§aON" : "§cOFF"));

        if (on) {
            long ranMs = feature.getRunTimeMs();
            lines.add("§7Running: §f" + HudFormat.duration(ranMs));
            if (timerEnabled()) {
                lines.add("§7Stops in: §f" + HudFormat.duration(timerMinutes() * 60_000L - ranMs));
            }
        }

        addExtraLines(lines);
        return lines;
    }

    @Override
    public List<String> previewLines() {
        return List.of("§6" + feature.getName() + ": §aON", "§7Running: §f12m 03s", "§7Stops in: §f47m 57s");
    }
}