package com.scg2tlm.elmomod.mixin;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.scg2tlm.elmomod.compat.SC2GunCompat;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;
import net.minecraft.world.entity.ai.behavior.StopAttackingIfTargetInvalid;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.function.Predicate;

@Mixin(targets = "com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.task.MaidAttackStrafingTask")
public class MaidAttackStrafingTaskMixin {

    @Redirect(method = "createStrafeTask", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/ai/behavior/StopAttackingIfTargetInvalid;create(Ljava/util/function/Predicate;)Lnet/minecraft/world/entity/ai/behavior/BehaviorControl;"), remap = false)
    private static BehaviorControl<?> redirectStopAttacking(Predicate<LivingEntity> predicate) {
        Predicate<LivingEntity> extended = target -> {
            if (target instanceof EntityMaid maid && SC2GunCompat.isSC2Gun(maid.getMainHandItem())) {
                return true;
            }
            return predicate.test(target);
        };
        return StopAttackingIfTargetInvalid.create(extended);
    }
}
