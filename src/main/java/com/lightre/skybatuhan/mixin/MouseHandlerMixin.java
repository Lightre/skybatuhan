package com.lightre.skybatuhan.mixin;

import com.lightre.skybatuhan.manager.ConfigManager;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public class MouseHandlerMixin {
    @Inject(method = "turnPlayer", at = @At("HEAD"), cancellable = true)
    private void onTurnPlayer(double mousea, CallbackInfo ci) {

        boolean isFarmingLocked = ConfigManager.config.farming.autoFarmEnabled && ConfigManager.config.farming.general.lockMouse;
        boolean isFishingLocked = ConfigManager.config.fishing.autoFishEnabled && ConfigManager.config.fishing.general.lockMouse;

        if (isFarmingLocked || isFishingLocked) {
            ci.cancel();
        }
    }
}