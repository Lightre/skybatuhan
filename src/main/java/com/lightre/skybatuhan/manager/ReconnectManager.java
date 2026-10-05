package com.lightre.skybatuhan.manager;

import com.lightre.skybatuhan.base.ModConfig;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLevelEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.network.chat.Component;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ThreadLocalRandom;

/**
 * "Reconnect for Farming" (Auto Farm only):
 *  - on a disconnect (kick / connection lost) or a world change while farming:
 *    stop Auto Farm and leave the server,
 *  - wait, then reconnect to the configured server,
 *  - after joining: wait, run the skyblock command, wait, run the warp command, wait,
 *    then switch Auto Farm back on,
 *  - failed attempts are remembered for a rolling time window (default 1 hour).
 *    When the limit is reached it pauses until the oldest failure falls out of the window,
 *    then keeps trying.
 *
 * Whenever the player leaves a game (kick, manual quit), Auto Farm is switched off.
 * If it was on, that is remembered (farmOnAtLeave) so a kick can still resume it.
 */
public class ReconnectManager {
    private enum State {
        IDLE,
        WAIT_BEFORE_CONNECT,
        CONNECTING,
        WAIT_AFTER_JOIN,      // then: skyblock command
        WAIT_AFTER_SKYBLOCK,  // then: warp command
        WAIT_AFTER_WARP,      // then: resume Auto Farm
        COOLDOWN              // attempt limit reached inside the window
    }

    private static final long QUICK_KICK_MS = 5 * 60 * 1000L;     // kicked again this soon after resuming = failed attempt
    private static final long WORLD_CHANGE_GRACE_MS = 20_000L;    // ignore world changes right after resuming
    private static final long HOME_COMMAND_GRACE_MS = 20_000L;   // ignore world changes right after Auto Farm's /home
    private static final long CONNECT_TIMEOUT_MS = 90_000L;
    private static final long DISCONNECT_WINDOW_MS = 5000L;

    private static State state = State.IDLE;
    private static long nextActionAt = 0L;
    private static long connectStartedAt = 0L;
    private static boolean farmWasEnabled = false;
    private static long lastResumeAt = 0L;
    private static long ignoreWorldChangeUntil = 0L;

    // Auto Farm was on when the player left the game (Auto Farm is switched off at that moment)
    private static boolean farmOnAtLeave = false;

    // timestamps of failed attempts, oldest first
    private static final Deque<Long> failureTimes = new ArrayDeque<>();

    // true while we are inside a level on a connection
    private static boolean inGame = false;
    private static long leftGameAt = 0L;
    private static Screen lastHandledScreen = null;

    public static void init() {
        ClientLevelEvents.AFTER_CLIENT_LEVEL_CHANGE.register((client, level) -> {
            if (level == null) {
                markLeftGame();
                return;
            }

            boolean wasInGame = inGame;
            inGame = true;

            if (!wasInGame) {
                // A fresh join: whatever Auto Farm was doing before leaving is history
                farmOnAtLeave = false;
            }

            if (wasInGame) {
                onWorldChange(client);
            }
        });

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> markLeftGame());

        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            if (screen instanceof DisconnectedScreen) {
                onDisconnectedScreen(client, screen);
            }
        });

        ClientTickEvents.END_CLIENT_TICK.register(ReconnectManager::onTick);
    }

    // ================= EVENTS =================

    private static void markLeftGame() {
        if (!inGame) return;

        inGame = false;
        leftGameAt = System.currentTimeMillis();

        // Not while recovering: Reconnect handles Auto Farm itself in that case
        if (state == State.IDLE) {
            farmOnAtLeave = ModuleManager.getFarmFeature().isEnabled();
            stopFarm(Minecraft.getInstance());
        }
    }

    private static void onWorldChange(Minecraft client) {
        ModConfig.ReconnectCategory cfg = ConfigManager.config.disconnect.reconnect;
        if (!cfg.enabled || !cfg.onWorldChange) return;
        if (state != State.IDLE) return; // world changes are expected while recovering
        long now = System.currentTimeMillis();
        if (now < ignoreWorldChangeUntil) return;
        // Auto Farm's own /home command can change the world: that is not a reason to leave
        if (now - ModuleManager.getFarmFeature().getLastHomeCommandAt() < HOME_COMMAND_GRACE_MS) return;

        beginRecovery(client, "World changed", true);
    }

    private static void onDisconnectedScreen(Minecraft client, Screen screen) {
        if (screen == lastHandledScreen) return; // window resize re-inits the same screen

        ModConfig.ReconnectCategory cfg = ConfigManager.config.disconnect.reconnect;
        if (!cfg.enabled) return;

        long now = System.currentTimeMillis();

        switch (state) {
            case CONNECTING -> {
                lastHandledScreen = screen;
                onAttemptFailed(client, "Could not connect");
            }
            case WAIT_AFTER_JOIN, WAIT_AFTER_SKYBLOCK, WAIT_AFTER_WARP -> {
                lastHandledScreen = screen;
                onAttemptFailed(client, "Kicked after joining");
            }
            case IDLE -> {
                boolean wasInGame = inGame || (leftGameAt != 0L && now - leftGameAt < DISCONNECT_WINDOW_MS);
                if (!wasInGame || !cfg.onDisconnect) return;

                lastHandledScreen = screen;
                inGame = false;
                leftGameAt = 0L;
                beginRecovery(client, "Disconnected", false);
            }
            default -> {
                // WAIT_BEFORE_CONNECT / COOLDOWN: this is our own disconnect screen, nothing to do
            }
        }
    }

    // ================= RECOVERY FLOW =================

    private static void beginRecovery(Minecraft client, String reason, boolean leaveNow) {
        ModConfig.ReconnectCategory cfg = ConfigManager.config.disconnect.reconnect;
        long now = System.currentTimeMillis();

        // Auto Farm may already have been switched off when the connection dropped
        boolean farmOn = ModuleManager.getFarmFeature().isEnabled() || farmOnAtLeave;
        farmOnAtLeave = false;
        if (cfg.onlyWhenActive && !farmOn) return;

        farmWasEnabled = farmOn;

        // Stop Auto Farm first, while the player still exists
        stopFarm(client);

        if (leaveNow) {
            leaveGame(client, reason);
        }

        // Kicked again soon after a successful recovery: that recovery did not really work
        if (lastResumeAt != 0L && now - lastResumeAt < QUICK_KICK_MS) {
            recordFailure(now, cfg);
        }
        lastResumeAt = 0L;

        pruneFailures(now, cfg);
        if (limitReached(cfg)) {
            enterCooldown(reason, cfg, now);
            return;
        }

        long waitMs = randomMs(cfg.minWaitSeconds, cfg.maxWaitSeconds);
        schedule(State.WAIT_BEFORE_CONNECT, waitMs);
        String text = reason + ". Reconnecting in " + waitMs / 1000 + "s (attempt " + attemptNumber() + "/" + cfg.maxAttempts + ").";
        log(text);
        notifyDiscord("**Reconnect for Farming**: " + text);
    }

    private static void onAttemptFailed(Minecraft client, String reason) {
        ModConfig.ReconnectCategory cfg = ConfigManager.config.disconnect.reconnect;
        long now = System.currentTimeMillis();

        stopFarm(client);
        if (inGame) {
            leaveGame(client, reason);
        }

        recordFailure(now, cfg);
        if (limitReached(cfg)) {
            enterCooldown(reason, cfg, now);
            return;
        }

        long waitMs = Math.max(1, cfg.retryWaitSeconds) * 1000L;
        schedule(State.WAIT_BEFORE_CONNECT, waitMs);
        String text = reason + ". Retrying in " + waitMs / 1000 + "s (attempt " + attemptNumber() + "/" + cfg.maxAttempts + ").";
        log(text);
        notifyDiscord("**Reconnect for Farming**: " + text);
    }

    private static void enterCooldown(String reason, ModConfig.ReconnectCategory cfg, long now) {
        long end = failureTimes.peekFirst() + windowMs(cfg);
        state = State.COOLDOWN;
        nextActionAt = end;

        long minutes = Math.max(1L, (end - now + 59_999L) / 60_000L);
        String text = reason + ". " + cfg.maxAttempts + " failed attempts within " + cfg.attemptWindowMinutes
                + " min. Paused, trying again in about " + minutes + " min.";
        log(text);
        notifyDiscord("**Reconnect for Farming**: " + text);
    }

    private static void cancelRecovery(String reason) {
        state = State.IDLE;
        failureTimes.clear();
        log("Recovery cancelled: " + reason);
    }

    private static void abort(String reason) {
        state = State.IDLE;
        log("Aborted: " + reason);
        notifyDiscord("**Reconnect for Farming**: aborted. " + reason);
    }

    private static void finishRecovery(Minecraft client, ModConfig.ReconnectCategory cfg) {
        long now = System.currentTimeMillis();

        boolean resume = farmWasEnabled && cfg.resumeFarming;
        if (resume) {
            ModuleManager.getFarmFeature().setState(client, true);
        }

        state = State.IDLE;
        lastResumeAt = now;
        ignoreWorldChangeUntil = now + WORLD_CHANGE_GRACE_MS;

        String text = "back in game" + (resume ? ", Auto Farm resumed." : ".");
        log(text);
        notifyDiscord("**Reconnect for Farming**: " + text);
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
        if (state == State.IDLE) return;

        ModConfig.ReconnectCategory cfg = ConfigManager.config.disconnect.reconnect;
        long now = System.currentTimeMillis();

        switch (state) {
            case COOLDOWN -> {
                if (inGame && client.player != null) {
                    cancelRecovery("player joined manually");
                    return;
                }
                if (now >= nextActionAt) {
                    pruneFailures(now, cfg);
                    schedule(State.WAIT_BEFORE_CONNECT, 0L);
                    log("Pause is over, trying again.");
                    notifyDiscord("**Reconnect for Farming**: pause is over, trying again.");
                }
            }
            case WAIT_BEFORE_CONNECT -> {
                if (inGame && client.player != null) {
                    cancelRecovery("player joined manually");
                    return;
                }
                if (now >= nextActionAt) {
                    startConnect(client, cfg);
                }
            }
            case CONNECTING -> {
                if (inGame && client.player != null) {
                    schedule(State.WAIT_AFTER_JOIN, randomMs(cfg.settleMinSeconds, cfg.settleMaxSeconds));
                    log("Joined, waiting before the skyblock command.");
                } else if (now - connectStartedAt > CONNECT_TIMEOUT_MS) {
                    onAttemptFailed(client, "Connection timed out");
                }
            }
            case WAIT_AFTER_JOIN -> {
                if (!connectedAfterJoin(client, now)) return;
                if (now >= nextActionAt) {
                    sendCommand(client, cfg.skyblockCommand);
                    schedule(State.WAIT_AFTER_SKYBLOCK, randomMs(cfg.settleMinSeconds, cfg.settleMaxSeconds));
                }
            }
            case WAIT_AFTER_SKYBLOCK -> {
                if (!connectedAfterJoin(client, now)) return;
                if (now >= nextActionAt) {
                    if (cfg.warpCommand == null || cfg.warpCommand.trim().isEmpty()) {
                        finishRecovery(client, cfg);
                    } else {
                        sendCommand(client, cfg.warpCommand);
                        schedule(State.WAIT_AFTER_WARP, randomMs(cfg.settleMinSeconds, cfg.settleMaxSeconds));
                    }
                }
            }
            case WAIT_AFTER_WARP -> {
                if (!connectedAfterJoin(client, now)) return;
                if (now >= nextActionAt) {
                    finishRecovery(client, cfg);
                }
            }
            default -> {
            }
        }
    }

    /** Backup check in case the kick screen event was missed. */
    private static boolean connectedAfterJoin(Minecraft client, long now) {
        if (inGame && client.player != null) return true;
        if (now > nextActionAt + 30_000L) {
            onAttemptFailed(client, "Lost connection after joining");
        }
        return false;
    }

    // ================= ACTIONS =================

    private static void stopFarm(Minecraft client) {
        var farm = ModuleManager.getFarmFeature();
        if (farm.isEnabled()) {
            try {
                farm.setState(client, false);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private static void leaveGame(Minecraft client, String reason) {
        try {
            ClientPacketListener listener = client.getConnection();
            if (listener != null) {
                listener.getConnection().disconnect(Component.literal("Reconnect for Farming: " + reason));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void startConnect(Minecraft client, ModConfig.ReconnectCategory cfg) {
        String address = cfg.serverAddress == null ? "" : cfg.serverAddress.trim();
        if (address.isEmpty()) {
            abort("Server address is empty");
            return;
        }

        connectStartedAt = System.currentTimeMillis();
        state = State.CONNECTING;
        log("Connecting to " + address + " (attempt " + attemptNumber() + "/" + cfg.maxAttempts + ")");

        try {
            ServerData data = new ServerData("Reconnect", address, ServerData.Type.OTHER);
            ConnectScreen.startConnecting(new TitleScreen(), client, ServerAddress.parseString(address), data, false, null);
        } catch (Exception e) {
            e.printStackTrace();
            onAttemptFailed(client, "Could not start connecting");
        }
    }

    private static void sendCommand(Minecraft client, String raw) {
        String command = raw == null ? "" : raw.trim();
        if (command.startsWith("/")) command = command.substring(1);
        if (command.isEmpty() || client.player == null) return;

        client.player.connection.sendCommand(command);
        log("Sent command: /" + command);
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

    private static void notifyDiscord(String text) {
        if (ConfigManager.config.disconnect != null && ConfigManager.config.disconnect.enabled) {
            DisconnectNotifier.send(text);
        }
    }

    private static void log(String text) {
        System.out.println("[Reconnect] " + text);
    }
}