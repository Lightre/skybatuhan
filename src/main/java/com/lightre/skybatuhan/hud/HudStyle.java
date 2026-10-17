package com.lightre.skybatuhan.hud;

import java.util.List;

/**
 * The one look every box shares.
 */
public final class HudStyle {
    public static final int PADDING = 3;
    public static final int LINE_SPACING = 1;
    public static final int TEXT_COLOR = 0xFFFFFFFF;
    public static final int BACKGROUND_COLOR = 0x80000000;
    public static final boolean SHADOW = true;

    private HudStyle() {
    }

    public static int width(HudCanvas canvas, List<String> lines) {
        int widest = 0;
        for (String line : lines) {
            widest = Math.max(widest, canvas.textWidth(line));
        }
        return widest + PADDING * 2;
    }

    public static int height(HudCanvas canvas, List<String> lines) {
        int count = lines.size();
        return count * canvas.lineHeight() + Math.max(0, count - 1) * LINE_SPACING + PADDING * 2;
    }

    public static void draw(HudCanvas canvas, List<String> lines, int x, int y) {
        if (lines.isEmpty()) return;

        canvas.fill(x, y, x + width(canvas, lines), y + height(canvas, lines), BACKGROUND_COLOR);

        int lineY = y + PADDING;
        for (String line : lines) {
            canvas.text(line, x + PADDING, lineY, TEXT_COLOR, SHADOW);
            lineY += canvas.lineHeight() + LINE_SPACING;
        }
    }
}