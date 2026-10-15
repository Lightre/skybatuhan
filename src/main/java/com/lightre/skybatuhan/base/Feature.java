package com.lightre.skybatuhan.base;

import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;

public abstract class Feature {
    private final String name;
    private KeyMapping keyBinding;
    private volatile boolean enabled = false;

    private long runAccumulatedMs = 0L;
    private long runStartedAt = 0L;
    private boolean keepRunTime = false;

    public Feature(String name) {
        this.name = name;
    }

    public void toggle(Minecraft client) {
        setState(client, !isEnabled());
    }

    public void setState(Minecraft client, boolean state) {
        updateRunTime(state);
        this.enabled = state;

        onToggle(client, state);

        if (client.player != null) {
            client.player.sendSystemMessage(Component.literal("[SkyBatuhan] ").withStyle(ChatFormatting.GOLD).append(Component.literal(name + ": ").withStyle(ChatFormatting.GRAY)).append(Component.literal(state ? "ON" : "OFF").withStyle(state ? ChatFormatting.GREEN : ChatFormatting.RED)));
        }
    }

    /**
     * Turns the feature back on without resetting its run time. Only for restarts the mod
     * does itself (reconnect); every other start begins counting from zero.
     */
    public void resume(Minecraft client) {
        keepRunTime = true;
        try {
            setState(client, true);
        } finally {
            keepRunTime = false;
        }
    }

    /**
     * Time the feature has been on, in milliseconds (time while it was off is not counted).
     */
    public long getRunTimeMs() {
        return runAccumulatedMs + (enabled ? System.currentTimeMillis() - runStartedAt : 0L);
    }

    public void resetRunTime() {
        runAccumulatedMs = 0L;
        if (enabled) runStartedAt = System.currentTimeMillis();
    }

    private void updateRunTime(boolean state) {
        long now = System.currentTimeMillis();

        if (state && !enabled) {
            if (!keepRunTime) runAccumulatedMs = 0L;
            runStartedAt = now;
        } else if (!state && enabled) {
            runAccumulatedMs += now - runStartedAt;
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setKeyBinding(KeyMapping kb) {
        this.keyBinding = kb;
    }

    public KeyMapping getKeyBinding() {
        return keyBinding;
    }

    public String getName() {
        return name;
    }

    public abstract void onTick(Minecraft client);

    public abstract void onToggle(Minecraft client, boolean state);
}