package com.lightre.skybatuhan.manager;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.lightre.skybatuhan.SkyBatuhan;
import com.lightre.skybatuhan.util.ModInfo;
import com.lightre.skybatuhan.util.Links;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.loader.api.Version;
import net.fabricmc.loader.api.VersionParsingException;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public class UpdateManager {

    private static final String RELEASES_API = "https://api.github.com/repos/lightre/skybatuhan/releases/latest";
    public static final String LATEST_RELEASE_URL = "https://github.com/Lightre/skybatuhan/releases/latest";

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    private static volatile String latestVersion = null;
    private static volatile boolean updateAvailable = false;
    private static boolean chatNotified = false;

    public static void init() {
        checkAsync();

        ClientPlayConnectionEvents.JOIN.register((_, _, client) -> {
            if (updateAvailable && !chatNotified) {
                chatNotified = true;
                client.execute(() -> notifyPlayerInChat(client));
            }
        });
    }

    public static void checkAsync() {
        Thread t = new Thread(() -> {
            try {
                HttpRequest req = HttpRequest.newBuilder().uri(URI.create(Links.RELEASES_API)).header("Accept", "application/vnd.github+json").header("User-Agent", "SkyBatuhan-Mod/" + ModInfo.getVersion()).timeout(Duration.ofSeconds(8)).GET().build();

                HttpResponse<String> res = HTTP_CLIENT.send(req, HttpResponse.BodyHandlers.ofString());

                if (res.statusCode() == 200) {
                    JsonObject json = JsonParser.parseString(res.body()).getAsJsonObject();
                    String tag = json.get("tag_name").getAsString();
                    latestVersion = tag.startsWith("v") ? tag.substring(1) : tag;

                    String current = ModInfo.getVersion();
                    updateAvailable = isNewer(latestVersion, current);

                    if (updateAvailable) {
                        SkyBatuhan.LOGGER.info("New update available: v{} (current: v{})", latestVersion, current);
                    }
                } else {
                    SkyBatuhan.LOGGER.debug("Update check returned HTTP {}", res.statusCode());
                }
            } catch (Exception e) {
                SkyBatuhan.LOGGER.warn("Failed to check for the latest version: {}", e.toString());
            }
        }, "SkyBatuhan-UpdateChecker");

        t.setDaemon(true);
        t.start();
    }

    private static boolean isNewer(String latest, String current) {
        try {
            return Version.parse(latest).compareTo(Version.parse(current)) > 0;
        } catch (VersionParsingException e) {
            return false;
        }
    }

    private static void notifyPlayerInChat(Minecraft client) {
        if (client.player == null) return;

        Component downloadMessage = Component.literal("A new version is available: ").withStyle(ChatFormatting.WHITE)
                .append(Component.literal("v" + latestVersion + " ").withStyle(ChatFormatting.YELLOW))
                .append(Component.literal("[Download]").withStyle(ChatFormatting.GREEN))
                .withStyle(style -> style.withUnderlined(true)
                        .withClickEvent(new ClickEvent.OpenUrl(URI.create(LATEST_RELEASE_URL)))
                        .withHoverEvent(new HoverEvent.ShowText(Component.literal("Click to open latest release page"))));
        Component message = Component.literal("[SkyBatuhan] ").withStyle(ChatFormatting.GOLD)
                .append(downloadMessage);
        client.player.sendSystemMessage(message);
    }

    public static boolean isUpdateAvailable() {
        return updateAvailable;
    }

    public static String getLatestVersion() {
        return latestVersion;
    }
}