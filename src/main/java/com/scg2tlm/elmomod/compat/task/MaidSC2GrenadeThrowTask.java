package com.scg2tlm.elmomod.compat.task;

import com.scg2tlm.elmomod.compat.SC2GunCompat;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.google.common.collect.ImmutableMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.entity.item.ItemEntity;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import top.ribs.scguns.entity.throwable.ThrowableGrenadeEntity;
import java.util.List;

public class MaidSC2GrenadeThrowTask extends Behavior<EntityMaid> {
    private int cooldown = 0;
    private int strafeTime = 0;
    private boolean strafeClockwise = true;
    private int postThrowTimer = 0;

    public MaidSC2GrenadeThrowTask() {
        super(ImmutableMap.of(
                MemoryModuleType.ATTACK_TARGET, MemoryStatus.VALUE_PRESENT,
                MemoryModuleType.WALK_TARGET, MemoryStatus.REGISTERED,
                MemoryModuleType.LOOK_TARGET, MemoryStatus.REGISTERED
        ), 200);
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, EntityMaid maid) {
        if (maid.isMaidInSittingPose()) return false;
        if (!SC2GunCompat.isGrenade(maid.getMainHandItem()) && !hasGrenadeInInventory(maid)) return false;
        var targetOpt = maid.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET);
        return targetOpt.isPresent() && maid.canSee(targetOpt.get());
    }

    @Override
    protected boolean canStillUse(ServerLevel level, EntityMaid maid, long gameTime) {
        if (maid.isMaidInSittingPose()) return false;
        if (!SC2GunCompat.isGrenade(maid.getMainHandItem()) && !hasGrenadeInInventory(maid)) return false;
        var targetOpt = maid.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET);
        return targetOpt.isPresent() && targetOpt.get().isAlive();
    }

    @Override
    protected void start(ServerLevel level, EntityMaid maid, long gameTime) {
        maid.setSwingingArms(true);
        cooldown = 0;
        strafeTime = 0;
        postThrowTimer = 0;
        strafeClockwise = maid.getRandom().nextBoolean();
    }

    @Override
    protected void stop(ServerLevel level, EntityMaid maid, long gameTime) {
        maid.setSwingingArms(false);
        maid.getNavigation().stop();
        maid.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        cooldown = 0;
        strafeTime = 0;
        postThrowTimer = 0;
    }

    @Override
    protected void tick(ServerLevel level, EntityMaid maid, long gameTime) {
        if (postThrowTimer > 0) {
            postThrowTimer--;
            if (postThrowTimer > 0 && fleeFromNearbyGrenades(level, maid)) return;
        }

        ensureGrenadeInHand(maid);

        strafeTime++;
        if (strafeTime >= 60) {
            strafeClockwise = !strafeClockwise;
            strafeTime = 0;
            maid.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET).ifPresent(target -> {
                double dx = target.getX() - maid.getX();
                double dz = target.getZ() - maid.getZ();
                double len = Math.sqrt(dx * dx + dz * dz);
                if (len > 0.1) {
                    double perpX = strafeClockwise ? -dz : dz;
                    double perpZ = strafeClockwise ? dx : -dx;
                    maid.getBrain().setMemory(MemoryModuleType.WALK_TARGET,
                            new WalkTarget(
                                    new BlockPos(
                                            (int) (maid.getX() + perpX / len * 5.0),
                                            (int) maid.getY(),
                                            (int) (maid.getZ() + perpZ / len * 5.0)
                                    ), 0.6f, 2));
                }
            });
        }

        if (cooldown > 0) {
            cooldown--;
            return;
        }

        ItemStack stack = maid.getMainHandItem();
        if (!SC2GunCompat.isGrenade(stack)) return;

        final ItemStack grenadeStack = stack;
        maid.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET).ifPresent(target -> {
            BehaviorUtils.lookAtEntity(maid, target);

            if (maid.canSee(target)) {
                SC2GunCompat.performGrenadeThrow(maid, target, grenadeStack);
                cooldown = 40;
                postThrowTimer = 20;
            }
        });
    }

    private static void ensureGrenadeInHand(EntityMaid maid) {
        if (SC2GunCompat.isGrenade(maid.getMainHandItem())) return;
        equipGrenadeFromInventory(maid);
    }

    private boolean fleeFromNearbyGrenades(ServerLevel level, EntityMaid maid) {
        AABB searchBox = maid.getBoundingBox().inflate(10.0);
        List<ThrowableGrenadeEntity> grenades = level.getEntitiesOfClass(ThrowableGrenadeEntity.class, searchBox,
                g -> g.isAlive() && g.distanceTo(maid) < 10.0);
        if (grenades.isEmpty()) return false;

        double closestDist = Double.MAX_VALUE;
        double toGrenadeX = 0;
        double toGrenadeZ = 0;
        for (ThrowableGrenadeEntity g : grenades) {
            double d = g.distanceTo(maid);
            if (d < closestDist) {
                closestDist = d;
                toGrenadeX = maid.getX() - g.getX();
                toGrenadeZ = maid.getZ() - g.getZ();
            }
        }

        if (closestDist < 5.0) {
            double len = Math.sqrt(toGrenadeX * toGrenadeX + toGrenadeZ * toGrenadeZ);
            if (len < 0.01) { toGrenadeX = 1.0; toGrenadeZ = 0.0; }
            else { toGrenadeX /= len; toGrenadeZ /= len; }
            maid.getBrain().setMemory(MemoryModuleType.WALK_TARGET,
                    new WalkTarget(new BlockPos(
                            (int) (maid.getX() + toGrenadeX * 5.0),
                            (int) maid.getY(),
                            (int) (maid.getZ() + toGrenadeZ * 5.0)
                    ), 1.0f, 0));
            return true;
        }
        return false;
    }

    private static boolean hasGrenadeInInventory(EntityMaid maid) {
        IItemHandler inv = maid.getAvailableInv(false);
        for (int i = 0; i < inv.getSlots(); i++) {
            if (SC2GunCompat.isGrenade(inv.getStackInSlot(i))) return true;
        }
        return false;
    }

    private static void equipGrenadeFromInventory(EntityMaid maid) {
        IItemHandler inv = maid.getAvailableInv(false);
        for (int i = 0; i < inv.getSlots(); i++) {
            ItemStack slot = inv.getStackInSlot(i);
            if (SC2GunCompat.isGrenade(slot)) {
                ItemStack taken = inv.extractItem(i, 1, false);
                ItemStack oldMainHand = maid.getMainHandItem();
                if (!oldMainHand.isEmpty()) {
                    ItemStack remaining = ItemHandlerHelper.insertItemStacked(inv, oldMainHand, false);
                    if (!remaining.isEmpty()) {
                        giveToOwnerOrDrop(maid, remaining);
                    }
                }
                maid.setItemSlot(EquipmentSlot.MAINHAND, taken);
                return;
            }
        }
    }

    private static void giveToOwnerOrDrop(EntityMaid maid, ItemStack stack) {
        LivingEntity owner = maid.getOwner();
        if (owner instanceof Player player) {
            if (!player.getInventory().add(stack)) {
                player.drop(stack, false);
            }
        } else {
            maid.level().addFreshEntity(new ItemEntity(maid.level(),
                    maid.getX(), maid.getY() + 0.5, maid.getZ(), stack));
        }
    }
}
