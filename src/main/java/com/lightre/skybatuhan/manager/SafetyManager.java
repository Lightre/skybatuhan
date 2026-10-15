package com.lightre.skybatuhan.manager;

import com.lightre.skybatuhan.SkyBatuhan;
import com.lightre.skybatuhan.base.Feature;
import com.lightre.skybatuhan.util.Alerts;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.chat.Component;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Every automatic stop (stuck, AFK timeout, ...) goes through here: alarm, chat, Discord
 * and, if enabled, leaving the server after a short random wait. Stopping by hand never does.
 */
public final class SafetyManager {
    private static final long LEAVE_MIN_MS = 2000L;
    private static final long LEAVE_MAX_MS = 5000L;

    private static long leaveAt = 0L;
    private static String leaveReason = "";
    private static ClientPacketListener leaveSession = null; // the connection we planned to leave

    private SafetyManager() {
    }

    public static void onTick(Minecraft client) {
        handlePendingLeave(client, System.currentTimeMillis());
    }

    /**
     * A feature stopped by itself: switch it off, tell the player and maybe leave the server.
     *
     * @param allowLeave false when something else decides what happens next (e.g. the session monitor)
     */
    public static void autoStop(Minecraft client, Feature feature, String reason, boolean allowLeave) {
        if (feature.isEnabled()) feature.setState(client, false);
        announce(client, feature.getName(), reason, allowLeave);
    }

    private static void announce(Minecraft client, String who, String reason, boolean allowLeave) {
        boolean leave = allowLeave && canLeave(client);

        SkyBatuhan.LOGGER.warn("[Safety] {} stopped: {}", who, reason);
        Alerts.warning(client);

        if (client.player != null) {
            client.player.sendSystemMessage(Component.literal("§c§l[WARNING] §f" + who + " stopped: " + reason + (leave ? ". Leaving the server soon." : ".")));
        }

        Webhook.notifyIfEnabled("**" + who + " stopped**: " + reason + (leave ? "\nLeaving the server in a few seconds." : ""));

        if (leave) scheduleLeave(client, reason);
    }

    private static boolean canLeave(Minecraft client) {
        return ConfigManager.config.guard.leaveOnStop && !SessionMonitor.isRecovering() && client.getCurrentServer() != null // singleplayer is never left
                && client.getConnection() != null;
    }

    private static void scheduleLeave(Minecraft client, String reason) {
        if (leaveAt != 0L) return; // already leaving

        leaveAt = System.currentTimeMillis() + ThreadLocalRandom.current().nextLong(LEAVE_MIN_MS, LEAVE_MAX_MS + 1);
        leaveReason = reason;
        leaveSession = client.getConnection();
    }

    private static void handlePendingLeave(Minecraft client, long now) {
        if (leaveAt == 0L) return;

        ClientPacketListener listener = client.getConnection();
        if (listener == null || listener != leaveSession) {
            clearLeave(); // the player already left or joined somewhere else
            return;
        }
        if (now < leaveAt) return;

        String reason = leaveReason;
        clearLeave();
        SkyBatuhan.LOGGER.info("[Safety] Leaving the server: {}", reason);
        listener.getConnection().disconnect(Component.literal("SkyBatuhan safety stop: " + reason));
    }

    private static void clearLeave() {
        leaveAt = 0L;
        leaveReason = "";
        leaveSession = null;
    }
}