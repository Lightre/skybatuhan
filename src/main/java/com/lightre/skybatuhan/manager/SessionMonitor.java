package com.lightre.skybatuhan.manager;

import com.lightre.skybatuhan.SkyBatuhan;
import com.lightre.skybatuhan.base.ModConfig;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLevelEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.network.chat.Component;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Watches the session, but only reacts while Auto Farm or Auto Fish is on.
 *  - Auto Fish: on a kick or world change, tell Discord and switch it off.
 *  - Auto Farm: on a kick or world change, switch it off, wait, get back to the garden
 *    (reconnect if needed, then /lobby (only when still connected), /skyblock, /warp), resume.
 */
public class SessionMonitor {
    private enum State {
        IDLE,
        WAIT_BEFORE,   // random wait, then reconnect or run the commands
        CONNECTING,
        RUN_COMMANDS,
        COOLDOWN       // failed attempt limit reached inside the window
    }

    private static final long QUICK_FAIL_MS = 5 * 60 * 1000L;       // trouble this soon after resuming = failed attempt
    private static final long WORLD_CHANGE_COOLDOWN_MS = 5000L;
    private static final long WORLD_CHANGE_GRACE_MS = 5_000L;
    private static final long CONNECT_TIMEOUT_MS = 90_000L;
    private static final long LEFT_GAME_WINDOW_MS = 5000L;
    private static final long GAP_TOLERANCE_MS = 45_000L;   // server transfers leave the game for a while

    private static State state = State.IDLE;
    private static long nextActionAt = 0L;
    private static long connectStartedAt = 0L;
    private static boolean wantReconnect = false;
    private static final Deque<String> commands = new ArrayDeque<>();
    private static final Deque<Long> failureTimes = new ArrayDeque<>();
    private static long lastResumeAt = 0L;
    private static long ignoreWorldChangeUntil = 0L;
    private static long lastWorldChangeAt = 0L;
    private static boolean awayDuringCommands = false;
    private static ServerData lastServerData = null;

    // true while we are inside a level on a connection
    private static boolean inGame = false;
    private static long leftGameAt = 0L;
    private static String lastServer = "unknown";
    private static String lastAddress = null;

    // What was running when the player left the game (everything is switched off at that moment)
    private static boolean farmOnAtLeave = false;
    private static boolean fishOnAtLeave = false;
    private static Screen lastHandledScreen = null;

    public static void init() {
        ClientLevelEvents.AFTER_CLIENT_LEVEL_CHANGE.register((client, _) -> {

            ServerData server = client.getCurrentServer();
            boolean multiplayer = server != null;
            if (multiplayer) {
                lastServer = server.ip;
                lastAddress = server.ip;
                lastServerData = server;
            } else {
                lastServer = "singleplayer";
            }

            boolean wasInGame = inGame;
            inGame = true;

            if (!wasInGame) {
                // A fresh join: whatever was running before leaving is history
                farmOnAtLeave = false;
                fishOnAtLeave = false;
                return;
            }

            if (multiplayer) onWorldChange(client);
        });

        ClientPlayConnectionEvents.DISCONNECT.register((_, client) -> markLeftGame(client));

        ScreenEvents.AFTER_INIT.register((client, screen, _, _) -> {
            if (screen instanceof DisconnectedScreen) {
                onDisconnectedScreen(client, screen);
            }
        });

        ClientTickEvents.END_CLIENT_TICK.register(SessionMonitor::onTick);
    }

    // ================= EVENTS =================

    private static void markLeftGame(Minecraft client) {
        if (!inGame) return;

        inGame = false;
        leftGameAt = System.currentTimeMillis();

        // While recovering, the recovery handles everything itself
        if (state == State.IDLE) {
            farmOnAtLeave = ModuleManager.getFarmFeature().isEnabled();
            fishOnAtLeave = ModuleManager.getFishFeature().isEnabled();
            stopFarm(client);
            stopFish(client);
        }
    }

    private static void onWorldChange(Minecraft client) {
        if (state != State.IDLE) return; // world changes are expected while recovering

        long now = System.currentTimeMillis();
        if (now < ignoreWorldChangeUntil || now - lastWorldChangeAt < WORLD_CHANGE_COOLDOWN_MS) return;

        boolean farmOn = ModuleManager.getFarmFeature().isEnabled();
        boolean fishOn = ModuleManager.getFishFeature().isEnabled();
        if (!farmOn && !fishOn) return; // nothing running: not our business

        lastWorldChangeAt = now;

        ModConfig.DisconnectCategory notify = ConfigManager.config.disconnect;
        ModConfig.ReconnectCategory cfg = notify.reconnect;

        if (notify.notifyWorldChange) {
            Webhook.notifyIfEnabled("**World changed** on `" + lastServer + "`." + (fishOn ? " Auto Fish stopped." : ""));
        }

        if (fishOn) stopFish(client);

        if (farmOn) {
            if (cfg.enabled && cfg.onWorldChange) {
                beginRecovery(client, "World changed");
            } else {
                stopFarm(client);
            }
        }
    }

    private static void onDisconnectedScreen(Minecraft client, Screen screen) {
        if (screen == lastHandledScreen) return; // window resize re-inits the same screen

        ModConfig.DisconnectCategory notify = ConfigManager.config.disconnect;
        ModConfig.ReconnectCategory cfg = notify.reconnect;
        long now = System.currentTimeMillis();

        switch (state) {
            case CONNECTING -> {
                lastHandledScreen = screen;
                onAttemptFailed(client, "Could not connect", readScreenText(screen));
            }
            case WAIT_BEFORE, RUN_COMMANDS -> {
                lastHandledScreen = screen;
                onAttemptFailed(client, "Kicked during recovery", readScreenText(screen));
            }
            case IDLE -> {
                // Only count it if we were actually in a game just before (not a failed connection attempt)
                boolean wasInGame = inGame || (leftGameAt != 0L && now - leftGameAt < LEFT_GAME_WINDOW_MS);
                if (!wasInGame) return;

                lastHandledScreen = screen;
                inGame = false;
                leftGameAt = 0L;

                boolean farmOn = ModuleManager.getFarmFeature().isEnabled() || farmOnAtLeave;
                boolean fishOn = ModuleManager.getFishFeature().isEnabled() || fishOnAtLeave;
                farmOnAtLeave = false;
                fishOnAtLeave = false;
                if (!farmOn && !fishOn) return; // nothing running: not our business

                String reason = readScreenText(screen);
                if (notify.notifyDisconnect) {
                    Webhook.notifyIfEnabled("**Disconnected** from `" + lastServer + "`:\n" + reason
                            + (fishOn ? "\nAuto Fish stopped." : ""));
                }

                if (fishOn) stopFish(client);

                if (farmOn) {
                    stopFarm(client);
                    if (cfg.enabled && cfg.onDisconnect) {
                        if (isBan(reason)) {
                            abort("The account looks banned, not reconnecting.");
                        } else {
                            beginRecovery(client, "Disconnected");
                        }
                    }
                }
            }
            default -> {
                // COOLDOWN: this is our own disconnect screen, nothing to do
            }
        }
    }

    // ================= RECOVERY FLOW =================

    private static void beginRecovery(Minecraft client, String reason) {
        ModConfig.ReconnectCategory cfg = ConfigManager.config.disconnect.reconnect;
        long now = System.currentTimeMillis();

        stopFarm(client);
        commands.clear();

        // Trouble again soon after a recovery that looked fine: that recovery did not really work
        if (lastResumeAt != 0L && now - lastResumeAt < QUICK_FAIL_MS) {
            recordFailure(now, cfg);
        }
        lastResumeAt = 0L;

        wantReconnect = !inGame;

        pruneFailures(now, cfg);
        if (limitReached(cfg)) {
            enterCooldown(reason, cfg, now);
            return;
        }

        long waitMs = randomMs(cfg.minWaitSeconds, cfg.maxWaitSeconds);
        schedule(State.WAIT_BEFORE, waitMs);
        report(reason + ". Recovering in " + waitMs / 1000 + "s (attempt " + attemptNumber() + "/" + cfg.maxAttempts + ").");
    }

    private static void onAttemptFailed(Minecraft client, String reason, String why) {
        ModConfig.ReconnectCategory cfg = ConfigManager.config.disconnect.reconnect;
        long now = System.currentTimeMillis();

        stopFarm(client);
        commands.clear();

        if (isBan(why)) {
            abort("The account looks banned, not trying again.");
            return;
        }

        recordFailure(now, cfg);
        wantReconnect = true;

        if (limitReached(cfg)) {
            enterCooldown(reason, cfg, now);
            return;
        }

        long waitMs = Math.max(1, cfg.retryWaitSeconds) * 1000L;
        schedule(State.WAIT_BEFORE, waitMs);
        report(reason + ". Retrying in " + waitMs / 1000 + "s (attempt " + attemptNumber() + "/" + cfg.maxAttempts + ").");
    }

    private static void enterCooldown(String reason, ModConfig.ReconnectCategory cfg, long now) {
        Long firstFailure = failureTimes.peekFirst();
        long end = (firstFailure != null ? firstFailure : now) + windowMs(cfg);
        state = State.COOLDOWN;
        nextActionAt = end;

        long minutes = Math.max(1L, (end - now + 59_999L) / 60_000L);
        report(reason + ". " + cfg.maxAttempts + " failed attempts within " + cfg.attemptWindowMinutes
                + " min. Paused, trying again in about " + minutes + " min.");
    }

    private static void cancelRecovery(String reason) {
        state = State.IDLE;
        wantReconnect = false;
        commands.clear();
        failureTimes.clear();
        lastResumeAt = 0L;
        SkyBatuhan.LOGGER.info("[Session] Recovery cancelled: {}", reason);
    }

    public static void resetAttempts() {
        failureTimes.clear();
        lastResumeAt = 0L;
        if (state == State.COOLDOWN) cancelRecovery("attempts reset by the player");

        var player = Minecraft.getInstance().player;
        if (player != null) {
            player.sendSystemMessage(Component.literal("§6[SkyBatuhan] §fReconnect attempts reset."));
        }
    }

    private static void abort(String reason) {
        state = State.IDLE;
        wantReconnect = false;
        commands.clear();
        lastResumeAt = 0L;
        report("Aborted. " + reason);
    }

    private static void finishRecovery(Minecraft client, ModConfig.ReconnectCategory cfg) {
        long now = System.currentTimeMillis();

        boolean resume = cfg.resumeFarming;
        if (resume) {
            var farm = ModuleManager.getFarmFeature();
            farm.resetDirection(); // /warp starts at the farm's beginning
            farm.setState(client, true);
            farm.startLanding(client);
        }

        state = State.IDLE;
        wantReconnect = false;
        commands.clear();
        lastResumeAt = now;
        ignoreWorldChangeUntil = now + WORLD_CHANGE_GRACE_MS;

        report("Back in the garden" + (resume ? ", Auto Farm resumed." : "."));
    }

    // ================= FAILURE MEMORY (rolling window) =================

    private static long windowMs(ModConfig.ReconnectCategory cfg) {
        return Math.max(1, cfg.attemptWindowMinutes) * 60_000L;
    }

    private static void recordFailure(long now, ModConfig.ReconnectCategory cfg) {
        failureTimes.addLast(now);
        pruneFailures(now, cfg);
    }

    private static void pruneFailures(long now, ModConfig.ReconnectCategory cfg) {
        long window = windowMs(cfg);
        while (!failureTimes.isEmpty() && now - failureTimes.peekFirst() >= window) {
            failureTimes.pollFirst();
        }
    }

    private static boolean limitReached(ModConfig.ReconnectCategory cfg) {
        return failureTimes.size() >= Math.max(1, cfg.maxAttempts);
    }

    private static int attemptNumber() {
        return failureTimes.size() + 1;
    }

    // ================= TICK =================

    private static void onTick(Minecraft client) {
        long now = System.currentTimeMillis();

        if (state == State.IDLE) {
            watchResume(client, now);
            return;
        }

        ModConfig.ReconnectCategory cfg = ConfigManager.config.disconnect.reconnect;
        boolean connected = inGame && client.player != null;

        switch (state) {
            case COOLDOWN, WAIT_BEFORE -> {
                if (connected && wantReconnect) {
                    cancelRecovery("player joined manually");
                    return;
                }
                if (!inGame && !wantReconnect) {
                    // Moving between servers leaves the game for a while: normal, wait for it
                    if (now - leftGameAt < GAP_TOLERANCE_MS) return;
                    // Still gone: the connection is really lost, reconnect ourselves
                    wantReconnect = true;
                    report("Connection lost while waiting, will reconnect.");
                }
                if (now < nextActionAt) return;

                if (state == State.COOLDOWN) {
                    pruneFailures(now, cfg);
                    schedule(State.WAIT_BEFORE, 0L);
                    report("Pause is over, trying again.");
                } else if (connected) {
                    buildCommands(cfg, true);
                    schedule(State.RUN_COMMANDS, 0L);
                } else {
                    startConnect(client, cfg);
                }
            }
            case CONNECTING -> {
                if (connected) {
                    wantReconnect = false;
                    buildCommands(cfg, false);
                    schedule(State.RUN_COMMANDS, randomMs(cfg.settleMinSeconds, cfg.settleMaxSeconds));
                    SkyBatuhan.LOGGER.info("[Session] Joined, running commands soon.");
                } else if (now - connectStartedAt > CONNECT_TIMEOUT_MS) {
                    onAttemptFailed(client, "Connection timed out", null);
                }
            }
            case RUN_COMMANDS -> {
                if (!connected) {
                    awayDuringCommands = true;
                    // Server transfers (reconfiguration screen, resource pack) take a while: wait for them
                    if (!inGame && now > Math.max(leftGameAt, nextActionAt) + GAP_TOLERANCE_MS) {
                        onAttemptFailed(client, "Lost connection during recovery", null);
                    }
                    return;
                }
                if (awayDuringCommands) {
                    // Just arrived somewhere new: settle before the next command
                    awayDuringCommands = false;
                    schedule(State.RUN_COMMANDS, randomMs(cfg.settleMinSeconds, cfg.settleMaxSeconds));
                    return;
                }
                if (now < nextActionAt) return;

                String command = commands.poll();
                if (command == null) {
                    finishRecovery(client, cfg);
                    return;
                }
                sendCommand(client, command);
                schedule(State.RUN_COMMANDS, randomMs(cfg.settleMinSeconds, cfg.settleMaxSeconds));
            }
            default -> {
            }
        }
    }

    /** Auto Farm stuck right after resuming means we are not where we should be. */
    private static void watchResume(Minecraft client, long now) {
        if (lastResumeAt == 0L) return;

        if (now - lastResumeAt >= QUICK_FAIL_MS) {
            lastResumeAt = 0L;
            return;
        }

        if (ModuleManager.getFarmFeature().getLastStuckStopAt() > lastResumeAt) {
            beginRecovery(client, "Auto Farm got stuck after resuming");
        }
    }

    // ================= ACTIONS =================

    private static void stopFarm(Minecraft client) {
        var farm = ModuleManager.getFarmFeature();
        if (farm.isEnabled()) {
            try {
                farm.setState(client, false);
            } catch (Exception e) {
                SkyBatuhan.LOGGER.warn("Could not stop Auto Farm", e);
            }
        }
    }

    private static void stopFish(Minecraft client) {
        var fish = ModuleManager.getFishFeature();
        if (fish.isEnabled()) {
            try {
                fish.setState(client, false);
            } catch (Exception e) {
                SkyBatuhan.LOGGER.warn("Could not stop Auto Fish", e);
            }
        }
    }

    private static void startConnect(Minecraft client, ModConfig.ReconnectCategory cfg) {
        // The server we were just on first, the menu address is only a fallback
        String address = lastAddress != null && !lastAddress.isBlank() ? lastAddress
                : (cfg.serverAddress == null ? "" : cfg.serverAddress.trim());
        if (address.isEmpty()) {
            abort("No server address known.");
            return;
        }

        connectStartedAt = System.currentTimeMillis();
        state = State.CONNECTING;
        SkyBatuhan.LOGGER.info("[Session] Connecting to {} (attempt {}/{})", address, attemptNumber(), cfg.maxAttempts);

        try {
            ServerData data = lastServerData != null ? lastServerData
                    : new ServerData("Reconnect", address, ServerData.Type.OTHER);
            ConnectScreen.startConnecting(new TitleScreen(), client, ServerAddress.parseString(address), data, false, null);
        } catch (Exception e) {
            SkyBatuhan.LOGGER.warn("Could not start connecting", e);
            onAttemptFailed(client, "Could not start connecting", null);
        }
    }

    private static void buildCommands(ModConfig.ReconnectCategory cfg, boolean includeLobby) {
        awayDuringCommands = false;
        commands.clear();
        // After a fresh join we are already in the lobby, /lobby is only for when we stayed connected
        if (includeLobby) addCommand(cfg.lobbyCommand);
        addCommand(cfg.skyblockCommand);
        addCommand(cfg.warpCommand);
    }

    private static void addCommand(String raw) {
        String command = raw == null ? "" : raw.trim();
        if (command.startsWith("/")) command = command.substring(1);
        if (!command.isEmpty()) commands.addLast(command);
    }

    private static void sendCommand(Minecraft client, String command) {
        if (client.player == null) return;
        client.player.connection.sendCommand(command);
        SkyBatuhan.LOGGER.info("[Session] Sent command: /{}", command);
    }

    // ================= HELPERS =================

    private static void schedule(State newState, long delayMs) {
        state = newState;
        nextActionAt = System.currentTimeMillis() + delayMs;
    }

    private static long randomMs(int minSeconds, int maxSeconds) {
        int lo = Math.max(1, Math.min(minSeconds, maxSeconds));
        int hi = Math.max(1, Math.max(minSeconds, maxSeconds));
        return ThreadLocalRandom.current().nextLong(lo * 1000L, hi * 1000L + 1);
    }

    private static boolean isBan(String text) {
        return text != null && text.toLowerCase(Locale.ROOT).contains("banned");
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
            SkyBatuhan.LOGGER.warn("Could not read the disconnect screen", e);
        }
        return parts.isEmpty() ? "(reason unavailable)" : String.join("\n", parts);
    }

    private static void report(String text) {
        SkyBatuhan.LOGGER.info("[Session] {}", text);
        Webhook.notifyIfEnabled("**Reconnect for Farming**: " + text);
    }
}