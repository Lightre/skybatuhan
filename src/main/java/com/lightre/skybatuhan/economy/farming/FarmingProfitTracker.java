package com.lightre.skybatuhan.economy.farming;

import com.lightre.skybatuhan.SkyBatuhan;
import com.lightre.skybatuhan.economy.BazaarPrices;
import com.lightre.skybatuhan.features.AutoFarmFeature;
import com.lightre.skybatuhan.manager.ModuleManager;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Counts what Auto Farm earns, from two sources:
 * <ul>
 *   <li>Sacks: the game sums the sack gains and prints them every ~30 seconds as "[Sacks] +N items. (Last 30s.)";
 *   the per-item list is in that message's hover text.</li>
 *   <li>Inventory: rare drops (Cropie, Squash, Overclocker 3000 ...) land in the inventory, so the inventory is
 *   scanned and every increase of those items is counted.</li>
 * </ul>
 * Because the last sack batch arrives up to 30 seconds after Auto Farm stops, counting (and the HUD box) goes on for
 * GRACE_MS after the stop.
 * <p>
 * The coins/hr number does not fall between two income events: the run time it is divided by is the run time at the
 * last income, so it only changes when something new is counted (or the prices refresh). The shown numbers ease
 * towards the real ones instead of jumping.
 * Everything runs on the client thread. Items are stored as counts and priced when shown, so a price that
 * arrives late still values the whole session.
 */
public final class FarmingProfitTracker {
    /**
     * A sack batch covers up to 30s, so wait a little longer than that after stopping.
     */
    public static final long GRACE_MS = 35_000L;

    private static final long MIN_TIME_MS = 30_000L;
    private static final int DEBUG_LOGS = 3;
    private static final int INVENTORY_SCAN_TICKS = 10;
    private static final double EASE = 0.12;
    private static final double SNAP = 0.002;
    private static final double TREND_THRESHOLD = 0.005;
    private static final long GAIN_FLASH_MS = 6_000L;

    // "+309 Brown Mushroom (Agronomy Sack, Agronomy Sack)" once the colors are stripped
    // "[Sacks] +96 items, -25,638 items. (Last 14s.)": the removal part means items were sold or moved, the seconds are the window
    private static final Pattern HEADER_ADDED = Pattern.compile("\\+\\s*(\\d[\\d,]*)\\s+items");
    private static final Pattern HEADER_REMOVED = Pattern.compile("-\\s*\\d[\\d,]*\\s+items");
    private static final Pattern HEADER_SECONDS = Pattern.compile("Last\\s+(\\d+)s");
    private static final int SKIPPED_LOGS = 10;
    private static final long SPIKE_MIN_ITEMS = 3_000L;
    private static final double SPIKE_FACTOR = 5.0;

    private static final Pattern HOVER_LINE = Pattern.compile("^\\s*\\+\\s*(\\d[\\d,]*)\\s+(.+?)\\s*(?:\\(.*\\))?\\s*$");

    private static final Map<FarmingItems.Item, Long> gained = new HashMap<>();
    private static final Set<String> unknownLogged = new HashSet<>();
    // Inventory items that also show up in sack messages: those are counted from the sacks only, never twice
    private static final Set<FarmingItems.Item> viaSack = new HashSet<>();

    private static Map<FarmingItems.Item, Long> inventoryBaseline = new HashMap<>();
    private static boolean baselineValid = false;
    private static int scanTicks = 0;

    private static long lastActiveAt = 0L;
    private static long lastRunMs = 0L;
    private static long lastCreditRunMs = 0L;
    private static int debugLogged = 0;
    private static int skippedLogged = 0;

    // Items per second of the sack messages counted so far, to spot a batch that cannot be farming
    private static double avgItemsPerSecond = 0;
    private static int batchesCounted = 0;

    private static double targetRate = -1;
    private static double shownRate = -1;
    private static double shownTotal = -1;
    private static int trend = 0;
    private static double lastGain = 0;
    private static long lastGainAt = 0L;

    private FarmingProfitTracker() {
    }

    public static void init() {
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            if (!overlay) onMessage(message);
        });
    }

    public static void onTick(Minecraft client) {
        AutoFarmFeature farm = ModuleManager.getFarmFeature();

        // The run time restarts from zero on a fresh start (a reconnect resume keeps it), so a drop means a new session
        long runMs = farm.getRunTimeMs();
        if (runMs < lastRunMs) reset();
        lastRunMs = runMs;

        if (farm.isEnabled()) {
            lastActiveAt = System.currentTimeMillis();
            BazaarPrices.refreshIfStale();
        }

        scanInventory(client);
        updateShown();
    }

    /**
     * True while Auto Farm runs and for GRACE_MS after it stops.
     */
    public static boolean isActive() {
        return ModuleManager.getFarmFeature().isEnabled() || System.currentTimeMillis() - lastActiveAt < GRACE_MS;
    }

    // ---------------------------------------------------------------- sacks

    private static void onMessage(Component message) {
        String text = message.getString();
        if (!text.startsWith("[Sacks]") || !isActive()) return;

        // The same hover text sits on more than one part of the message, so keep each distinct text once
        Set<String> hovers = new LinkedHashSet<>();
        collectHover(message, hovers);

        if (debugLogged < DEBUG_LOGS) {
            debugLogged++;
            SkyBatuhan.LOGGER.info("[FarmProfit] sack message '{}', hover text: {}", text, hovers);
        }

        // Selling or moving sack contents also prints sack messages, and those can carry thousands of items that
        // were not farmed. A message with removals, or one far above the usual rate, is left out and logged
        long headerAdded = headerAdded(text);
        int seconds = headerSeconds(text);
        double itemsPerSecond = headerAdded / (double) Math.max(seconds, 1);
        boolean removal = HEADER_REMOVED.matcher(text).find();
        boolean spike = batchesCounted >= 3 && headerAdded >= SPIKE_MIN_ITEMS && itemsPerSecond > avgItemsPerSecond * SPIKE_FACTOR;
        if (removal || spike) {
            if (skippedLogged < SKIPPED_LOGS) {
                skippedLogged++;
                SkyBatuhan.LOGGER.info("[FarmProfit] sack message not counted ({}): '{}', hover text: {}",
                        removal ? "has removals" : "far above the usual rate", text, hovers);
            }
            return;
        }
        if (seconds >= 10) {
            avgItemsPerSecond = batchesCounted == 0 ? itemsPerSecond : avgItemsPerSecond * 0.8 + itemsPerSecond * 0.2;
            batchesCounted++;
        }

        double batchValue = 0;
        boolean any = false;

        for (String hover : hovers) {
            for (String line : hover.split("\\R")) {
                Matcher matcher = HOVER_LINE.matcher(line);
                if (!matcher.matches()) continue;

                String name = matcher.group(2);
                FarmingItems.Item item = FarmingItems.byName(name);
                if (item == null) {
                    if (unknownLogged.add(name)) {
                        SkyBatuhan.LOGGER.info("[FarmProfit] sack item not counted (unknown name): '{}'", name);
                    }
                    continue;
                }

                if (item.inventory() && viaSack.add(item)) {
                    SkyBatuhan.LOGGER.info("[FarmProfit] '{}' arrives through the sacks, so it is no longer counted from the inventory", item.name());
                }

                long amount = Long.parseLong(matcher.group(1).replace(",", ""));
                batchValue += add(item, amount);
                any = true;
            }
        }

        if (any) markCredit(batchValue);
    }

    private static long headerAdded(String text) {
        Matcher matcher = HEADER_ADDED.matcher(text);
        return matcher.find() ? Long.parseLong(matcher.group(1).replace(",", "")) : 0L;
    }

    private static int headerSeconds(String text) {
        Matcher matcher = HEADER_SECONDS.matcher(text);
        return matcher.find() ? Integer.parseInt(matcher.group(1)) : 30;
    }

    private static void collectHover(Component component, Set<String> out) {
        if (component.getStyle().getHoverEvent() instanceof HoverEvent.ShowText(Component value)) {
            out.add(value.getString());
        }
        for (Component sibling : component.getSiblings()) {
            collectHover(sibling, out);
        }
    }

    // ---------------------------------------------------------------- inventory

    /**
     * Watches the inventory for rare drops. Every few ticks the stacks are summed per item; an increase while
     * Auto Farm is active counts as a drop. A container screen being open (items get moved around) or a world change
     * only resets the baseline, so nothing is counted from it.
     */
    private static void scanInventory(Minecraft client) {
        if (client.player == null || client.level == null) {
            baselineValid = false;
            return;
        }
        if (++scanTicks < INVENTORY_SCAN_TICKS) return;
        scanTicks = 0;

        if (client.gui.screen() instanceof AbstractContainerScreen<?>) {
            baselineValid = false;
            return;
        }

        Map<FarmingItems.Item, Long> now = new HashMap<>();
        Inventory inventory = client.player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.isEmpty()) continue;

            FarmingItems.Item item = FarmingItems.byName(stack.getHoverName().getString());
            if (item == null || !item.inventory() || viaSack.contains(item)) continue;
            now.merge(item, (long) stack.getCount(), Long::sum);
        }

        if (baselineValid && isActive()) {
            double value = 0;
            boolean any = false;
            for (Map.Entry<FarmingItems.Item, Long> entry : now.entrySet()) {
                long delta = entry.getValue() - inventoryBaseline.getOrDefault(entry.getKey(), 0L);
                if (delta > 0) {
                    value += add(entry.getKey(), delta);
                    any = true;
                }
            }
            if (any) markCredit(value);
        }

        inventoryBaseline = now;
        baselineValid = true;
    }

    // ---------------------------------------------------------------- counting

    /**
     * Adds to the session and returns what the added amount is worth right now (0 while the price is unknown).
     */
    private static double add(FarmingItems.Item item, long amount) {
        gained.merge(item, amount, Long::sum);
        return amount * BazaarPrices.priceOf(item.priceId()) / item.divisor();
    }

    /**
     * Something new was counted: the rate's time base moves to now, so the rate changes here and only here.
     */
    private static void markCredit(double gainValue) {
        lastCreditRunMs = ModuleManager.getFarmFeature().getRunTimeMs();
        if (gainValue > 0) {
            lastGain = gainValue;
            lastGainAt = System.currentTimeMillis();
        }
    }

    public static void reset() {
        gained.clear();
        lastCreditRunMs = 0L;
        targetRate = -1;
        shownRate = -1;
        shownTotal = -1;
        trend = 0;
        lastGain = 0;
        lastGainAt = 0L;
        baselineValid = false;
    }

    /**
     * Value of everything gained so far, at the current Bazaar prices.
     */
    public static double getTotalCoins() {
        double total = 0;
        for (Map.Entry<FarmingItems.Item, Long> entry : gained.entrySet()) {
            FarmingItems.Item item = entry.getKey();
            total += entry.getValue() * BazaarPrices.priceOf(item.priceId()) / item.divisor();
        }
        return total;
    }

    // ---------------------------------------------------------------- shown numbers

    private static void updateShown() {
        double total = getTotalCoins();

        double newTarget = -1;
        if (BazaarPrices.isReady() && lastCreditRunMs >= MIN_TIME_MS) {
            newTarget = total / (lastCreditRunMs / 3_600_000.0);
        }

        if (newTarget > 0 && targetRate > 0) {
            double change = (newTarget - targetRate) / targetRate;
            if (Math.abs(change) > TREND_THRESHOLD) trend = change > 0 ? 1 : -1;
        }
        targetRate = newTarget;

        shownRate = ease(shownRate, newTarget);
        shownTotal = ease(shownTotal, BazaarPrices.isReady() ? total : -1);
    }

    private static double ease(double shown, double target) {
        if (target < 0) return -1;
        if (shown < 0) return target;
        double next = shown + (target - shown) * EASE;
        return Math.abs(target - next) <= Math.abs(target) * SNAP ? target : next;
    }

    /**
     * Coins per hour for the box, easing towards the real number, or -1 while there is too little data.
     */
    public static double getCoinsPerHour() {
        return shownRate;
    }

    /**
     * Total for the box, easing towards the real number, or -1 while prices are missing.
     */
    public static double getShownTotal() {
        return shownTotal;
    }

    /**
     * 1 when the last real change of the rate was up, -1 when down, 0 before any change.
     */
    public static int getTrend() {
        return trend;
    }

    /**
     * What the latest income was worth, for a few seconds after it, otherwise 0.
     */
    public static double getRecentGain() {
        return System.currentTimeMillis() - lastGainAt < GAIN_FLASH_MS ? lastGain : 0;
    }
}