package com.lightre.skybatuhan.manager;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.lightre.skybatuhan.SkyBatuhan;
import com.lightre.skybatuhan.util.Links;
import com.lightre.skybatuhan.util.ModInfo;
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
import java.util.concurrent.atomic.AtomicBoolean;

public class UpdateManager {
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    private static volatile String latestVersion = null;
    private static volatile boolean updateAvailable = false;
    private static boolean chatNotified = false;
    private static final AtomicBoolean checking = new AtomicBoolean(false);

    public static void init() {
        if (ConfigManager.config.about.checkForUpdates) {
            check(false);
        }

        ClientPlayConnectionEvents.JOIN.register((_, _, client) -> client.execute(() -> notifyIfNeeded(client)));
    }

    /**
     * Menu button: check right now and always answer in chat.
     */
    public static void checkNow() {
        check(true);
    }

    private static void check(boolean manual) {
        if (!checking.compareAndSet(false, true)) return;

        Thread t = new Thread(() -> {
            boolean success = false;
            try {
                HttpRequest req = HttpRequest.newBuilder().uri(URI.create(Links.RELEASES_API)).header("Accept", "application/vnd.github+json").header("User-Agent", "SkyBatuhan-Mod/" + ModInfo.getVersion()).timeout(Duration.ofSeconds(8)).GET().build();

                HttpResponse<String> res = HTTP_CLIENT.send(req, HttpResponse.BodyHandlers.ofString());

                if (res.statusCode() == 200) {
                    JsonObject json = JsonParser.parseString(res.body()).getAsJsonObject();
                    String tag = json.get("tag_name").getAsString();
                    latestVersion = tag.startsWith("v") ? tag.substring(1) : tag;

                    String current = ModInfo.getVersion();
                    updateAvailable = isNewer(latestVersion, current);
                    success = true;

                    if (updateAvailable) {
                        SkyBatuhan.LOGGER.info("New update available: v{} (current: v{})", latestVersion, current);
                    }
                } else {
                    SkyBatuhan.LOGGER.debug("Update check returned HTTP {}", res.statusCode());
                }
            } catch (Exception e) {
                SkyBatuhan.LOGGER.warn("Failed to check for the latest version: {}", e.toString());
            }

            // Back on the client thread: the check may finish after the player already joined
            boolean finished = success;
            Minecraft client = Minecraft.getInstance();
            checking.set(false);
            client.execute(() -> onCheckFinished(client, manual, finished));
        }, "SkyBatuhan-UpdateChecker");

        t.setDaemon(true);
        t.start();
    }

    private static void onCheckFinished(Minecraft client, boolean manual, boolean success) {
        if (manual) {
            if (!success) {
                sendChat(client, Component.literal("Could not check for updates.").withStyle(ChatFormatting.RED));
                return;
            }
            if (!updateAvailable) {
                sendChat(client, Component.literal("You are on the latest version").withStyle(ChatFormatting.WHITE)
                        .append(Component.literal(" (v" + ModInfo.getVersion() + ")").withStyle(ChatFormatting.GREEN)));
                return;
            }
            chatNotified = false; // the player asked, so show the message again
        }
        notifyIfNeeded(client);
    }

    private static boolean isNewer(String latest, String current) {
        try {
            return Version.parse(latest).compareTo(Version.parse(current)) > 0;
        } catch (VersionParsingException e) {
            return false;
        }
    }

    private static void notifyIfNeeded(Minecraft client) {
        if (!updateAvailable || chatNotified || client.player == null) return;
        chatNotified = true;

        sendChat(client, Component.literal("A new version is available: ").withStyle(ChatFormatting.WHITE)
                .append(Component.literal("v" + latestVersion + " ").withStyle(ChatFormatting.YELLOW))
                .append(Component.literal("[Download]").withStyle(ChatFormatting.GREEN))
                .withStyle(style -> style.withUnderlined(true)
                        .withClickEvent(new ClickEvent.OpenUrl(URI.create(Links.LATEST_RELEASE)))
                        .withHoverEvent(new HoverEvent.ShowText(Component.literal("Click to open latest release page")))));
    }

    private static void sendChat(Minecraft client, Component text) {
        if (client.player == null) return;
        client.player.sendSystemMessage(Component.literal("[SkyBatuhan] ").withStyle(ChatFormatting.GOLD).append(text));
    }

    public static boolean isUpdateAvailable() {
        return updateAvailable;
    }

    public static String getLatestVersion() {
        return latestVersion;
    }
}