package com.lightre.skybatuhan.hud.elements;

import com.lightre.skybatuhan.hud.HudAnchor;
import com.lightre.skybatuhan.hud.HudElement;
import com.lightre.skybatuhan.hud.HudFormat;
import com.lightre.skybatuhan.manager.ConfigManager;

import java.util.List;

/**
 * Time since the game was opened.
 */
public class SessionElement extends HudElement {
    private static final long STARTED_AT = System.currentTimeMillis();

    public SessionElement() {
        super("session", "Session Time", HudAnchor.TOP_LEFT, 4, 4);
    }

    @Override
    public boolean isEnabled() {
        return ConfigManager.config.hud.session.enabled;
    }

    @Override
    public boolean isActive() {
        return true;
    }

    @Override
    public List<String> buildLines() {
        return List.of("§6Session: §f" + HudFormat.duration(System.currentTimeMillis() - STARTED_AT));
    }

    @Override
    public List<String> previewLines() {
        return List.of("§6Session: §f1h 05m 12s");
    }
}