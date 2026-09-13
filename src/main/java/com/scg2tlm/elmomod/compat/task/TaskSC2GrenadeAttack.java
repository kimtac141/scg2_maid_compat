package com.scg2tlm.elmomod.compat.task;

import com.scg2tlm.elmomod.ExampleMod;
import com.scg2tlm.elmomod.SCG2TLMConfig;
import com.scg2tlm.elmomod.compat.SC2GunCompat;
import com.github.tartaricacid.touhoulittlemaid.api.task.IRangedAttackTask;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.init.InitSounds;
import com.google.common.collect.Lists;
import com.mojang.datafixers.util.Pair;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.entity.ai.behavior.StartAttacking;
import net.minecraft.world.entity.ai.behavior.StopAttackingIfTargetInvalid;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

public class TaskSC2GrenadeAttack implements IRangedAttackTask {
    public static final ResourceLocation UID = ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID, "grenade_attack");
    private static final ItemStack ICON = initIcon();

    private static ItemStack initIcon() {
        Item icon = ForgeRegistries.ITEMS.getValue(ResourceLocation.fromNamespaceAndPath("scguns", "grenade"));
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

    /**
     * 环境语音：掷弹兵同样是远程战斗任务，播「远程攻击模式」语音。
     *
     * <p>刻意不用 TLM 的 {@code SoundUtil.attackSound}（50% 会返回「发现敌人」那句）——
     * 环境音每隔几秒触发一次，随机播「发现敌人」会误导玩家；真正发现目标时由
     * {@code MaidSC2GunShootTask.start()} 明确播一次。详见 {@code TaskSC2GunAttack}。</p>
     */
    @Nullable
    @Override
    public SoundEvent getAmbientSound(EntityMaid maid) {
        return InitSounds.MAID_RANGE_ATTACK.get();
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
                cannotFight(maid) || farAway(target, maid) || maid.isOwnedBy(target);
        BehaviorControl<EntityMaid> stopIfInvalid = StopAttackingIfTargetInvalid.create(shouldStopTarget);
        BehaviorControl<EntityMaid> throwGrenade = new MaidSC2GrenadeThrowTask();

        return Lists.newArrayList(
                Pair.of(5, findTarget),
                Pair.of(5, stopIfInvalid),
                Pair.of(5, throwGrenade)
        );
    }

    private static Optional<LivingEntity> findValidTarget(EntityMaid maid) {
        return IRangedAttackTask.findFirstValidAttackTarget(maid).filter(t -> !maid.isOwnedBy(t)).map(t -> t);
    }

    @Override
    public List<Pair<Integer, BehaviorControl<? super EntityMaid>>> createRideBrainTasks(EntityMaid maid) {
        BehaviorControl<EntityMaid> findTarget = StartAttacking.create(
                m -> !cannotFight(m), TaskSC2GrenadeAttack::findValidTarget);
        Predicate<LivingEntity> shouldStopTarget = target ->
                cannotFight(maid) || farAway(target, maid) || maid.isOwnedBy(target);
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
        return SCG2TLMConfig.GRENADE_RADIUS.get().floatValue();
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
