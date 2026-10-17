package com.lightre.skybatuhan.hud;

/**
 * The only things the HUD needs from the game's drawing code. One adapter implements it.
 */
public interface HudCanvas {
    int textWidth(String text);

    int lineHeight();

    void fill(int x1, int y1, int x2, int y2, int argb);

    void text(String text, int x, int y, int argb, boolean shadow);
}