package com.lightre.skybatuhan.hud;

import java.util.List;

/**
 * One box on the screen. Adding a box means writing one subclass and registering it in HudManager.
 */
public abstract class HudElement {
    private final String id;
    private final String displayName;
    private final HudLayout defaultLayout;

    protected HudElement(String id, String displayName, HudAnchor anchor, int offsetX, int offsetY) {
        this.id = id;
        this.displayName = displayName;
        this.defaultLayout = new HudLayout(anchor, offsetX, offsetY);
    }

    /**
     * Stable key for the saved position. Never rename it once released.
     */
    public final String getId() {
        return id;
    }

    public final String getDisplayName() {
        return displayName;
    }

    public final HudLayout createDefaultLayout() {
        return defaultLayout.copy();
    }

    /**
     * The on/off switch from the menu.
     */
    public abstract boolean isEnabled();

    /**
     * Runtime condition, e.g. "only while the feature runs". The position editor ignores it.
     */
    public abstract boolean isActive();

    /**
     * The text lines, color codes allowed. Called about once a second, not every frame.
     */
    public abstract List<String> buildLines();

    /**
     * What the editor shows while buildLines has nothing to show.
     */
    public List<String> previewLines() {
        return List.of("§7" + displayName);
    }
}