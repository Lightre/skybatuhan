package com.lightre.skybatuhan.manager;

import com.lightre.skybatuhan.SkyBatuhan;
import com.lightre.skybatuhan.base.Feature;
import com.lightre.skybatuhan.util.Alerts;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;

import java.util.concurrent.ThreadLocalRandom;
import java.util.Locale;

/**
 * Every automatic stop (stuck, AFK timeout, ...) goes through here: alarm, chat, Discord
 * and, if enabled, leaving the server after a short random wait. Stopping by hand never does.
 */
public final class SafetyManager {
    private static final long LEAVE_MIN_MS = 2000L;
    private static final long LEAVE_MAX_MS = 5000L;
    private static final long HOME_TELEPORT_GRACE_MS = 5000L;
    private static final double HOME_ARRIVAL_RADIUS = 3.0;

    private static long leaveAt = 0L;
    private static String leaveReason = "";
    private static ClientPacketListener leaveSession = null; // the connection we planned to leave
    private static Vec3 lastPos = null;
    private static ClientLevel lastLevel = null;

    private SafetyManager() {
    }

    public static void onTick(Minecraft client) {
        handlePendingLeave(client, System.currentTimeMillis());
        checkRunLimits(client);
        checkGuards(client);
    }

    private static void checkGuards(Minecraft client) {
        var farm = ModuleManager.getFarmFeature();
        var fish = ModuleManager.getFishFeature();

        // Nothing is running: nothing to protect, and the next start must begin fresh
        if (!farm.isEnabled() && !fish.isEnabled()) {
            lastPos = null;
            lastLevel = null;
            return;
        }

        var player = client.player;
        if (player == null || client.level == null) return;

        var cfg = ConfigManager.config.guard;

        if (player.isDeadOrDying()) {
            trigger(client, "you died");
            return;
        }

        if (cfg.healthPercent > 0) {
            float max = player.getMaxHealth();
            if (max > 0f) {
                float percent = player.getHealth() / max * 100f;
                if (percent < cfg.healthPercent) {
                    trigger(client, String.format(Locale.ROOT, "health is at %.0f%% (limit %d%%)", percent, cfg.healthPercent));
                    return;
                }
            }
        }

        Vec3 pos = new Vec3(player.getX(), player.getY(), player.getZ());

        if (client.level != lastLevel) {
            // New world: the position jump belongs to the world change, the session monitor handles that
            lastLevel = client.level;
            lastPos = pos;
            return;
        }

        Vec3 previous = lastPos;
        lastPos = pos;

        if (cfg.teleportDistance > 0 && previous != null) {
            double moved = pos.distanceTo(previous);
            var home = PointConfigManager.data.homePoint;
            boolean ownHome = System.currentTimeMillis() - farm.getLastHomeCommandAt() < HOME_TELEPORT_GRACE_MS || (home != null && home.distanceTo(pos) < HOME_ARRIVAL_RADIUS);

            if (moved > cfg.teleportDistance && !ownHome) {
                trigger(client, String.format(Locale.ROOT, "you were moved %.0f blocks in one tick", moved));
            }
        }
    }

    /**
     * A guard rule fired: stop both features and handle it like any other automatic stop.
     */
    private static void trigger(Minecraft client, String reason) {
        var farm = ModuleManager.getFarmFeature();
        var fish = ModuleManager.getFishFeature();

        if (farm.isEnabled()) farm.setState(client, false);
        if (fish.isEnabled()) fish.setState(client, false);
        farm.resetRunTime();
        fish.resetRunTime();

        announce(client, "SkyBatuhan", reason, true);
    }

    /**
     * A feature stopped by itself: switch it off, tell the player and maybe leave the server.
     *
     * @param allowLeave false when something else decides what happens next (e.g. the session monitor)
     */
    public static void autoStop(Minecraft client, Feature feature, String reason, boolean allowLeave) {
        if (feature.isEnabled()) feature.setState(client, false);
        feature.resetRunTime();
        announce(client, feature.getName(), reason, allowLeave);
    }

    private static void announce(Minecraft client, String who, String reason, boolean allowLeave) {
        boolean leave = allowLeave && canLeave(client);

        SkyBatuhan.LOGGER.warn("[Safety] {} stopped: {}", who, reason);
        Alerts.warning(client);
        Alerts.flashTaskbar(client);

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

    private static void checkRunLimits(Minecraft client) {
        var config = ConfigManager.config;

        checkRunLimit(client, ModuleManager.getFarmFeature(), config.farming.safety.stopAfterEnabled, config.farming.safety.stopAfterMinutes);
        checkRunLimit(client, ModuleManager.getFishFeature(), config.fishing.safety.stopAfterEnabled, config.fishing.safety.stopAfterMinutes);
    }

    private static void checkRunLimit(Minecraft client, Feature feature, boolean timerOn, int minutes) {
        if (!timerOn || minutes <= 0 || !feature.isEnabled()) return;
        if (feature.getRunTimeMs() < minutes * 60_000L) return;

        autoStop(client, feature, "time limit of " + minutes + " min reached", true);
    }
}