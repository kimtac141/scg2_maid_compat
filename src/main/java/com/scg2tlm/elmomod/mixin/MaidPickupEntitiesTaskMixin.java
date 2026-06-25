package com.scg2tlm.elmomod.mixin;

import com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.task.MaidPickupEntitiesTask;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.scg2tlm.elmomod.compat.SC2GunCompat;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MaidPickupEntitiesTask.class)
public class MaidPickupEntitiesTaskMixin {
    @Inject(method = "checkExtraStartConditions", at = @At("HEAD"), cancellable = true, remap = false)
    private void onCheckExtraStartConditions(ServerLevel level, EntityMaid maid, CallbackInfoReturnable<Boolean> cir) {
        if (SC2GunCompat.isSC2Gun(maid.getMainHandItem()) && maid.getBrain().hasMemoryValue(MemoryModuleType.ATTACK_TARGET)) {
            cir.setReturnValue(false);
        }
    }
}
