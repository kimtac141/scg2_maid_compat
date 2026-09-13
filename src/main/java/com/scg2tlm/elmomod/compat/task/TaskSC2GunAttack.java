package com.scg2tlm.elmomod.compat.task;

import com.scg2tlm.elmomod.ExampleMod;
import com.scg2tlm.elmomod.SCG2TLMConfig;
import com.scg2tlm.elmomod.compat.SC2AdvancementTriggers;
import com.scg2tlm.elmomod.compat.SC2GunCompat;
import com.github.tartaricacid.touhoulittlemaid.api.task.IRangedAttackTask;
import com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.task.MaidUseShieldTask;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.init.InitSounds;
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

    /**
     * 女仆的「环境语音」（由 TLM 的语音包/声库提供实际音频）。
     *
     * <p>原版链条：{@code Mob.baseTick()}（{@code m_6075_}，每隔几秒触发一次）
     * {@code → playAmbientSound() → EntityMaid.getAmbientSound() → task.getAmbientSound(maid)}。
     * 原来这里返回 {@code null}，等于枪手模式全程没有环境语音，只剩「发现敌人」「开火」两声 ——
     * 也就是「只有在发现敌人和战斗时播放语音」的原因。</p>
     *
     * <p><b>刻意不用 TLM 的 {@code SoundUtil.attackSound}（它 50% 返回 {@code MAID_FIND_TARGET}）</b>：
     * 那个事件的字幕就是「发现敌人」，把它当作每隔几秒随机触发的环境音，
     * 会让玩家分不清「女仆真的看到敌人了」还是「随口喊一句」——
     * 而真正发现目标时，{@code MaidSC2GunShootTask.start()} 会<b>明确播一次</b> {@code MAID_FIND_TARGET}。
     * 所以环境音只用「远程攻击模式」语音，让那句「发现敌人」重新变得可信。</p>
     */
    @Nullable
    @Override
    public SoundEvent getAmbientSound(EntityMaid maid) {
        return InitSounds.MAID_RANGE_ATTACK.get();
    }

    @Override
    public boolean enableLookAndRandomWalk(EntityMaid maid) {
        if (maid.getBrain().hasMemoryValue(MemoryModuleType.ATTACK_TARGET)) return false;
        // 补搜（回到被打断的最后目击点）期间没有攻击目标，但同样不能放任待机行为乱走 ——
        // 否则 MaidRunOne 的随机漫步会不断覆盖我们设的 WALK_TARGET。
        if (MaidSC2GunShootTask.hasPendingSearch(maid)) return false;
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
                m -> this.mainhandHoldGun(m) && !cannotFight(m), TaskSC2GunAttack::findValidTarget);
        Predicate<LivingEntity> shouldStopTarget = target ->
                !target.isAlive() || maid.isOwnedBy(target)
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

    /**
     * 按本任务自己的优先级找一个「有效目标」。
     *
     * <p><b>包内可见</b>（不是 private）：{@code MaidSC2GunShootTask} 在目标躲进掩体后要用它
     * 判断「周围还有没有别的、看得见的敌人」，从而决定是继续长搜索还是立刻换目标。
     * 复用同一个方法才能保证「女仆会自动换的目标」和「这里用来判定的目标」完全一致。</p>
     *
     * <p>注意原版 {@code StartAttacking} 的构造是
     * {@code group(absent(ATTACK_TARGET), registered(CANT_REACH_WALK_TARGET_SINCE))}
     * —— <b>只有当没有攻击目标时才会去选新目标</b>。所以只要我们还握着
     * {@code ATTACK_TARGET}（例如正在长搜索），女仆就永远不会自己换到旁边那个看得见的敌人，
     * 必须由我们主动放弃目标才行。</p>
     */
    static Optional<LivingEntity> findValidTarget(EntityMaid maid) {
        // Priority 1: Revenge attack - whoever last hurt the maid and is still visible
        if (maid.getBrain().hasMemoryValue(MemoryModuleType.HURT_BY_ENTITY)) {
            LivingEntity attacker = maid.getBrain().getMemory(MemoryModuleType.HURT_BY_ENTITY).get();
            if (attacker.isAlive() && !maid.isOwnedBy(attacker) && BehaviorUtils.canSee(maid, attacker)) {
                return Optional.of(attacker);
            }
        }

        // Priority 2: Find nearest visible entity that has this maid as its attack target
        if (maid.getBrain().hasMemoryValue(MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES)) {
            Optional<LivingEntity> closest = maid.getBrain().getMemory(MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES).get()
                    .findClosest(e -> e.isAlive() && !maid.isOwnedBy(e) && e instanceof Mob mob && mob.getTarget() == maid);
            if (closest.isPresent()) {
                return closest;
            }
        }

        // Priority 3: Delegate to TLM's sensor-driven target finding
        return IRangedAttackTask.findFirstValidAttackTarget(maid).filter(t -> !maid.isOwnedBy(t)).map(t -> t);
    }

    private static boolean mainhandHoldGunStatic(EntityMaid maid) {
        return SC2GunCompat.isSC2Gun(maid.getMainHandItem());
    }

    @Override
    public List<Pair<Integer, BehaviorControl<? super EntityMaid>>> createRideBrainTasks(EntityMaid maid) {
        BehaviorControl<EntityMaid> findTarget = StartAttacking.create(
                m -> this.mainhandHoldGun(m) && !cannotFight(m), TaskSC2GunAttack::findValidTarget);
        Predicate<LivingEntity> shouldStopTarget = target ->
                !target.isAlive() || maid.isOwnedBy(target)
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
        if (SC2GunCompat.needsReload(gun) && !SC2GunCompat.hasAmmoInInventory(maid, gun)) {
            return 8.0f;
        }
        if (SC2GunCompat.isMeleeClassGun(gun)) {
            return 16.0f;
        }
        Gun.WeaponType type = SC2GunCompat.getWeaponType(gun);
        if (type == null) return SCG2TLMConfig.GUN_RANGE.get().floatValue();
        String special = SC2GunCompat.getSpecialRangeClass(gun);
        if (special != null) {
            if (special.equals("sniper")) return SCG2TLMConfig.SNIPER_SEARCH_RADIUS.get().floatValue();
            if (special.equals("shotgun")) return SCG2TLMConfig.SMG_SHOTGUN_PISTOL_SEARCH_RADIUS.get().floatValue();
            if (special.equals("rifle")) return SCG2TLMConfig.RIFLE_MAGNUM_SEARCH_RADIUS.get().floatValue();
        }
        if (type == Gun.WeaponType.sniper || type == Gun.WeaponType.plasma || type == Gun.WeaponType.laser) return SCG2TLMConfig.SNIPER_SEARCH_RADIUS.get().floatValue();
        if (type == Gun.WeaponType.rifle || type == Gun.WeaponType.magnum) return SCG2TLMConfig.RIFLE_MAGNUM_SEARCH_RADIUS.get().floatValue();
        if (type == Gun.WeaponType.smg || type == Gun.WeaponType.shotgun || type == Gun.WeaponType.pistol
                || type == Gun.WeaponType.flamethrower || type == Gun.WeaponType.shock) return SCG2TLMConfig.SMG_SHOTGUN_PISTOL_SEARCH_RADIUS.get().floatValue();
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
        if (!(SC2GunCompat.needsReload(gun) && !SC2GunCompat.hasAmmoInInventory(maid, gun))) {
            return false;
        }
        return !SC2GunCompat.hasBayonet(gun);
    }
}
