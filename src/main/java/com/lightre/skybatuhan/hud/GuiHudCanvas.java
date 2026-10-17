package com.lightre.skybatuhan.hud;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * The only class that touches the game's drawing code.
 */
public class GuiHudCanvas implements HudCanvas {
    private final GuiGraphicsExtractor graphics;
    private final Font font;

    public GuiHudCanvas(GuiGraphicsExtractor graphics, Font font) {
        this.graphics = graphics;
        this.font = font;
    }

    @Override
    public int textWidth(String text) {
        return font.width(text);
    }

    @Override
    public int lineHeight() {
        return font.lineHeight;
    }

    @Override
    public void fill(int x1, int y1, int x2, int y2, int argb) {
        graphics.fill(x1, y1, x2, y2, argb);
    }

    @Override
    public void text(String text, int x, int y, int argb, boolean shadow) {
        graphics.text(font, text, x, y, argb, shadow);
    }
}