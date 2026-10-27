package com.scg2tlm.elmomod.compat.task;

import com.scg2tlm.elmomod.compat.SC2GunCompat;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.google.common.collect.ImmutableMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

public class MaidSC2GunWalkToTarget extends Behavior<EntityMaid> {
    private final float speed;

    public MaidSC2GunWalkToTarget(float speed) {
        super(ImmutableMap.of(
                MemoryModuleType.ATTACK_TARGET, MemoryStatus.VALUE_PRESENT,
                MemoryModuleType.WALK_TARGET, MemoryStatus.REGISTERED
        ), 1200);
        this.speed = speed;
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, EntityMaid maid) {
        if (maid.isMaidInSittingPose()) return false;
        return maid.getBrain().hasMemoryValue(MemoryModuleType.ATTACK_TARGET);
    }

    @Override
    protected boolean canStillUse(ServerLevel level, EntityMaid maid, long gameTime) {
        return checkExtraStartConditions(level, maid);
    }

    @Override
    protected void stop(ServerLevel level, EntityMaid maid, long gameTime) {
        maid.getNavigation().stop();
        maid.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
    }

    @Override
    protected void tick(ServerLevel level, EntityMaid maid, long gameTime) {
        if (maid.isMaidInSittingPose()) return;
        ItemStack gun = maid.getMainHandItem();
        if (!SC2GunCompat.isSC2Gun(gun)) return;

        maid.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET).ifPresent(target -> {
            maid.setTarget(target);
            double idealRange = SC2GunCompat.getIdealRange(gun);
            double minRange = SC2GunCompat.getMinRange(gun);
            double dist = maid.distanceTo(target);

            if (dist > 8.0) {
                maid.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
                maid.getNavigation().stop();
                return;
            }

            if (dist > idealRange) {
                BehaviorUtils.setWalkAndLookTargetMemories(maid, target, speed, (int)(idealRange * 0.7));
            } else if (dist < minRange) {
                Vec3 away = maid.position().subtract(target.position()).normalize().scale(minRange * 2).add(maid.position());
                BehaviorUtils.setWalkAndLookTargetMemories(maid, BlockPos.containing(away), speed, 0);
            }
        });
    }
}