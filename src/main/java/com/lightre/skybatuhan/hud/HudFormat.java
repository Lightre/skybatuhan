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

    /**
     * 950, 12.5K, 1.24M or 3.10B.
     */
    public static String coins(double value) {
        double abs = Math.abs(value);
        if (abs >= 1_000_000_000d) return String.format(Locale.ROOT, "%.2fB", value / 1_000_000_000d);
        if (abs >= 1_000_000d) return String.format(Locale.ROOT, "%.2fM", value / 1_000_000d);
        if (abs >= 1_000d) return String.format(Locale.ROOT, "%.1fK", value / 1_000d);
        return String.format(Locale.ROOT, "%.0f", value);
    }
}
