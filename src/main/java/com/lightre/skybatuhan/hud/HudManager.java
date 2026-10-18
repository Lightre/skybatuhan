package com.lightre.skybatuhan.hud;

import com.lightre.skybatuhan.SkyBatuhan;
import com.lightre.skybatuhan.hud.elements.FarmElement;
import com.lightre.skybatuhan.hud.elements.FishElement;
import com.lightre.skybatuhan.hud.elements.SessionElement;
import com.lightre.skybatuhan.manager.ConfigManager;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Owns every HUD box, draws them through Fabric's HUD registry and keeps their saved positions.
 */
public final class HudManager {
    private static final long LINE_REFRESH_MS = 250L;

    private record CachedLines(List<String> lines, long builtAt) {
    }

    private static final List<HudElement> elements = new ArrayList<>();
    private static final Map<String, HudLayout> defaultLayouts = new HashMap<>();
    private static final Map<String, CachedLines> cache = new HashMap<>();
    private static final Set<String> failed = new HashSet<>();

    private HudManager() {
    }

    public static void init() {
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(SkyBatuhan.MOD_ID, "hud"), (graphics, _) -> render(graphics));

        register(new SessionElement());
        register(new FarmElement());
        register(new FishElement());
    }

    public static void register(HudElement element) {
        for (HudElement existing : elements) {
            if (existing.getId().equals(element.getId())) {
                throw new IllegalArgumentException("Duplicate HUD element id: " + element.getId());
            }
        }
        elements.add(element);
        defaultLayouts.put(element.getId(), element.createDefaultLayout());
    }

    public static List<HudElement> getElements() {
        return Collections.unmodifiableList(elements);
    }

    /**
     * The saved position, or the box's default if the player never moved it.
     */
    public static HudLayout getLayout(HudElement element) {
        HudLayout saved = ConfigManager.config.hud.layouts.get(element.getId());
        return saved != null ? saved : defaultLayouts.computeIfAbsent(element.getId(), _ -> element.createDefaultLayout());
    }

    /**
     * Remembers a position. Saving the config file is up to the caller (the position editor).
     */
    public static void setLayout(String id, HudLayout layout) {
        ConfigManager.config.hud.layouts.put(id, layout);
    }

    public static void resetLayout(String id) {
        ConfigManager.config.hud.layouts.remove(id);
    }

    private static void render(GuiGraphicsExtractor graphics) {
        Minecraft client = Minecraft.getInstance();

        // F1 hides the whole HUD, Fabric does not hide our boxes by itself
        if (client.player == null || client.gui.hud.isHidden()) return;
        if (client.gui.screen() instanceof HudEditorScreen) return; // the editor draws its own copies
        if (!ConfigManager.config.hud.enabled || elements.isEmpty()) return;

        HudCanvas canvas = new GuiHudCanvas(graphics, client.font);
        int screenWidth = client.getWindow().getGuiScaledWidth();
        int screenHeight = client.getWindow().getGuiScaledHeight();
        long now = System.currentTimeMillis();

        for (HudElement element : elements) {
            if (!element.isEnabled() || !element.isActive()) continue;

            List<String> lines = linesOf(element, now);
            if (lines.isEmpty()) continue;

            HudLayout layout = getLayout(element);
            int width = HudStyle.width(canvas, lines);
            int height = HudStyle.height(canvas, lines);
            HudStyle.draw(canvas, lines, layout.resolveX(screenWidth, width), layout.resolveY(screenHeight, height));
        }
    }

    /**
     * The text is rebuilt about four times a second, not every frame.
     */
    private static List<String> linesOf(HudElement element, long now) {
        CachedLines cached = cache.get(element.getId());
        if (cached != null && now - cached.builtAt() < LINE_REFRESH_MS) return cached.lines();

        List<String> lines;
        try {
            lines = element.buildLines();
        } catch (RuntimeException e) {
            // A broken box must never crash the game
            if (failed.add(element.getId())) {
                SkyBatuhan.LOGGER.warn("HUD element '{}' failed to build its lines", element.getId(), e);
            }
            lines = List.of();
        }

        cache.put(element.getId(), new CachedLines(lines, now));
        return lines;
    }
}