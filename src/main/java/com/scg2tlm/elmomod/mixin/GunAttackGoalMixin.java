package com.scg2tlm.elmomod.mixin;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.world.entity.PathfinderMob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import top.ribs.scguns.entity.ai.GunAttackGoal;

@Mixin(GunAttackGoal.class)
public abstract class GunAttackGoalMixin {
    @Accessor(value = "shooter", remap = false)
    abstract PathfinderMob getShooter();

    @Inject(method = "canUse", at = @At("HEAD"), cancellable = true, remap = false)
    private void onCanUse(CallbackInfoReturnable<Boolean> cir) {
        if (getShooter() instanceof EntityMaid) {
            cir.setReturnValue(false);
        }
    }
}
