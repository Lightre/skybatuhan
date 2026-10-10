package com.lightre.skybatuhan.util;

import com.lightre.skybatuhan.SkyBatuhan;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.util.Util;

import java.net.URI;

public final class Links {
    public static final String GITHUB = "https://github.com/Lightre/skybatuhan";
    public static final String RELEASES = GITHUB + "/releases";
    public static final String LATEST_RELEASE = RELEASES + "/latest";
    public static final String ISSUES = GITHUB + "/issues";
    public static final String RELEASES_API = "https://api.github.com/repos/Lightre/skybatuhan/releases/latest";

    private Links() {
    }

    /** Opens a link through the vanilla confirmation screen and returns to the previous screen. */
    public static void open(String url) {
        Minecraft client = Minecraft.getInstance();
        Screen previousScreen = client.gui.screen();

        client.execute(() -> client.setScreenAndShow(new ConfirmLinkScreen(confirmed -> {
            if (confirmed) {
                try {
                    Util.getPlatform().openUri(URI.create(url));
                } catch (Exception e) {
                    SkyBatuhan.LOGGER.error("Could not open link: {}", url, e);
                }
            }
            if (previousScreen != null) {
                client.setScreenAndShow(previousScreen);
            }
        }, url, false)));
    }
}