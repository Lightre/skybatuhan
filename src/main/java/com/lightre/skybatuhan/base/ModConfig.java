package com.lightre.skybatuhan.base;

import com.google.gson.annotations.Expose;
import com.lightre.skybatuhan.manager.UpdateManager;
import com.lightre.skybatuhan.manager.Webhook;
import com.lightre.skybatuhan.manager.SessionMonitor;
import com.lightre.skybatuhan.util.Links;
import com.lightre.skybatuhan.util.ModInfo;
import com.lightre.skybatuhan.base.enums.FishingOptions.*;
import io.github.notenoughupdates.moulconfig.Config;
import io.github.notenoughupdates.moulconfig.Social;
import io.github.notenoughupdates.moulconfig.annotations.*;
import io.github.notenoughupdates.moulconfig.common.MyResourceLocation;
import io.github.notenoughupdates.moulconfig.common.text.StructuredText;

import java.util.ArrayList;
import java.util.List;

@SuppressWarnings("unused")
public class ModConfig extends Config {

    @Expose
    @Category(name = "About", desc = "Info about SkyBatuhan")
    public AboutCategory about = new AboutCategory();

    @Expose
    @Category(name = "Farming", desc = "Auto Farm settings")
    public FarmingCategory farming = new FarmingCategory();

    @Expose
    @Category(name = "Fishing", desc = "Auto Fish settings")
    public FishingCategory fishing = new FishingCategory();

    @Expose
    @Category(name = "Disconnect", desc = "Discord notifications for disconnects and world changes")
    public DisconnectCategory disconnect = new DisconnectCategory();

    @Expose
    @Category(name = "Misc", desc = "Miscellaneous settings")
    public MiscCategory misc = new MiscCategory();

    // ==========================================
    // 1. ABOUT CATEGORY & SUB-CLASSES
    // ==========================================

    public static class AboutCategory {
        @ConfigOption(name = "§a§lReleases", desc = "§7Open releases and changelog on GitHub.")
        @ConfigEditorButton(buttonText = "Open")
        public transient Runnable changelog = () -> Links.open(Links.RELEASES);

        @ConfigOption(name = "GitHub", desc = "Open the project page.")
        @ConfigEditorButton(buttonText = "Open")
        public transient Runnable openGithub = () -> Links.open(Links.GITHUB);

        @ConfigOption(name = "Report a bug", desc = "Open the issue tracker.")
        @ConfigEditorButton(buttonText = "Open")
        public transient Runnable openIssues = () -> Links.open(Links.ISSUES);

        @Expose
        @Accordion
        @ConfigOption(name = "Libraries", desc = "What SkyBatuhan is built on.")
        public LibrariesCategory libraries = new LibrariesCategory();
    }

    public static class LibrariesCategory {
        @ConfigOption(name = "Minecraft", desc = "Target Minecraft & Java runtime.")
        @ConfigEditorInfoValue
        public transient String minecraft = "§a" + ModInfo.getMinecraftVersion() + " §7(Java " + ModInfo.getJavaVersion() + ")";

        @ConfigOption(name = "Fabric Loader", desc = "Mod loader version.")
        @ConfigEditorInfoValue
        public transient String loader = "§6v" + ModInfo.getLoaderVersion();

        @ConfigOption(name = "Fabric API", desc = "Events, keybinds and hooks.")
        @ConfigEditorInfoValue
        public transient String fabricApi = "§ev" + ModInfo.getFabricApiVersion();

        @ConfigOption(name = "MoulConfig", desc = "Settings GUI library.")
        @ConfigEditorInfoValue
        public transient String moulConfig = "§bv" + ModInfo.getMoulConfigVersion();

        @ConfigOption(name = "MoulConfig on GitHub", desc = "Open the library page.")
        @ConfigEditorButton(buttonText = "Open")
        public transient Runnable openMoulConfig = () -> Links.open("https://github.com/NotEnoughUpdates/MoulConfig");

        @ConfigOption(name = "Fabric", desc = "Open the Fabric website.")
        @ConfigEditorButton(buttonText = "Open")
        public transient Runnable openFabric = () -> Links.open("https://fabricmc.net");
    }

    // ==========================================
    // 2. FARMING CATEGORY & SUB-CLASSES
    // ==========================================

    public static class FarmingCategory {
        @Expose
        @ConfigOption(name = "Auto Farm", desc = "Toggle Auto Farm on/off.")
        @ConfigEditorBoolean
        public boolean autoFarmEnabled = false;

        @Expose
        @Accordion
        @ConfigOption(name = "General Settings", desc = "")
        public GeneralSettings general = new GeneralSettings();

        @Expose
        @Accordion
        @ConfigOption(name = "Movements", desc = "")
        public FarmingMovements farmingMovements = new FarmingMovements();

        @Expose
        @Accordion
        @ConfigOption(name = "Safety Settings", desc = "")
        public SafetyCategory safety = new SafetyCategory();

        public static class GeneralSettings {
            @Expose
            @ConfigOption(name = "Point Range", desc = "Waypoint reach range. (default: 0.8)")
            @ConfigEditorSlider(minValue = 0.1f, maxValue = 3f, minStep = 0.1f)
            public double pointRange = 0.8;

            @Expose
            @ConfigOption(name = "Attack Enabled", desc = "Break the crops while auto farming.")
            @ConfigEditorBoolean
            public boolean attackEnabled = true;

            @Expose
            @ConfigOption(name = "Lock Mouse", desc = "Lock the mouse while auto farming.")
            @ConfigEditorBoolean
            public boolean lockMouse = false;
        }

        public static class FarmingMovements {
            @Expose
            @Accordion
            @ConfigOption(name = "First Movement", desc = "")
            public MoveSettings firstMove = new MoveSettings();

            @Expose
            @Accordion
            @ConfigOption(name = "Second Movement", desc = "")
            public MoveSettings secondMove = new MoveSettings();
        }

        public static class MoveSettings {
            @Expose
            @ConfigOption(name = "Forward", desc = "")
            @ConfigEditorBoolean
            public boolean forward = false;
            @Expose
            @ConfigOption(name = "Back", desc = "")
            @ConfigEditorBoolean
            public boolean back = false;
            @Expose
            @ConfigOption(name = "Left", desc = "")
            @ConfigEditorBoolean
            public boolean left = false;
            @Expose
            @ConfigOption(name = "Right", desc = "")
            @ConfigEditorBoolean
            public boolean right = false;
        }

        public static class SafetyCategory {
            @Expose
            @ConfigOption(name = "Timeout (ms)", desc = "The time required for the feature to disable and sound an alarm if the player remains stationary. (default: 3000ms)")
            @ConfigEditorSlider(minValue = 500f, maxValue = 10000f, minStep = 10f)
            public int timeoutMs = 3000;

            @Expose
            @ConfigOption(name = "Threshold", desc = "Safety threshold. (default: 0.1)")
            @ConfigEditorSlider(minValue = 0.1f, maxValue = 1f, minStep = 0.1f)
            public double threshold = 0.1;
        }
    }

    // ==========================================
    // 3. FISHING CATEGORY & SUB-CLASSES
    // ==========================================

    public static class FishingCategory {
        @Expose
        @ConfigOption(name = "Auto Fish", desc = "Toggle Auto Fish on/off.")
        @ConfigEditorBoolean
        public boolean autoFishEnabled = false;

        @Expose
        @Accordion
        @ConfigOption(name = "General Settings", desc = "")
        public GeneralSettings general = new GeneralSettings();

        @Expose
        @Accordion
        @ConfigOption(name = "Safety Settings", desc = "")
        public SafetyCategory safety = new SafetyCategory();

        public static class GeneralSettings {
            @Expose
            @ConfigOption(name = "Fishing Mode", desc = "Vanilla or SkyBlock.")
            @ConfigEditorDropdown
            public FishMode fishMode = FishMode.VANILLA;

            @Expose
            @ConfigOption(name = "Jump On Reel", desc = "Jump when reeling in the rod.")
            @ConfigEditorBoolean
            public boolean reelJump = false;

            @Expose
            @ConfigOption(name = "Enable Action Slot", desc = "Automatically use the weapon in your selected slot to kill sea creatures.")
            @ConfigEditorBoolean
            public boolean useActionSlot = false;

            @Expose
            @ConfigOption(name = "Action Slot", desc = "The slot number of the item used to right-click ability for killing sea creatures.")
            @ConfigEditorDropdown
            public ActionSlot actionSlot = ActionSlot.SLOT_3;

            @Expose
            @ConfigOption(name = "Lock Mouse", desc = "Lock the mouse while auto fishing.")
            @ConfigEditorBoolean
            public boolean lockMouse = false;
        }

        public static class SafetyCategory {
            @Expose
            @ConfigOption(name = "Min Reel Delay (ms)", desc = "Minimum random delay before reeling in the rod after a catch to simulate human reaction time. (default: 400ms)")
            @ConfigEditorSlider(minValue = 10f, maxValue = 2000f, minStep = 10f)
            public double minReelDelay = 400.0;

            @Expose
            @ConfigOption(name = "Max Reel Delay (ms)", desc = "Maximum random delay before reeling in the rod after a catch. (default: 1000ms)")
            @ConfigEditorSlider(minValue = 10f, maxValue = 2000f, minStep = 10f)
            public double maxReelDelay = 1000.0;

            @Expose
            @ConfigOption(name = "Min Cast Delay (ms)", desc = "Minimum random delay before casting the rod again. (default: 200ms)")
            @ConfigEditorSlider(minValue = 200f, maxValue = 2000f, minStep = 10f)
            public double minCastDelay = 200.0;

            @Expose
            @ConfigOption(name = "Max Cast Delay (ms)", desc = "Maximum random delay before casting the rod again. (default: 1000ms)")
            @ConfigEditorSlider(minValue = 200f, maxValue = 2000f, minStep = 10f)
            public double maxCastDelay = 1000.0;

            @Expose
            @ConfigOption(name = "AFK Timeout (s)", desc = "Time in seconds of inactivity (no fish caught) before sounding an alarm and disabling the feature. (default: 30s)")
            @ConfigEditorSlider(minValue = 5f, maxValue = 180f, minStep = 1f)
            public double afkTimeoutSeconds = 30.0;

            public static final long MIN_CAST_DELAY_MS = 200;
        }
    }

    // ==========================================
    // 4. DISCONNECT CATEGORY & SUB-CLASSES
    // ==========================================

    public static class DisconnectCategory {
        @Expose
        @ConfigOption(name = "Enabled", desc = "Toggle Discord notifications on/off.")
        @ConfigEditorBoolean
        public boolean enabled = false;

        @Expose
        @ConfigOption(name = "Webhook URL", desc = "Discord channel webhook URL. Keep it private.")
        @ConfigEditorText
        public String webhookUrl = "";

        @Expose
        @ConfigOption(name = "Discord User ID", desc = "Your numeric ID, used to ping you.")
        @ConfigEditorText
        public String discordUserId = "";

        @Expose
        @ConfigOption(name = "Notify On Disconnect", desc = "Message when the server kicks you or the connection drops.")
        @ConfigEditorBoolean
        public boolean notifyDisconnect = true;

        @Expose
        @ConfigOption(name = "Notify On World Change", desc = "Message when you move to another world or dimension.")
        @ConfigEditorBoolean
        public boolean notifyWorldChange = true;

        @ConfigOption(name = "Send Test Message", desc = "Sends a test message to check the webhook.")
        @ConfigEditorButton(buttonText = "Send")
        public transient Runnable sendTest = Webhook::sendTest;

        @Expose
        @ConfigOption(name = "Reconnect for Farming", desc = "Leave, wait and rejoin after a disconnect or world change.")
        @Accordion
        public ReconnectCategory reconnect = new ReconnectCategory();
    }

    public static class ReconnectCategory {
        @Expose
        @ConfigOption(name = "Enabled", desc = "Toggle Reconnect for Farming on/off.")
        @ConfigEditorBoolean
        public boolean enabled = false;

        @Expose
        @ConfigOption(name = "Server Address", desc = "Fallback server. The server you were just on is used first.")
        @ConfigEditorText
        public String serverAddress = "hypixel.net";

        @Expose
        @ConfigOption(name = "Trigger On Disconnect", desc = "Start when the server kicks you or the connection drops.")
        @ConfigEditorBoolean
        public boolean onDisconnect = true;

        @Expose
        @ConfigOption(name = "Trigger On World Change", desc = "Start when the world changes.")
        @ConfigEditorBoolean
        public boolean onWorldChange = true;

        @Expose
        @ConfigOption(name = "Min Wait (s)", desc = "Shortest wait before the first reconnect. (default: 30s)")
        @ConfigEditorSlider(minValue = 5f, maxValue = 300f, minStep = 1f)
        public int minWaitSeconds = 30;

        @Expose
        @ConfigOption(name = "Max Wait (s)", desc = "Longest wait before the first reconnect. (default: 60s)")
        @ConfigEditorSlider(minValue = 5f, maxValue = 300f, minStep = 1f)
        public int maxWaitSeconds = 60;

        @Expose
        @ConfigOption(name = "Retry Wait (s)", desc = "Wait after a failed attempt. (default: 60s)")
        @ConfigEditorSlider(minValue = 10f, maxValue = 300f, minStep = 1f)
        public int retryWaitSeconds = 60;

        @Expose
        @ConfigOption(name = "Max Failed Attempts", desc = "Failed attempts allowed inside the time window before pausing. (default: 3)")
        @ConfigEditorSlider(minValue = 1f, maxValue = 10f, minStep = 1f)
        public int maxAttempts = 3;

        @Expose
        @ConfigOption(name = "Attempt Memory (min)", desc = "How long failed attempts are remembered. After this the pause ends and it tries again. (default: 60min)")
        @ConfigEditorSlider(minValue = 5f, maxValue = 240f, minStep = 5f)
        public int attemptWindowMinutes = 60;

        @ConfigOption(name = "Reset Attempts", desc = "Forget past failed attempts and end a pause.")
        @ConfigEditorButton(buttonText = "Reset")
        public transient Runnable resetAttempts = SessionMonitor::resetAttempts;

        @Expose
        @ConfigOption(name = "Settle Min (s)", desc = "Shortest wait after joining and after each command. (default: 10s)")
        @ConfigEditorSlider(minValue = 3f, maxValue = 60f, minStep = 1f)
        public int settleMinSeconds = 10;

        @Expose
        @ConfigOption(name = "Settle Max (s)", desc = "Longest wait after joining and after each command. (default: 15s)")
        @ConfigEditorSlider(minValue = 3f, maxValue = 60f, minStep = 1f)
        public int settleMaxSeconds = 15;

        @Expose
        @ConfigOption(name = "Lobby Command", desc = "Sent first when the world changed but you are still connected. Leave empty to skip.")
        @ConfigEditorText
        public String lobbyCommand = "/lobby";

        @Expose
        @ConfigOption(name = "Join Command", desc = "Command that enters SkyBlock from the lobby.")
        @ConfigEditorText
        public String skyblockCommand = "/skyblock";

        @Expose
        @ConfigOption(name = "Warp Command", desc = "Second command, before Auto Farm starts. Leave empty to skip.")
        @ConfigEditorText
        public String warpCommand = "/warp garden";

        @Expose
        @ConfigOption(name = "Resume Auto Farm", desc = "Turn Auto Farm back on at the end. (only if it was on before)")
        @ConfigEditorBoolean
        public boolean resumeFarming = true;
    }

    // ==========================================
    // 5. MISC CATEGORY & SUB-CLASSES
    // ==========================================

    public static class MiscCategory {
        @Expose
        @ConfigOption(name = "Dark Auction Notifier", desc = "Receive notifications when a dark auction starts.")
        @ConfigEditorBoolean
        public boolean darkAuctionNotifier = false;
    }

    // ==========================================
    // UTILITY METHODS (Version Check, Links, etc.)
    // ==========================================

    @Override
    public StructuredText getTitle() {
        String installed = ModInfo.getVersion();
        StructuredText.Mutable title = StructuredText.of("SkyBatuhan").aqua().append(StructuredText.of(" v" + installed + " by ").grey()).append(StructuredText.of("Lightre, Peregrints").red());

        if (UpdateManager.isUpdateAvailable()) {
            title.append(StructuredText.of(" (v" + UpdateManager.getLatestVersion() + " available)").green());
        }
        return title;
    }

    @Override
    public boolean isValidRunnable(int runnableId) {
        return false;
    }

    @Override
    public List<Social> getSocials() {
        List<Social> list = new ArrayList<>();
        list.add(Social.forLink(StructuredText.of("GitHub"), new MyResourceLocation("skybatuhan", "textures/github.png"), Links.GITHUB));
        return list;
    }

    @Override
    public boolean shouldAutoFocusSearchbar() {
        return true;
    }
}