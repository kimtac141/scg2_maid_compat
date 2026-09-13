package com.scg2tlm.elmomod.compat;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.scg2tlm.elmomod.SCG2TLMConfig;
import com.scg2tlm.elmomod.SCGExtraCompatHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;

/**
 * 医疗兵职业的 SC2 医疗物品判定与治疗执行。
 *
 * <h2>SC2 的医疗物品（0.5.5 实测）</h2>
 * <table border="1">
 *   <caption>物品与作用</caption>
 *   <tr><th>物品 ID</th><th>作用</th></tr>
 *   <tr><td>{@code scguns:crusader}（十字军）</td>
 *       <td><b>医疗枪</b>：{@code weaponType=special}、{@code damage=0.0}，
 *           发射 {@code scguns:syringe} 弹丸，命中必给
 *           {@code minecraft:instant_health}（amplifier=1，chance=1.0）。弹匣 4 发，
 *           换弹物 {@code scguns:syringe}。</td></tr>
 *   <tr><td>{@code scguns:syringe}（注射器）</td>
 *       <td>十字军的弹药与换弹物</td></tr>
 *   <tr><td>{@code scguns:healing_bandage}（治疗绷带）</td>
 *       <td>使用类物品：{@code HealingBandageItem}，含 {@code healingAmount} + 药水效果</td></tr>
 *   <tr><td>{@code scguns:enchanted_bandage}（附魔治疗绷带）</td>
 *       <td>{@code GlintedHealingBandageItem extends HealingBandageItem}</td></tr>
 * </table>
 *
 * <h2>治疗是怎么生效的（重要）</h2>
 * <p>{@code SyringeProjectileEntity.onHitEntity} 自己<b>不</b>施加效果，它调用
 * {@code ProjectileEntity.onHitEntity}；施加逻辑在<b>基类</b>里：</p>
 * <pre>
 *   MobEffect effect = ForgeRegistries.MOB_EFFECTS.getValue(projectile.getImpactEffect());
 *   if (random &lt; projectile.getImpactEffectChance()) {
 *       命中实体.addEffect(new MobEffectInstance(effect, duration, amplifier));
 *   }
 * </pre>
 * <p><b>没有友军判定——打谁治谁。</b>所以「治疗友军」这件事完全由<b>选目标</b>决定，
 * 必须由本模组自己把 {@code ATTACK_TARGET} 指向受伤的友军。</p>
 *
 * <h2>唯一的例外：给自己治</h2>
 * <p>{@code ProjectileEntity.onHitEntity} 的第一句就是
 * {@code if (entity.getId() == this.shooterId) return;} —— <b>弹丸永远忽略发射者本人</b>，
 * 而命中山丘/方块（{@code SyringeProjectileEntity.onHitBlock}）只出粒子、不施加效果。
 * 所以「朝自己开枪」这条路无论怎么瞄都治不了自己，
 * 自疗必须在服务端直接施加 {@code impactEffect}（见 {@link #applyImpactEffectToSelf}），
 * 弹丸只负责「朝自己脚底下打一枪」的表现。</p>
 */
public final class SC2HealCompat {

    /** 十字军（SC2 的医疗枪）。 */
    public static final ResourceLocation CRUSADER = ResourceLocation.fromNamespaceAndPath("scguns", "crusader");
    /** 注射器（十字军弹药 / 换弹物）。 */
    public static final ResourceLocation SYRINGE = ResourceLocation.fromNamespaceAndPath("scguns", "syringe");

    // 治疗类消耗品<b>不再按 id 登记</b>：见 isHealingBandage 的说明。
    // 曾经的 HEALING_BANDAGE / ENCHANTED_BANDAGE 两个 id 常量已删除——
    // 它们只被那个有漏项的判定用到，改为类型判定后就没有存在意义了。

    /**
     * SC2 治疗弹丸施加的效果（十字军的 {@code projectile.impactEffect}）。
     * 用于识别「治疗弹」，见 {@code ProjectileEntityMixin}。
     */
    public static final ResourceLocation INSTANT_HEALTH =
            ResourceLocation.fromNamespaceAndPath("minecraft", "instant_health");

    private SC2HealCompat() {
    }

    /** 物品是否注册存在（用于图标等可选引用，避免硬编码导致 NPE）。 */
    public static boolean exists(ResourceLocation id) {
        return ForgeRegistries.ITEMS.containsKey(id);
    }

    @Nullable
    public static ItemStack stackOf(ResourceLocation id) {
        var item = ForgeRegistries.ITEMS.getValue(id);
        return item == null ? null : item.getDefaultInstance();
    }

    /** 是不是十字军（SC2 的医疗枪）。 */
    public static boolean isCrusader(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        ResourceLocation key = ForgeRegistries.ITEMS.getKey(stack.getItem());
        return CRUSADER.equals(key);
    }

    /** 是不是注射器。 */
    public static boolean isSyringe(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        ResourceLocation key = ForgeRegistries.ITEMS.getKey(stack.getItem());
        return SYRINGE.equals(key);
    }

    /**
     * 是不是 SC2 的「治疗类消耗品」（绷带 / 药膏 / 龙之药膏…）。
     *
     * <h3>为什么用类型判定而不是硬编码 id</h3>
     * <p>旧实现只认两个 id：{@code scguns:healing_bandage} 与 {@code scguns:enchanted_bandage}。
     * 但 SC2 里<b>所有</b>治疗类消耗品都是 {@link HealingBandageItem}（或其子类
     * {@code GlintedHealingBandageItem}）的实例，实测已注册的有：</p>
     * <table border="1">
     *   <caption>SC2 的治疗类消耗品（自 ModItems 实测）</caption>
     *   <tr><th>物品</th><th>回血</th><th>药水效果</th></tr>
     *   <tr><td>{@code scguns:basic_poultice}</td><td>4</td><td>无</td></tr>
     *   <tr><td>{@code scguns:healing_bandage}</td><td>（本版另有注册）</td><td>无</td></tr>
     *   <tr><td>{@code scguns:honey_sulfur_poultice}</td><td>8</td><td>再生 100t</td></tr>
     *   <tr><td>{@code scguns:enchanted_bandage}</td><td>12</td><td>再生 II 100t + 吸收 400t</td></tr>
     *   <tr><td>{@code scguns:dragon_salve}</td><td>16</td><td>再生 II 200t + 吸收 700t</td></tr>
     * </table>
     * <p>旧的 id 白名单漏掉了三种药膏（{@code basic_poultice}、{@code honey_sulfur_poultice}、
     * {@code dragon_salve}），表现就是「药膏类治疗物品没有被识别」——医疗兵既找不到它们，
     * 也不认为它们算医疗装备。改用类型判定后，SC2 本体及<b>未来新增/附属的治疗类物品</b>
     * 全部自动覆盖，无需再维护名单。</p>
     *
     * <p>治疗数值本身由 {@link com.scg2tlm.elmomod.mixin.HealingBandageAccessor} 读取
     * （{@code healingAmount} / {@code potionEffects} 是包私有字段），与本判定无关——
     * 所以新增的药膏会自动按各自的数值生效，不需要在本类里登记。</p>
     */
    public static boolean isHealingBandage(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        return stack.getItem() instanceof top.ribs.scguns.item.HealingBandageItem;
    }

    /** scgextra 的治疗物品类（{@code bandage} 与 {@code medkit} 的基类之一）。 */
    private static final String SCGEXTRA_MULTI_USE_HEALING = "net.zincstudios.scgextra.item.MultiUseHealingItem";

    /** 延迟解析的 {@code MultiUseHealingItem} 类；不存在时保持 {@code null}（并只尝试一次）。 */
    private static Class<?> SCGEXTRA_MEDKIT_CLASS;
    private static boolean scgextraClassResolved;

    /** {@code MultiUseHealingItem.healingAmount}（包私有 int 字段）。 */
    private static java.lang.reflect.Field SCGEXTRA_HEAL_FIELD;
    /** {@code MultiUseHealingItem.cooldown}（包私有 int 字段）。 */
    private static java.lang.reflect.Field SCGEXTRA_COOLDOWN_FIELD;
    /** {@code MultiUseHealingItem.potionEffects}（包私有字段）。 */
    private static java.lang.reflect.Field SCGEXTRA_EFFECTS_FIELD;

    /**
     * 是不是 scgextra 的 {@code medkit}（{@code MultiUseHealingItem}）。
     *
     * <p><b>为什么用反射而不是直接 import</b>：scgextra 是可选依赖。直接引用它的类会让本模组
     * 在未安装 scgextra 时出现类加载问题（本项目已因「懒加载的可失败类出现在热路径上」
     * 崩过一次，见 {@code SCGExtraCompatHelper} 的注释）。这里用字符串类名 + 一次解析，
     * 未安装时安静地返回 false。</p>
     */
    public static boolean isScgExtraMedkit(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        Class<?> clazz = scgextraMedkitClass();
        return clazz != null && clazz.isInstance(stack.getItem());
    }

    @Nullable
    private static synchronized Class<?> scgextraMedkitClass() {
        if (!scgextraClassResolved) {
            scgextraClassResolved = true;
            try {
                SCGEXTRA_MEDKIT_CLASS = Class.forName(SCGEXTRA_MULTI_USE_HEALING);
                SCGEXTRA_HEAL_FIELD = SCGEXTRA_MEDKIT_CLASS.getDeclaredField("healingAmount");
                SCGEXTRA_HEAL_FIELD.setAccessible(true);
                SCGEXTRA_COOLDOWN_FIELD = SCGEXTRA_MEDKIT_CLASS.getDeclaredField("cooldown");
                SCGEXTRA_COOLDOWN_FIELD.setAccessible(true);
                SCGEXTRA_EFFECTS_FIELD = SCGEXTRA_MEDKIT_CLASS.getDeclaredField("potionEffects");
                SCGEXTRA_EFFECTS_FIELD.setAccessible(true);
            } catch (Throwable t) {
                SCGEXTRA_MEDKIT_CLASS = null;
            }
        }
        return SCGEXTRA_MEDKIT_CLASS;
    }

    /**
     * 是不是「任意治疗消耗品」——SC2 的绷带/药膏，或 scgextra 的 medkit。
     *
     * <p>注意 scgextra 的 {@code bandage} <b>本身就是 {@code HealingBandageItem} 的子类</b>
     * （实测其 ModItems 里 {@code BANDAGE} 的泛型就是 {@code top.ribs.scguns.item.HealingBandageItem}），
     * 所以它由 {@link #isHealingBandage} 覆盖；只有 {@code medkit} 是另一套类，需要单独判定。</p>
     */
    public static boolean isHealingConsumable(ItemStack stack) {
        return isHealingBandage(stack) || isScgExtraMedkit(stack);
    }

    /** {@code medkit} 的回血量；取不到时返回 6（实测 scgextra 注册值）。 */
    public static float medkitHealAmount(ItemStack stack) {
        Object v = readField(stack, SCGEXTRA_HEAL_FIELD);
        return v instanceof Number n ? n.floatValue() : 6.0f;
    }

    /** {@code medkit} 的物品冷却（刻）；取不到时返回 100（实测注册值）。 */
    public static int medkitCooldown(ItemStack stack) {
        Object v = readField(stack, SCGEXTRA_COOLDOWN_FIELD);
        return v instanceof Number n ? n.intValue() : 100;
    }

    /** {@code medkit} 的药水效果列表；取不到时返回空列表。 */
    @SuppressWarnings("unchecked")
    public static java.util.List<net.minecraft.world.effect.MobEffectInstance> medkitEffects(ItemStack stack) {
        Object v = readField(stack, SCGEXTRA_EFFECTS_FIELD);
        return v instanceof java.util.List<?> list
                ? (java.util.List<net.minecraft.world.effect.MobEffectInstance>) list
                : java.util.List.of();
    }

    @Nullable
    private static Object readField(ItemStack stack, @Nullable java.lang.reflect.Field field) {
        if (field == null || stack == null || stack.isEmpty()) return null;
        try {
            return field.get(stack.getItem());
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * 是不是「医疗兵专用装备」（十字军，或任意治疗消耗品）。
     * 用于判断女仆是否处于医疗兵模式。
     *
     * <p>治疗消耗品走 {@link #isHealingConsumable}，因此覆盖：SC2 的绷带/药膏、
     * scgextra 的绷带（本身是 {@code HealingBandageItem} 子类）、以及 scgextra 的 medkit。</p>
     */
    public static boolean isMedicEquipment(ItemStack stack) {
        return isCrusader(stack) || isHealingConsumable(stack);
    }

    /**
     * 目标是否值得治疗（血没满）。
     *
     * <p>用 {@code getMaxHealth() - getHealth() > 0.5} 而不是 {@code getHealth() < getMaxHealth()}，
     * 避免浮点误差导致对满血目标反复施法。</p>
     */
    public static boolean isWounded(LivingEntity entity) {
        if (entity == null || !entity.isAlive() || entity.isRemoved()) return false;
        return entity.getMaxHealth() - entity.getHealth() > 0.5f;
    }

    /**
     * 判断 b 是不是 a 的友军（医疗兵的合法治疗对象）。
     *
     * <p>判定顺序：</p>
     * <ol>
     *   <li>自己 → 是</li>
     *   <li>同一主人的女仆（TLM 原生语义，不依赖 scgextra）→ 是</li>
     *   <li>女仆的主人（玩家）→ 是</li>
     *   <li>scgextra 阵营友军 → 是（未安装 scgextra 时该步安全返回 false）</li>
     * </ol>
     */
    public static boolean isFriendly(EntityMaid medic, LivingEntity target) {
        if (target == null) return false;
        if (medic == target) return true;

        // 女仆认主：同一主人的女仆互为友军
        if (target instanceof EntityMaid other) {
            LivingEntity mine = medic.getOwner();
            LivingEntity theirs = other.getOwner();
            if (mine != null && mine == theirs) return true;
        }

        // 主人的话直接算友军
        LivingEntity owner = medic.getOwner();
        if (owner != null && owner == target) return true;
        // 女仆可能没有 owner 记录，但玩家是它的主人之一
        if (target instanceof net.minecraft.world.entity.player.Player player && medic.isOwnedBy(player)) return true;

        // scgextra 阵营友军（未装 scgextra 时 isFriendlies 返回 false）
        // ModList 检查刻意留在这里而不是 SCGExtraCompatHelper 里：那个类要保持零依赖（纯反射）。
        if (net.minecraftforge.fml.ModList.get().isLoaded("scgextra")
                && SCGExtraCompatHelper.isFriendlies(medic, target)) {
            return true;
        }
        return false;
    }

    /**
     * 让女仆对目标使用治疗绷带（服务端）。
     *
     * <p><b>为什么不能直接调 SC2 的 use 逻辑</b>：{@code HealingBandageItem.finishUsingItem}
     * 的第一句就是 {@code if (!(entity instanceof Player)) return stack;}，
     * 女仆不是 {@code Player}，走那条路会什么都不做。所以这里复刻它的效果：</p>
     * <pre>
     *   player.heal(this.healingAmount);          // 回血
     *   player.removeEffect(LACERATED);           // 顺带治 SC2 的撕裂伤
     *   this.potionEffects.forEach(player::addEffect);
     * </pre>
     * <p>真实数值通过 {@code HealingBandageAccessor} 读取（字段是包私有的）。
     * accessor 不可用时（SC2 未安装/类结构变化）退回安全默认值，保证不崩。</p>
     *
     * @return true 表示确实执行了治疗
     */
    public static boolean applyBandage(EntityMaid medic, LivingEntity target, ItemStack bandage) {
        if (!isHealingBandage(bandage)) return false;
        if (!isWounded(target) && !hasLacerated(target)) return false;

        float heal = 4.0f;
        java.util.List<net.minecraft.world.effect.MobEffectInstance> effects = java.util.List.of();
        try {
            var acc = (com.scg2tlm.elmomod.mixin.HealingBandageAccessor) bandage.getItem();
            heal = acc.scg2tlm$getHealingAmount();
            var list = acc.scg2tlm$getPotionEffects();
            if (list != null) effects = list;
        } catch (Throwable ignored) {
            // accessor 不可用：用默认值继续，至少能治疗
        }

        target.heal(heal);
        clearLacerated(target);
        for (net.minecraft.world.effect.MobEffectInstance inst : effects) {
            if (inst != null) {
                target.addEffect(new net.minecraft.world.effect.MobEffectInstance(inst));
            }
        }

        // 消耗一个绷带。女仆没有玩家的创造模式概念，一律消耗。
        if (!bandage.isEmpty()) {
            bandage.shrink(1);
        }
        return true;
    }

    // ---- SC2 的「撕裂伤」效果（LACERATED）----

    /** SC2 自定义效果注册 ID。 */
    private static final ResourceLocation LACERATED =
            ResourceLocation.fromNamespaceAndPath("scguns", "lacerated");

    @Nullable
    private static net.minecraft.world.effect.MobEffect laceratedEffect() {
        return ForgeRegistries.MOB_EFFECTS.getValue(LACERATED);
    }

    /** 目标是否带撕裂伤（绷带能治这个）。 */
    public static boolean hasLacerated(LivingEntity target) {
        var eff = laceratedEffect();
        return eff != null && target.hasEffect(eff);
    }

    private static void clearLacerated(LivingEntity target) {
        var eff = laceratedEffect();
        if (eff != null && target.hasEffect(eff)) {
            target.removeEffect(eff);
        }
    }

    /**
     * 在女仆身上找一份治疗消耗品（SC2 绷带/药膏、scgextra 绷带/medkit 通用）。
     *
     * <p>查找顺序：副手 → 主手 → 女仆可用物品栏（<b>含背包</b>）。</p>
     *
     * <p>用 {@code maid.getAvailableInv(true)}（与弹药/换弹逻辑同一入口，
     * 已被实测可用），而不是 {@code getMaidInv()} —— 后者取不到背包内容，
     * 会导致「绷带放进背包就不生效」。找不到返回 {@code null}。</p>
     */
    @Nullable
    public static ItemStack findBandage(EntityMaid maid) {
        return findHealingConsumable(maid);
    }

    /** 同上，语义更准确的名字（覆盖 SC2 药膏与 scgextra medkit，不只是「绷带」）。 */
    @Nullable
    public static ItemStack findHealingConsumable(EntityMaid maid) {
        ItemStack off = maid.getOffhandItem();
        if (isHealingConsumable(off)) return off;
        ItemStack main = maid.getMainHandItem();
        if (isHealingConsumable(main)) return main;

        var inv = maid.getAvailableInv(true);
        if (inv != null) {
            for (int i = 0; i < inv.getSlots(); i++) {
                ItemStack s = inv.getStackInSlot(i);
                if (isHealingConsumable(s)) return s;
            }
        }
        return null;
    }

    /**
     * 对目标施加一次治疗消耗品的效果（服务端）。
     *
     * <p>按物品类型分派：SC2 的 {@code HealingBandageItem} 系走 {@link #applyBandage}
     * （读 {@code healingAmount}/{@code potionEffects}）；scgextra 的 {@code medkit}
     * 走 {@link #applyMedkit}。两者都<b>不是</b>给 {@code Player} 用的，
     * 所以不能调它们的 {@code finishUsingItem}。</p>
     *
     * @return true 表示确实执行了治疗（并已消耗一个物品）
     */
    public static boolean applyHealingConsumable(EntityMaid medic, LivingEntity target, ItemStack stack) {
        if (isHealingBandage(stack)) return applyBandage(medic, target, stack);
        if (isScgExtraMedkit(stack)) return applyMedkit(medic, target, stack);
        return false;
    }

    /**
     * 复刻 scgextra {@code medkit}（{@code MultiUseHealingItem}）的效果。
     *
     * <h3>为什么必须复刻</h3>
     * <p>它的 {@code finishUsingItem(ItemStack, Level, LivingEntity)} 第一句就是
     * {@code if (!(entity instanceof Player)) ...} —— 女仆不是 {@code Player}，
     * 走原版路径什么都 <b>不</b> 会发生（与 SC2 的绷带同一类问题）。</p>
     * <p>实测 scgextra 的注册值：回血 6、冷却 100 刻、效果 = 生命恢复(200t) + 抗性提升 II(300t)。
     * 这里通过反射读取真实字段，所以 scgextra 改数值后本模组自动跟随。</p>
     */
    public static boolean applyMedkit(EntityMaid medic, LivingEntity target, ItemStack medkit) {
        if (!isScgExtraMedkit(medkit)) return false;
        if (!isWounded(target) && !hasLacerated(target)) return false;

        target.heal(medkitHealAmount(medkit));
        clearLacerated(target);
        for (net.minecraft.world.effect.MobEffectInstance inst : medkitEffects(medkit)) {
            if (inst != null) {
                target.addEffect(new net.minecraft.world.effect.MobEffectInstance(inst));
            }
        }
        if (!medkit.isEmpty()) {
            medkit.shrink(1);
        }
        return true;
    }

    // ==================== 治疗射击 ====================

    /**
     * 医疗兵朝友军开一发治疗弹（十字军）。
     *
     * <h3>为什么不能复用 {@code SC2GunCompat.performGunAttack}</h3>
     * <p>那个方法里有：</p>
     * <pre>
     *   if (target instanceof Mob mobTarget) {
     *       mobTarget.setLastHurtByMob(maid);   // 把友军标记成"被女仆伤害"
     *       mobTarget.setTarget(mob);           // 让友军反过来把女仆当敌人
     *   }
     * </pre>
     * <p>那是给<b>攻击</b>用的语义。治疗的对象是主人/队友，绝不能改它们的仇恨状态，
     * 否则会出现「被治疗的女仆反手打医疗兵」这种荒谬结果。因此这里走一条干净通道：
     * 只做「转向 → 摆臂 → 发射 → 扣弹药 → 音效」，不碰任何攻击/仇恨状态。</p>
     *
     * <p>发射本身委托给 SC2 自己的 {@code AIGunEvent.performGunAttack}，
     * 它负责生成弹丸；弹丸命中友军时由 SC2 基类施加 {@code instant_health}。</p>
     */
    public static boolean fireHealingShot(EntityMaid medic, LivingEntity target,
                                          ItemStack gun, top.ribs.scguns.item.GunItem gunItem) {
        if (medic.level().isClientSide) return false;
        if (!isCrusader(gun)) return false;
        if (!top.ribs.scguns.common.Gun.hasAmmo(gun)) return false;

        // 自疗：目标就是自己。这条路径必须特殊处理，原因见 selfHeal 的注释。
        boolean selfHeal = target == medic;

        try {
            // 1) 转向目标并摆臂（单纯的动画/朝向，无攻击语义）
            if (selfHeal) {
                aimAtOwnFeet(medic);
            } else {
                medic.getLookControl().setLookAt(target, 30.0f, 30.0f);
            }
            medic.swing(net.minecraft.world.InteractionHand.MAIN_HAND, true);

            // 2) 发射：交给 SC2 的 AI 射击通道
            top.ribs.scguns.common.Gun modifiedGun = gunItem.getModifiedGun(gun);
            top.ribs.scguns.common.Gun useGun =
                    (modifiedGun != null && modifiedGun.getProjectile() != null) ? modifiedGun : gunItem.getGun();
            top.ribs.scguns.entity.ai.AIGunEvent.performGunAttack(
                    medic, target, gun, useGun, SCG2TLMConfig.GUN_ACCURACY.get().floatValue());

            // 3) 自疗时直接施加效果 —— 弹丸这条路对自己<b>根本不可能生效</b>：
            //    ProjectileEntity.onHitEntity 开头就是
            //        if (entity.getId() == this.shooterId) return;   // 命中发射者直接忽略
            //    而命中山丘/方块走的 SyringeProjectileEntity.onHitBlock 只出粒子、
            //    不施加任何效果。所以「朝自己脚底下开枪」只能拿到视觉，治不了伤，
            //    必须在服务端照 SC2 命中实体时做的那件事补一遍（同样的 effect/duration/amplifier/chance）。
            if (selfHeal) {
                applyImpactEffectToSelf(medic, useGun);
            }

            // 4) 扣一发弹药（十字军弹匣 4 发，用注射器换弹）
            int ammo = top.ribs.scguns.common.Gun.getAmmoCount(gun);
            if (ammo > 0) {
                gun.getOrCreateTag().putInt("AmmoCount", ammo - 1);
            }
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    /** 自疗时的俯角：接近垂直朝下，打在脚前一点的地面上。 */
    private static final float SELF_HEAL_PITCH = 82.0f;

    /**
     * 让女仆朝<b>自己脚前下方</b>瞄准。
     *
     * <h3>为什么不能只靠 {@code setLookAt(自己)}</h3>
     * <p>SC2 的 AI 射击方向是
     * {@code getVectorFromRotation(shooter.getViewXRot(1.0F), shooter.getViewYRot(1.0F))}
     * —— <b>只看女仆自己的俯仰/偏航，不看目标位置</b>（目标位置只在困难难度用于提前量）。
     * 而「看向自己」时水平偏移≈0，偏航是退化的（等于沿用上一刻的朝向），
     * 表现就是枪口方向飘忽、乱飞。</p>
     * <p>这里显式把俯仰压到 {@value #SELF_HEAL_PITCH} 度、偏航固定为当前朝向，
     * 于是弹丸确定地打在脚前地面（外＋枪口火光、命中方块的注射器粒子），
     * 真正的回血由 {@link #applyImpactEffectToSelf} 负责。</p>
     */
    private static void aimAtOwnFeet(EntityMaid medic) {
        float yaw = medic.getYRot();
        float pitch = SELF_HEAL_PITCH;
        // 直接写俯仰/偏航：AIGunEvent.getDirection 用的是 getViewXRot(1.0F)/getViewYRot(1.0F)，
        // partialTicks == 1 时不做插值，读的就是这两个值，所以本刻开火方向是确定的。
        medic.setYRot(yaw);
        medic.setXRot(pitch);
        medic.yRotO = yaw;
        medic.xRotO = pitch;
        medic.setYHeadRot(yaw);

        Vec3 point = medic.position().add(Vec3.directionFromRotation(pitch, yaw).scale(1.5));
        medic.getLookControl().setLookAt(point.x, point.y, point.z, 30.0f, 30.0f);
    }

    /**
     * 把十字军弹丸的 {@code impactEffect} 直接施加到女仆自己身上
     * （照 {@code ProjectileEntity.onHitEntity} 的写法：effect + duration + amplifier + chance）。
     */
    private static void applyImpactEffectToSelf(EntityMaid medic, top.ribs.scguns.common.Gun gun) {
        if (gun == null || gun.getProjectile() == null) return;
        ResourceLocation effectId = gun.getProjectile().getImpactEffect();
        if (effectId == null) return;
        float chance = gun.getProjectile().getImpactEffectChance();
        if (chance < 1.0f && medic.getRandom().nextFloat() >= chance) return;

        MobEffect effect = ForgeRegistries.MOB_EFFECTS.getValue(effectId);
        if (effect == null) return;
        medic.addEffect(new MobEffectInstance(effect,
                gun.getProjectile().getImpactEffectDuration(),
                gun.getProjectile().getImpactEffectAmplifier()));

        // 复刻命中的视觉/音效反馈（SC2 在命中实体时就是这两句）
        if (medic.level() instanceof net.minecraft.server.level.ServerLevel serverLevel) {
            serverLevel.sendParticles(net.minecraft.core.particles.ParticleTypes.ENTITY_EFFECT,
                    medic.getX(), medic.getY() + 0.5, medic.getZ(), 1, 0.3, 0.3, 0.3, 0.0);
        }
        medic.level().levelEvent(2002, medic.blockPosition(), effect.getColor());
    }
}
