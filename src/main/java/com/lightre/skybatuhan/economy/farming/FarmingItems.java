package com.lightre.skybatuhan.economy.farming;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The items that count as farming income, found by the name the sack message / inventory stack shows.
 * Plain crops are valued through their enchanted form (price of the enchanted item / how many crops it needs),
 * because the enchanted prices on the Bazaar are far steadier than the raw ones.
 * Wrong or missing Bazaar ids are reported once in the log by BazaarPrices.
 */
public final class FarmingItems {
    /**
     * @param name      the name in the sack message
     * @param priceId   the Bazaar product used for the price
     * @param divisor   how many of this item one priceId item is worth (1 = the item itself)
     * @param inventory true for items that land in the inventory (rare crop drops), so the inventory is watched for them
     */
    public record Item(String name, String priceId, int divisor, boolean inventory) {
    }

    private static final Map<String, Item> BY_NAME = new HashMap<>();
    private static final Set<String> PRICE_IDS = new LinkedHashSet<>();

    static {
        // Plain crops, priced through the first enchanted tier
        raw("Wheat", "ENCHANTED_BREAD", 60);
        raw("Seeds", "ENCHANTED_SEEDS", 160);
        raw("Carrot", "ENCHANTED_CARROT", 160);
        raw("Potato", "ENCHANTED_POTATO", 160);
        raw("Pumpkin", "ENCHANTED_PUMPKIN", 160);
        raw("Melon", "ENCHANTED_MELON", 160);
        raw("Melon Slice", "ENCHANTED_MELON", 160);
        raw("Sugar Cane", "ENCHANTED_SUGAR", 160);
        raw("Nether Wart", "ENCHANTED_NETHER_STALK", 160);
        raw("Cactus", "ENCHANTED_CACTUS_GREEN", 160);
        raw("Cactus Green", "ENCHANTED_CACTUS_GREEN", 160);
        raw("Cocoa Beans", "ENCHANTED_COCOA", 160);
        raw("Brown Mushroom", "ENCHANTED_BROWN_MUSHROOM", 160);
        raw("Red Mushroom", "ENCHANTED_RED_MUSHROOM", 160);
        raw("Sunflower", "ENCHANTED_SUNFLOWER", 160);
        raw("Moonflower", "ENCHANTED_MOONFLOWER", 160);
        raw("Wild Rose", "ENCHANTED_WILD_ROSE", 160);

        // First enchanted tier, priced as it is
        own("Enchanted Bread", "ENCHANTED_BREAD");
        own("Enchanted Seeds", "ENCHANTED_SEEDS");
        own("Enchanted Carrot", "ENCHANTED_CARROT");
        own("Enchanted Potato", "ENCHANTED_POTATO");
        own("Enchanted Pumpkin", "ENCHANTED_PUMPKIN");
        own("Enchanted Melon Slice", "ENCHANTED_MELON");
        own("Enchanted Sugar", "ENCHANTED_SUGAR");
        own("Enchanted Nether Wart", "ENCHANTED_NETHER_STALK");
        own("Enchanted Cactus Green", "ENCHANTED_CACTUS_GREEN");
        own("Enchanted Cocoa Beans", "ENCHANTED_COCOA");
        own("Enchanted Brown Mushroom", "ENCHANTED_BROWN_MUSHROOM");
        own("Enchanted Red Mushroom", "ENCHANTED_RED_MUSHROOM");
        own("Enchanted Sunflower", "ENCHANTED_SUNFLOWER");
        own("Enchanted Moonflower", "ENCHANTED_MOONFLOWER");
        own("Enchanted Wild Rose", "ENCHANTED_WILD_ROSE");

        // Higher tiers, priced as they are
        own("Enchanted Hay Bale", "ENCHANTED_HAY_BLOCK");
        own("Enchanted Golden Carrot", "ENCHANTED_GOLDEN_CARROT");
        own("Enchanted Baked Potato", "ENCHANTED_BAKED_POTATO");
        own("Polished Pumpkin", "POLISHED_PUMPKIN");
        own("Enchanted Melon", "ENCHANTED_MELON_BLOCK"); // the game calls the Bazaar block item just "Enchanted Melon" (160 Enchanted Melon Slice)
        own("Enchanted Melon Block", "ENCHANTED_MELON_BLOCK");
        own("Enchanted Sugar Cane", "ENCHANTED_SUGAR_CANE");
        own("Mutant Nether Wart", "MUTANT_NETHER_STALK");
        own("Enchanted Cactus", "ENCHANTED_CACTUS");
        own("Enchanted Cookie", "ENCHANTED_COOKIE");
        own("Enchanted Brown Mushroom Block", "ENCHANTED_HUGE_MUSHROOM_1");
        own("Enchanted Red Mushroom Block", "ENCHANTED_HUGE_MUSHROOM_2");
        own("Compacted Sunflower", "COMPACTED_SUNFLOWER");
        own("Compacted Moonflower", "COMPACTED_MOONFLOWER");
        own("Compacted Wild Rose", "COMPACTED_WILD_ROSE");

        // Rare crop drops and other Garden drops. They drop into the inventory, so the inventory is watched for them
        drop("Cropie", "CROPIE");
        drop("Squash", "SQUASH");
        drop("Fermento", "FERMENTO");
        drop("Condensed Fermento", "CONDENSED_FERMENTO");
        drop("Helianthus", "HELIANTHUS");
        drop("Condensed Helianthus", "CONDENSED_HELIANTHUS");
        drop("Overclocker 3000", "OVERCLOCKER_3000");
        drop("Warty", "WARTY");
        drop("Burrowing Spores", "BURROWING_SPORES");
        drop("Ethereal Vine", "ETHEREAL_VINE");
        drop("Tasty Cheese", "TASTY_CHEESE");
        drop("Dung", "DUNG");
        drop("Tool Exp Capsule", "TOOL_EXP_CAPSULE");
    }

    private FarmingItems() {
    }

    private static void raw(String name, String enchantedId, int divisor) {
        add(new Item(name, enchantedId, divisor, false));
    }

    private static void own(String name, String id) {
        add(new Item(name, id, 1, false));
    }

    private static void drop(String name, String id) {
        add(new Item(name, id, 1, true));
    }

    private static void add(Item item) {
        BY_NAME.put(item.name().toLowerCase(Locale.ROOT), item);
        PRICE_IDS.add(item.priceId());
    }

    /**
     * The farming item with this name, or null when it is not one of ours.
     */
    public static Item byName(String name) {
        return BY_NAME.get(name.trim().toLowerCase(Locale.ROOT));
    }

    /**
     * Every Bazaar product we need a price for.
     */
    public static Set<String> priceIds() {
        return Collections.unmodifiableSet(PRICE_IDS);
    }
}