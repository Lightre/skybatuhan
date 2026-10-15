package com.lightre.skybatuhan.features;

import com.lightre.skybatuhan.manager.Webhook;
import com.lightre.skybatuhan.manager.ConfigManager;
import com.lightre.skybatuhan.util.Alerts;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

import java.time.Instant;
import java.util.Locale;

public class DarkAuctionNotifier {
    private static final int FIVE_MINUTES_LEFT_MARK = 50;
    private static final int ONE_MINUTE_LEFT_MARK = 54;

    private static long lastFiveMinuteEpochHour = -1;
    private static long lastOneMinuteEpochHour = -1;

    public static void onTick(Minecraft client) {
        if (client.player == null) return;

        var misc = ConfigManager.config.misc;
        if (!misc.darkAuctionNotifier && !misc.darkAuctionDiscord) return;
        if (!isOnHypixel(client)) return;

        long epochSec = Instant.now().getEpochSecond();
        long epochHour = epochSec / 3600;
        int minute = (int) ((epochSec / 60) % 60); // Hypixel's schedule follows UTC

        if (minute == FIVE_MINUTES_LEFT_MARK && epochHour != lastFiveMinuteEpochHour) {
            lastFiveMinuteEpochHour = epochHour;

            if (misc.darkAuctionNotifier) {
                client.player.sendSystemMessage(Component.literal("§6[SkyBatuhan] §eThe Dark Auction starts in 5 minutes!"));
                client.player.playSound(SoundEvents.EXPERIENCE_ORB_PICKUP, 2.0f, 1.0f);
            }
            if (misc.darkAuctionDiscord) {
                Webhook.notifyIfEnabled("**Dark Auction** starts in 5 minutes!");
            }
        }

        if (minute == ONE_MINUTE_LEFT_MARK && epochHour != lastOneMinuteEpochHour) {
            lastOneMinuteEpochHour = epochHour;

            if (misc.darkAuctionNotifier) {
                client.gui.hud.setTitle(Component.literal("§6Dark Auction!"));
                Alerts.notice(client);
            }
            if (misc.darkAuctionDiscord) {
                Webhook.notifyIfEnabled("**Dark Auction** starts in 1 minute!");
            }
        }
    }

    /**
     * The Dark Auction only exists on Hypixel, so stay quiet in singleplayer and on other servers.
     */
    private static boolean isOnHypixel(Minecraft client) {
        ServerData server = client.getCurrentServer();
        if (server == null) return false;

        String host = server.ip.toLowerCase(Locale.ROOT);
        int portSeparator = host.indexOf(':');
        if (portSeparator >= 0) host = host.substring(0, portSeparator);

        return host.equals("hypixel.net") || host.endsWith(".hypixel.net");
    }
}