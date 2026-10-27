package com.scg2tlm.elmomod.compat.task;

import com.scg2tlm.elmomod.compat.SC2GunCompat;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.google.common.collect.ImmutableMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.util.Mth;

public class SC2GunStrafingTask extends Behavior<EntityMaid> {
    private boolean strafingClockwise;
    private boolean strafingBackwards;
    private int strafingTime = -1;

    public SC2GunStrafingTask() {
        super(ImmutableMap.of(
                MemoryModuleType.WALK_TARGET, MemoryStatus.VALUE_ABSENT,
                MemoryModuleType.LOOK_TARGET, MemoryStatus.REGISTERED,
                MemoryModuleType.ATTACK_TARGET, MemoryStatus.VALUE_PRESENT,
                MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES, MemoryStatus.VALUE_PRESENT
        ), 1200);
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, EntityMaid maid) {
        if (maid.isMaidInSittingPose()) return false;
        if (!SC2GunCompat.isSC2Gun(maid.getMainHandItem())) return false;
        return maid.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET)
                .filter(t -> maid.canSee(t)).isPresent();
    }

    @Override
    protected boolean canStillUse(ServerLevel level, EntityMaid maid, long gameTime) {
        return checkExtraStartConditions(level, maid);
    }

    @Override
    protected void start(ServerLevel level, EntityMaid maid, long gameTime) {
        maid.setSwingingArms(true);
    }

    @Override
    protected void stop(ServerLevel level, EntityMaid maid, long gameTime) {
        maid.setSwingingArms(false);
        maid.getMoveControl().strafe(0, 0);
    }

    @Override
    protected void tick(ServerLevel level, EntityMaid maid, long gameTime) {
        if (maid.isMaidInSittingPose()) return;
        ItemStack gun = maid.getMainHandItem();
        if (!SC2GunCompat.isSC2Gun(gun)) return;

        maid.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET).ifPresent(target -> {
            maid.setTarget(target);
            double dist = maid.distanceTo(target);
            double maxStrafeDist = 8.0;

            if (dist < maxStrafeDist) {
                strafingTime++;
            } else {
                strafingTime = -1;
            }

            if (strafingTime >= 20) {
                if (maid.getRandom().nextFloat() < 0.3) {
                    strafingClockwise = !strafingClockwise;
                }
                if (maid.getRandom().nextFloat() < 0.3) {
                    strafingBackwards = !strafingBackwards;
                }
                strafingTime = 0;
            }

            if (strafingTime > -1) {
                if (dist < 6.0) {
                    strafingBackwards = true;
                } else {
                    strafingBackwards = false;
                }

                maid.getMoveControl().strafe(
                        strafingBackwards ? -0.4f : 0.0f,
                        strafingClockwise ? 0.3f : -0.3f
                );
                maid.setYRot(Mth.rotateIfNecessary(maid.getYRot(), maid.yHeadRot, 0));
            } else {
                maid.getMoveControl().strafe(0, 0);
            }

            BehaviorUtils.lookAtEntity(maid, target);
        });
    }
}
