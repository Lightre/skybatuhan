package com.lightre.skybatuhan.mixin;

import com.lightre.skybatuhan.manager.ModuleManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public class AutoFarmInputMixin {
    @Shadow private int missTime;
    @Unique private boolean skybatuhan$hadScreen;

    @Inject(method = "tick", at = @At("HEAD"))
    private void rememberOpenScreen(CallbackInfo ci) {
        Minecraft client = (Minecraft) (Object) this;
        if (client.gui.screen() != null) skybatuhan$hadScreen = true;
    }

    @Inject(method = "handleKeybinds", at = @At("HEAD"))
    private void clearClosedMenuAttackDelay(CallbackInfo ci) {
        if (!skybatuhan$hadScreen) return;
        skybatuhan$hadScreen = false;
        Minecraft client = (Minecraft) (Object) this;
        if (ModuleManager.getFarmFeature().isEnabled() && client.gui.screen() == null
                && client.player != null && client.player.containerMenu == client.player.inventoryMenu) {
            missTime = 0;
        }
    }

    @Redirect(method = "handleKeybinds", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/MouseHandler;isMouseGrabbed()Z"))
    private boolean allowBackgroundFarming(MouseHandler mouse) {
        if (mouse.isMouseGrabbed()) return true;
        Minecraft client = (Minecraft) (Object) this;
        return ModuleManager.getFarmFeature().isEnabled()
                && client.gui.screen() == null && client.player != null
                && client.player.containerMenu == client.player.inventoryMenu;
    }
}
