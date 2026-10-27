package com.scg2tlm.elmomod.mixin;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import top.ribs.scguns.entity.ai.GunAttackGoal;

@Mixin(value = GunAttackGoal.class, remap = false)
public class GunAttackGoalMixin {

    @Inject(method = "canUse", at = @At("HEAD"), cancellable = true)
    private void onCanUse(CallbackInfoReturnable<Boolean> cir) {
        Goal goal = (Goal) (Object) this;
        LivingEntity shooter = null;
        try {
            var field = goal.getClass().getDeclaredField("shooter");
            field.setAccessible(true);
            shooter = (LivingEntity) field.get(goal);
        } catch (Exception ignored) {}

        if (shooter instanceof EntityMaid) {
            cir.setReturnValue(false);
        }
    }
}