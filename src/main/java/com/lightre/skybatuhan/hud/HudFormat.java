package com.lightre.skybatuhan.hud;

import java.util.Locale;

public final class HudFormat {
    private HudFormat() {
    }

    /**
     * 12s, 5m 03s or 1h 05m 12s. Negative values show as 0s.
     */
    public static String duration(long ms) {
        long totalSeconds = Math.max(0L, ms) / 1000L;
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds / 60) % 60;
        long seconds = totalSeconds % 60;

        if (hours > 0) return String.format(Locale.ROOT, "%dh %02dm %02ds", hours, minutes, seconds);
        if (minutes > 0) return String.format(Locale.ROOT, "%dm %02ds", minutes, seconds);
        return seconds + "s";
    }
}