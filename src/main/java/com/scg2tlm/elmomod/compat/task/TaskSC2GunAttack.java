package com.scg2tlm.elmomod.compat.task;

import com.scg2tlm.elmomod.ExampleMod;
import com.scg2tlm.elmomod.SCG2TLMConfig;
import com.scg2tlm.elmomod.compat.SC2AdvancementTriggers;
import com.scg2tlm.elmomod.compat.SC2GunCompat;
import com.github.tartaricacid.touhoulittlemaid.api.task.IRangedAttackTask;
import com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.task.MaidUseShieldTask;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.google.common.collect.Lists;
import com.mojang.datafixers.util.Pair;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.entity.ai.behavior.StartAttacking;
import net.minecraft.world.entity.ai.behavior.StopAttackingIfTargetInvalid;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraft.world.phys.AABB;
import top.ribs.scguns.common.Gun;
import top.ribs.scguns.entity.monster.SulfurheadEntity;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

public class TaskSC2GunAttack implements IRangedAttackTask {
    public static final ResourceLocation UID = ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID, "gun_attack");
    private static final ItemStack ICON = initIcon();

    private static ItemStack initIcon() {
        Item icon = ForgeRegistries.ITEMS.getValue(ResourceLocation.fromNamespaceAndPath("scguns", "flintlock_pistol"));
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
        if (maid.getBrain().hasMemoryValue(MemoryModuleType.ATTACK_TARGET)) return false;
        return true;
    }

    @Override
    public List<Pair<Integer, BehaviorControl<? super EntityMaid>>> createBrainTasks(EntityMaid maid) {
        if (!maid.level().isClientSide) {
            String flagKey = ExampleMod.MODID + ":first_shot_triggered";
            if (!maid.getPersistentData().getBoolean(flagKey)) {
                maid.getPersistentData().putBoolean(flagKey, true);
                SC2AdvancementTriggers.triggerForMaidOwner(maid, SC2AdvancementTriggers.FIRST_SHOT);
            }
        }
        BehaviorControl<EntityMaid> findTarget = StartAttacking.create(
                m -> !m.isMaidInSittingPose() && this.mainhandHoldGun(m) && !cannotFight(m), TaskSC2GunAttack::findValidTarget);
        Predicate<LivingEntity> shouldStopTarget = target ->
                !target.isAlive() || target instanceof SulfurheadEntity || maid.isMaidInSittingPose()
                        || !this.mainhandHoldGun(maid) || cannotFight(maid);
        BehaviorControl<EntityMaid> stopIfInvalid = StopAttackingIfTargetInvalid.create(shouldStopTarget);
        BehaviorControl<EntityMaid> walkToTarget = new MaidSC2GunWalkToTarget(0.35f);
        BehaviorControl<EntityMaid> strafeTask = new SC2GunStrafingTask();
        BehaviorControl<EntityMaid> shootTarget = new MaidSC2GunShootTask();
        BehaviorControl<EntityMaid> shieldTask = new MaidUseShieldTask();

        return Lists.newArrayList(
                Pair.of(2, findTarget),
                Pair.of(2, stopIfInvalid),
                Pair.of(1, shootTarget),
                Pair.of(2, walkToTarget),
                Pair.of(3, strafeTask),
                Pair.of(4, shieldTask)
        );
    }

    private static Optional<LivingEntity> findValidTarget(EntityMaid maid) {
        // Priority 1: Revenge attack - whoever last hurt the maid and is still visible
        if (maid.getBrain().hasMemoryValue(MemoryModuleType.HURT_BY_ENTITY)) {
            LivingEntity attacker = maid.getBrain().getMemory(MemoryModuleType.HURT_BY_ENTITY).get();
            if (attacker.isAlive() && !(attacker instanceof SulfurheadEntity)
                    && BehaviorUtils.canSee(maid, attacker)) {
                return Optional.of(attacker);
            }
        }

        // Priority 2: Find nearest visible entity that has this maid as its attack target
        if (maid.getBrain().hasMemoryValue(MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES)) {
            Optional<LivingEntity> closest = maid.getBrain().getMemory(MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES).get()
                    .findClosest(e -> e.isAlive() && e instanceof Mob mob && mob.getTarget() == maid && !(e instanceof SulfurheadEntity));
            if (closest.isPresent()) {
                return closest;
            }
        }

        // Priority 3: Delegate to TLM's sensor-driven target finding
        Optional<? extends LivingEntity> target = IRangedAttackTask.findFirstValidAttackTarget(maid);
        if (target.isPresent() && target.get() instanceof SulfurheadEntity) {
            return Optional.empty();
        }
        return target.map(t -> t);
    }

    private static boolean mainhandHoldGunStatic(EntityMaid maid) {
        return SC2GunCompat.isSC2Gun(maid.getMainHandItem());
    }

    @Override
    public List<Pair<Integer, BehaviorControl<? super EntityMaid>>> createRideBrainTasks(EntityMaid maid) {
        BehaviorControl<EntityMaid> findTarget = StartAttacking.create(
                m -> !m.isMaidInSittingPose() && this.mainhandHoldGun(m) && !cannotFight(m), TaskSC2GunAttack::findValidTarget);
        Predicate<LivingEntity> shouldStopTarget = target ->
                !target.isAlive() || target instanceof SulfurheadEntity || maid.isMaidInSittingPose()
                        || !this.mainhandHoldGun(maid) || cannotFight(maid);
        BehaviorControl<EntityMaid> stopIfInvalid = StopAttackingIfTargetInvalid.create(shouldStopTarget);
        BehaviorControl<EntityMaid> shootTarget = new MaidSC2GunShootTask();

        return Lists.newArrayList(
                Pair.of(2, findTarget),
                Pair.of(2, stopIfInvalid),
                Pair.of(1, shootTarget)
        );
    }

    @Override
    public void performRangedAttack(EntityMaid shooter, LivingEntity target, float distanceFactor) {
    }

    @Override
    public boolean isWeapon(EntityMaid maid, ItemStack stack) {
        return SC2GunCompat.isSC2Gun(stack);
    }

    @Override
    public boolean canSee(EntityMaid maid, LivingEntity target) {
        if (!SC2GunCompat.isSC2Gun(maid.getMainHandItem())) {
            return BehaviorUtils.canSee(maid, target);
        }
        float range = searchRadius(maid);
        return TargetingConditions.forCombat().range(range).test(maid, target);
    }

    @Override
    public AABB searchDimension(EntityMaid maid) {
        if (mainhandHoldGun(maid)) {
            float radius = searchRadius(maid);
            return maid.getBoundingBox().inflate(radius);
        }
        return IRangedAttackTask.super.searchDimension(maid);
    }

    @Override
    public float searchRadius(EntityMaid maid) {
        ItemStack gun = maid.getMainHandItem();
        Gun.WeaponType type = SC2GunCompat.getWeaponType(gun);
        if (type == null) return SCG2TLMConfig.GUN_RANGE.get().floatValue();
        if (type == Gun.WeaponType.sniper) return SCG2TLMConfig.SNIPER_SEARCH_RADIUS.get().floatValue();
        if (type == Gun.WeaponType.rifle || type == Gun.WeaponType.magnum) return SCG2TLMConfig.RIFLE_MAGNUM_SEARCH_RADIUS.get().floatValue();
        if (type == Gun.WeaponType.smg || type == Gun.WeaponType.shotgun || type == Gun.WeaponType.pistol) return SCG2TLMConfig.SMG_SHOTGUN_PISTOL_SEARCH_RADIUS.get().floatValue();
        if (type == Gun.WeaponType.heavy || type == Gun.WeaponType.lmg) return SCG2TLMConfig.RIFLE_MAGNUM_SEARCH_RADIUS.get().floatValue();
        return SCG2TLMConfig.GUN_RANGE.get().floatValue();
    }

    @Override
    public List<Pair<String, Predicate<EntityMaid>>> getConditionDescription(EntityMaid maid) {
        return Lists.newArrayList(Pair.of("has_scg2_gun", m -> isWeapon(m, m.getMainHandItem())));
    }

    private boolean mainhandHoldGun(EntityMaid maid) {
        return isWeapon(maid, maid.getMainHandItem());
    }

    private static boolean cannotFight(EntityMaid maid) {
        ItemStack gun = maid.getMainHandItem();
        return SC2GunCompat.needsReload(gun) && !SC2GunCompat.hasAmmoInInventory(maid, gun);
    }
}
