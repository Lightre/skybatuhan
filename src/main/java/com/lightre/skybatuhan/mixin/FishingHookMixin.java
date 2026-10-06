package com.lightre.skybatuhan.mixin;

import com.lightre.skybatuhan.manager.ConfigManager;
import com.lightre.skybatuhan.manager.ModuleManager;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FishingHook.class)
public class FishingHookMixin {

    @Shadow
    private static @Final EntityDataAccessor<Boolean> DATA_BITING;

    @Inject(method = "onSyncedDataUpdated", at = @At("TAIL"))
    private void onClientTrackedDataChanged(EntityDataAccessor<?> accessor, CallbackInfo ci) {
        if (ConfigManager.config == null || "Skyblock".equalsIgnoreCase(String.valueOf(ConfigManager.config.fishing.fishMode))) {
            return;
        }

        FishingHook bobber = (FishingHook) (Object) this;
        Minecraft client = Minecraft.getInstance();

        if (bobber.level() != null && bobber.level().isClientSide()) {

            if (DATA_BITING.equals(accessor) && bobber.getEntityData().get(DATA_BITING)) {

                if (client.player != null && bobber.getPlayerOwner() != null &&
                        bobber.getPlayerOwner().getUUID().equals(client.player.getUUID())) {

                    ModuleManager.getFishFeature().onFishHooked(client);
                }
            }
        }
    }
}