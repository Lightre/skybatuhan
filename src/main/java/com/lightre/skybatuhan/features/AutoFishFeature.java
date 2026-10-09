package com.lightre.skybatuhan.features;

import com.lightre.skybatuhan.SkyBatuhan;
import com.lightre.skybatuhan.base.Feature;
import com.lightre.skybatuhan.manager.ConfigManager;
import com.lightre.skybatuhan.base.ModConfig;
import com.lightre.skybatuhan.manager.Webhook;
import com.lightre.skybatuhan.base.enums.FishingOptions.FishMode;
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
import java.util.concurrent.atomic.AtomicInteger;

public class AutoFishFeature extends Feature {
    private static final ScheduledExecutorService threadScheduler = Executors.newScheduledThreadPool(1, r -> {
        Thread t = new Thread(r, "SkyBatuhan-Scheduler");
        t.setDaemon(true);
        return t;
    });

    private long lastHookTime = 0;
    private long lastSkyBlockClickTime = 0;
    private int jumpTicksLeft = 0;
    private static final long JUMP_LEAD_MS = 100L;
    private final AtomicInteger generation = new AtomicInteger();

    private static final long ACTION_SLOT_MIN_DELAY_MS = 25L;
    private static final long ACTION_SLOT_MAX_DELAY_MS = 75L;
    private static final long ENTITY_RECOVERY_MIN_MS = 25L;
    private static final long ENTITY_RECOVERY_MAX_MS = 75L;
    private static final double ENTITY_CHECK_RADIUS = 2.0;
    private static final long ENTITY_WAIT_MIN_MS = 1000L;
    private static final long ENTITY_WAIT_MAX_MS = 2000L;
    private static final long ENTITY_HOOK_COOLDOWN_MS = 1500L;
    private static final double SKYBLOCK_MARKER_RADIUS_SQUARED = 9.0;
    private long lastEntityHookTime = 0L;
    private int pendingRestoreSlot = -1;

    public AutoFishFeature() {
        super("Auto Fish");
    }

    @Override
    public void setState(Minecraft client, boolean state) {
        if (state && (client.player == null || !hasFishingRod(client))) {
            if (client.player != null) {
                client.player.sendSystemMessage(Component.literal("§c[AutoFish] §fHold a fishing rod to start."));
            }
            return;
        }
        super.setState(client, state);
    }

    private boolean hasFishingRod(Minecraft client) {
        if (client.player == null) return false;
        return client.player.getMainHandItem().is(Items.FISHING_ROD) || client.player.getOffhandItem().is(Items.FISHING_ROD);
    }

    public void onFishHooked(Minecraft client) {
        if (!this.isEnabled() || client.player == null || client.gameMode == null) return;

        final int gen = generation.get();

        SkyBatuhan.LOGGER.info("Fish hooked for local player!");

        lastHookTime = System.currentTimeMillis();

        ModConfig.FishingCategory fishConfig = ConfigManager.config.fishing;

        long minReel = (long) fishConfig.safety.minReelDelay;
        long maxReel = (long) fishConfig.safety.maxReelDelay;
        long minCast = (long) fishConfig.safety.minCastDelay;
        long maxCast = (long) fishConfig.safety.maxCastDelay;

        long actualMinReel = Math.min(minReel, maxReel);
        long actualMaxReel = Math.max(minReel, maxReel);
        long lowCast = Math.min(minCast, maxCast);
        long highCast = Math.max(minCast, maxCast);
        long actualMinCast = Math.max(ModConfig.FishingCategory.SafetyCategory.MIN_CAST_DELAY_MS, lowCast);
        long actualMaxCast = Math.max(ModConfig.FishingCategory.SafetyCategory.MIN_CAST_DELAY_MS, highCast);

        long reelDelay = ThreadLocalRandom.current().nextLong(actualMinReel, actualMaxReel + 1);
        SkyBatuhan.LOGGER.info("[AutoFish] Reel-in scheduled with menu delay: {}ms", reelDelay);

        long jumpOffset = Math.min(reelDelay, JUMP_LEAD_MS);
        long jumpDelay = reelDelay - jumpOffset;

        threadScheduler.schedule(() -> {
            if (client.player != null && client.gameMode != null) {
                client.execute(() -> {
                    if (!this.isEnabled() || gen != generation.get()) return;
                    if (ConfigManager.config.fishing.general.reelJump) {
                        startJump(client);
                        SkyBatuhan.LOGGER.info("[AutoFish] Pre-reel jump executed right before pulling!");
                    }
                });
            }
        }, jumpDelay, TimeUnit.MILLISECONDS);

        threadScheduler.schedule(() -> {
            if (client.player != null && client.gameMode != null) {
                client.execute(() -> {
                    if (!this.isEnabled() || gen != generation.get()) return;

                    var player = client.player;
                    var gameMode = client.gameMode;
                    if (player == null || gameMode == null) return;

                    InteractionHand fishingHand = InteractionHand.MAIN_HAND;
                    if (player.getOffhandItem().is(Items.FISHING_ROD)) {
                        fishingHand = InteractionHand.OFF_HAND;
                    }

                    gameMode.useItem(player, fishingHand);
                    player.swing(fishingHand);
                    SkyBatuhan.LOGGER.info("[AutoFish] Organic Reel-in action executed successfully.");

                    final InteractionHand finalHand = fishingHand;

                    if (shouldUseActionSlot()) {
                        scheduleActionSlot(client, finalHand, gen, actualMinCast, actualMaxCast);
                    } else {
                        scheduleRecast(client, finalHand, gen, actualMinCast, actualMaxCast);
                    }
                });
            }
        }, reelDelay, TimeUnit.MILLISECONDS);
    }

    private void scheduleRecast(Minecraft client, InteractionHand finalHand, int gen, long actualMinCast, long actualMaxCast) {
        long castDelay = ThreadLocalRandom.current().nextLong(actualMinCast, actualMaxCast + 1);
        SkyBatuhan.LOGGER.info("[AutoFish] Recast scheduled with delay: {}ms", castDelay);

        threadScheduler.schedule(() -> {
            if (client.player != null && client.gameMode != null) {
                client.execute(() -> {
                    if (!this.isEnabled() || gen != generation.get()) return;

                    var p2 = client.player;
                    var gm2 = client.gameMode;
                    if (p2 == null || gm2 == null) return;

                    gm2.useItem(p2, finalHand);
                    p2.swing(finalHand);
                    SkyBatuhan.LOGGER.info("[AutoFish] Organic Recast action executed successfully. Loop continues!");

                    lastSkyBlockClickTime = System.currentTimeMillis();
                });
            }
        }, castDelay, TimeUnit.MILLISECONDS);
    }

    // ================= ACTION SLOT =================
    private boolean shouldUseActionSlot() {
        return ConfigManager.config.fishing.general.useActionSlot && ConfigManager.config.fishing.general.actionSlot != null;
    }

    private void scheduleActionSlot(Minecraft client, InteractionHand finalHand, int gen, long actualMinCast, long actualMaxCast) {
        int targetSlot = ConfigManager.config.fishing.general.actionSlot.getHotbarIndex();
        long switchDelay = ThreadLocalRandom.current().nextLong(ACTION_SLOT_MIN_DELAY_MS, ACTION_SLOT_MAX_DELAY_MS + 1);

        threadScheduler.schedule(() -> {
            if (client.player != null && client.gameMode != null) {
                client.execute(() -> executeActionSlot(client, finalHand, gen, targetSlot, actualMinCast, actualMaxCast));
            }
        }, switchDelay, TimeUnit.MILLISECONDS);
    }

    private void executeActionSlot(Minecraft client, InteractionHand finalHand, int gen, int targetSlot, long actualMinCast, long actualMaxCast) {
        if (!this.isEnabled() || gen != generation.get()) return;

        var player = client.player;
        var gameMode = client.gameMode;
        if (player == null || gameMode == null) return;

        int originalSlot = player.getInventory().getSelectedSlot();
        if (targetSlot == originalSlot) {
            scheduleRecast(client, finalHand, gen, actualMinCast, actualMaxCast);
            return;
        }
        pendingRestoreSlot = originalSlot;
        player.getInventory().setSelectedSlot(targetSlot);

        gameMode.useItem(player, InteractionHand.MAIN_HAND);
        player.swing(InteractionHand.MAIN_HAND);
        SkyBatuhan.LOGGER.info("[AutoFish] Action slot activated");

        long returnDelay = ThreadLocalRandom.current().nextLong(ACTION_SLOT_MIN_DELAY_MS, ACTION_SLOT_MAX_DELAY_MS + 1);

        threadScheduler.schedule(() -> {
            if (client.player != null) {
                client.execute(() -> returnToRodSlot(client, finalHand, gen, originalSlot, actualMinCast, actualMaxCast));
            }
        }, returnDelay, TimeUnit.MILLISECONDS);
    }

    private void returnToRodSlot(Minecraft client, InteractionHand finalHand, int gen, int originalSlot, long actualMinCast, long actualMaxCast) {
        var player = client.player;
        if (player == null) return;

        player.getInventory().setSelectedSlot(originalSlot);
        pendingRestoreSlot = -1;

        if (!this.isEnabled() || gen != generation.get()) return;

        SkyBatuhan.LOGGER.info("[AutoFish] Returned to rod slot");
        scheduleRecast(client, finalHand, gen, actualMinCast, actualMaxCast);
    }

    @Override
    public void onTick(Minecraft client) {
        if (client.player == null || client.level == null || !this.isEnabled()) return;

        tickJump(client);
        handleEntityHookRecovery(client);

        FishMode currentMode = ConfigManager.config.fishing.general.fishMode;

        // ================= SKYBLOCK MODE =================
        if (currentMode == FishMode.SKYBLOCK && client.player.fishing != null) {
            long now = System.currentTimeMillis();

            if (now - lastSkyBlockClickTime > 3000) {
                var hook = client.player.fishing;

                for (Entity entity : client.level.entitiesForRendering()) {
                    if (entity instanceof ArmorStand || entity.getType().toString().contains("armor_stand")) {
                        if (entity.hasCustomName() && entity.getCustomName() != null) {
                            String nameString = entity.getCustomName().getString();

                            if (nameString.contains("!!!") && hook.distanceToSqr(entity) <= SKYBLOCK_MARKER_RADIUS_SQUARED) {
                                SkyBatuhan.LOGGER.info("[AutoFish] SkyBlock ArmorStand '!!!' detected near bobber! Triggering organic loop...");
                                lastSkyBlockClickTime = now;
                                onFishHooked(client);
                                break;
                            }
                        }
                    }
                }
            }
        }

        // ================= AFK / LAG TIME OUT PROTECTION =================
        long timeoutMs = (long) (ConfigManager.config.fishing.safety.afkTimeoutSeconds * 1000);

        if (lastHookTime > 0 && (System.currentTimeMillis() - lastHookTime > timeoutMs)) {
            client.player.sendSystemMessage(Component.literal("§c§l[WARNING] §fSystem stopped! AFK/Lag safety timeout triggered."));
            Webhook.notifyIfEnabled("**Auto Fish stopped**: AFK/lag timeout.");

            playSafetyAlarm(client);
            this.toggle(client);
        }
    }

    @Override
    public void onToggle(Minecraft client, boolean state) {
        int gen = generation.incrementAndGet();
        if (state) {
            lastHookTime = System.currentTimeMillis();
            lastSkyBlockClickTime = 0;
            lastEntityHookTime = 0L;
            scheduleInitialCast(client, gen);
        }
        releaseJump(client);

        if (pendingRestoreSlot >= 0 && client.player != null) {
            client.player.getInventory().setSelectedSlot(pendingRestoreSlot);
        }
        pendingRestoreSlot = -1;
    }

    // ================= ENTITY HOOK RECOVERY =================
    private boolean isEntityNotHooked(Minecraft client) {
        return client.player == null || client.player.fishing == null || client.player.fishing.getHookedIn() == null;
    }

    private void handleEntityHookRecovery(Minecraft client) {
        if (isEntityNotHooked(client)) return;

        long now = System.currentTimeMillis();
        if (now - lastEntityHookTime <= ENTITY_HOOK_COOLDOWN_MS) return;
        lastEntityHookTime = now;

        final int gen = generation.get();
        long recoveryDelay = ThreadLocalRandom.current().nextLong(ENTITY_RECOVERY_MIN_MS, ENTITY_RECOVERY_MAX_MS + 1);
        SkyBatuhan.LOGGER.info("[AutoFish] Entity hook detected, recovery in {}ms", recoveryDelay);

        threadScheduler.schedule(() -> client.execute(() -> recoverFromEntityHook(client, gen)), recoveryDelay, TimeUnit.MILLISECONDS);
    }

    private void recoverFromEntityHook(Minecraft client, int gen) {
        if (!this.isEnabled() || gen != generation.get()) return;

        var player = client.player;
        var gameMode = client.gameMode;
        if (player == null || gameMode == null) return;

        if (isEntityNotHooked(client)) return;

        lastEntityHookTime = System.currentTimeMillis();

        InteractionHand hand = player.getMainHandItem().is(Items.FISHING_ROD) ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;

        gameMode.useItem(player, hand);
        player.swing(hand);
        SkyBatuhan.LOGGER.info("[AutoFish] Recovered from entity hook");

        ModConfig.FishingCategory fishConfig = ConfigManager.config.fishing;
        long lowCast = Math.min((long) fishConfig.safety.minCastDelay, (long) fishConfig.safety.maxCastDelay);
        long highCast = Math.max((long) fishConfig.safety.minCastDelay, (long) fishConfig.safety.maxCastDelay);
        long minCast = Math.max(ModConfig.FishingCategory.SafetyCategory.MIN_CAST_DELAY_MS, lowCast);
        long maxCast = Math.max(ModConfig.FishingCategory.SafetyCategory.MIN_CAST_DELAY_MS, highCast);

        if (hasNearbyEntities(client)) {
            SkyBatuhan.LOGGER.info("[AutoFish] Entities nearby, waiting before recast...");
            long waitDelay = ThreadLocalRandom.current().nextLong(ENTITY_WAIT_MIN_MS, ENTITY_WAIT_MAX_MS + 1);
            threadScheduler.schedule(() -> client.execute(() -> {
                if (!this.isEnabled() || gen != generation.get()) return;
                scheduleRecast(client, hand, gen, minCast, maxCast);
            }), waitDelay, TimeUnit.MILLISECONDS);
        } else {
            SkyBatuhan.LOGGER.info("[AutoFish] No entities nearby, continuing...");
            scheduleRecast(client, hand, gen, minCast, maxCast);
        }
    }

    private boolean hasNearbyEntities(Minecraft client) {
        if (client.player == null || client.level == null) return false;
        double radiusSquared = ENTITY_CHECK_RADIUS * ENTITY_CHECK_RADIUS;

        for (Entity entity : client.level.entitiesForRendering()) {
            if (entity != client.player && !(entity instanceof ArmorStand)) {
                if (client.player.distanceToSqr(entity) < radiusSquared) {
                    return true;
                }
            }
        }
        return false;
    }

    // ================= INITIAL CAST =================
    private void scheduleInitialCast(Minecraft client, int gen) {
        if (client.player == null) return;

        InteractionHand rodHand = null;
        if (client.player.getMainHandItem().is(Items.FISHING_ROD)) {
            rodHand = InteractionHand.MAIN_HAND;
        } else if (client.player.getOffhandItem().is(Items.FISHING_ROD)) {
            rodHand = InteractionHand.OFF_HAND;
        }

        if (rodHand == null || client.player.fishing != null) return;

        final InteractionHand castHand = rodHand;
        long delay = ThreadLocalRandom.current().nextLong(150L, 400L);

        threadScheduler.schedule(() -> client.execute(() -> {
            if (!this.isEnabled() || gen != generation.get()) return;

            var player = client.player;
            var gameMode = client.gameMode;
            if (player == null || gameMode == null) return;
            if (player.fishing != null) return;

            gameMode.useItem(player, castHand);
            player.swing(castHand);
            SkyBatuhan.LOGGER.info("[AutoFish] Initial cast executed");
        }), delay, TimeUnit.MILLISECONDS);
    }

    private void startJump(Minecraft client) {
        client.options.keyJump.setDown(true);
        jumpTicksLeft = 2;
    }

    private void tickJump(Minecraft client) {
        if (jumpTicksLeft > 0 && --jumpTicksLeft == 0) {
            client.options.keyJump.setDown(false);
        }
    }

    private void releaseJump(Minecraft client) {
        if (jumpTicksLeft > 0) {
            client.options.keyJump.setDown(false);
        }
        jumpTicksLeft = 0;
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