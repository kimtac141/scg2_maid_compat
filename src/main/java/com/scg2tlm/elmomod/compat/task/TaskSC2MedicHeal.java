package com.scg2tlm.elmomod.compat.task;

import com.github.tartaricacid.touhoulittlemaid.api.task.IRangedAttackTask;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.init.InitSounds;
import com.github.tartaricacid.touhoulittlemaid.util.SoundUtil;
import com.google.common.collect.Lists;
import com.mojang.datafixers.util.Pair;
import com.scg2tlm.elmomod.ExampleMod;
import com.scg2tlm.elmomod.compat.SC2GunCompat;
import com.scg2tlm.elmomod.compat.SC2HealCompat;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;

import javax.annotation.Nullable;
import java.util.List;
import java.util.function.Predicate;

/**
 * 医疗兵工作模式：用 SC2 的医疗装备治疗受伤的主人、友方女仆与同阵营实体。
 *
 * <h2>装备</h2>
 * <ul>
 *   <li><b>十字军</b>（{@code scguns:crusader}）—— SC2 的医疗枪，{@code damage=0.0}，
 *       发射注射器弹丸，命中必给 {@code instant_health}，用于远程治疗。</li>
 *   <li><b>治疗绷带</b>（{@code scguns:healing_bandage} / {@code enchanted_bandage}）——
 *       用于近身贴用。SC2 原版逻辑只对玩家生效，女仆路径由本模组复刻（见 {@link SC2HealCompat}）。</li>
 * </ul>
 *
 * <h2>⚠️ 为什么治疗不走攻击系统（重要设计约束）</h2>
 * <p>第一版实现为了复用 {@code MaidSC2GunShootTask}，把治疗目标塞进了
 * {@code MemoryModuleType.ATTACK_TARGET}。那是错的：</p>
 * <ul>
 *   <li><b>TLM 设定女仆不能攻击任何玩家</b>，而主人正是最重要的治疗对象 —— 目标根本建立不起来；</li>
 *   <li>{@code IRangedAttackTask.TARGET_CONDITIONS}、原版 {@code StartAttacking} /
 *       {@code StopAttackingIfTargetInvalid} 都按<b>战斗</b>语义校验目标；</li>
 *   <li>更危险的是 {@code SC2GunCompat.performGunAttack} 会执行
 *       {@code mobTarget.setLastHurtByMob(maid); mobTarget.setTarget(mob);}
 *       —— 用来打友军会让友军反过来仇恨医疗兵。</li>
 * </ul>
 * <p><b>治疗不是攻击</b>，所以本职业的治疗全部由 {@link MedicHealTask} 自包含实现：
 * 自己找目标、自己走位、自己施法，不碰任何攻击 memory 与仇恨状态。</p>
 *
 * <p>本类仍实现 {@link IRangedAttackTask}（以获得 TLM 的通用默认行为），
 * 但 {@code createBrainTasks} 只挂治疗行为 —— 医疗兵默认不参战。</p>
 */
public class TaskSC2MedicHeal implements IRangedAttackTask {

    public static final ResourceLocation UID =
            ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID, "medic_heal");

    /** 治疗目标的搜索半径（与 {@link MedicHealTask} 保持一致）。 */
    private static final float HEAL_SEARCH_RADIUS = 24.0f;

    private static final ItemStack ICON = initIcon();

    /**
     * 取一个代表「医疗兵」的图标。
     *
     * <p>优先十字军；其次<b>遍历注册表找任意一个治疗类消耗品</b>
     * （{@code instanceof HealingBandageItem}），而不是写死某个 id ——
     * 这样无论 SC2 版本里叫 {@code healing_bandage}、{@code basic_poultice}
     * 还是 {@code dragon_salve}，都能取到；SC2 改了名字也不会退化成金苹果。</p>
     */
    private static ItemStack initIcon() {
        ItemStack crusader = SC2HealCompat.stackOf(SC2HealCompat.CRUSADER);
        if (crusader != null) return crusader;

        for (Item item : net.minecraftforge.registries.ForgeRegistries.ITEMS) {
            if (item instanceof top.ribs.scguns.item.HealingBandageItem) {
                return item.getDefaultInstance();
            }
        }
        return Items.GOLDEN_APPLE.getDefaultInstance();
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
     * 环境语音：医疗兵没有对应的「模式」语音，用 TLM 里最贴近的 {@code MAID_FEED}（照顾人）作为兜底，
     * 并按 TLM 自己的写法带上环境变化（清晨/夜晚/雨/雪/冷/热）——照 {@code TaskFeedOwner} 的用法。
     *
     * <p>原实现返回 null，等于医疗兵全程没有环境语音。</p>
     */
    @Nullable
    @Override
    public SoundEvent getAmbientSound(EntityMaid maid) {
        return SoundUtil.environmentSound(maid, InitSounds.MAID_FEED.get(), 0.5f);
    }

    /** 医疗兵不主动索敌：没有治疗目标时可以四处看看、随便走走。 */
    @Override
    public boolean enableLookAndRandomWalk(EntityMaid maid) {
        return !MedicHealTask.findTarget(maid).isPresent();
    }

    /** 医疗兵不该惊慌乱跑 —— 要留在队友身边。 */
    @Override
    public boolean enablePanic(EntityMaid maid) {
        return false;
    }

    @Override
    public List<Pair<Integer, BehaviorControl<? super EntityMaid>>> createBrainTasks(EntityMaid maid) {
        BehaviorControl<EntityMaid> healTask = new MedicHealTask();
        return Lists.newArrayList(
                Pair.of(1, healTask)
        );
    }

    @Override
    public List<Pair<Integer, BehaviorControl<? super EntityMaid>>> createRideBrainTasks(EntityMaid maid) {
        BehaviorControl<EntityMaid> healTask = new MedicHealTask();
        return Lists.newArrayList(
                Pair.of(1, healTask)
        );
    }

    @Override
    public void performRangedAttack(EntityMaid shooter, LivingEntity target, float distanceFactor) {
        // 治疗射击由 MedicHealTask 直接执行，不经过此处
    }

    /** 医疗兵的"武器"是十字军。 */
    @Override
    public boolean isWeapon(EntityMaid maid, ItemStack stack) {
        // 医疗兵的「武器」= 整个医疗装备集，不只是十字军。
        //
        // 修过的 bug：曾经只写 isCrusader(stack)，于是女仆拿着治疗绷带 / 附魔绷带时
        // 这一项判为 false。而该判定正是 AI 界面里那条条件的来源
        // （TLM 用 "task.<命名空间>.<路径>.condition.<键名>" 拼翻译键），
        // 表现就是「其他医疗物品不能用」。
        //
        // SC2HealCompat.isMedicEquipment = 十字军 + isHealingBandage，
        // 而后者已改为按类型判定（HealingBandageItem 及其子类），
        // 因此覆盖治疗绷带、附魔绷带、基础药膏、蜂蜜硫磺药膏、龙之药膏全部五种。
        // 与 MedicHealTask 实际会用的装备集合一致。
        // 注射器只是十字军的弹药/换弹物，不作为主手武器判定。
        return SC2HealCompat.isMedicEquipment(stack);
    }

    /** 用非战斗条件判断可见性 —— 治疗目标可能是玩家。 */
    @Override
    public boolean canSee(EntityMaid maid, LivingEntity target) {
        return TargetingConditions.forNonCombat().range(HEAL_SEARCH_RADIUS).test(maid, target);
    }

    @Override
    public AABB searchDimension(EntityMaid maid) {
        return maid.getBoundingBox().inflate(HEAL_SEARCH_RADIUS);
    }

    @Override
    public float searchRadius(EntityMaid maid) {
        return HEAL_SEARCH_RADIUS;
    }

    @Override
    public List<Pair<String, Predicate<EntityMaid>>> getConditionDescription(EntityMaid maid) {
        // 键名要与 lang 文件里的 task.scg2_maid_compat.medic_heal.condition.<键名> 对应。
        // 第一项已覆盖整个医疗装备集，故键名用 has_scg2_medic_item（不再是 has_scg2_crusader）。
        // 第二项会真的做一次实体搜索——见 MedicHealTask.findTarget；它带 24 格范围查询，
        // 而这个列表在 AI 界面里会被反复求值，故保持轻量（仅判断有无目标，不做治疗决策）。
        return Lists.newArrayList(
                Pair.of("has_scg2_medic_item", m -> isWeapon(m, m.getMainHandItem())),
                Pair.of("has_wounded_friendly", m -> MedicHealTask.findTarget(m).isPresent())
        );
    }

    /** 手上有没有医疗装备（供 UI/外部判断）。 */
    public static boolean hasMedicEquipment(EntityMaid maid) {
        return SC2HealCompat.isMedicEquipment(maid.getMainHandItem())
                || SC2HealCompat.findBandage(maid) != null;
    }

    /** 是否需要 SC2（用于提示该职业依赖 SC2）。 */
    public static boolean sc2Available(EntityMaid maid) {
        return SC2GunCompat.isSC2Gun(maid.getMainHandItem());
    }
}
