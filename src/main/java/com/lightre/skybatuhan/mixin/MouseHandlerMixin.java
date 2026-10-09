package com.lightre.skybatuhan.mixin;

import com.lightre.skybatuhan.manager.ConfigManager;
import com.lightre.skybatuhan.manager.ModuleManager;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public class MouseHandlerMixin {
    @Inject(method = "turnPlayer", at = @At("HEAD"), cancellable = true)
    private void onTurnPlayer(double mousea, CallbackInfo ci) {
        var config = ConfigManager.config;
        boolean farmLocked = ModuleManager.getFarmFeature().isEnabled() && config.farming.general.lockMouse;
        boolean fishLocked = ModuleManager.getFishFeature().isEnabled() && config.fishing.general.lockMouse;

        if (farmLocked || fishLocked) {
            ci.cancel();
        }
    }
}