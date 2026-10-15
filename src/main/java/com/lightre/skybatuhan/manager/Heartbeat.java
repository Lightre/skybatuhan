package com.lightre.skybatuhan.manager;

import com.lightre.skybatuhan.base.Feature;
import net.minecraft.client.Minecraft;

/**
 * Sends a quiet "still running" Discord message while Auto Farm or Auto Fish is on.
 */
public final class Heartbeat {
    private static long nextAt = 0L;

    private Heartbeat() {
    }

    public static void onTick(Minecraft client) {
        var discord = ConfigManager.config.discord;
        var farm = ModuleManager.getFarmFeature();
        var fish = ModuleManager.getFishFeature();

        // Only while something runs: a fresh start always waits a full interval before the first message
        if (!discord.enabled || !discord.heartbeatEnabled || (!farm.isEnabled() && !fish.isEnabled())) {
            nextAt = 0L;
            return;
        }

        long now = System.currentTimeMillis();
        long interval = Math.max(1, discord.heartbeatMinutes) * 60_000L;

        if (nextAt == 0L) {
            nextAt = now + interval;
            return;
        }
        if (now < nextAt) return;

        nextAt = now + interval;
        Webhook.notifyIfEnabled(buildMessage(client), false);
    }

    private static String buildMessage(Minecraft client) {
        var config = ConfigManager.config;
        var server = client.getCurrentServer();

        StringBuilder text = new StringBuilder("**Still running** on `").append(server != null ? server.ip : "singleplayer").append("`");

        appendFeature(text, ModuleManager.getFarmFeature(), config.farming.safety.stopAfterEnabled, config.farming.safety.stopAfterMinutes);
        appendFeature(text, ModuleManager.getFishFeature(), config.fishing.safety.stopAfterEnabled, config.fishing.safety.stopAfterMinutes);

        var player = client.player;
        if (player != null && player.getMaxHealth() > 0f) {
            text.append("\nHealth: ").append(Math.round(player.getHealth() / player.getMaxHealth() * 100f)).append("%");
        }

        return text.toString();
    }

    private static void appendFeature(StringBuilder text, Feature feature, boolean timerOn, int limitMinutes) {
        if (!feature.isEnabled()) return;

        long ranMs = feature.getRunTimeMs();
        text.append("\n").append(feature.getName()).append(": running ").append(formatDuration(ranMs));
        if (timerOn) {
            text.append(", stops in ").append(formatDuration(Math.max(0L, limitMinutes * 60_000L - ranMs)));
        }
    }

    private static String formatDuration(long ms) {
        long totalMinutes = ms / 60_000L;
        long hours = totalMinutes / 60;
        long minutes = totalMinutes % 60;
        return hours > 0 ? hours + "h " + minutes + "m" : minutes + "m";
    }
}