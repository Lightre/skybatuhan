package com.lightre.skybatuhan.features;

import com.lightre.skybatuhan.base.Feature;
import com.lightre.skybatuhan.manager.ConfigManager;
import com.lightre.skybatuhan.manager.PointConfigManager;
import com.lightre.skybatuhan.util.FarmPoint;
import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;

public class AutoFarmFeature extends Feature {
    private boolean isReversed = false, homeCommandDone = false, alarmTriggered = false;
    private FarmPoint lastTriggeredPoint = null;
    private Vec3 lastPos = Vec3.ZERO;
    private long lastMovementTime = 0;
    private long lastWaypointTime = 0;
    private boolean keysHeld = false;

    public AutoFarmFeature() {
        super("Auto Farm");
    }

    @Override
    public void onTick(Minecraft client) {
        if (client.player == null || client.level == null) return;
        if (!this.isEnabled()) return;

        long now = System.currentTimeMillis();
        Vec3 currentPos = new Vec3(client.player.getX(), client.player.getY(), client.player.getZ());

        handleSafety(client, currentPos, now);

        if (!alarmTriggered) {
            applyMovement(client);
            checkPoints(client, currentPos);
        }
    }

    private void handleSafety(Minecraft client, Vec3 currentPos, long now) {
        var player = client.player;
        if (player == null) return;

        if (currentPos.distanceTo(lastPos) > ConfigManager.config.safety.threshold) {
            lastMovementTime = now;
            lastPos = currentPos;
        }

        if (now - lastMovementTime > ConfigManager.config.safety.timeoutMs) {
            if (!alarmTriggered) {
                alarmTriggered = true;
                playAlarm(client);
                player.sendSystemMessage(Component.literal("§c§l[WARNING] §fSystem stopped! Stuck was detected."));
                this.toggle(client);
                resetMovement(client);
            }
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
                }
                homeCommandDone = true;
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

    private void applyMovement(Minecraft client) {
        var movement = isReversed ? ConfigManager.config.farming.farmingMovements.secondMove : ConfigManager.config.farming.farmingMovements.firstMove;

        boolean anyMovementEnabled = movement.forward || movement.left || movement.back || movement.right;

        if (!anyMovementEnabled) {
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
        } else {
            resetMovement(client);
        }
    }

    private void playAlarm(Minecraft client) {
        new Thread(() -> {
            for (int i = 0; i < 15; i++) {
                if (client.player != null) {
                    float pitch = 1.0f + ((i % 3) * 0.2f);
                    client.execute(() -> {
                        if (client.player != null) {
                            client.player.playSound(SoundEvents.NOTE_BLOCK_PLING.value(), 2.0f, pitch);
                        }
                    });
                }
                try {
                    Thread.sleep(80);
                } catch (Exception ignored) {
                }
            }
        }).start();
    }

    public void undoLastPoint(Minecraft client) {
        if (!PointConfigManager.data.waypoints.isEmpty()) {
            PointConfigManager.data.waypoints.removeLast();
            PointConfigManager.save();
            if (client.player != null)
                client.player.sendSystemMessage(Component.literal("§e[SkyBatuhan] Last turning point removed."));
        }
    }

    public void clearAll(Minecraft client) {
        PointConfigManager.data.waypoints.clear();
        PointConfigManager.data.homePoint = null;
        PointConfigManager.save();
        if (client.player != null)
            client.player.sendSystemMessage(Component.literal("§c[SkyBatuhan] All turning points data removed."));
    }
}