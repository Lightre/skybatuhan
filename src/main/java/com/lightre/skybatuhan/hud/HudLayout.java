package com.lightre.skybatuhan.hud;

import com.google.gson.annotations.Expose;

/**
 * Where a box sits: an anchor plus a pixel offset from it. Because the box follows its anchor,
 * it stays in place when the window or GUI scale changes, and it is always kept on screen.
 */
public class HudLayout {
    @Expose
    public HudAnchor anchor = HudAnchor.TOP_LEFT;
    @Expose
    public int offsetX = 0;
    @Expose
    public int offsetY = 0;
    @Expose
    public float scale = 1.0f; // reserved for the editor's mouse wheel scaling

    public HudLayout() {
    }

    public HudLayout(HudAnchor anchor, int offsetX, int offsetY) {
        this.anchor = anchor;
        this.offsetX = offsetX;
        this.offsetY = offsetY;
    }

    public HudLayout copy() {
        HudLayout copy = new HudLayout(anchor, offsetX, offsetY);
        copy.scale = scale;
        return copy;
    }

    /**
     * Left edge of a box of this width, kept fully on screen.
     */
    public int resolveX(int screenWidth, int boxWidth) {
        int x = Math.round(anchor.fx * (screenWidth - boxWidth)) + offsetX;
        return Math.max(0, Math.clamp(screenWidth - boxWidth, 0, x));
    }

    /**
     * Top edge of a box of this height, kept fully on screen.
     */
    public int resolveY(int screenHeight, int boxHeight) {
        int y = Math.round(anchor.fy * (screenHeight - boxHeight)) + offsetY;
        return Math.max(0, Math.clamp(screenHeight - boxHeight, 0, y));
    }

    /**
     * Stores a dropped position: picks the nearest anchor and the offset from it.
     */
    public void moveTo(int x, int y, int screenWidth, int screenHeight, int boxWidth, int boxHeight) {
        if (screenWidth <= 0 || screenHeight <= 0) return;

        float centerX = (x + boxWidth / 2f) / screenWidth;
        float centerY = (y + boxHeight / 2f) / screenHeight;

        anchor = HudAnchor.nearest(centerX, centerY);
        offsetX = x - Math.round(anchor.fx * (screenWidth - boxWidth));
        offsetY = y - Math.round(anchor.fy * (screenHeight - boxHeight));
    }
}