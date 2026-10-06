package com.lightre.skybatuhan.manager;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.lightre.skybatuhan.SkyBatuhan;
import com.lightre.skybatuhan.base.ModConfig;
import org.jspecify.annotations.NonNull;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.regex.Pattern;

public class Webhook {
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();
    private static final Gson GSON = new Gson();

    private static final Pattern WEBHOOK_PATTERN = Pattern.compile(
            "^https://(?:(?:ptb|canary)\\.)?(?:discord|discordapp)\\.com/api(?:/v\\d+)?/webhooks/\\d+/[\\w-]+(?:\\?.*)?$");
    private static final Pattern USER_ID_PATTERN = Pattern.compile("^\\d{15,25}$");
    private static final int MAX_MESSAGE_LENGTH = 1900;

    /** sends it only if Discord notifications are turned on in the menu. */
    public static void notifyIfEnabled(String text) {
        if (ConfigManager.config.disconnect != null && ConfigManager.config.disconnect.enabled) {
            send(text);
        }
    }

    public static void sendTest() {
        send("**Test message** from SkyBatuhan. Webhook is working.");
    }

    public static void send(String text) {
        ModConfig.DisconnectCategory cfg = ConfigManager.config.disconnect;

        String url = cfg.webhookUrl == null ? "" : cfg.webhookUrl.trim();
        if (!WEBHOOK_PATTERN.matcher(url).matches()) {
            SkyBatuhan.LOGGER.warn("Webhook URL is missing or invalid, message not sent.");
            return;
        }

        String id = cfg.discordUserId == null ? "" : cfg.discordUserId.trim();
        boolean ping = USER_ID_PATTERN.matcher(id).matches();

        JsonObject body = getBody(text, ping, id);

        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(body)))
                    .build();

            HTTP.sendAsync(request, HttpResponse.BodyHandlers.discarding()).whenComplete((response, error) -> {
                if (error != null) {
                    SkyBatuhan.LOGGER.warn("Webhook request failed: {}", error.toString());
                } else if (response.statusCode() >= 300) {
                    SkyBatuhan.LOGGER.warn("Webhook returned HTTP {}", response.statusCode());
                }
            });
        } catch (Exception e) {
            SkyBatuhan.LOGGER.warn("Could not send webhook: {}", e.toString());
        }
    }

    private static @NonNull JsonObject getBody(String text, boolean ping, String id) {
        String content = (ping ? "<@" + id + "> " : "") + text;
        if (content.length() > MAX_MESSAGE_LENGTH) {
            content = content.substring(0, MAX_MESSAGE_LENGTH);
        }

        JsonObject body = new JsonObject();
        body.addProperty("content", content);

        // only the configured user may be pinged
        JsonObject allowedMentions = new JsonObject();
        JsonArray users = new JsonArray();
        if (ping) users.add(id);
        allowedMentions.add("users", users);
        body.add("allowed_mentions", allowedMentions);
        return body;
    }
}