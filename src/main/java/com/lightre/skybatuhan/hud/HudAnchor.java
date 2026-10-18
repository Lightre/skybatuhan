package com.lightre.skybatuhan.hud;

/**
 * The nine points of the screen a box can be tied to. fx/fy: 0 = left/top, 0.5 = middle, 1 = right/bottom.
 */
public enum HudAnchor {
    TOP_LEFT(0f, 0f), TOP_CENTER(0.5f, 0f), TOP_RIGHT(1f, 0f),
    MIDDLE_LEFT(0f, 0.5f), CENTER(0.5f, 0.5f), MIDDLE_RIGHT(1f, 0.5f),
    BOTTOM_LEFT(0f, 1f), BOTTOM_CENTER(0.5f, 1f), BOTTOM_RIGHT(1f, 1f);

    public final float fx;
    public final float fy;

    HudAnchor(float fx, float fy) {
        this.fx = fx;
        this.fy = fy;
    }

    /**
     * The anchor whose third of the screen contains the point. Both values are fractions (0..1) of the screen.
     */
    public static HudAnchor nearest(float px, float py) {
        int column = px < 1f / 3f ? 0 : (px < 2f / 3f ? 1 : 2);
        int row = py < 1f / 3f ? 0 : (py < 2f / 3f ? 1 : 2);
        return values()[row * 3 + column];
    }
}