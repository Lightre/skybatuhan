package com.lightre.skybatuhan.manager;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.lightre.skybatuhan.SkyBatuhan;
import com.lightre.skybatuhan.economy.farming.FarmingProfitTracker;
import com.lightre.skybatuhan.util.SafeFile;
import net.fabricmc.loader.api.FabricLoader;

import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Keeps the ten most recent Auto Farm sessions. */
public final class FarmSessionHistory {
    private static final int MAX_RECORDS = 10;
    private static final long CLOSE_DELAY_MS = FarmingProfitTracker.GRACE_MS;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("skybatuhan").resolve("sessions.json");

    private static final List<Record> records = new ArrayList<>();
    private static long startedAt;
    private static long runTimeMs;
    private static long finishAt;
    private static String stopReason;
    private static String nextStopReason;

    private FarmSessionHistory() {}

    public static void load() {
        if (!Files.exists(FILE)) {
            save();
            return;
        }
        try (Reader reader = Files.newBufferedReader(FILE)) {
            Record[] loaded = GSON.fromJson(reader, Record[].class);
            records.clear();
            if (loaded != null) {
                for (int i = Math.max(0, loaded.length - MAX_RECORDS); i < loaded.length; i++) {
                    if (loaded[i] != null) records.add(loaded[i]);
                }
            }
        } catch (Exception e) {
            var backup = SafeFile.backupBroken(FILE);
            SkyBatuhan.LOGGER.error("Could not read sessions.json, the old file was moved to {}", backup, e);
            records.clear();
            save();
        }
    }

    public static void onFarmStarted(boolean reconnectResume) {
        if (finishAt != 0L) {
            finishAt = 0L;
            finish(stopReason == null ? "Stopped" : stopReason);
        }
        if (startedAt == 0L) {
            startedAt = System.currentTimeMillis();
            runTimeMs = 0L;
        }
    }

    public static void onFarmStopped(long totalRunTimeMs, String reason, boolean recovering) {
        if (startedAt == 0L || recovering) return;
        runTimeMs = totalRunTimeMs;
        stopReason = reason;
        finishAt = System.currentTimeMillis() + CLOSE_DELAY_MS;
    }

    public static void setNextStopReason(String reason) {
        nextStopReason = reason;
    }

    public static String consumeStopReason() {
        String reason = nextStopReason;
        nextStopReason = null;
        return reason == null ? "Manual stop" : reason;
    }

    /** Keep the current record open while reconnect commands are running. */
    public static void preserveForRecovery() {
        finishAt = 0L;
    }

    public static void onTick() {
        if (finishAt != 0L && System.currentTimeMillis() >= finishAt) finish(stopReason);
    }

    public static List<Record> getRecords() {
        return List.copyOf(records);
    }

    private static void finish(String reason) {
        if (startedAt == 0L) return;
        long duration = Math.max(runTimeMs, ModuleManager.getFarmFeature().getRunTimeMs());
        double coins = Math.max(0.0, FarmingProfitTracker.getTotalCoins());
        double rate = Math.max(0.0, FarmingProfitTracker.getCoinsPerHour());
        Record record = new Record(Instant.ofEpochMilli(startedAt).toString(), duration, coins, rate,
                FarmingProfitTracker.getGainedItems(), reason == null ? "Stopped" : reason);
        records.add(record);
        while (records.size() > MAX_RECORDS) records.removeFirst();
        startedAt = 0L;
        runTimeMs = 0L;
        finishAt = 0L;
        stopReason = null;
        save();
    }

    public static void finishOnGameClose() {
        if (startedAt == 0L) return;
        runTimeMs = Math.max(runTimeMs, ModuleManager.getFarmFeature().getRunTimeMs());
        finish("Game closed");
    }

    private static void save() {
        try {
            SafeFile.writeAtomically(FILE, GSON.toJson(records));
        } catch (Exception e) {
            SkyBatuhan.LOGGER.error("Could not save sessions.json", e);
        }
    }

    public static final class Record {
        public String startedAt;
        public long runTimeMs;
        public double coins;
        public double coinsPerHour;
        public Map<String, Long> items;
        public String stopReason;

        public Record(String startedAt, long runTimeMs, double coins, double coinsPerHour, Map<String, Long> items, String stopReason) {
            this.startedAt = startedAt;
            this.runTimeMs = runTimeMs;
            this.coins = coins;
            this.coinsPerHour = coinsPerHour;
            this.items = items;
            this.stopReason = stopReason;
        }
    }
}
