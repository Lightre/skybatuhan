package com.lightre.skybatuhan.features;

import com.lightre.skybatuhan.base.Feature;
import com.lightre.skybatuhan.SkyBatuhan;
import com.lightre.skybatuhan.manager.ConfigManager;
import com.lightre.skybatuhan.manager.ModuleManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;

import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Pattern;

/** Tracks pest cooldown locally and switches loadouts around actual pest spawns. */
public class PestFarmingFeature extends Feature {
    private static final Pattern TAB_COOLDOWN = Pattern.compile("^Cooldown: (?:(\\d+)m\\s*)?(?:(\\d+)s)?$", Pattern.CASE_INSENSITIVE);
    private enum Phase { IDLE, WAIT_PEST_COMMAND, OPEN_PEST_MENU, WAIT_PEST_MENU_CLOSE, WAIT_SPAWN, WAIT_FARM_COMMAND, OPEN_FARM_MENU, WAIT_FARM_MENU_CLOSE, WAIT_ABORT_MENU_CLOSE }

    private Phase phase = Phase.IDLE;
    private long clickAt;
    private long commandAt;
    private long menuDeadline;
    private int loadoutContainerId = -1;
    private boolean farmWasRunning;
    private boolean resumeAfterAbort;
    private boolean spawnPending;
    private boolean menuReady;
    private ClientLevel trackedLevel;
    private long lastPestSpawnAt;
    private long initialCooldownEndAt;
    private long nextCooldownCheckAt;
    private boolean cooldownTriggered;
    private boolean waitingForInitialSync;

    public PestFarmingFeature() {
        super("Pest Farming");
    }

    public static void onChatMessage(Component message) {
        String text = message.getString().replaceAll("§.", "").toLowerCase(Locale.ROOT);
        if (!text.contains("pest have spawned in plot")) return;
        Minecraft client = Minecraft.getInstance();
        client.execute(() -> {
            PestFarmingFeature feature = ModuleManager.getPestFarmingFeature();
            feature.updateWorld(client);
            feature.lastPestSpawnAt = System.currentTimeMillis();
            feature.initialCooldownEndAt = 0L;
            feature.cooldownTriggered = false;
            feature.waitingForInitialSync = false;
            if (!feature.isEnabled()) return;
            if (!ModuleManager.getFarmFeature().isEnabled()
                    && (feature.phase == Phase.IDLE || feature.phase == Phase.WAIT_SPAWN)) {
                feature.phase = Phase.IDLE;
                return;
            }

            SkyBatuhan.LOGGER.info("[PestFarming] Pest spawned; reset local cooldown timer");
            feedback(client, "Pest spawned. Local cooldown timer reset.");
            if (feature.phase == Phase.WAIT_PEST_COMMAND || feature.phase == Phase.OPEN_PEST_MENU || feature.phase == Phase.WAIT_PEST_MENU_CLOSE) {
                feature.spawnPending = true;
            } else {
                feature.startFarmLoadout(client);
            }
        });
    }

    private void updateWorld(Minecraft client) {
        if (trackedLevel == client.level) return;
        trackedLevel = client.level;
        phase = Phase.IDLE;
        lastPestSpawnAt = 0L;
        initialCooldownEndAt = 0L;
        nextCooldownCheckAt = 0L;
        cooldownTriggered = false;
        waitingForInitialSync = false;
        spawnPending = false;
        farmWasRunning = false;
        resumeAfterAbort = false;
        loadoutContainerId = -1;
    }

    private void tickCooldown(Minecraft client) {
        if (phase != Phase.IDLE || cooldownTriggered || !ModuleManager.getFarmFeature().isEnabled()) return;
        long now = System.currentTimeMillis();
        if (now < nextCooldownCheckAt) return;
        nextCooldownCheckAt = now + 250L;

        long tabCooldown = readTabCooldownMillis(client);
        if (tabCooldown == -2L) return; // The Pests widget reports the spawn limit.
        var settings = ConfigManager.config.farming.pestFarming;
        long cooldownEndAt;
        if (lastPestSpawnAt != 0L) {
            int seconds = settings.finneganPestEradicator ? settings.finneganCooldownSeconds : settings.cooldownSeconds;
            cooldownEndAt = lastPestSpawnAt + Math.clamp(seconds, 75, 300) * 1000L;
        } else {
            if (initialCooldownEndAt == 0L && tabCooldown >= 0L) {
                initialCooldownEndAt = now + tabCooldown;
                waitingForInitialSync = false;
                feedback(client, "Cooldown timer synced from the Pests widget.");
            }
            if (initialCooldownEndAt == 0L) {
                if (!waitingForInitialSync) {
                    feedback(client, "Waiting for a pest spawn or Pests widget cooldown. Enable the Pests widget with /widget.", ChatFormatting.YELLOW);
                    waitingForInitialSync = true;
                }
                return;
            }
            cooldownEndAt = initialCooldownEndAt;
        }
        if (now < cooldownEndAt - Math.clamp(settings.swapBeforeSeconds, 1, 30) * 1000L) return;
        cooldownTriggered = true;
        feedback(client, "Local pest cooldown reached the loadout swap time.");
        startPestLoadout(client);
    }

    private long readTabCooldownMillis(Minecraft client) {
        if (client.getConnection() == null) return -1L;
        boolean pestsWidget = false;
        boolean maxPests = false;
        long remaining = -1L;
        for (var playerInfo : client.getConnection().getListedOnlinePlayers()) {
            Component name = playerInfo.getTabListDisplayName();
            if (name == null) continue;
            String line = name.getString().replaceAll("§.", "").trim();
            if (line.equalsIgnoreCase("Pests:")) pestsWidget = true;
            if (line.equalsIgnoreCase("Cooldown: MAX PESTS") || line.equalsIgnoreCase("Alive: 8")) maxPests = true;
            if (line.equalsIgnoreCase("Cooldown: READY")) remaining = 0L;
            var matcher = TAB_COOLDOWN.matcher(line);
            if (matcher.matches() && (matcher.group(1) != null || matcher.group(2) != null)) {
                long minutes = matcher.group(1) == null ? 0L : Long.parseLong(matcher.group(1));
                long seconds = matcher.group(2) == null ? 0L : Long.parseLong(matcher.group(2));
                remaining = (minutes * 60L + seconds) * 1000L;
            }
        }
        return !pestsWidget ? -1L : maxPests ? -2L : remaining;
    }

    private void startPestLoadout(Minecraft client) {
        if (phase != Phase.IDLE || !ModuleManager.getFarmFeature().isEnabled()) return;
        if (normalizeLoadoutName(ConfigManager.config.farming.pestFarming.pestLoadoutName).isEmpty()
                || normalizeLoadoutName(ConfigManager.config.farming.pestFarming.farmLoadoutName).isEmpty()) {
            feedback(client, "Enter both loadout names in the Pest Farming settings first.", ChatFormatting.RED);
            return;
        }
        farmWasRunning = ModuleManager.getFarmFeature().isEnabled();
        spawnPending = false;
        stopAutoFarm(client);
        openLoadout(client, Phase.OPEN_PEST_MENU);
    }

    private void startFarmLoadout(Minecraft client) {
        if (phase != Phase.WAIT_SPAWN) return;
        stopAutoFarm(client);
        openLoadout(client, Phase.OPEN_FARM_MENU);
    }

    private void openLoadout(Minecraft client, Phase nextPhase) {
        phase = nextPhase == Phase.OPEN_PEST_MENU ? Phase.WAIT_PEST_COMMAND : Phase.WAIT_FARM_COMMAND;
        if (client.player == null || client.getConnection() == null) {
            abortLoadout(client, "Connection lost before opening /loadouts.");
            return;
        }
        commandAt = System.currentTimeMillis() + actionDelay();
    }

    private void sendLoadoutCommand(Minecraft client) {
        if (client.getConnection() == null) {
            abortLoadout(client, "Connection lost before opening /loadouts.");
            return;
        }
        phase = phase == Phase.WAIT_PEST_COMMAND ? Phase.OPEN_PEST_MENU : Phase.OPEN_FARM_MENU;
        client.getConnection().sendCommand("loadouts");
        long now = System.currentTimeMillis();
        menuReady = false;
        menuDeadline = now + 10_000L;
        feedback(client, "Sent /loadouts for " + (phase == Phase.OPEN_PEST_MENU ? "pest" : "farm") + " loadout.");
        SkyBatuhan.LOGGER.info("[PestFarming] Sent /loadouts for {} selection", phase);
    }

    private void stopAutoFarm(Minecraft client) {
        var farm = ModuleManager.getFarmFeature();
        if (farm.isEnabled()) farm.setState(client, false);
    }

    @Override
    public void onTick(Minecraft client) {
        if (!isEnabled() || client.player == null || client.level == null || client.gameMode == null) return;
        updateWorld(client);
        tickCooldown(client);
        if (phase == Phase.IDLE) return;
        if (phase == Phase.WAIT_SPAWN) return;
        if (phase == Phase.WAIT_PEST_COMMAND || phase == Phase.WAIT_FARM_COMMAND) {
            if (System.currentTimeMillis() >= commandAt) sendLoadoutCommand(client);
            return;
        }
        if (phase == Phase.WAIT_PEST_MENU_CLOSE || phase == Phase.WAIT_FARM_MENU_CLOSE || phase == Phase.WAIT_ABORT_MENU_CLOSE) {
            if (!closeLoadoutMenu(client)) return;
            loadoutContainerId = -1;
            if (phase == Phase.WAIT_ABORT_MENU_CLOSE) {
                phase = Phase.IDLE;
                boolean resumeFarm = resumeAfterAbort;
                resumeAfterAbort = false;
                if (resumeFarm) ModuleManager.getFarmFeature().resume(client);
                return;
            }
            if (phase == Phase.WAIT_FARM_MENU_CLOSE) {
                phase = Phase.IDLE;
                farmWasRunning = false;
                if (client.getConnection() != null) {
                    client.getConnection().sendCommand("sethome");
                    SkyBatuhan.LOGGER.info("[PestFarming] Sent /sethome after selecting farm loadout; Auto Farm stays off");
                    feedback(client, "Farm loadout selected. Sent /sethome; Auto Farm stays off.", ChatFormatting.GREEN);
                }
                return;
            }
            phase = Phase.WAIT_SPAWN;
            if (phase == Phase.WAIT_SPAWN && spawnPending) {
                spawnPending = false;
                startFarmLoadout(client);
            } else if (farmWasRunning) {
                ModuleManager.getFarmFeature().resume(client);
            }
            return;
        }
        if (System.currentTimeMillis() >= menuDeadline) {
            abortLoadout(client, "Could not find loadout \"" + selectedLoadoutName() + "\" in /loadouts. Check its name and menu page.");
            return;
        }
        if (!(client.gui.screen() instanceof AbstractContainerScreen<?> screen)
                || !screen.getTitle().getString().toLowerCase(Locale.ROOT).contains("loadout")) {
            return;
        }
        loadoutContainerId = screen.getMenu().containerId;
        String expectedName = normalizeLoadoutName(selectedLoadoutName());
        if (expectedName.isEmpty()) {
            abortLoadout(client, "The loadout name is empty. Enter it in the Pest Farming settings.");
            return;
        }
        Slot target = null;
        for (Slot candidate : screen.getMenu().slots) {
            if (candidate.container == client.player.getInventory() || candidate.getItem().isEmpty()) continue;
            if (!normalizeLoadoutName(candidate.getItem().getHoverName().getString()).equals(expectedName)) continue;
            if (target != null) {
                abortLoadout(client, "More than one menu item matches loadout \"" + selectedLoadoutName() + "\". Use a unique name.");
                return;
            }
            target = candidate;
        }
        if (target == null) {
            menuReady = false;
            return;
        }
        if (!menuReady) {
            menuReady = true;
            clickAt = System.currentTimeMillis() + actionDelay();
        }
        if (System.currentTimeMillis() < clickAt) return;

        String chosenName = selectedLoadoutName();
        loadoutContainerId = screen.getMenu().containerId;
        client.gameMode.handleContainerInput(screen.getMenu().containerId, target.index, 0, ContainerInput.PICKUP, client.player);
        boolean pestLoadout = phase == Phase.OPEN_PEST_MENU;
        phase = pestLoadout ? Phase.WAIT_PEST_MENU_CLOSE : Phase.WAIT_FARM_MENU_CLOSE;
        menuDeadline = System.currentTimeMillis() + actionDelay();
        SkyBatuhan.LOGGER.info("[PestFarming] Clicked loadout '{}' in slot {}", chosenName, target.index);
        feedback(client, "Selected " + (pestLoadout ? "pest" : "farm") + " loadout: " + chosenName + ".");
    }

    private boolean closeLoadoutMenu(Minecraft client) {
        if (client.gui.screen() != null) {
            if (!(client.gui.screen() instanceof AbstractContainerScreen<?> screen)) return false;
            if (loadoutContainerId == -1 && phase == Phase.WAIT_ABORT_MENU_CLOSE
                    && screen.getTitle().getString().toLowerCase(Locale.ROOT).contains("loadout")) {
                loadoutContainerId = screen.getMenu().containerId;
            }
            if (screen.getMenu().containerId != loadoutContainerId) {
                if (!screen.getTitle().getString().toLowerCase(Locale.ROOT).contains("loadout")) return false;
                loadoutContainerId = screen.getMenu().containerId;
            }
            if (System.currentTimeMillis() < menuDeadline) return false;
            for (Slot candidate : screen.getMenu().slots) {
                if (candidate.container == client.player.getInventory() || candidate.getItem().isEmpty()) continue;
                if (!normalizeLoadoutName(candidate.getItem().getHoverName().getString()).equals("close")) continue;
                client.gameMode.handleContainerInput(screen.getMenu().containerId, candidate.index, 0, ContainerInput.PICKUP, client.player);
                menuDeadline = System.currentTimeMillis() + 2500L;
                SkyBatuhan.LOGGER.info("[PestFarming] Clicked Close in slot {}", candidate.index);
                return false;
            }
            screen.onClose();
        }
        if (client.player.containerMenu != client.player.inventoryMenu) {
            if (client.gui.screen() != null || client.player.containerMenu.containerId != loadoutContainerId) return false;
            client.player.closeContainer();
        }
        return client.gui.screen() == null && client.player.containerMenu == client.player.inventoryMenu;
    }

    private String selectedLoadoutName() {
        var settings = ConfigManager.config.farming.pestFarming;
        return phase == Phase.OPEN_PEST_MENU ? settings.pestLoadoutName : settings.farmLoadoutName;
    }

    private static String normalizeLoadoutName(String name) {
        return name == null ? "" : name.replaceAll("§.", "").trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    private static void feedback(Minecraft client, String text) {
        feedback(client, text, ChatFormatting.AQUA);
    }

    private static void feedback(Minecraft client, String text, ChatFormatting color) {
        if (client.player != null) client.player.sendSystemMessage(
                Component.literal("[SkyBatuhan] ").withStyle(ChatFormatting.GOLD)
                        .append(Component.literal(text).withStyle(color)));
    }

    private static long actionDelay() {
        return ThreadLocalRandom.current().nextLong(100L, 151L);
    }

    private void abortLoadout(Minecraft client, String reason) {
        SkyBatuhan.LOGGER.warn("[PestFarming] {}", reason);
        feedback(client, reason, ChatFormatting.RED);
        resumeAfterAbort = farmWasRunning && phase != Phase.WAIT_FARM_COMMAND
                && phase != Phase.OPEN_FARM_MENU && phase != Phase.WAIT_FARM_MENU_CLOSE;
        spawnPending = false;
        phase = Phase.WAIT_ABORT_MENU_CLOSE;
        menuDeadline = System.currentTimeMillis();
    }

    @Override
    public void onToggle(Minecraft client, boolean state) {
        if (state) return;
        phase = Phase.IDLE;
        if (!ConfigManager.config.farming.pestFarming.enabled
                && client.player != null && client.getConnection() != null) {
            client.getConnection().sendCommand("sethome");
        }
    }

    public static void onManualAutoFarmStop(Minecraft client) {
        if (!ModuleManager.getPestFarmingFeature().isEnabled()) return;
        PestFarmingFeature feature = ModuleManager.getPestFarmingFeature();
        feature.phase = Phase.IDLE;
        feature.spawnPending = false;
        feature.farmWasRunning = false;
        feature.resumeAfterAbort = false;
        SkyBatuhan.LOGGER.info("[PestFarming] Cancelled pending loadout changes after manually stopping Auto Farm");
    }
}
