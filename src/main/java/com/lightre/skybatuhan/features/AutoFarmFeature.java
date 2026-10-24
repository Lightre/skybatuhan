package com.lightre.skybatuhan.features;

import com.lightre.skybatuhan.base.Feature;
import com.lightre.skybatuhan.base.ModConfig;
import com.lightre.skybatuhan.manager.*;
import com.lightre.skybatuhan.util.FarmPoint;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;

public class AutoFarmFeature extends Feature {
    private static final long LANDING_MAX_MS = 3000L;

    private boolean isReversed = false, homeCommandDone = false, alarmTriggered = false;
    private FarmPoint lastTriggeredPoint = null;
    private Vec3 lastPos = Vec3.ZERO;
    private long lastMovementTime = 0;
    private long lastWaypointTime = 0;
    private volatile long lastStuckStopAt = 0L;
    private volatile long lastHomeCommandAt = 0L;
    private boolean keysHeld = false;
    private ClientLevel lastLevel = null;
    private boolean landing = false;
    private long landingStartedAt = 0L;

    public AutoFarmFeature() {
        super("Auto Farm");
    }

    /**
     * Time of the last stuck stop (the session monitor uses it to spot a failed recovery).
     */
    public long getLastStuckStopAt() {
        return lastStuckStopAt;
    }

    /**
     * Time of the last /home sent by Auto Farm (the safety guard ignores that teleport).
     */
    public long getLastHomeCommandAt() {
        return lastHomeCommandAt;
    }

    /**
     * Called before resuming after a recovery: the player is back at the start.
     */
    public void resetDirection() {
        isReversed = false;
        lastTriggeredPoint = null;
    }

    /**
     * Hypixel sometimes spawns the player in the air after a world change: sneak until on the ground (max 3s).
     */
    public void startLanding() {
        landing = true;
        landingStartedAt = System.currentTimeMillis();
    }

    @Override
    public void onTick(Minecraft client) {
        if (client.player == null || client.level == null) return;
        if (!this.isEnabled()) return;

        long now = System.currentTimeMillis();
        Vec3 currentPos = new Vec3(client.player.getX(), client.player.getY(), client.player.getZ());

        if (client.level != lastLevel) {
            lastLevel = client.level;
            lastPos = currentPos;
            lastMovementTime = now;
            lastTriggeredPoint = null;
            homeCommandDone = false;
        }

        if (landing) {
            if (client.player.onGround() || now - landingStartedAt > LANDING_MAX_MS) {
                client.options.keyShift.setDown(false);
                landing = false;
            } else {
                client.options.keyShift.setDown(true);
            }
            // The stuck timer starts after landing
            lastPos = currentPos;
            lastMovementTime = now;
            return;
        }

        handleSafety(client, currentPos, now);

        if (!alarmTriggered) {
            applyMovement(client);
            checkPoints(client, currentPos);
        }
    }

    private void handleSafety(Minecraft client, Vec3 currentPos, long now) {
        if (client.player == null) return;

        if (hasNoMovement(currentMovement())) {
            lastMovementTime = now;
            lastPos = currentPos;
            return;
        }

        if (currentPos.distanceTo(lastPos) > ConfigManager.config.farming.safety.threshold) {
            lastMovementTime = now;
            lastPos = currentPos;
        }

        if (now - lastMovementTime > ConfigManager.config.farming.safety.timeoutMs && !alarmTriggered) {
            alarmTriggered = true;
            lastStuckStopAt = now;
            SafetyManager.autoStop(client, this, "stuck detected", !SessionMonitor.isWatchingResume());
        }
    }

    private void checkPoints(Minecraft client, Vec3 currentPos) {
        var player = client.player;
        if (player == null) return;

        var data = PointConfigManager.data;
        boolean nearTurn = false;

        for (FarmPoint point : data.waypoints) {
            if (point.distanceTo(currentPos) < ConfigManager.config.farming.general.pointRange) {
                nearTurn = true;
                if (lastTriggeredPoint == null || !lastTriggeredPoint.equals(point)) {
                    isReversed = !isReversed;
                    lastTriggeredPoint = point;
                    player.playSound(SoundEvents.NOTE_BLOCK_HAT.value(), 1.0f, 1.0f);
                }
                break;
            }
        }

        if (!nearTurn) lastTriggeredPoint = null;

        if (data.homePoint != null && data.homePoint.distanceTo(currentPos) < ConfigManager.config.farming.general.pointRange) {
            if (!homeCommandDone) {
                if (client.getConnection() != null) {
                    client.getConnection().sendCommand("home");
                    lastHomeCommandAt = System.currentTimeMillis();
                }
                homeCommandDone = true;
                isReversed = false;
                player.playSound(SoundEvents.NOTE_BLOCK_BELL.value(), 1.0f, 1.0f);
            }
        } else if (data.homePoint != null && data.homePoint.distanceTo(currentPos) >= ConfigManager.config.farming.general.pointRange) {
            homeCommandDone = false;
        }
    }

    public void addWaypoint(Minecraft client) {
        if (client.player == null) return;

        long currentTime = System.currentTimeMillis();
        if (currentTime - lastWaypointTime < 1000) return;

        lastWaypointTime = currentTime;

        FarmPoint p = new FarmPoint(client.player.getX(), client.player.getY(), client.player.getZ());
        PointConfigManager.data.waypoints.add(p);
        PointConfigManager.save();

        client.player.sendSystemMessage(Component.literal("§b[SkyBatuhan] §fTurning point saved."));
        client.player.playSound(SoundEvents.NOTE_BLOCK_CHIME.value(), 1.0f, 2.0f);
    }

    public void setHomePoint(Minecraft client) {
        if (client.player == null) return;

        PointConfigManager.data.homePoint = new FarmPoint(client.player.getX(), client.player.getY(), client.player.getZ());
        PointConfigManager.save();

        client.player.sendSystemMessage(Component.literal("§d[SkyBatuhan] §fEnd Point (/Home) set."));
        client.player.playSound(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
    }

    private ModConfig.FarmingCategory.MoveSettings currentMovement() {
        return isReversed ? ConfigManager.config.farming.farmingMovements.secondMove : ConfigManager.config.farming.farmingMovements.firstMove;
    }

    private boolean hasNoMovement(ModConfig.FarmingCategory.MoveSettings m) {
        return !m.forward && !m.left && !m.back && !m.right;
    }

    private void applyMovement(Minecraft client) {
        var movement = currentMovement();

        if (hasNoMovement(movement)) {
            if (keysHeld) resetMovement(client);
            return;
        }

        client.options.keyAttack.setDown(ConfigManager.config.farming.general.attackEnabled);

        client.options.keyUp.setDown(movement.forward);
        client.options.keyLeft.setDown(movement.left);
        client.options.keyDown.setDown(movement.back);
        client.options.keyRight.setDown(movement.right);

        keysHeld = true;
    }

    private void resetMovement(Minecraft client) {
        client.options.keyAttack.setDown(false);
        client.options.keyUp.setDown(false);
        client.options.keyLeft.setDown(false);
        client.options.keyDown.setDown(false);
        client.options.keyRight.setDown(false);
        keysHeld = false;
    }

    @Override
    public void onToggle(Minecraft client, boolean state) {
        if (state && client.player != null) {
            lastPos = new Vec3(client.player.getX(), client.player.getY(), client.player.getZ());
            lastMovementTime = System.currentTimeMillis();
            alarmTriggered = false;
            homeCommandDone = false;
            landing = false;
        } else {
            if (!ConfigManager.config.farming.autoFarmEnabled) {
                PestFarmingFeature.onManualAutoFarmStop(client);
            }
            if (landing) {
                client.options.keyShift.setDown(false);
                landing = false;
            }
            resetMovement(client);
            lastLevel = null;
        }
    }

    /**
     * Removes the waypoint with the given 1-based ID. The IDs of the points after it shift down by one.
     */
    public FarmPoint removeWaypoint(int id) {
        var waypoints = PointConfigManager.data.waypoints;
        if (id < 1 || id > waypoints.size()) return null;

        FarmPoint removed = waypoints.remove(id - 1);
        if (removed == lastTriggeredPoint) lastTriggeredPoint = null;
        PointConfigManager.save();
        return removed;
    }

    public boolean undoLastPoint() {
        var waypoints = PointConfigManager.data.waypoints;
        if (waypoints.isEmpty()) return false;

        FarmPoint removed = waypoints.removeLast();
        if (removed == lastTriggeredPoint) lastTriggeredPoint = null;
        PointConfigManager.save();
        return true;
    }

    public void clearAll() {
        PointConfigManager.data.waypoints.clear();
        PointConfigManager.data.homePoint = null;
        lastTriggeredPoint = null;
        PointConfigManager.save();
    }
}
