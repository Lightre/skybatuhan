package com.lightre.skybatuhan.economy;

import com.google.gson.stream.JsonReader;
import com.lightre.skybatuhan.SkyBatuhan;
import com.lightre.skybatuhan.economy.farming.FarmingItems;
import com.lightre.skybatuhan.util.ModInfo;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.GZIPInputStream;

/**
 * Bazaar prices of the farming items. The public endpoint needs no API key.
 * The request is async (no thread of our own) and only started when the cached prices are old,
 * so the game thread never waits for the network. The answer is several MB of JSON, so it is asked
 * for gzipped and read as a stream: only the prices we need are kept and the rest is skipped.
 * A cut-off answer is thrown away whole and the old prices stay until the retry works.
 */
public final class BazaarPrices {
    private static final URI ENDPOINT = URI.create("https://api.hypixel.net/v2/skyblock/bazaar");
    private static final long REFRESH_MS = 120_000L;
    private static final long RETRY_MS = 30_000L;
    private static final long STUCK_MS = 90_000L;

    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private static final AtomicBoolean fetching = new AtomicBoolean(false);

    private static volatile Map<String, Double> prices = Map.of();
    private static volatile long updatedAt = 0L;
    private static volatile long lastAttemptAt = 0L;
    private static volatile long fetchStartedAt = 0L;
    private static volatile boolean missingReported = false;

    private BazaarPrices() {
    }

    public static boolean isReady() {
        return !prices.isEmpty();
    }

    /**
     * Coins you get for selling one item right away, 0 when the price is unknown.
     */
    public static double priceOf(String id) {
        return prices.getOrDefault(id, 0.0);
    }

    /**
     * Cheap to call every tick: starts a request only when the prices are old and no request is running.
     */
    public static void refreshIfStale() {
        long now = System.currentTimeMillis();
        if (now - updatedAt < REFRESH_MS || now - lastAttemptAt < RETRY_MS) return;

        // A request whose body stalled never finishes by itself: let a new one start
        if (fetching.get() && now - fetchStartedAt > STUCK_MS) fetching.set(false);
        if (!fetching.compareAndSet(false, true)) return;

        fetchStartedAt = now;
        lastAttemptAt = now;
        HttpRequest request = HttpRequest.newBuilder()
                .uri(ENDPOINT)
                .header("User-Agent", "SkyBatuhan-Mod/" + ModInfo.getVersion())
                .header("Accept-Encoding", "gzip")
                .timeout(Duration.ofSeconds(15))
                .GET()
                .build();

        HTTP.sendAsync(request, HttpResponse.BodyHandlers.ofInputStream()).whenComplete((response, error) -> {
            try {
                if (error != null) {
                    SkyBatuhan.LOGGER.warn("Bazaar request failed: {}", error.toString());
                } else if (response.statusCode() != 200) {
                    response.body().close();
                    SkyBatuhan.LOGGER.warn("Bazaar request returned HTTP {}", response.statusCode());
                } else {
                    read(response);
                }
            } catch (IOException | RuntimeException e) {
                SkyBatuhan.LOGGER.warn("Could not read the Bazaar response: {}", e.toString());
            } finally {
                fetching.set(false);
            }
        });
    }

    private static void read(HttpResponse<InputStream> response) throws IOException {
        boolean gzip = response.headers().firstValue("Content-Encoding").orElse("").equalsIgnoreCase("gzip");

        try (InputStream raw = response.body(); InputStream in = gzip ? new GZIPInputStream(raw) : raw) {
            parse(in);
        }
    }

    private static void parse(InputStream in) throws IOException {
        Set<String> wanted = FarmingItems.priceIds();
        Map<String, Double> result = new HashMap<>();

        try (JsonReader reader = new JsonReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            reader.beginObject();
            while (reader.hasNext()) {
                if (reader.nextName().equals("products")) {
                    readProducts(reader, wanted, result);
                } else {
                    reader.skipValue();
                }
            }
            reader.endObject();
        }

        if (result.isEmpty()) throw new IOException("no prices in the response");

        if (!missingReported) {
            Set<String> missing = new TreeSet<>(wanted);
            missing.removeAll(result.keySet());
            if (!missing.isEmpty()) {
                missingReported = true;
                SkyBatuhan.LOGGER.warn("Farming item ids not in the Bazaar response (check FarmingItems): {}", String.join(" ", missing));
            }
        }

        prices = Map.copyOf(result);
        updatedAt = System.currentTimeMillis();
    }

    private static void readProducts(JsonReader reader, Set<String> wanted, Map<String, Double> result) throws IOException {
        reader.beginObject();
        while (reader.hasNext()) {
            String id = reader.nextName();
            if (!wanted.contains(id)) {
                reader.skipValue();
                continue;
            }
            double sell = readSellPrice(reader);
            if (sell > 0) result.put(id, sell);
        }
        reader.endObject();
    }

    /**
     * quick_status.sellPrice: what you get for selling instantly into the buy orders.
     */
    private static double readSellPrice(JsonReader reader) throws IOException {
        double sell = 0;
        reader.beginObject();
        while (reader.hasNext()) {
            if (reader.nextName().equals("quick_status")) {
                reader.beginObject();
                while (reader.hasNext()) {
                    if (reader.nextName().equals("sellPrice")) {
                        sell = reader.nextDouble();
                    } else {
                        reader.skipValue();
                    }
                }
                reader.endObject();
            } else {
                reader.skipValue();
            }
        }
        reader.endObject();
        return sell;
    }
}
