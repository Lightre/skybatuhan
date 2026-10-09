package com.lightre.skybatuhan.features;

import com.lightre.skybatuhan.SkyBatuhan;
import com.lightre.skybatuhan.base.ModConfig;
import com.lightre.skybatuhan.manager.ConfigManager;
import com.lightre.skybatuhan.util.Alerts;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

import java.time.Instant;

public class DarkAuctionNotifier {
    private static long lastNotifiedChatEpochHour = -1;
    private static long lastNotifiedAlarmEpochHour = -1;

    public static void onTick(Minecraft client) {
        try {
            if (client.player == null) return;

            ModConfig cfg = ConfigManager.config;
            if (cfg == null || cfg.misc == null || !cfg.misc.darkAuctionNotifier) return;

            long epochSec = Instant.now().getEpochSecond();
            long epochHour = epochSec / 3600;
            int minute = (int) ((epochSec / 60) % 60);

            if (minute == 50 && epochHour != lastNotifiedChatEpochHour) {
                lastNotifiedChatEpochHour = epochHour;
                client.player.sendSystemMessage(Component.literal("§6[SkyBatuhan] §eThe Dark Auction starts in 5 minutes!"));
                client.player.playSound(SoundEvents.EXPERIENCE_ORB_PICKUP, 2.0f, 1);
            }

            if (minute == 54 && epochHour != lastNotifiedAlarmEpochHour) {
                lastNotifiedAlarmEpochHour = epochHour;
                client.gui.hud.setTitle(Component.literal("§6Dark Auction!"));
                Alerts.notice(client);
            }
        } catch (Throwable t) {
            SkyBatuhan.LOGGER.error("[SkyBatuhan] DarkAuctionNotifier error", t);
        }
    }

}