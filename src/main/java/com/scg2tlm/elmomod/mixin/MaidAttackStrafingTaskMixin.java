package com.scg2tlm.elmomod.mixin;

import com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.task.MaidAttackStrafingTask;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MaidAttackStrafingTask.class)
public abstract class MaidAttackStrafingTaskMixin {
    @Unique private boolean scg2tlm$strafingClockwise;
    @Unique private boolean scg2tlm$strafingBackwards;
    @Unique private int scg2tlm$strafingTime = -1;
    @Unique private int scg2tlm$navigationCooldown;

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true, remap = false)
    private void scg2tlm$onTick(ServerLevel level, EntityMaid maid, long gameTime, CallbackInfo ci) {
        if (!maid.getBrain().hasMemoryValue(MemoryModuleType.ATTACK_TARGET)) {
            maid.getNavigation().stop();
            maid.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
            ci.cancel();
            return;
        }
        LivingEntity target = maid.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET).get();
        if (target == null) {
            ci.cancel();
            return;
        }

        maid.setTarget(target);
        double dist = maid.distanceTo(target);

        if (dist < maid.searchRadius()) {
            scg2tlm$strafingTime++;
        } else {
            scg2tlm$strafingTime = -1;
        }

        if (scg2tlm$strafingTime >= 20) {
            if (maid.getRandom().nextFloat() < 0.3f) {
                scg2tlm$strafingClockwise = !scg2tlm$strafingClockwise;
            }
            if (maid.getRandom().nextFloat() < 0.3f) {
                scg2tlm$strafingBackwards = !scg2tlm$strafingBackwards;
            }
            scg2tlm$strafingTime = 0;
            scg2tlm$navigationCooldown = 0;
        }

        if (scg2tlm$strafingTime >= 0) {
            boolean backward = scg2tlm$strafingBackwards;
            if (dist > maid.searchRadius() * 0.5) {
                backward = false;
            } else if (dist < maid.searchRadius() * 0.2) {
                backward = true;
            }
            scg2tlm$strafingBackwards = backward;

            if (--scg2tlm$navigationCooldown <= 0) {
                Vec3 lookVec = maid.getLookAngle();
                Vec3 right = new Vec3(-lookVec.z, 0, lookVec.x).normalize();
                Vec3 forward = new Vec3(lookVec.x, 0, lookVec.z).normalize();

                if (forward.lengthSqr() < 0.01) forward = new Vec3(0, 0, 1);

                int fwdSign = backward ? -1 : 1;
                int strafeSign = scg2tlm$strafingClockwise ? 1 : -1;
                Vec3 move = forward.scale(fwdSign * 2.5).add(right.scale(strafeSign * 1.5));
                Vec3 dest = maid.position().add(move);

                BlockPos targetPos = BlockPos.containing(dest);
                if (maid.level().getBlockState(targetPos).isAir() || maid.level().getBlockState(targetPos.below()).isSolid()) {
                    BehaviorUtils.setWalkAndLookTargetMemories(maid, targetPos, 0.4f, 1);
                } else {
                    scg2tlm$strafingClockwise = !scg2tlm$strafingClockwise;
                }
                scg2tlm$navigationCooldown = 10;
            }
        }

        BehaviorUtils.lookAtEntity(maid, target);
        ci.cancel();
    }
}
