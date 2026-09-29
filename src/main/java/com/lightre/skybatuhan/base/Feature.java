package com.lightre.skybatuhan.base;

import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;

public abstract class Feature {
    private final String name;
    private KeyMapping keyBinding;
    private volatile boolean enabled = false;

    public Feature(String name) {
        this.name = name;
    }

    public void toggle(Minecraft client) {
        setState(client, !isEnabled());
    }

    public void setState(Minecraft client, boolean state) {
        this.enabled = state;

        onToggle(client, state);

        if (client.player != null) {
            client.player.sendSystemMessage(Component.literal("[SkyBatuhan] ").withStyle(ChatFormatting.GOLD).append(Component.literal(name + ": ").withStyle(ChatFormatting.GRAY)).append(Component.literal(state ? "ON" : "OFF").withStyle(state ? ChatFormatting.GREEN : ChatFormatting.RED)));
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