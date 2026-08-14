package com.scg2tlm.elmomod.compat.task;

import com.scg2tlm.elmomod.compat.SC2GunCompat;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.google.common.collect.ImmutableMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.item.ItemStack;

import java.util.Optional;

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
        if (maid.isMaidInSittingPose() || maid.isPassenger()) return false;
        if (!SC2GunCompat.isSC2Gun(maid.getMainHandItem())) return false;
        return maid.getBrain().hasMemoryValue(MemoryModuleType.ATTACK_TARGET);
    }

    @Override
    protected boolean canStillUse(ServerLevel level, EntityMaid maid, long gameTime) {
        if (!checkExtraStartConditions(level, maid)) return false;
        Optional<LivingEntity> targetOpt = maid.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET);
        if (targetOpt.isEmpty()) return false;
        LivingEntity target = targetOpt.get();
        if (!target.isAlive() || target.isRemoved()) {
            maid.getBrain().eraseMemory(MemoryModuleType.ATTACK_TARGET);
            maid.setTarget(null);
            return false;
        }
        ItemStack gun = maid.getMainHandItem();
        double dist = maid.distanceTo(target);
        double idealRange = SC2GunCompat.getIdealRange(gun);
        double minRange = SC2GunCompat.getMinRange(gun);
        return dist > idealRange * 1.1 || dist < minRange * 0.9;
    }

    @Override
    protected void stop(ServerLevel level, EntityMaid maid, long gameTime) {
        maid.getNavigation().stop();
        maid.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
    }

    @Override
    protected void tick(ServerLevel level, EntityMaid maid, long gameTime) {
        if (maid.isMaidInSittingPose() || maid.isPassenger()) return;
        ItemStack gun = maid.getMainHandItem();
        if (!SC2GunCompat.isSC2Gun(gun)) return;

        maid.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET).ifPresent(target -> {
            maid.setTarget(target);
            double dist = maid.distanceTo(target);

            if (dist > 8.0) {
                maid.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
                maid.getNavigation().stop();
                return;
            }

            double idealRange = SC2GunCompat.getIdealRange(gun);
            double minRange = SC2GunCompat.getMinRange(gun);
            boolean hasBayonet = SC2GunCompat.hasBayonet(gun);
            boolean outOfAmmo = SC2GunCompat.needsReload(gun) && !SC2GunCompat.hasAmmoInInventory(maid, gun);
            float meleeReach = hasBayonet ? SC2GunCompat.getMeleeReach(gun) : 0;

            // Out of ammo with bayonet -> close to melee range
            if (outOfAmmo && hasBayonet) {
                if (dist > meleeReach) {
                    BehaviorUtils.setWalkAndLookTargetMemories(maid, target, speed * 1.3f, 0);
                } else {
                    maid.getNavigation().stop();
                    maid.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
                }
                return;
            }

            if (meleeReach > 0 && dist <= meleeReach + 1.0) {
                // Target is within bayonet range - hold ground
                maid.getNavigation().stop();
                maid.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
            } else if (dist > idealRange) {
                BehaviorUtils.setWalkAndLookTargetMemories(maid, target, speed, (int)(idealRange * 0.7));
            } else if (dist < minRange) {
                if (hasBayonet && meleeReach > 0) {
                    // Has bayonet - close into melee range instead of backing away
                    BehaviorUtils.setWalkAndLookTargetMemories(maid, target, speed, 0);
                } else {
                    // Within strafing range the strafing task handles positioning, so do not set a walk target
                    maid.getNavigation().stop();
                    maid.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
                }
            } else {
                maid.getNavigation().stop();
                maid.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
            }
        });
    }
}
