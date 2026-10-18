package com.lightre.skybatuhan.hud.elements;

import com.lightre.skybatuhan.hud.HudAnchor;
import com.lightre.skybatuhan.hud.HudElement;
import com.lightre.skybatuhan.hud.HudFormat;
import com.lightre.skybatuhan.manager.ConfigManager;
import com.lightre.skybatuhan.economy.BazaarPrices;
import com.lightre.skybatuhan.economy.farming.FarmingProfitTracker;

import java.util.List;

/**
 * Coins per hour and total coins earned by Auto Farm, valued with Bazaar prices.
 */
public class FarmProfitElement extends HudElement {
    public FarmProfitElement() {
        super("farm_profit", "Farming Profit", HudAnchor.TOP_LEFT, 4, 114);
    }

    @Override
    public boolean isEnabled() {
        return ConfigManager.config.hud.farmProfit.enabled;
    }

    @Override
    public boolean isActive() {
        return FarmingProfitTracker.isActive();
    }

    @Override
    public List<String> buildLines() {
        if (!BazaarPrices.isReady()) {
            return List.of("§6Farming Profit", "§7Loading prices...");
        }

        double perHour = FarmingProfitTracker.getCoinsPerHour();
        String rate;
        if (perHour < 0) {
            rate = "§7calculating...";
        } else {
            int trend = FarmingProfitTracker.getTrend();
            String arrow = trend > 0 ? " §a▲" : trend < 0 ? " §c▼" : "";
            rate = "§f" + HudFormat.coins(perHour) + arrow;
        }

        double gain = FarmingProfitTracker.getRecentGain();
        String gainText = gain > 0 ? " §a+" + HudFormat.coins(gain) : "";

        return List.of(
                "§6Farming Profit",
                "§7Coins/hr: " + rate,
                "§7Total: §f" + HudFormat.coins(Math.max(0, FarmingProfitTracker.getShownTotal())) + gainText
        );
    }

    @Override
    public List<String> previewLines() {
        return List.of("§6Farming Profit", "§7Coins/hr: §f1.24M §a▲", "§7Total: §f620K §a+48.2K");
    }
}