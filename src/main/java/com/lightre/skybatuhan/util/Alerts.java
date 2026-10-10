package com.lightre.skybatuhan.util;

import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public final class Alerts {
    private static final int REPEATS = 15;
    private static final long INTERVAL_MS = 80L;

    private static final ScheduledExecutorService SCHEDULER = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "SkyBatuhan-Alerts");
        t.setDaemon(true);
        return t;
    });

    private Alerts() {
    }

    /**
     * Sharp alarm for when a feature stopped by itself (stuck, AFK timeout).
     */
    public static void warning(Minecraft client) {
        alarm(client, SoundEvents.NOTE_BLOCK_PLING.value(), 1.0f);
    }

    /**
     * Softer, higher alarm for reminders such as the Dark Auction.
     */
    public static void notice(Minecraft client) {
        alarm(client, SoundEvents.EXPERIENCE_ORB_PICKUP, 1.4f);
    }

    private static void alarm(Minecraft client, SoundEvent sound, float startPitch) {
        for (int i = 0; i < REPEATS; i++) {
            float pitch = startPitch + (i % 3) * 0.2f;
            SCHEDULER.schedule(() -> client.execute(() -> {
                if (client.player != null) {
                    client.player.playSound(sound, 2.0f, pitch);
                }
            }), i * INTERVAL_MS, TimeUnit.MILLISECONDS);
        }
    }
}