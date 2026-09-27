package com.lightre.skybatuhan.base;

import com.lightre.skybatuhan.manager.ConfigManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;

public abstract class Feature {
    private final String name;
    private KeyMapping keyBinding;

    public Feature(String name) {
        this.name = name;
        ConfigManager.config.featureStates.putIfAbsent(name, false);
    }

    public void toggle(Minecraft client) {
        boolean newState = !isEnabled();
        setState(client, newState);
    }

    public void setState(Minecraft client, boolean state) {
        ConfigManager.config.featureStates.put(name, state);
        ConfigManager.save();

        onToggle(client, state);

        if (client.player != null) {
            client.player.sendSystemMessage(Component.literal("[SkyBatuhan] ").withStyle(ChatFormatting.GOLD).append(Component.literal(name + ": ").withStyle(ChatFormatting.GRAY)).append(Component.literal(state ? "ON" : "OFF").withStyle(state ? ChatFormatting.GREEN : ChatFormatting.RED)));
        }
    }

    public boolean isEnabled() {
        return ConfigManager.config.featureStates.getOrDefault(name, false);
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