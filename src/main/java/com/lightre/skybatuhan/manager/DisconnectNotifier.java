package com.lightre.skybatuhan.manager;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.lightre.skybatuhan.base.ModConfig;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLevelEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.Screen;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Sends a Discord webhook message (pinging the configured user) when:
 *  - the player is kicked / loses the connection (a DisconnectedScreen appears), or
 *  - the client level changes while still connected (world change).
 */
public class DisconnectNotifier {
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();
    private static final Gson GSON = new Gson();

    private static final Pattern WEBHOOK_PATTERN = Pattern.compile(
            "^https://(?:(?:ptb|canary)\\.)?(?:discord|discordapp)\\.com/api(?:/v\\d+)?/webhooks/\\d+/[\\w-]+(?:\\?.*)?$");
    private static final Pattern USER_ID_PATTERN = Pattern.compile("^\\d{15,25}$");

    private static final long WORLD_CHANGE_COOLDOWN_MS = 5000L;
    private static final long DISCONNECT_WINDOW_MS = 5000L;
    private static final int MAX_MESSAGE_LENGTH = 1900;

    // true while we are inside a level on a connection
    private static boolean inGame = false;
    private static long leftGameAt = 0L;
    private static Screen lastNotifiedScreen = null;
    private static long lastWorldChangeNotify = 0L;
    private static String lastServer = "unknown";

    public static void init() {
        // Fires on login, world change and when the level goes away (disconnect)
        ClientLevelEvents.AFTER_CLIENT_LEVEL_CHANGE.register((client, level) -> {
            if (level == null) {
                markLeftGame();
                return;
            }

            ServerData server = client.getCurrentServer();
            lastServer = server != null ? server.ip : "singleplayer";

            if (inGame) {
                onWorldChange();
            }
            inGame = true;
        });

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> markLeftGame());

        // The kick/connection-lost screen: read the text the player would see
        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            if (screen instanceof DisconnectedScreen) {
                onDisconnectedScreen(screen);
            }
        });
    }

    private static void markLeftGame() {
        if (inGame) {
            inGame = false;
            leftGameAt = System.currentTimeMillis();
        }
    }

    private static void onWorldChange() {
        ModConfig.DisconnectCategory cfg = ConfigManager.config.disconnect;
        if (!cfg.enabled || !cfg.notifyWorldChange) return;

        long now = System.currentTimeMillis();
        if (now - lastWorldChangeNotify < WORLD_CHANGE_COOLDOWN_MS) return;
        lastWorldChangeNotify = now;

        send("**World changed** on `" + lastServer + "`.");
    }

    private static void onDisconnectedScreen(Screen screen) {
        ModConfig.DisconnectCategory cfg = ConfigManager.config.disconnect;
        if (!cfg.enabled || !cfg.notifyDisconnect) return;

        // Re-init (window resize) of the same screen must not notify twice
        if (screen == lastNotifiedScreen) return;

        // Only count it if we were actually in a game just before (not a failed connection attempt)
        long now = System.currentTimeMillis();
        boolean wasInGame = inGame || (leftGameAt != 0L && now - leftGameAt < DISCONNECT_WINDOW_MS);
        if (!wasInGame) return;

        lastNotifiedScreen = screen;
        inGame = false;
        leftGameAt = 0L;

        send("**Disconnected** from `" + lastServer + "`:\n" + readScreenText(screen));
    }

    private static String readScreenText(Screen screen) {
        List<String> parts = new ArrayList<>();
        try {
            for (var child : screen.children()) {
                if (child instanceof AbstractWidget widget && !(child instanceof AbstractButton)) {
                    String text = widget.getMessage().getString().trim();
                    if (!text.isEmpty()) parts.add(text);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return parts.isEmpty() ? "(reason unavailable)" : String.join("\n", parts);
    }

    public static void sendTest() {
        send("**Test message** from SkyBatuhan. Webhook is working.");
    }

    public static void send(String text) {
        ModConfig.DisconnectCategory cfg = ConfigManager.config.disconnect;

        String url = cfg.webhookUrl == null ? "" : cfg.webhookUrl.trim();
        if (!WEBHOOK_PATTERN.matcher(url).matches()) {
            System.out.println("[SkyBatuhan] Disconnect webhook URL is missing or invalid, message not sent.");
            return;
        }

        String id = cfg.discordUserId == null ? "" : cfg.discordUserId.trim();
        boolean ping = USER_ID_PATTERN.matcher(id).matches();

        String content = (ping ? "<@" + id + "> " : "") + text;
        if (content.length() > MAX_MESSAGE_LENGTH) {
            content = content.substring(0, MAX_MESSAGE_LENGTH);
        }

        JsonObject body = new JsonObject();
        body.addProperty("content", content);

        // Only the configured user may be pinged
        JsonObject allowedMentions = new JsonObject();
        JsonArray users = new JsonArray();
        if (ping) users.add(id);
        allowedMentions.add("users", users);
        body.add("allowed_mentions", allowedMentions);

        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(body)))
                    .build();

            HTTP.sendAsync(request, HttpResponse.BodyHandlers.discarding()).whenComplete((response, error) -> {
                if (error != null) {
                    System.err.println("[SkyBatuhan] Webhook request failed: " + error);
                } else if (response.statusCode() >= 300) {
                    System.err.println("[SkyBatuhan] Webhook returned HTTP " + response.statusCode());
                }
            });
        } catch (Exception e) {
            System.err.println("[SkyBatuhan] Could not send webhook: " + e);
        }
    }
}