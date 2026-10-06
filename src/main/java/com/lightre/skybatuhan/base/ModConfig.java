package com.lightre.skybatuhan.base;

import com.google.gson.annotations.Expose;
import com.lightre.skybatuhan.manager.DisconnectNotifier;
import io.github.notenoughupdates.moulconfig.Config;
import io.github.notenoughupdates.moulconfig.Social;
import io.github.notenoughupdates.moulconfig.annotations.*;
import io.github.notenoughupdates.moulconfig.common.ClickType;
import io.github.notenoughupdates.moulconfig.common.IMinecraft;
import io.github.notenoughupdates.moulconfig.common.MyResourceLocation;
import io.github.notenoughupdates.moulconfig.common.text.StructuredText;
import net.fabricmc.loader.api.FabricLoader;

import java.util.ArrayList;
import java.util.List;

public class ModConfig extends Config {
    @Override
    public StructuredText getTitle() {
        checkLatestVersion();
        String installed = FabricLoader.getInstance()
                .getModContainer("skybatuhan")
                .map(c -> c.getMetadata().getVersion().getFriendlyString())
                .orElse("?");
        String latest = latestVersion;
        String update = (latest != null && !latest.equals(installed))
                ? " (v" + latest + " available)"
                : "";
        return StructuredText.of("SkyBatuhan").aqua()
                .append(StructuredText.of(" v" + installed + " by ").grey())
                .append(StructuredText.of("Lightre, Peregrints").red())
                .append(StructuredText.of(update).green());
    }

    @Override
    public boolean isValidRunnable(int runnableId) {
        return false;
    }

    @Override
    public List<Social> getSocials() {
        List<Social> list = new ArrayList<>();
        list.add(Social.forLink(
                StructuredText.of("GitHub"),
                new MyResourceLocation("skybatuhan", "textures/github.png"),
                "https://github.com/lightre/skybatuhan"
        ));
        return list;
    }

    private static void openLink(String url) {
        try {
            String os = System.getProperty("os.name", "").toLowerCase();
            if (os.contains("win")) {
                new ProcessBuilder("rundll32", "url.dll,FileProtocolHandler", url).start();
            } else if (os.contains("mac")) {
                new ProcessBuilder("open", url).start();
            } else {
                new ProcessBuilder("xdg-open", url).start();
            }
        } catch (Exception e) {
            e.printStackTrace();
            IMinecraft.INSTANCE.sendClickableChatMessage(
                    StructuredText.of("Click here to open the link"),
                    url,
                    ClickType.OPEN_LINK
            );
        }
    }

    private static volatile String latestVersion = null;
    private static boolean versionCheckStarted = false;

    private static void checkLatestVersion() {
        if (versionCheckStarted) return;
        versionCheckStarted = true;
        Thread t = new Thread(() -> {
            try {
                java.net.http.HttpClient client = java.net.http.HttpClient.newHttpClient();
                java.net.http.HttpRequest req = java.net.http.HttpRequest.newBuilder()
                        .uri(java.net.URI.create("https://api.github.com/repos/lightre/skybatuhan/releases/latest"))
                        .header("Accept", "application/vnd.github+json")
                        .build();
                java.net.http.HttpResponse<String> res =
                        client.send(req, java.net.http.HttpResponse.BodyHandlers.ofString());
                if (res.statusCode() == 200) {
                    String tag = com.google.gson.JsonParser.parseString(res.body())
                            .getAsJsonObject().get("tag_name").getAsString();
                    latestVersion = tag.startsWith("v") ? tag.substring(1) : tag;
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }, "SkyBatuhan-VersionCheck");
        t.setDaemon(true);
        t.start();
    }

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


    public static class AboutCategory {
        @ConfigOption(name = "§a§lv" + com.lightre.skybatuhan.BuildInfo.VERSION, desc = "§7Changelog on GitHub")
        @ConfigEditorButton(buttonText = "Open")
        public transient Runnable changelog = () -> openLink("https://github.com/lightre/skybatuhan/releases");

        @Expose
        @Accordion
        @ConfigOption(name = "Libraries", desc = "What SkyBatuhan is built on")
        public LibrariesCategory libraries = new LibrariesCategory();

        @ConfigOption(name = "GitHub", desc = "Open the project page")
        @ConfigEditorButton(buttonText = "Open")
        public transient Runnable openGithub = () -> openLink("https://github.com/lightre/skybatuhan");

        @ConfigOption(name = "Report a bug", desc = "Open the issue tracker")
        @ConfigEditorButton(buttonText = "Open")
        public transient Runnable openIssues = () -> openLink("https://github.com/lightre/skybatuhan/issues");
    }

    public static class LibrariesCategory {
        @ConfigOption(name = "Minecraft", desc = "26.2 (Java 25)")
        @ConfigEditorInfoText
        public transient String minecraft = "";

        @ConfigOption(name = "Fabric Loader", desc = "v0.19.5, mod loading")
        @ConfigEditorInfoText
        public transient String loader = "";

        @ConfigOption(name = "Fabric API", desc = "v0.161.0, events, keybinds and rendering hooks")
        @ConfigEditorInfoText
        public transient String fabricApi = "";

        @ConfigOption(name = "MoulConfig", desc = "v4.7.2, this settings menu")
        @ConfigEditorInfoText
        public transient String moulConfig = "";

        @ConfigOption(name = "MoulConfig on GitHub", desc = "Open the library page")
        @ConfigEditorButton(buttonText = "Open")
        public transient Runnable openMoulConfig = () -> openLink("https://github.com/NotEnoughUpdates/MoulConfig");

        @ConfigOption(name = "Fabric", desc = "Open the Fabric website")
        @ConfigEditorButton(buttonText = "Open")
        public transient Runnable openFabric = () -> openLink("https://fabricmc.net");
    }

    public static class SafetyCategory {
        @Expose
        @ConfigOption(name = "Timeout (ms)", desc = "Safety timeout")
        @ConfigEditorSlider(minValue = 0f, maxValue = 10000f, minStep = 10f)
        public int timeoutMs = 3000;

        @Expose
        @ConfigOption(name = "Threshold", desc = "Safety threshold")
        @ConfigEditorSlider(minValue = 0f, maxValue = 1f, minStep = 0.1f)
        public double threshold = 0.1;
    }

    public static class FarmingCategory {
        @Expose
        @ConfigOption(name = "Auto Farm", desc = "Toggle Auto Farm on/off")
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
    }

    public static class GeneralSettings {
        @Expose
        @ConfigOption(name = "Point Range", desc = "Waypoint reach range")
        @ConfigEditorSlider(minValue = 0f, maxValue = 3f, minStep = 0.1f)
        public double pointRange = 0.8;

        @Expose
        @ConfigOption(name = "Attack Enabled", desc = "Attack nearby targets while farming")
        @ConfigEditorBoolean
        public boolean attackEnabled = true;
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
        @Expose @ConfigOption(name = "Forward", desc = "") @ConfigEditorBoolean
        public boolean forward = false;
        @Expose @ConfigOption(name = "Back", desc = "") @ConfigEditorBoolean
        public boolean back = false;
        @Expose @ConfigOption(name = "Left", desc = "") @ConfigEditorBoolean
        public boolean left = false;
        @Expose @ConfigOption(name = "Right", desc = "") @ConfigEditorBoolean
        public boolean right = false;
    }

    public static class FishingCategory {
        @Expose
        @ConfigOption(name = "Auto Fish", desc = "Toggle Auto Fish on/off")
        @ConfigEditorBoolean
        public boolean autoFishEnabled = false;

        @Expose
        @ConfigOption(name = "Fishing Mode", desc = "Vanilla or Skyblock")
        @ConfigEditorDropdown(values = {"Vanilla", "Skyblock"})
        public String fishMode = "Vanilla";

        @Expose
        @ConfigOption(name = "Jump On Reel", desc = "Jump when reeling in the rod")
        @ConfigEditorBoolean
        public boolean reelJump = false;

        @Expose
        @ConfigOption(name = "Min Reel Delay (ms)", desc = "")
        @ConfigEditorSlider(minValue = 10f, maxValue = 2000f, minStep = 10f)
        public double minReelDelay = 400.0;

        @Expose
        @ConfigOption(name = "Max Reel Delay (ms)", desc = "")
        @ConfigEditorSlider(minValue = 10f, maxValue = 2000f, minStep = 10f)
        public double maxReelDelay = 1000.0;

        @Expose
        @ConfigOption(name = "Min Cast Delay (ms)", desc = "")
        @ConfigEditorSlider(minValue = 200f, maxValue = 2000f, minStep = 10f)
        public double minCastDelay = 200.0;

        @Expose
        @ConfigOption(name = "Max Cast Delay (ms)", desc = "")
        @ConfigEditorSlider(minValue = 200f, maxValue = 2000f, minStep = 10f)
        public double maxCastDelay = 1000.0;

        @Expose
        @ConfigOption(name = "AFK Timeout (s)", desc = "")
        @ConfigEditorSlider(minValue = 5f, maxValue = 180f, minStep = 1f)
        public double afkTimeoutSeconds = 30.0;

        public static final long MIN_CAST_DELAY_MS = 200;

        @Expose
        @ConfigOption(name = "Enable Action Slot", desc = "")
        @ConfigEditorBoolean
        public boolean useActionSlot = false;

        @Expose
        @ConfigOption(name = "Action Slot", desc = "")
        @ConfigEditorDropdown(values = {"Slot 1", "Slot 2", "Slot 3", "Slot 4", "Slot 5", "Slot 6", "Slot 7", "Slot 8", "Slot 9"})
        public String actionSlot = "Slot 3";
    }

    public static class DisconnectCategory {
        @Expose
        @ConfigOption(name = "Enabled", desc = "Turn Discord notifications on or off")
        @ConfigEditorBoolean
        public boolean enabled = false;

        @Expose
        @ConfigOption(name = "Webhook URL", desc = "Discord channel webhook URL. Keep it private.")
        @ConfigEditorText
        public String webhookUrl = "";

        @Expose
        @ConfigOption(name = "Discord User ID", desc = "Your numeric ID, used to ping you")
        @ConfigEditorText
        public String discordUserId = "";

        @Expose
        @ConfigOption(name = "Notify On Disconnect", desc = "Message when the server kicks you or the connection drops")
        @ConfigEditorBoolean
        public boolean notifyDisconnect = true;

        @Expose
        @ConfigOption(name = "Notify On World Change", desc = "Message when you move to another world or dimension")
        @ConfigEditorBoolean
        public boolean notifyWorldChange = true;

        @ConfigOption(name = "Send Test Message", desc = "Sends a test message to check the webhook")
        @ConfigEditorButton(buttonText = "Send")
        public transient Runnable sendTest = DisconnectNotifier::sendTest;

        @Expose
        @ConfigOption(name = "Reconnect for Farming", desc = "Leave, wait and rejoin after a disconnect or world change")
        @Accordion
        public ReconnectCategory reconnect = new ReconnectCategory();
    }


    public static class ReconnectCategory {
        @Expose
        @ConfigOption(name = "Enabled", desc = "Turn Reconnect for Farming on or off")
        @ConfigEditorBoolean
        public boolean enabled = false;

        @Expose
        @ConfigOption(name = "Server Address", desc = "Server to rejoin")
        @ConfigEditorText
        public String serverAddress = "eu.hypixel.net";

        @Expose
        @ConfigOption(name = "Trigger On Disconnect", desc = "Start when the server kicks you or the connection drops")
        @ConfigEditorBoolean
        public boolean onDisconnect = true;

        @Expose
        @ConfigOption(name = "Trigger On World Change", desc = "Leave the server when the world changes")
        @ConfigEditorBoolean
        public boolean onWorldChange = true;

        @Expose
        @ConfigOption(name = "Only While Farming", desc = "Do nothing if Auto Farm is off")
        @ConfigEditorBoolean
        public boolean onlyWhenActive = true;

        @Expose
        @ConfigOption(name = "Min Wait (s)", desc = "Shortest wait before the first reconnect")
        @ConfigEditorSlider(minValue = 5f, maxValue = 300f, minStep = 1f)
        public int minWaitSeconds = 30;

        @Expose
        @ConfigOption(name = "Max Wait (s)", desc = "Longest wait before the first reconnect")
        @ConfigEditorSlider(minValue = 5f, maxValue = 300f, minStep = 1f)
        public int maxWaitSeconds = 60;

        @Expose
        @ConfigOption(name = "Retry Wait (s)", desc = "Wait after a failed attempt")
        @ConfigEditorSlider(minValue = 10f, maxValue = 300f, minStep = 1f)
        public int retryWaitSeconds = 60;

        @Expose
        @ConfigOption(name = "Max Failed Attempts", desc = "Failed attempts allowed inside the time window before pausing")
        @ConfigEditorSlider(minValue = 1f, maxValue = 10f, minStep = 1f)
        public int maxAttempts = 3;

        @Expose
        @ConfigOption(name = "Attempt Memory (min)", desc = "How long failed attempts are remembered. After this the pause ends and it tries again")
        @ConfigEditorSlider(minValue = 5f, maxValue = 240f, minStep = 5f)
        public int attemptWindowMinutes = 60;

        @Expose
        @ConfigOption(name = "Settle Min (s)", desc = "Shortest wait after joining and after each command")
        @ConfigEditorSlider(minValue = 3f, maxValue = 60f, minStep = 1f)
        public int settleMinSeconds = 10;

        @Expose
        @ConfigOption(name = "Settle Max (s)", desc = "Longest wait after joining and after each command")
        @ConfigEditorSlider(minValue = 3f, maxValue = 60f, minStep = 1f)
        public int settleMaxSeconds = 15;

        @Expose
        @ConfigOption(name = "Join Command", desc = "First command after joining")
        @ConfigEditorText
        public String skyblockCommand = "/skyblock";

        @Expose
        @ConfigOption(name = "Warp Command", desc = "Second command, before Auto Farm starts. Leave empty to skip")
        @ConfigEditorText
        public String warpCommand = "/warp garden";

        @Expose
        @ConfigOption(name = "Resume Auto Farm", desc = "Turn Auto Farm back on at the end (only if it was on before)")
        @ConfigEditorBoolean
        public boolean resumeFarming = true;
    }
}