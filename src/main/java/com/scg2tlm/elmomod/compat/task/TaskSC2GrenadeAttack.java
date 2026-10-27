package com.scg2tlm.elmomod.compat.task;

import com.scg2tlm.elmomod.ExampleMod;
import com.scg2tlm.elmomod.compat.SC2GunCompat;
import com.github.tartaricacid.touhoulittlemaid.api.task.IRangedAttackTask;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.google.common.collect.Lists;
import com.mojang.datafixers.util.Pair;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.entity.ai.behavior.StartAttacking;
import net.minecraft.world.entity.ai.behavior.StopAttackingIfTargetInvalid;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.items.IItemHandler;
import top.ribs.scguns.entity.monster.SulfurheadEntity;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

public class TaskSC2GrenadeAttack implements IRangedAttackTask {
    public static final ResourceLocation UID = ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID, "grenade_attack");
    private static final ItemStack ICON = initIcon();

    private static ItemStack initIcon() {
        Item icon = BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("scguns", "grenade"));
        return icon != null ? icon.getDefaultInstance() : Items.CROSSBOW.getDefaultInstance();
    }

    @Override
    public ResourceLocation getUid() {
        return UID;
    }

    @Override
    public ItemStack getIcon() {
        return ICON;
    }

    @Nullable
    @Override
    public SoundEvent getAmbientSound(EntityMaid maid) {
        return null;
    }

    @Override
    public boolean enableLookAndRandomWalk(EntityMaid maid) {
        return false;
    }

    @Override
    public List<Pair<Integer, BehaviorControl<? super EntityMaid>>> createBrainTasks(EntityMaid maid) {
        BehaviorControl<EntityMaid> findTarget = StartAttacking.create(
                m -> !cannotFight(m), TaskSC2GrenadeAttack::findValidTarget);
        Predicate<LivingEntity> shouldStopTarget = target ->
                target instanceof SulfurheadEntity || maid.isMaidInSittingPose()
                        || cannotFight(maid) || farAway(target, maid);
        BehaviorControl<EntityMaid> stopIfInvalid = StopAttackingIfTargetInvalid.create(shouldStopTarget);
        BehaviorControl<EntityMaid> throwGrenade = new MaidSC2GrenadeThrowTask();

        return Lists.newArrayList(
                Pair.of(5, findTarget),
                Pair.of(5, stopIfInvalid),
                Pair.of(5, throwGrenade)
        );
    }

    private static Optional<LivingEntity> findValidTarget(EntityMaid maid) {
        Optional<? extends LivingEntity> target = IRangedAttackTask.findFirstValidAttackTarget(maid);
        if (target.isPresent() && target.get() instanceof SulfurheadEntity) {
            return Optional.empty();
        }
        return target.map(t -> t);
    }

    @Override
    public List<Pair<Integer, BehaviorControl<? super EntityMaid>>> createRideBrainTasks(EntityMaid maid) {
        BehaviorControl<EntityMaid> findTarget = StartAttacking.create(
                m -> !cannotFight(m), TaskSC2GrenadeAttack::findValidTarget);
        Predicate<LivingEntity> shouldStopTarget = target ->
                target instanceof SulfurheadEntity || maid.isMaidInSittingPose()
                        || cannotFight(maid) || farAway(target, maid);
        BehaviorControl<EntityMaid> stopIfInvalid = StopAttackingIfTargetInvalid.create(shouldStopTarget);
        BehaviorControl<EntityMaid> throwGrenade = new MaidSC2GrenadeThrowTask();

        return Lists.newArrayList(
                Pair.of(5, findTarget),
                Pair.of(5, stopIfInvalid),
                Pair.of(5, throwGrenade)
        );
    }

    @Override
    public void performRangedAttack(EntityMaid shooter, LivingEntity target, float distanceFactor) {
        ItemStack stack = shooter.getMainHandItem();
        if (SC2GunCompat.isGrenade(stack)) {
            SC2GunCompat.performGrenadeThrow(shooter, target, stack);
        }
    }

    @Override
    public boolean isWeapon(EntityMaid maid, ItemStack stack) {
        return SC2GunCompat.isGrenade(stack);
    }

    @Override
    public boolean canSee(EntityMaid maid, LivingEntity target) {
        return BehaviorUtils.canSee(maid, target);
    }

    @Override
    public AABB searchDimension(EntityMaid maid) {
        if (SC2GunCompat.isGrenade(maid.getMainHandItem())) {
            float radius = searchRadius(maid);
            if (maid.hasRestriction()) {
                BlockPos center = maid.getRestrictCenter();
                return new AABB(center).inflate(radius);
            }
            return maid.getBoundingBox().inflate(radius);
        }
        return IRangedAttackTask.super.searchDimension(maid);
    }

    @Override
    public float searchRadius(EntityMaid maid) {
        return 24.0f;
    }

    @Override
    public List<Pair<String, Predicate<EntityMaid>>> getConditionDescription(EntityMaid maid) {
        return Lists.newArrayList(Pair.of("has_scg2_grenade", m -> isWeapon(m, m.getMainHandItem())));
    }

    private boolean farAway(LivingEntity target, EntityMaid maid) {
        return maid.distanceTo(target) > searchRadius(maid);
    }

    private static boolean cannotFight(EntityMaid maid) {
        if (SC2GunCompat.isGrenade(maid.getMainHandItem())) return false;
        return !hasGrenadeInInventory(maid);
    }

    private static boolean hasGrenadeInInventory(EntityMaid maid) {
        IItemHandler inv = maid.getAvailableInv(false);
        for (int i = 0; i < inv.getSlots(); i++) {
            if (SC2GunCompat.isGrenade(inv.getStackInSlot(i))) return true;
        }
        return false;
    }
}
