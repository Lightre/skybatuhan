package com.lightre.skybatuhan.features;

import com.lightre.skybatuhan.base.Feature;
import com.lightre.skybatuhan.manager.ConfigManager;
import com.lightre.skybatuhan.base.ModConfig;
// import com.lightre.skybatuhan.manager.ModuleManager;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Items;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.network.chat.Component;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ThreadLocalRandom;

public class AutoFishFeature extends Feature {
    private static final ScheduledExecutorService threadScheduler = Executors.newScheduledThreadPool(1);

    private long lastHookTime = 0;

    private long lastSkyblockClickTime = 0;

    public AutoFishFeature() {
        super("Auto Fish");
    }

    public void onFishHooked(Minecraft client) {
        if (!this.isEnabled() || client.player == null || client.gameMode == null) return;

        System.out.println("[Client-AntiCheat-Safe] Fish hooked for local player!");

        lastHookTime = System.currentTimeMillis();

        ModConfig.FishingCategory fishConfig = ConfigManager.config.fishing;

        long minReel = (long) fishConfig.minReelDelay;
        long maxReel = (long) fishConfig.maxReelDelay;
        long minCast = (long) fishConfig.minCastDelay;
        long maxCast = (long) fishConfig.maxCastDelay;

        long actualMinReel = Math.min(minReel, maxReel);
        long actualMaxReel = Math.max(minReel, maxReel);
        long actualMinCast = Math.min(minCast, maxCast);
        long actualMaxCast = Math.max(minCast, maxCast);

        long reelDelay = ThreadLocalRandom.current().nextLong(actualMinReel, actualMaxReel + 1);
        System.out.println("[AutoFish] Reel-in scheduled with menu delay: " + reelDelay + "ms");

        threadScheduler.schedule(() -> {
            if (client.player != null && client.gameMode != null) {
                client.execute(() -> {
                    if (!this.isEnabled()) return;

                    var player = client.player;
                    var gameMode = client.gameMode;
                    if (player == null || gameMode == null) return;

                    InteractionHand fishingHand = InteractionHand.MAIN_HAND;
                    if (player.getOffhandItem().is(Items.FISHING_ROD)) {
                        fishingHand = InteractionHand.OFF_HAND;
                    }

                    player.input.makeJump();
                    gameMode.useItem(player, fishingHand);
                    player.swing(fishingHand);
                    System.out.println("[AutoFish] Organic Reel-in action executed successfully.");

                    long rawCastDelay = ThreadLocalRandom.current().nextLong(actualMinCast, actualMaxCast + 1);
                    long castDelay = Math.max(400, rawCastDelay);
                    System.out.println("[AutoFish] Recast scheduled with safety-adjusted delay: " + castDelay + "ms");

                    final InteractionHand finalHand = fishingHand;

                    threadScheduler.schedule(() -> {
                        if (client.player != null && client.gameMode != null) {
                            client.execute(() -> {
                                if (!this.isEnabled()) return;

                                var p2 = client.player;
                                var gm2 = client.gameMode;
                                if (p2 == null || gm2 == null) return;

                                gm2.useItem(p2, finalHand);
                                p2.swing(finalHand);
                                System.out.println("[AutoFish] Organic Recast action executed successfully. Loop continues!");

                                lastSkyblockClickTime = System.currentTimeMillis();
                            });
                        }
                    }, castDelay, TimeUnit.MILLISECONDS);
                });
            }
        }, reelDelay, TimeUnit.MILLISECONDS);
    }

    @Override
    public void onTick(Minecraft client) {
        if (client.player == null || client.level == null || !this.isEnabled()) return;

        String currentMode = ConfigManager.config.fishing.fishMode;

        // ================= SKYBLOCK MODE =================
        if ("Skyblock".equalsIgnoreCase(currentMode)) {
            long now = System.currentTimeMillis();
            if (now - lastSkyblockClickTime > 3000) {
                for (Entity entity : client.level.entitiesForRendering()) {
                    if (entity instanceof ArmorStand || entity.getType().toString().contains("armor_stand")) {
                        if (entity.hasCustomName() && entity.getCustomName() != null) {
                            String nameString = entity.getCustomName().getString();
                            if (nameString.contains("!") || nameString.contains("§c!")) {
                                if (client.player.distanceToSqr(entity) < 144.0) {
                                    System.out.println("[AutoFish] Skyblock ArmorStand '!' detected! Triggering organic loop...");
                                    lastSkyblockClickTime = now;
                                    onFishHooked(client);
                                    break;
                                }
                            }
                        }
                    }
                }
            }
        }

        // ================= AFK / LAG TIME OUT PROTECTION =================
        long timeoutMs = (long) (ConfigManager.config.fishing.afkTimeoutSeconds * 1000);

        if (lastHookTime > 0 && (System.currentTimeMillis() - lastHookTime > timeoutMs)) {

            client.player.sendSystemMessage(Component.literal("§c§l[WARNING] §fSystem stopped! AFK/Lag safety timeout triggered."));

            playSafetyAlarm(client);
            this.toggle(client);
        }
    }

    @Override
    public void onToggle(Minecraft client, boolean state) {
        if (state) {
            lastHookTime = System.currentTimeMillis();
            lastSkyblockClickTime = 0;
        }
    }

    private void playSafetyAlarm(Minecraft client) {
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
}