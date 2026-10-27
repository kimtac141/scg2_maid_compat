package com.scg2tlm.elmomod.compat;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.scg2tlm.elmomod.mixin.HealingBandageAccessor;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import top.ribs.scguns.common.Gun;
import top.ribs.scguns.item.GunItem;
import top.ribs.scguns.item.HealingBandageItem;

import javax.annotation.Nullable;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Optional;

/**
 * 衛生兵(Medic)職の SC2 医療アイテム判定と治療の実行(NeoForge 1.21.1 版)。
 *
 * <ul>
 *   <li>scguns:crusader … 医療銃。damage=0 で注射器弾を撃ち、命中で instant_health を付与する。</li>
 *   <li>HealingBandageItem(包帯・膏薬類) … finishUsingItem が Player 専用なので、メイド用に効果を再現する。</li>
 * </ul>
 *
 * <p>ProjectileEntity.onHitEntity は発射者本人を無視するため、自己治療だけは
 * impactEffect を直接付与する(applyImpactEffectToSelf)。</p>
 */
public final class SC2HealCompat {

    public static final ResourceLocation CRUSADER = ResourceLocation.fromNamespaceAndPath("scguns", "crusader");
    public static final ResourceLocation SYRINGE = ResourceLocation.fromNamespaceAndPath("scguns", "syringe");

    /** 治療弾の識別に使う効果(十字軍の projectile.impactEffect)。 */
    public static final ResourceLocation INSTANT_HEALTH =
            ResourceLocation.fromNamespaceAndPath("minecraft", "instant_health");

    private static final ResourceLocation LACERATED =
            ResourceLocation.fromNamespaceAndPath("scguns", "lacerated");

    private SC2HealCompat() {
    }

    public static boolean exists(ResourceLocation id) {
        return BuiltInRegistries.ITEM.containsKey(id);
    }

    @Nullable
    public static ItemStack stackOf(ResourceLocation id) {
        Optional<Item> item = BuiltInRegistries.ITEM.getOptional(id);
        return item.map(Item::getDefaultInstance).orElse(null);
    }

    public static boolean isCrusader(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        return CRUSADER.equals(BuiltInRegistries.ITEM.getKey(stack.getItem()));
    }

    public static boolean isSyringe(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        return SYRINGE.equals(BuiltInRegistries.ITEM.getKey(stack.getItem()));
    }

    /**
     * SC2 の治療系消耗品(包帯・膏薬・ドラゴンの膏薬…)か。
     * idの白名簿ではなく型で判定する(HealingBandageItem とそのサブクラス)ので、追加アイテムも自動で対象になる。
     */
    public static boolean isHealingBandage(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        return stack.getItem() instanceof HealingBandageItem;
    }

    // ---- scgextra の medkit(任意依存・リフレクション) ----

    private static final String SCGEXTRA_MULTI_USE_HEALING = "net.zincstudios.scgextra.item.MultiUseHealingItem";
    private static Class<?> SCGEXTRA_MEDKIT_CLASS;
    private static boolean scgextraClassResolved;
    private static Field SCGEXTRA_HEAL_FIELD;
    private static Field SCGEXTRA_COOLDOWN_FIELD;
    private static Field SCGEXTRA_EFFECTS_FIELD;

    /** scgextra が無い環境でも安全に false を返す(クラス名の文字列参照+一度だけ解決)。 */
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

    public static boolean isHealingConsumable(ItemStack stack) {
        return isHealingBandage(stack) || isScgExtraMedkit(stack);
    }

    public static float medkitHealAmount(ItemStack stack) {
        Object v = readField(stack, SCGEXTRA_HEAL_FIELD);
        return v instanceof Number n ? n.floatValue() : 6.0f;
    }

    public static int medkitCooldown(ItemStack stack) {
        Object v = readField(stack, SCGEXTRA_COOLDOWN_FIELD);
        return v instanceof Number n ? n.intValue() : 100;
    }

    @SuppressWarnings("unchecked")
    public static List<MobEffectInstance> medkitEffects(ItemStack stack) {
        Object v = readField(stack, SCGEXTRA_EFFECTS_FIELD);
        return v instanceof List<?> list ? (List<MobEffectInstance>) list : List.of();
    }

    @Nullable
    private static Object readField(ItemStack stack, @Nullable Field field) {
        if (field == null || stack == null || stack.isEmpty()) return null;
        try {
            return field.get(stack.getItem());
        } catch (Throwable t) {
            return null;
        }
    }

    /** 衛生兵専用装備(十字軍、または治療系消耗品)か。 */
    public static boolean isMedicEquipment(ItemStack stack) {
        return isCrusader(stack) || isHealingConsumable(stack);
    }

    /** 治療に値するか(最大HP-HP > 0.5。浮動小数の誤差で満タンの相手に何度も施術しないため)。 */
    public static boolean isWounded(LivingEntity entity) {
        if (entity == null || !entity.isAlive() || entity.isRemoved()) return false;
        return entity.getMaxHealth() - entity.getHealth() > 0.5f;
    }

    /**
     * target が medic の味方(治療対象)か。自分 / 同じ主人のメイド / 主人 の順で判定する。
     * (scgextra の陣営判定は 1.21.1 版では未対応)
     */
    public static boolean isFriendly(EntityMaid medic, LivingEntity target) {
        if (target == null) return false;
        if (medic == target) return true;

        if (target instanceof EntityMaid other) {
            LivingEntity mine = medic.getOwner();
            LivingEntity theirs = other.getOwner();
            if (mine != null && mine == theirs) return true;
        }

        LivingEntity owner = medic.getOwner();
        if (owner != null && owner == target) return true;
        return target instanceof Player player && medic.isOwnedBy(player);
    }

    // ---- 撕裂傷(LACERATED) ----

    @Nullable
    private static Holder<MobEffect> effectHolder(ResourceLocation id) {
        return BuiltInRegistries.MOB_EFFECT.getHolder(id).orElse(null);
    }

    public static boolean hasLacerated(LivingEntity target) {
        Holder<MobEffect> eff = effectHolder(LACERATED);
        return eff != null && target.hasEffect(eff);
    }

    private static void clearLacerated(LivingEntity target) {
        Holder<MobEffect> eff = effectHolder(LACERATED);
        if (eff != null && target.hasEffect(eff)) {
            target.removeEffect(eff);
        }
    }

    /**
     * 包帯・膏薬の効果をメイドが対象に適用する(サーバー側)。
     * 実数値は HealingBandageAccessor 経由で読む。取れない場合は安全な既定値で続行する。
     */
    public static boolean applyBandage(EntityMaid medic, LivingEntity target, ItemStack bandage) {
        if (!isHealingBandage(bandage)) return false;
        if (!isWounded(target) && !hasLacerated(target)) return false;

        float heal = 4.0f;
        List<MobEffectInstance> effects = List.of();
        try {
            var acc = (HealingBandageAccessor) bandage.getItem();
            heal = acc.scg2tlm$getHealingAmount();
            List<MobEffectInstance> list = acc.scg2tlm$getPotionEffects();
            if (list != null) effects = list;
        } catch (Throwable ignored) {
            // accessor が使えない場合は既定値で続行
        }

        target.heal(heal);
        clearLacerated(target);
        for (MobEffectInstance inst : effects) {
            if (inst != null) {
                target.addEffect(new MobEffectInstance(inst));
            }
        }
        if (!bandage.isEmpty()) {
            bandage.shrink(1);
        }
        return true;
    }

    /** 手持ち → 副手 → 主手 → 利用可能インベントリ(バックパック含む)の順で治療消耗品を探す。 */
    @Nullable
    public static ItemStack findBandage(EntityMaid maid) {
        return findHealingConsumable(maid);
    }

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

    public static boolean applyHealingConsumable(EntityMaid medic, LivingEntity target, ItemStack stack) {
        if (isHealingBandage(stack)) return applyBandage(medic, target, stack);
        if (isScgExtraMedkit(stack)) return applyMedkit(medic, target, stack);
        return false;
    }

    public static boolean applyMedkit(EntityMaid medic, LivingEntity target, ItemStack medkit) {
        if (!isScgExtraMedkit(medkit)) return false;
        if (!isWounded(target) && !hasLacerated(target)) return false;

        target.heal(medkitHealAmount(medkit));
        clearLacerated(target);
        for (MobEffectInstance inst : medkitEffects(medkit)) {
            if (inst != null) {
                target.addEffect(new MobEffectInstance(inst));
            }
        }
        if (!medkit.isEmpty()) {
            medkit.shrink(1);
        }
        return true;
    }

    // ==================== 治療射撃 ====================

    /**
     * 衛生兵が味方に治療弾(十字軍)を1発撃つ。
     *
     * <p>SC2GunCompat.performGunAttack の hostile=false 版を使う。通常版は標的に
     * setLastHurtByMob / setTarget をして仇恨を立てるため、味方に撃つと反撃されてしまう。
     * 弾の生成・弾薬の消費・発射音は performGunAttack 側が行う。</p>
     */
    public static boolean fireHealingShot(EntityMaid medic, LivingEntity target, ItemStack gun, GunItem gunItem) {
        if (medic.level().isClientSide) return false;
        if (!isCrusader(gun)) return false;
        if (!Gun.hasAmmo(gun)) return false;

        boolean selfHeal = target == medic;

        try {
            if (selfHeal) {
                aimAtOwnFeet(medic);
            } else {
                medic.getLookControl().setLookAt(target, 30.0f, 30.0f);
            }
            medic.swing(InteractionHand.MAIN_HAND, true);

            Gun modifiedGun = gunItem.getModifiedGun(gun);
            Gun useGun = (modifiedGun != null && modifiedGun.getProjectile() != null) ? modifiedGun : gunItem.getGun();

            SC2GunCompat.performGunAttack(medic, target, gun, false);

            // 自己治療: 弾は発射者を無視するので、効果を直接付与する
            if (selfHeal) {
                applyImpactEffectToSelf(medic, useGun);
            }
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    /** 自己治療時の俯角(ほぼ真下、足元前方の地面に着弾させる)。 */
    private static final float SELF_HEAL_PITCH = 82.0f;

    private static void aimAtOwnFeet(EntityMaid medic) {
        float yaw = medic.getYRot();
        float pitch = SELF_HEAL_PITCH;
        medic.setYRot(yaw);
        medic.setXRot(pitch);
        medic.yRotO = yaw;
        medic.xRotO = pitch;
        medic.setYHeadRot(yaw);

        Vec3 point = medic.position().add(Vec3.directionFromRotation(pitch, yaw).scale(1.5));
        medic.getLookControl().setLookAt(point.x, point.y, point.z, 30.0f, 30.0f);
    }

    /** 十字軍弾の impactEffect(効果・持続・レベル・確率)を自分自身に付与する。 */
    private static void applyImpactEffectToSelf(EntityMaid medic, Gun gun) {
        if (gun == null || gun.getProjectile() == null) return;
        ResourceLocation effectId = gun.getProjectile().getImpactEffect();
        if (effectId == null) return;
        float chance = gun.getProjectile().getImpactEffectChance();
        if (chance < 1.0f && medic.getRandom().nextFloat() >= chance) return;

        Holder<MobEffect> effect = effectHolder(effectId);
        if (effect == null) return;
        medic.addEffect(new MobEffectInstance(effect,
                gun.getProjectile().getImpactEffectDuration(),
                gun.getProjectile().getImpactEffectAmplifier()));

        if (medic.level() instanceof ServerLevel) {
            medic.level().levelEvent(2002, medic.blockPosition(), effect.value().getColor());
        }
    }
}
