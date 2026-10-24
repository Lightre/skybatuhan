package com.lightre.skybatuhan.mixin;

import com.lightre.skybatuhan.features.PestFarmingFeature;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.chat.GuiMessageSource;
import net.minecraft.client.multiplayer.chat.GuiMessageTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MessageSignature;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChatComponent.class)
public class PestChatMixin {
    @Inject(method = "addMessage", at = @At("TAIL"))
    private void onChatMessage(Component message, MessageSignature signature, GuiMessageSource source,
                               GuiMessageTag tag, CallbackInfo ci) {
        PestFarmingFeature.onChatMessage(message);
    }
}
