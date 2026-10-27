package com.scg2tlm.elmomod.mixin;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.ribs.scguns.item.WaraxeItem;

@Mixin(WaraxeItem.class)
public class WaraxeItemMixin {
    @Inject(method = "applyHoldingPose", at = @At("HEAD"), cancellable = true, remap = false)
    private void scg2tlm$cancelApplyHoldingPose(HumanoidModel<?> model, LivingEntity entity, CallbackInfo ci) {
        ci.cancel();
    }
}
