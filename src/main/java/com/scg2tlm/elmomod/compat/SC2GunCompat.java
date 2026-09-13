package com.scg2tlm.elmomod.compat;

import com.scg2tlm.elmomod.SCG2TLMConfig;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.item.enchantment.DamageEnchantment;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.IItemHandlerModifiable;
import net.minecraftforge.items.ItemHandlerHelper;
import net.minecraftforge.items.wrapper.RangedWrapper;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import com.mojang.logging.LogUtils;
import top.ribs.scguns.util.GunModifierHelper;

import com.github.tartaricacid.touhoulittlemaid.geckolib3.core.builder.Animation;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.core.builder.AnimationBuilder;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.core.builder.ILoopType;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.core.controller.AnimationController;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.energy.IEnergyStorage;
import top.ribs.scguns.common.Gun;
import top.ribs.scguns.common.Gun.WeaponType;
import top.ribs.scguns.common.GripType;
import top.ribs.scguns.init.ModTags;
import top.ribs.scguns.item.BayonetItem;
import top.ribs.scguns.item.attachment.IAttachment;
import top.ribs.scguns.entity.ai.AIGunEvent;
import top.ribs.scguns.entity.throwable.ThrowableGrenadeEntity;
import top.ribs.scguns.item.BeaconGrenadeItem;
import top.ribs.scguns.item.GasGrenadeItem;
import top.ribs.scguns.item.GrenadeItem;
import top.ribs.scguns.item.AmmoBoxItem;
import top.ribs.scguns.item.ammo_boxes.CreativeAmmoBoxItem;
import top.ribs.scguns.item.GunItem;

public final class SC2GunCompat {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Class<?> ENERGY_GUN_CLASS = initEnergyGunClass();

    private static Class<?> initEnergyGunClass() {
        try {
            return Class.forName("top.ribs.scguns.common.item.gun.RechargeableEnergyGunItem");
        } catch (ClassNotFoundException e) {
            return null;
        }
    }

    private static final Set<String> SC2_MINIGUN_GUNS = Set.of(
            "thunderhead", "gattaler", "weevil", "hullbreaker",
            "cr4k_mining_laser", "flayed_god", "spitfire", "cyclone"
    );
    private static final Set<String> CC_MINIGUN_GUNS = Set.of(
            "electrothermal_autocannon"
    );
    private static final Set<String> SC2_RPG_GUNS = Set.of(
            "kiln_gun", "scratches", "dozier_rl", "terra_incognita"
    );
    private static final Set<String> CC_RPG_GUNS = Set.of(
            "lustre"
    );
    private static final Set<String> OREG_MINIGUN_GUNS = Set.of(
            "electrum_spoolgun", "mauvite_beam_rifle"
    );
    private static final Set<String> OREG_RPG_GUNS = Set.of(
            "electrum_bomber", "mauvite_launcher"
    );

    public static final String SCGUNS_NS = "scguns";
    public static final String SCCAVES_NS = "scguns_cnc";
    public static final String OREGUNIZED_NS = "scguns_oregunized";

    /**
     * 这个物品是不是「SC2 体系的枪」。
     *
     * <h2>判据：类型，而不是命名空间</h2>
     * <p>SC2 自己就是这么判的 —— {@code NetworkGunManager.prepare} 里
     * {@code ForgeRegistries.ITEMS.getValues().stream().filter(item -> item instanceof GunItem)}，
     * 它遍历<b>全部</b>注册物品做这个判断。实测证据：</p>
     * <ul>
     *   <li>{@code AnimatedGunItem} / {@code ScorchedWeapon} 都 {@code extends GunItem}；</li>
     *   <li>SC2 本体 {@code GunItem} 的 16 个子类<b>全是枪</b>
     *       （AirGun / Energy / Underwater / Silenced / DiamondSteel / DualWield / Sculk …），
     *       没有一个刺刀、弹药之类；</li>
     *   <li>附属的枪定义都在 {@code data/<附属modId>/guns/}，即物品位于附属自己的命名空间。</li>
     * </ul>
     *
     * <h2>为什么不再维护命名空间白名单</h2>
     * <p>曾经的实现是「{@code scguns} / {@code scguns_cnc} / {@code scguns_oregunized}
     * + 一张手写集合」。但 SC2 的装载约定是<b>枪定义放在自己 modId 的命名空间下</b>，
     * 于是「每来一个新附属就多一个新命名空间」是结构性的，白名单必然一直欠债
     * （实测就先后为 {@code scguns_cnc}、{@code scgunsww1} 各补过一次）。</p>
     * <p>改成类型判据后<b>零维护</b>：任何走 SC2 体系的附属（含以后新出的）自动生效。</p>
     *
     * <h2>顺带修掉的一个假阳性</h2>
     * <p>旧的白名单只看命名空间，不看它是不是枪：{@code scguns:syringe}、{@code scguns:gun_frame}
     * 这类同命名空间的非枪物品也会被判为 true。凡是<b>只</b>调用本方法、没有额外
     * {@code instanceof GunItem} 的地方都会被带偏（例：女仆受击反击的判定）。类型判据没有这个问题。</p>
     *
     * <h2>性能</h2>
     * <p>不劣于旧实现，实际略优：{@code instanceof} 是 JVM 类型检查，
     * 而旧实现要先做一次 {@code ForgeRegistries.ITEMS.getKey} 注册表反查。</p>
     */
    public static boolean isSC2Gun(ItemStack stack) {
        if (stack.isEmpty()) return false;
        return stack.getItem() instanceof GunItem;
    }

    /**
     * 「排队枪毙时代的古董枪」名单 —— 只影响 {@link #isLineInfantryGun} 那个配置开关。
     *
     * <p>这里的 {@code path} 是相对物品 id 的<b>路径</b>，与命名空间无关，所以
     * {@code scguns:musket} 之类的原版 SC2 古董枪照旧命中。但 {@code scgunsww1} 那些
     * 一战的枪（{@code mosin_nagant}、{@code luger} …）路径不同名，<b>不会</b>落进来。</p>
     *
     * <p>这是有意保留的：{@code LINE_INFANTRY} 是<b>玩法设计选择</b>（哪些枪该站成横队），
     * 不是「这物品是不是枪」这类可推导的事实，因此不适合做成自动判据。</p>
     */
    public static boolean isAntiqueGun(ItemStack stack) {
        if (stack.isEmpty()) return false;
        ResourceLocation key = ForgeRegistries.ITEMS.getKey(stack.getItem());
        if (key == null) return false;
        String path = key.getPath();
        return "musket".equals(path) || "flintlock_pistol".equals(path)
                || "repeating_musket".equals(path) || "laser_musket".equals(path)
                || "vulcanic_repeater".equals(path) || "turnpike".equals(path)
                || "forlorn_hope".equals(path);
    }

    public static boolean isLineInfantryGun(ItemStack stack) {
        return SCG2TLMConfig.LINE_INFANTRY.get() && isAntiqueGun(stack);
    }

    public static Vec3 getFormationPosition(EntityMaid maid, LivingEntity target, ItemStack gun) {
        double range = getIdealRange(gun);
        Entity owner = maid.getOwner();
        Vec3 anchor = owner != null ? owner.position() : maid.position();
        Vec3 enemyPos = target.position();
        Vec3 n = enemyPos.subtract(anchor);
        double hd = Math.sqrt(n.x * n.x + n.z * n.z);
        Vec3 normal;
        if (hd < 0.001) {
            normal = new Vec3(0, 0, 1);
        } else {
            normal = new Vec3(n.x / hd, 0, n.z / hd);
        }
        Vec3 lineDir = new Vec3(-normal.z, 0, normal.x);
        double forward = Math.min(range, Math.max(4.0, hd * 0.6));
        Vec3 center = enemyPos.subtract(normal.scale(forward));
        double spacing = SCG2TLMConfig.FORMATION_SPACING.get();
        int size = SCG2TLMConfig.FORMATION_SIZE.get();
        Vec3 rel = maid.position().subtract(center);
        double proj = rel.x * lineDir.x + rel.z * lineDir.z;
        double slotF = Math.round(proj / spacing);
        int half = (size - 1) / 2;
        slotF = Math.max(-half, Math.min(half, slotF));
        return center.add(lineDir.scale(slotF * spacing));
    }

    public static boolean isCCGun(ItemStack stack) {
        if (stack.isEmpty()) return false;
        ResourceLocation key = ForgeRegistries.ITEMS.getKey(stack.getItem());
        return key != null && SCCAVES_NS.equals(key.getNamespace());
    }

    public static boolean isOregunizedGun(ItemStack stack) {
        if (stack.isEmpty()) return false;
        ResourceLocation key = ForgeRegistries.ITEMS.getKey(stack.getItem());
        return key != null && OREGUNIZED_NS.equals(key.getNamespace());
    }

    public static ResourceLocation getGunId(ItemStack stack) {
        if (isSC2Gun(stack)) {
            return ForgeRegistries.ITEMS.getKey(stack.getItem());
        }
        return null;
    }

    public static boolean isEnergyGun(ItemStack stack) {
        if (stack.isEmpty()) return false;
        if (ENERGY_GUN_CLASS == null) return false;
        return ENERGY_GUN_CLASS.isInstance(stack.getItem());
    }

    private static final Map<String, Integer> ENERGY_RECHARGE_RATES = Map.of(
            "lustre", 2,
            "electrothermal_autocannon", 50,
            "scatterer", 10
    );

    public static void tickEnergyRecharge(ItemStack stack) {
        if (ENERGY_GUN_CLASS == null) return;
        if (!ENERGY_GUN_CLASS.isInstance(stack.getItem())) return;
        ResourceLocation key = ForgeRegistries.ITEMS.getKey(stack.getItem());
        if (key == null) return;
        int rate = ENERGY_RECHARGE_RATES.getOrDefault(key.getPath(), 10);
        stack.getCapability(ForgeCapabilities.ENERGY, null).ifPresent(energy -> {
            if (energy.getEnergyStored() < energy.getMaxEnergyStored()) {
                energy.receiveEnergy(rate, false);
            }
        });
    }

    public static int performGunAttack(LivingEntity maid, LivingEntity target, ItemStack gun) {
        if (!(maid instanceof Mob mob)) return 10;
        if (maid.level().isClientSide) return 10;
        if (!(gun.getItem() instanceof GunItem gunItem)) {
            return 10;
        }

        Gun modifiedGun = gunItem.getModifiedGun(gun);
        Gun.General general = getGeneral(gun, gunItem);
        int rate = GunModifierHelper.getModifiedRate(gun, general.getRate());

        if (!Gun.hasAmmo(gun) && !isEnergyGun(gun)) {
            return rate;
        }

        rotateToFace(maid, target);

        mob.swing(InteractionHand.MAIN_HAND);
        if (modifiedGun != null && modifiedGun.getProjectile() != null && modifiedGun.getProjectile().getItem() != null) {
            AIGunEvent.performGunAttack(mob, target, gun, modifiedGun, SCG2TLMConfig.GUN_ACCURACY.get().floatValue());
        } else {
            AIGunEvent.performGunAttack(mob, target, gun, gunItem.getGun(), SCG2TLMConfig.GUN_ACCURACY.get().floatValue());
        }

        if (target instanceof Mob mobTarget) {
            mobTarget.setLastHurtByMob(maid);
            mobTarget.setTarget(mob);
        }

        playFireSound(maid, gun, gunItem);

        if (!isEnergyGun(gun)) {
            int ammoCount = Gun.getAmmoCount(gun);
            if (ammoCount > 0) {
                gun.getOrCreateTag().putInt("AmmoCount", ammoCount - 1);
                Gun.Projectile projectile = getProjectile(gun, gunItem);
                if (projectile != null && projectile.ejectsCasing() && !projectile.ejectDuringReload()
                        && maid.getRandom().nextFloat() < 0.8f) {
                    spawnCasing(maid, gun, gunItem, 1);
                }
            }
        }

        int bursts = general.getBurstAmount();
        if (bursts > 1) {
            int shotInBurst = gun.getOrCreateTag().getInt("scg2tlm:burst_shot");
            shotInBurst++;
            if (shotInBurst >= bursts) {
                gun.getOrCreateTag().putInt("scg2tlm:burst_shot", 0);
                return Math.max(general.getBurstCooldown(), rate * 2);
            }
            gun.getOrCreateTag().putInt("scg2tlm:burst_shot", shotInBurst);
        }

        return rate;
    }

    public static int getAttackCooldown(ItemStack gun) {
        if (!(gun.getItem() instanceof GunItem gunItem)) return 5;
        return GunModifierHelper.getModifiedRate(gun, getGeneral(gun, gunItem).getRate());
    }

    private static Gun.General getGeneral(ItemStack gun, GunItem gunItem) {
        Gun modifiedGun = gunItem.getModifiedGun(gun);
        if (modifiedGun != null) {
            Gun.General general = modifiedGun.getGeneral();
            if (general != null) return general;
        }
        return gunItem.getGun().getGeneral();
    }

    private static Gun.Reloads getReloads(ItemStack gun, GunItem gunItem) {
        Gun modifiedGun = gunItem.getModifiedGun(gun);
        if (modifiedGun != null) {
            Gun.Reloads reloads = modifiedGun.getReloads();
            if (reloads != null) return reloads;
        }
        return gunItem.getGun().getReloads();
    }

    private static Gun.Sounds getSounds(ItemStack gun, GunItem gunItem) {
        Gun modifiedGun = gunItem.getModifiedGun(gun);
        if (modifiedGun != null) {
            Gun.Sounds sounds = modifiedGun.getSounds();
            if (sounds != null) return sounds;
        }
        return gunItem.getGun().getSounds();
    }

    private static Gun.Projectile getProjectile(ItemStack gun, GunItem gunItem) {
        Gun modifiedGun = gunItem.getModifiedGun(gun);
        if (modifiedGun != null) {
            Gun.Projectile projectile = modifiedGun.getProjectile();
            if (projectile != null) return projectile;
        }
        return gunItem.getGun().getProjectile();
    }

    private static void playFireSound(LivingEntity entity, ItemStack gun, GunItem gunItem) {
        boolean silenced = GunModifierHelper.isSilencedFire(gun);
        Gun.Sounds sounds = getSounds(gun, gunItem);
        ResourceLocation soundId = silenced ? sounds.getSilencedFire() : sounds.getFire();
        if (soundId == null && silenced) {
            soundId = sounds.getFire();
        }
        if (soundId != null) {
            SoundEvent soundEvent = ForgeRegistries.SOUND_EVENTS.getValue(soundId);
            if (soundEvent != null) {
                float volume = GunModifierHelper.getFireSoundVolume(gun) * 4.0f;
                entity.level().playSound(null, entity.getX(), entity.getY(), entity.getZ(),
                        soundEvent, SoundSource.NEUTRAL, volume, 0.9f + entity.getRandom().nextFloat() * 0.2f);
            }
        }
    }

    public static void playGunSound(LivingEntity entity, ItemStack gun, ResourceLocation soundId, float volume) {
        if (soundId == null) return;
        SoundEvent soundEvent = ForgeRegistries.SOUND_EVENTS.getValue(soundId);
        if (soundEvent != null) {
            entity.level().playSound(null, entity.getX(), entity.getY(), entity.getZ(),
                    soundEvent, SoundSource.NEUTRAL, volume, 0.9f + entity.getRandom().nextFloat() * 0.2f);
        }
    }

    public static void playPreReloadSound(LivingEntity entity, ItemStack gun) {
        if (!(gun.getItem() instanceof GunItem gunItem)) return;
        playGunSound(entity, gun, getSounds(gun, gunItem).getPreReload(), 1.0f);
    }

    public static void playReloadSound(LivingEntity entity, ItemStack gun) {
        if (!(gun.getItem() instanceof GunItem gunItem)) return;
        playGunSound(entity, gun, getSounds(gun, gunItem).getReload(), 1.0f);
    }

    public static void playCockSound(LivingEntity entity, ItemStack gun) {
        if (!(gun.getItem() instanceof GunItem gunItem)) return;
        playGunSound(entity, gun, getSounds(gun, gunItem).getCock(), 1.0f);
    }

    /**
     * 播放枪械<b>自己配置的</b>开火音效（含消音分支）。
     *
     * <p>给医疗兵（十字军）那条非攻击路径复用：{@code SC2HealCompat.fireHealingShot} 直接调
     * {@code AIGunEvent.performGunAttack} 生成弹丸，绕过了 {@link #performGunAttack}，
     * 于是这一枪<b>完全没有音效</b>。</p>
     */
    public static void playGunFireSound(LivingEntity entity, ItemStack gun) {
        if (!(gun.getItem() instanceof GunItem gunItem)) return;
        playFireSound(entity, gun, gunItem);
    }

    /**
     * 播放「换弹完成」音效（配置项 {@code reload_end_sound}，空字符串 = 静音）。
     *
     * <p>不用枪械的 {@code cock} 字段：SC2 那把 {@code item/pistol/cock.ogg} 实际是换弹录音，
     * 放在换弹结束的位置听起来就是又换了一次弹。</p>
     */
    public static void playReloadEndSound(LivingEntity entity, ItemStack gun) {
        String configured = SCG2TLMConfig.RELOAD_END_SOUND.get();
        if (configured == null || configured.isBlank()) return;
        ResourceLocation id = ResourceLocation.tryParse(configured.trim());
        if (id == null) return;
        playGunSound(entity, gun, id, 1.0f);
    }

    public static boolean isGrenade(ItemStack stack) {
        if (stack.isEmpty()) return false;
        Item item = stack.getItem();
        if (item instanceof GrenadeItem || item instanceof GasGrenadeItem || item instanceof BeaconGrenadeItem) {
            return true;
        }
        return false;
    }

    public static void performGrenadeThrow(EntityMaid maid, LivingEntity target, ItemStack stack) {
        if (maid.level().isClientSide) return;
        rotateToFace(maid, target);

        Item item = stack.getItem();
        ThrowableGrenadeEntity projectile = null;

        if (item instanceof GrenadeItem grenadeItem) {
            projectile = grenadeItem.create(maid.level(), maid, 30);
        } else if (item instanceof GasGrenadeItem gasItem) {
            projectile = gasItem.create(maid.level(), maid, 30, 4.0f);
        } else if (item instanceof BeaconGrenadeItem beaconItem) {
            projectile = beaconItem.create(maid.level(), maid, 30);
        }

        if (projectile != null) {
            projectile.setPos(maid.getX(), maid.getEyeY(), maid.getZ());
            double dx = target.getX() - maid.getX();
            double dy = target.getEyeY() - maid.getEyeY();
            double dz = target.getZ() - maid.getZ();
            double horizontalDist = Math.sqrt(dx * dx + dz * dz);
            projectile.shoot(dx, dy + horizontalDist * 0.15, dz, 1.2f, 0.5f);
            maid.level().addFreshEntity(projectile);
            stack.shrink(1);
        }
    }

    private static void rotateToFace(LivingEntity entity, LivingEntity target) {
        double dx = target.getX() - entity.getX();
        double dz = target.getZ() - entity.getZ();
        double dy = target.getEyeY() - entity.getEyeY();
        double dist = Math.sqrt(dx * dx + dz * dz);

        float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0F);
        float pitch = (float) (-Math.toDegrees(Math.atan2(dy, dist)));

        entity.setYRot(yaw);
        entity.setXRot(pitch);
        entity.yRotO = yaw;
        entity.xRotO = pitch;

        if (entity instanceof Mob mob) {
            mob.yHeadRot = yaw;
            mob.yHeadRotO = yaw;
        }
    }

    public static boolean needsReload(ItemStack stack) {
        if (stack.isEmpty() || !(stack.getItem() instanceof GunItem)) {
            return false;
        }
        if (isEnergyGun(stack)) {
            return false;
        }
        if (Gun.Reloads.hasInfiniteAmmo(stack)) {
            return false;
        }
        return !Gun.hasAmmo(stack);
    }

    public static int getReloadTime(ItemStack stack) {
        if (stack.isEmpty() || !(stack.getItem() instanceof GunItem gunItem)) return 20;
        Gun.Reloads reloads = getReloads(stack, gunItem);
        double baseTime = reloads.getReloadTimer() + reloads.getEmptyMagTimer();
        return (int) GunModifierHelper.getModifiedReloadSpeed(stack, baseTime);
    }

    public static int getMaxAmmo(ItemStack stack) {
        if (stack.isEmpty() || !(stack.getItem() instanceof GunItem gunItem)) return 0;
        int capacity = GunModifierHelper.getModifiedAmmoCapacity(stack, gunItem.getGun());
        LOGGER.info("getMaxAmmo: capacity={}, stack={}", capacity, stack);
        return capacity;
    }

    public static void setFullAmmo(ItemStack stack) {
        if (stack.isEmpty() || !(stack.getItem() instanceof GunItem)) {
            return;
        }
        int maxAmmo = getMaxAmmo(stack);
        stack.getOrCreateTag().putInt("AmmoCount", maxAmmo);
    }

    public static boolean hasAmmoInInventory(EntityMaid maid, ItemStack gun) {        if (SCG2TLMConfig.RELOAD_FREE_AMMO.get()) {
            return true;
        }
        if (isEnergyGun(gun)) {
            return true;
        }
        if (!(gun.getItem() instanceof GunItem gunItem)) {
            return false;
        }
        Item ammoType = getProjectileItem(gun, gunItem);
        if (ammoType == null) {
            return false;
        }
        IItemHandler inv = maid.getAvailableInv(true);
        if (hasCreativeAmmoBox(inv) && isAmmoProvidedByCreativeBox(ammoType)) {
            return true;
        }
        for (int i = 0; i < inv.getSlots(); i++) {
            ItemStack slot = inv.getStackInSlot(i);
            if (slot.isEmpty()) continue;
            if (slot.getItem() == ammoType) return true;
            if (slot.getItem() instanceof AmmoBoxItem) {
                if (AmmoBoxItem.getContents(slot).anyMatch(s -> s.getItem() == ammoType)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean hasCreativeAmmoBox(IItemHandler inv) {        for (int i = 0; i < inv.getSlots(); i++) {
            ItemStack slot = inv.getStackInSlot(i);
            if (!slot.isEmpty() && slot.getItem() instanceof CreativeAmmoBoxItem) {
                return true;
            }
        }
        return false;
    }

    private static boolean isAmmoProvidedByCreativeBox(Item ammoType) {
        TagKey<Item> scgunsAmmo = TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath("scguns", "ammo"));
        return ammoType.builtInRegistryHolder().is(scgunsAmmo);
    }

    private static Item getProjectileItem(ItemStack gun, GunItem gunItem) {
        Gun modifiedGun = gunItem.getModifiedGun(gun);
        if (modifiedGun != null) {
            var projectile = modifiedGun.getProjectile();
            if (projectile != null) {
                Item item = projectile.getItem();
                if (item != null) return item;
            }
        }
        var baseProjectile = gunItem.getGun().getProjectile();
        if (baseProjectile != null) {
            Item baseItem = baseProjectile.getItem();
            if (baseItem != null) return baseItem;
        }
        return null;
    }

    public static boolean reloadFromInventory(EntityMaid maid, ItemStack gun) {
        if (isEnergyGun(gun)) {
            return false;
        }
        if (!(gun.getItem() instanceof GunItem gunItem)) {
            return false;
        }
        if (SCG2TLMConfig.RELOAD_FREE_AMMO.get()) {
            int maxAmmo = getMaxAmmo(gun);
            CompoundTag tag = gun.getOrCreateTag();
            tag.putInt("AmmoCount", maxAmmo);
            tag.putInt("PrevAmmoCount", maxAmmo);
            LOGGER.info("reloadFromInventory(free): maxAmmo={}, stack={}", maxAmmo, gun);
            return true;
        }
        Gun modifiedGun = gunItem.getModifiedGun(gun);
        int maxAmmo = getMaxAmmo(gun);
        Item ammoType = getProjectileItem(gun, gunItem);
        if (ammoType == null) {
            LOGGER.warn("reloadFromInventory: ammoType is null, cannot reload");
            return false;
        }

        ResourceLocation ammoId = ForgeRegistries.ITEMS.getKey(ammoType);
        boolean isSingleReload = isSingleReloadAmmo(ammoType);
        boolean isEnergyCore = ammoId != null && "scguns".equals(ammoId.getNamespace())
                && "energy_core".equals(ammoId.getPath());
        boolean isBlazeFuel = ammoId != null && "scguns".equals(ammoId.getNamespace())
                && "blaze_fuel".equals(ammoId.getPath());

        IItemHandler inv = maid.getAvailableInv(true);
        if (hasCreativeAmmoBox(inv) && isAmmoProvidedByCreativeBox(ammoType)) {
            int maxAmmo2 = getMaxAmmo(gun);
            CompoundTag tag = gun.getOrCreateTag();
            tag.putInt("AmmoCount", maxAmmo2);
            tag.putInt("PrevAmmoCount", maxAmmo2);
            LOGGER.info("reloadFromInventory(creative_box): maxAmmo={}, stack={}", maxAmmo2, gun);
            return true;
        }
        int found = 0;

        if (isSingleReload) {
            for (int i = 0; i < inv.getSlots(); i++) {
                ItemStack slot = inv.getStackInSlot(i);
                if (!slot.isEmpty() && slot.getItem() == ammoType) {
                    ItemStack taken = inv.extractItem(i, 1, false);
                    found = taken.getCount();
                    break;
                }
            }
            if (found == 0) {
                found = extractFromAmmoBoxes(maid, inv, ammoType, 1);
            }
            if (found > 0) {
                LOGGER.info("reloadFromInventory(single): maxAmmo={}, found={}", maxAmmo, found);
                gun.getOrCreateTag().putInt("AmmoCount", maxAmmo);
                if (isBlazeFuel) {
                    spawnEmptyTank(maid);
                } else if (ammoId != null && SCCAVES_NS.equals(ammoId.getNamespace())) {
                    spawnDepletedNSItem(maid, ammoId.getNamespace(), ammoId.getPath(), "depleted_crystal");
                } else if (ammoId != null && OREGUNIZED_NS.equals(ammoId.getNamespace())) {
                    spawnDepletedNSItem(maid, ammoId.getNamespace(), ammoId.getPath(), "depleted_electrum_cell");
                } else {
                    spawnDepletedCore(maid);
                }
            }
            return found > 0;
        } else {
            for (int i = 0; i < inv.getSlots(); i++) {
                ItemStack slot = inv.getStackInSlot(i);
                if (!slot.isEmpty() && slot.getItem() == ammoType) {
                    int toTake = Math.min(slot.getCount(), maxAmmo - found);
                    ItemStack taken = inv.extractItem(i, toTake, false);
                    found += taken.getCount();
                    if (found >= maxAmmo) break;
                }
            }
            if (found < maxAmmo) {
                found += extractFromAmmoBoxes(maid, inv, ammoType, maxAmmo - found);
            }
            if (found > 0) {
                int newCount = Math.min(found, maxAmmo);
                CompoundTag tag = gun.getOrCreateTag();
                int prevAmmo = tag.getInt("AmmoCount");

                int casingCount = tag.contains("PrevAmmoCount") ? tag.getInt("PrevAmmoCount") : 0;
                tag.putInt("AmmoCount", newCount);
                tag.putInt("PrevAmmoCount", newCount);
                LOGGER.info("reloadFromInventory: maxAmmo={}, found={}, newCount={}, prevAmmo={}, ammoType={}", maxAmmo, found, newCount, prevAmmo, ammoType);
                Gun.Projectile proj = getProjectile(gun, gunItem);
                if (proj != null && proj.ejectDuringReload()) {
                    spawnCasing(maid, gun, gunItem, casingCount);
                }
            }
            return found > 0;
        }

    }

    private static int extractFromAmmoBoxes(EntityMaid maid, IItemHandler inv, Item ammoType, int maxAmount) {
        int extracted = 0;
        for (int i = 0; i < inv.getSlots() && extracted < maxAmount; i++) {
            ItemStack slot = inv.getStackInSlot(i);
            if (slot.isEmpty() || !(slot.getItem() instanceof AmmoBoxItem)) continue;

            if (AmmoBoxItem.getContents(slot).noneMatch(s -> s.getItem() == ammoType)) continue;

            ItemStack boxStack = inv.extractItem(i, Integer.MAX_VALUE, false);
            if (boxStack.isEmpty()) continue;

            int toExtract = Math.min(AmmoBoxItem.getTotalItemCount(boxStack), maxAmount - extracted);
            int taken = removeFromBoxTag(boxStack, ammoType, toExtract);
            extracted += taken;

            ItemStack remainder = inv.insertItem(i, boxStack, false);
            if (!remainder.isEmpty()) {
                ItemEntity entity = new ItemEntity(maid.level(),
                        maid.getX(), maid.getY() + 1.0, maid.getZ(), remainder);
                entity.setPickUpDelay(10);
                maid.level().addFreshEntity(entity);
            }
        }
        return extracted;
    }

    private static int removeFromBoxTag(ItemStack boxStack, Item ammoType, int maxRemove) {
        CompoundTag tag = boxStack.getOrCreateTag();
        if (!tag.contains("Items", Tag.TAG_LIST)) return 0;

        ListTag items = tag.getList("Items", Tag.TAG_COMPOUND);
        int remaining = maxRemove;

        for (int j = 0; j < items.size() && remaining > 0; j++) {
            CompoundTag itemTag = items.getCompound(j);
            ItemStack itemStack = ItemStack.of(itemTag);
            if (itemStack.getItem() == ammoType) {
                int take = Math.min(itemStack.getCount(), remaining);
                itemStack.shrink(take);
                remaining -= take;
                if (itemStack.isEmpty()) {
                    items.remove(j);
                    j--;
                } else {
                    itemTag.putInt("Count", itemStack.getCount());
                }
            }
        }

        int removed = maxRemove - remaining;
        if (items.isEmpty()) {
            tag.remove("Items");
        } else {
            tag.put("Items", items);
        }
        return removed;
    }

    private static int insertIntoAmmoBoxes(IItemHandler inv, ItemStack stack) {
        int remaining = stack.getCount();
        for (int i = 0; i < inv.getSlots() && remaining > 0; i++) {
            ItemStack slot = inv.getStackInSlot(i);
            if (slot.isEmpty() || !(slot.getItem() instanceof AmmoBoxItem)) continue;

            ItemStack toAdd = stack.copy();
            toAdd.setCount(remaining);
            int added = AmmoBoxItem.add(slot, toAdd);
            remaining -= added;
        }
        return remaining;
    }

    private static void spawnDepletedCore(EntityMaid maid) {
        Item depletedCore = ForgeRegistries.ITEMS.getValue(
                ResourceLocation.fromNamespaceAndPath("scguns", "depleted_energy_core"));
        if (depletedCore == null) {
            return;
        }
        ItemStack stack = new ItemStack(depletedCore);
        IItemHandler inv = maid.getAvailableInv(false);
        ItemStack remaining = ItemHandlerHelper.insertItemStacked(inv, stack, false);
        if (!remaining.isEmpty()) {
            ItemEntity entity = new ItemEntity(maid.level(),
                    maid.getX(), maid.getY() + 1.0, maid.getZ(),
                    remaining);
            entity.setPickUpDelay(10);
            maid.level().addFreshEntity(entity);
        }
    }

    private static boolean isSingleReloadAmmo(Item item) {
        if (item == null) return false;
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(item);
        if (id == null) return false;
        String ns = id.getNamespace();
        String path = id.getPath();
        if (SCGUNS_NS.equals(ns)) {
            return "energy_core".equals(path) || "blaze_fuel".equals(path);
        }
        if (SCCAVES_NS.equals(ns)) {
            return "pulse_core".equals(path);
        }
        if (OREGUNIZED_NS.equals(ns)) {
            return "electrum_cell".equals(path) || "mauvite_core".equals(path);
        }
        return false;
    }

    private static void spawnDepletedNSItem(EntityMaid maid, String namespace, String ammoPath, String defaultDepletedPath) {
        String depletedItemPath = "depleted_" + ammoPath;
        Item depleted = ForgeRegistries.ITEMS.getValue(
                ResourceLocation.fromNamespaceAndPath(namespace, depletedItemPath));
        if (depleted == null) {
            depleted = ForgeRegistries.ITEMS.getValue(
                    ResourceLocation.fromNamespaceAndPath(namespace, defaultDepletedPath));
        }
        if (depleted == null) return;
        ItemStack stack = new ItemStack(depleted);
        IItemHandler inv = maid.getAvailableInv(false);
        ItemStack remaining = ItemHandlerHelper.insertItemStacked(inv, stack, false);
        if (!remaining.isEmpty()) {
            ItemEntity entity = new ItemEntity(maid.level(),
                    maid.getX(), maid.getY() + 1.0, maid.getZ(), remaining);
            entity.setPickUpDelay(10);
            maid.level().addFreshEntity(entity);
        }
    }

    private static void spawnEmptyTank(EntityMaid maid) {
        Item emptyTank = ForgeRegistries.ITEMS.getValue(
                ResourceLocation.fromNamespaceAndPath("scguns", "empty_tank"));
        if (emptyTank == null) {
            return;
        }
        ItemStack stack = new ItemStack(emptyTank);
        IItemHandler inv = maid.getAvailableInv(false);
        ItemStack remaining = ItemHandlerHelper.insertItemStacked(inv, stack, false);
        if (!remaining.isEmpty()) {
            ItemEntity entity = new ItemEntity(maid.level(),
                    maid.getX(), maid.getY() + 1.0, maid.getZ(),
                    remaining);
            entity.setPickUpDelay(10);
            maid.level().addFreshEntity(entity);
        }
    }

    private static void spawnCasing(LivingEntity entity, ItemStack gun, GunItem gunItem, int count) {
        if (SCG2TLMConfig.RELOAD_FREE_AMMO.get()) {
            return;
        }

        Item ammoType = getProjectileItem(gun, gunItem);
        if (ammoType == null || isSingleReloadAmmo(ammoType)) {
            return;
        }

        Gun.Projectile projectile = getProjectile(gun, gunItem);
        if (projectile == null) return;

        boolean ejects = projectile.ejectsCasing();
        if (!ejects) return;

        ResourceLocation casingId = projectile.getCasingType();
        if (casingId == null) {
            casingId = projectile.casingType;
            if (casingId == null) {
                return;
            }
        }

        Item casingItem = ForgeRegistries.ITEMS.getValue(casingId);
        if (casingItem == null) {
            return;
        }

        ItemStack casingStack = new ItemStack(casingItem, count);

        if (entity instanceof EntityMaid maid) {
            IItemHandlerModifiable inv = maid.getAvailableInv(false);

            int remaining = insertIntoAmmoBoxes(inv, casingStack.copy());
            if (remaining <= 0) return;

            IItemHandler noOffhand = new RangedWrapper(inv, 0, inv.getSlots() - 1);
            ItemStack leftover = ItemHandlerHelper.insertItemStacked(noOffhand, new ItemStack(casingItem, remaining), false);
            if (!leftover.isEmpty()) {
                ItemEntity entityItem = new ItemEntity(entity.level(),
                        entity.getX(), entity.getY() + 0.5, entity.getZ(),
                        leftover);
                entityItem.setPickUpDelay(10);
                entity.level().addFreshEntity(entityItem);
            }
        } else {
            ItemEntity entityItem = new ItemEntity(entity.level(),
                    entity.getX(), entity.getY() + 0.5, entity.getZ(),
                    casingStack);
            entityItem.setPickUpDelay(20);
            entity.level().addFreshEntity(entityItem);
        }
    }

    public static float getGunRange() {
        return 64.0f;
    }

    public static WeaponType getWeaponType(ItemStack stack) {
        if (stack.isEmpty() || !(stack.getItem() instanceof GunItem gunItem)) return null;
        return getGeneral(stack, gunItem).getWeaponType();
    }

    public static boolean isMinigunGun(ItemStack stack) {
        ResourceLocation key = ForgeRegistries.ITEMS.getKey(stack.getItem());
        if (key == null) return false;
        return SC2_MINIGUN_GUNS.contains(key.getPath())
                || CC_MINIGUN_GUNS.contains(key.getPath())
                || OREG_MINIGUN_GUNS.contains(key.getPath());
    }

    public static boolean isRpgGun(ItemStack stack) {
        ResourceLocation key = ForgeRegistries.ITEMS.getKey(stack.getItem());
        if (key == null) return false;
        return SC2_RPG_GUNS.contains(key.getPath())
                || CC_RPG_GUNS.contains(key.getPath())
                || OREG_RPG_GUNS.contains(key.getPath());
    }

    public static boolean setAnimOnce(AnimationController<?> controller, String name, ILoopType loopType) {
        Animation current = controller.getCurrentAnimation();
        if (current != null && name.equals(current.animationName)) {
            return true;
        }
        controller.setAnimation(new AnimationBuilder().addAnimation(name, loopType));
        return false;
    }

    /** 特定武器 id 的射程分类覆盖（用户手动指定，忽略其本体 WeaponType）。 */
    public static String getSpecialRangeClass(ItemStack stack) {
        ResourceLocation key = ForgeRegistries.ITEMS.getKey(stack.getItem());
        if (key == null || !key.getNamespace().equals("scguns")) {
            return null;
        }
        return switch (key.getPath()) {
            case "sterilizer" -> "rifle";
            case "plasmabuss", "libertas" -> "shotgun";
            case "gauss_rifle" -> "sniper";
            default -> null;
        };
    }

    /** 近战类枪械（霰弹枪/喷火器/电击）：需要贴近目标输出，不受远程走位限制。 */
    public static boolean isMeleeClassGun(ItemStack stack) {
        String special = getSpecialRangeClass(stack);
        if ("shotgun".equals(special)) {
            return true;
        }
        WeaponType type = getWeaponType(stack);
        return type == WeaponType.shotgun || type == WeaponType.flamethrower || type == WeaponType.shock;
    }

    public static double getIdealRange(ItemStack stack) {
        double baseRange = SCG2TLMConfig.GUN_RANGE.get();
        String special = getSpecialRangeClass(stack);
        if (special != null) {
            if (special.equals("sniper")) return Math.min(baseRange * 1.25, SCG2TLMConfig.SNIPER_SEARCH_RADIUS.get() * 0.85);
            if (special.equals("rifle")) return baseRange * 0.5;
            if (special.equals("shotgun")) return baseRange * 0.125;
        }
        WeaponType type = getWeaponType(stack);
        if (type == null) return baseRange * 0.375;
        if (type == WeaponType.sniper) return Math.min(baseRange * 1.25, SCG2TLMConfig.SNIPER_SEARCH_RADIUS.get() * 0.85);
        if (type == WeaponType.rifle) return baseRange * 0.5;
        if (type == WeaponType.magnum) return baseRange * 0.4375;
        if (type == WeaponType.smg) return baseRange * 0.25;
        if (type == WeaponType.shotgun) return baseRange * 0.125;
        if (type == WeaponType.pistol) return baseRange * 0.1875;
        if (type == WeaponType.heavy) return baseRange * 0.5;
        if (type == WeaponType.lmg) return baseRange * 0.5625;
        if (type == WeaponType.flamethrower) return baseRange * 0.09375;
        if (type == WeaponType.shock) return baseRange * 0.15625;
        if (type == WeaponType.plasma || type == WeaponType.laser) return Math.min(baseRange * 1.25, SCG2TLMConfig.SNIPER_SEARCH_RADIUS.get() * 0.85);
        return baseRange * 0.375;
    }

    public static double getMinRange(ItemStack stack) {
        double baseRange = SCG2TLMConfig.GUN_RANGE.get();
        String special = getSpecialRangeClass(stack);
        if (special != null) {
            if (special.equals("sniper")) return baseRange * 0.4375;
            if (special.equals("rifle")) return baseRange * 0.125;
            if (special.equals("shotgun")) return baseRange * 0.03125;
        }
        WeaponType type = getWeaponType(stack);
        if (type == null) return baseRange * 0.078125;
        if (type == WeaponType.sniper) return baseRange * 0.4375;
        if (type == WeaponType.rifle) return baseRange * 0.125;
        if (type == WeaponType.magnum) return baseRange * 0.125;
        if (type == WeaponType.smg) return baseRange * 0.0625;
        if (type == WeaponType.shotgun) return baseRange * 0.03125;
        if (type == WeaponType.pistol) return baseRange * 0.046875;
        if (type == WeaponType.heavy) return baseRange * 0.25;
        if (type == WeaponType.lmg) return baseRange * 0.15625;
        if (type == WeaponType.flamethrower) return baseRange * 0.03125;
        if (type == WeaponType.shock) return baseRange * 0.046875;
        if (type == WeaponType.plasma || type == WeaponType.laser) return baseRange * 0.3125;
        return baseRange * 0.078125;
    }

    /**
     * 取该枪的握持类型，失败或无枪时返回 {@code null}。
     *
     * <p>与 {@code getModifiedGun} 路径解耦，供 {@link #getAnimationGunType} 等
     * 需要在「武器数据不全」时兜底的地方使用（实测 SC2 附属 mod 的部分枪缺字段）。</p>
     */
    public static GripType getGripTypeOf(ItemStack stack) {
        if (stack.isEmpty() || !(stack.getItem() instanceof GunItem gunItem)) return null;
        try {
            Gun modifiedGun = gunItem.getModifiedGun(stack);
            if (modifiedGun == null || modifiedGun.getGeneral() == null) return null;
            return modifiedGun.getGeneral().getGripType(stack);
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * 该枪用哪套持枪动画：{@code "pistol"} / {@code "rifle"} / {@code "rpg"}。
     *
     * <h3>判据：握持方式优先（GripType），武器类别兜底</h3>
     * <p>姿势本质上由<b>怎么握</b>决定，而不是由<b>算什么武器</b>决定，所以主判据用 SC2 的
     * {@code GripType}（12 种）：</p>
     * <table border="1">
     *   <caption>握持 → 动画</caption>
     *   <tr><th>GripType</th><th>动画</th></tr>
     *   <tr><td>{@code ONE_HANDED} {@code ONE_HANDED_2} {@code DUAL_WIELD}</td><td>{@code pistol}</td></tr>
     *   <tr><td>{@code TWO_HANDED} {@code TWO_HANDED_SHOTGUN} {@code TWO_HANDED_SMG}</td><td>{@code rifle}</td></tr>
     * </table>
     *
     * <p>这样能天然覆盖一些「按类别判会判错」的情形：</p>
     * <ul>
     *   <li>手枪装了枪托 → 握持变成 {@code TWO_HANDED} → 自动走步枪姿势
     *       （旧实现为此写了一段特判，现在由握持统一处理）；</li>
     *   <li>单手握持的截短霰弹枪 / 手枪型短管枪 → 手枪姿势；</li>
     *   <li>SC2 附属 mod 里<b>只写 gripType、不写 weaponType</b> 的枪
     *       （实测 {@code scgunsww1:luger}）→ 不再被误判成步枪。</li>
     * </ul>
     *
     * <h3>两类特例必须优先于握持判定</h3>
     * <ul>
     *   <li><b>机枪</b>（{@code isMinigunGun}）→ {@code rifle}：机枪的握持类型是
     *       {@code MINI_GUN*}，不属于上面两类；按握持会落到兜底，
     *       这里显式归为步枪姿势。</li>
     *   <li><b>火箭筒</b>（{@code isRpgGun}）→ {@code rpg}：握持为 {@code BAZOOKA}，
     *       同样不属于上面两类。</li>
     * </ul>
     *
     * <h3>已知限制（暂不处理）</h3>
     * <p><b>{@code DUAL_WIELD} 是「假双持」</b>——实测 {@code scguns:rat_king_and_queen}：</p>
     * <ul>
     *   <li>它的几何文件里是 {@code gun_body} 统一下的
     *       {@code main1}/{@code main2}、{@code barrel1}/{@code barrel2}、
     *       {@code magazine}/{@code magazine_2}，即<b>一个物品、两根枪管</b>，并不是两把枪；</li>
     *   <li>整个 SC2 里 {@code DUAL_WIELD} <b>只出现在 {@code GripType} 的枚举定义中</b>，
     *       没有任何逻辑类引用它 —— 也就是说 SC2 自己不为它做任何特殊处理
     *       （不换姿势、不换渲染），它更像一个分类标记。</li>
     * </ul>
     * <p>因此「女仆缺少双持动画」这个说法不成立。目前它按 {@code DUAL_WIELD} 归到
     * {@code pistol} 姿势，<b>视觉上是否合适尚未实测判定</b>，暂按现状保留；
     * 若实测发现应改判，把它从上面那一支挪到 {@code TWO_HANDED} 支即可。</p>
     *
     * <p>兜底顺序：握持读不到 → 用武器类别（手枪/马格南 → {@code pistol}，其余 → {@code rifle}）；
     * 类别也读不到 → {@code rifle}（最保守的通用姿势）。</p>
     */
    public static String getAnimationGunType(ItemStack stack) {
        if (stack.isEmpty() || !(stack.getItem() instanceof GunItem)) return "";

        // ---- 特例优先：这两类有专属动画，且握持类型不在下面的两支里 ----
        if (isMinigunGun(stack)) return "rifle";
        if (isRpgGun(stack)) return "rpg";

        // ---- 主判据：握持方式 ----
        GripType grip = getGripTypeOf(stack);
        if (grip == GripType.ONE_HANDED || grip == GripType.ONE_HANDED_2
                || grip == GripType.DUAL_WIELD) {
            return "pistol";
        }
        if (grip == GripType.TWO_HANDED || grip == GripType.TWO_HANDED_SHOTGUN
                || grip == GripType.TWO_HANDED_SMG) {
            return "rifle";
        }

        // ---- 兜底：握持读不到（或用的是 MINI_GUN/BAZOOKA 之类）时按武器类别 ----
        WeaponType type = getWeaponType(stack);
        if (type == WeaponType.pistol || type == WeaponType.magnum) return "pistol";
        return "rifle";
    }

    public static boolean hasBayonet(ItemStack stack) {
        if (!(stack.getItem() instanceof GunItem gunItem)) return false;
        return gunItem.hasBayonet(stack) || stack.is(ModTags.Items.BUILT_IN_BAYONET);
    }

    public static float getMeleeReach(ItemStack stack) {
        float base = 2.5f;
        if (stack.getItem() instanceof GunItem gunItem) {
            Gun modified = gunItem.getModifiedGun(stack);
            if (modified != null && modified.getGeneral() != null) {
                base = modified.getGeneral().getMeleeReach();
            }
        }
        return base + 0.5f;
    }

    public static int getMeleeCooldownTicks(ItemStack stack) {
        return 20;
    }

    public static float getMeleeDamage(ItemStack stack) {
        if (!(stack.getItem() instanceof GunItem gunItem)) return 1.0f;
        Gun modified = gunItem.getModifiedGun(stack);
        if (modified == null || modified.getGeneral() == null) return 1.0f;
        return modified.getGeneral().getMeleeDamage();
    }

    public static int performMeleeAttack(EntityMaid maid, LivingEntity target, ItemStack gun) {
        if (!(gun.getItem() instanceof GunItem gunItem)) return 10;
        float baseDamage = getMeleeDamage(gun);
        boolean hasBayonet = gunItem.hasBayonet(gun);
        if (hasBayonet) {
            baseDamage += GunModifierHelper.getAdditionalDamage(gun, true);
        }

        float damage = baseDamage;
        if (hasBayonet) {
            damage += getBayonetEnchantmentDamage(gun, target);
        }

        DamageSource damageSource = maid.damageSources().mobAttack(maid);
        target.hurt(damageSource, damage);
        if (hasBayonet) {
            applyBayonetEnchantmentEffects(maid, target, gun);
        }
        applyKnockback(maid, target, gun);

        // 横扫 AoE（参考 TLM EntityMaid.doSweepHurt：以目标为中心按扇形/范围伤害）
        AABB sweepRange = target.getBoundingBox().inflate(1.0D, 0.25D, 1.0D);
        List<LivingEntity> swept = maid.level().getEntitiesOfClass(LivingEntity.class, sweepRange,
                e -> e != maid && e != target && e.isAlive()
                        && !maid.isAlliedTo(e) && maid.hasLineOfSight(e) && maid.canAttackType(e.getType()));
        if (!swept.isEmpty()) {
            float yawRadians = maid.getYRot() * ((float) Math.PI / 180F);
            double knockX = (double) Mth.sin(yawRadians) * 0.5D;
            double knockZ = (double) (-Mth.cos(yawRadians)) * 0.5D;
            for (LivingEntity entity : swept) {
                float sweepDamage = baseDamage * 0.5F;
                if (hasBayonet) {
                    sweepDamage += getBayonetEnchantmentDamage(gun, entity) * 0.5F;
                    applyBayonetEnchantmentEffects(maid, entity, gun);
                }
                entity.push(knockX, 0.0D, knockZ);
                entity.hurt(damageSource, sweepDamage);
            }
        }

        maid.playSound(SoundEvents.PLAYER_ATTACK_SWEEP, 1.0f, 0.9f + maid.getRandom().nextFloat() * 0.2f);
        if (maid.level() instanceof ServerLevel serverLevel) {
            // 横扫粒子放在女仆「正前方」。
            //
            // 修过的 bug：旧实现自己算三角函数，写成
            //     px = x - sin(yaw)
            //     pz = z + cos(yaw)
            // 而 Minecraft 里 yaw 对应的前方向量是 (sin(yaw), 0, -cos(yaw))，
            // 所以那两个符号正好写反，粒子落在了女仆<b>正后方</b>。
            // （同一段代码上方的击退量用的是正确符号，两者当初就不一致。）
            //
            // 现在直接取 getLookAngle()（视线单位向量），既不必再手算三角函数、
            // 也不会出现符号写反的可能。
            Vec3 look = maid.getLookAngle();
            serverLevel.sendParticles(ParticleTypes.SWEEP_ATTACK,
                    maid.getX() + look.x, maid.getY() + maid.getBbHeight() * 0.5D, maid.getZ() + look.z,
                    1, 0.0D, 0.0D, 0.0D, 0.0D);
        }
        return getMeleeCooldownTicks(gun);
    }

    /** 刺刀附魔伤害（对齐 SCG MeleeAttackHandler.getEnchantmentDamageFromBayonet：锐利/亡灵杀手/节肢杀手 × 0.70）。 */
    private static float getBayonetEnchantmentDamage(ItemStack gun, LivingEntity target) {
        if (!(gun.getItem() instanceof GunItem gunItem)) return 0.0f;
        float enchantmentDamage = 0.0f;
        for (IAttachment.Type type : IAttachment.Type.values()) {
            ItemStack attachmentStack = gunItem.getAttachment(gun, type);
            if (!attachmentStack.isEmpty() && attachmentStack.getItem() instanceof BayonetItem) {
                Map<Enchantment, Integer> enchantments = EnchantmentHelper.getEnchantments(attachmentStack);
                for (Map.Entry<Enchantment, Integer> entry : enchantments.entrySet()) {
                    if (entry.getKey() instanceof DamageEnchantment damageEnchantment) {
                        enchantmentDamage += damageEnchantment.getDamageBonus(entry.getValue(), target.getMobType()) * 0.70F;
                    }
                }
            }
        }
        return enchantmentDamage;
    }

    /** 刺刀附魔效果（对齐 SCG applyEnchantmentEffects：火焰附加 / 击退）。 */
    private static void applyBayonetEnchantmentEffects(LivingEntity attacker, LivingEntity target, ItemStack gun) {
        if (!(gun.getItem() instanceof GunItem gunItem)) return;
        for (IAttachment.Type type : IAttachment.Type.values()) {
            ItemStack attachmentStack = gunItem.getAttachment(gun, type);
            if (!attachmentStack.isEmpty() && attachmentStack.getItem() instanceof BayonetItem) {
                Map<Enchantment, Integer> enchantments = EnchantmentHelper.getEnchantments(attachmentStack);
                for (Map.Entry<Enchantment, Integer> entry : enchantments.entrySet()) {
                    Enchantment enchantment = entry.getKey();
                    int level = entry.getValue();
                    if (enchantment == Enchantments.FIRE_ASPECT) {
                        target.setSecondsOnFire(level * 4);
                    } else if (enchantment == Enchantments.KNOCKBACK) {
                        Vec3 direction = target.position().subtract(attacker.position()).normalize();
                        target.knockback(level * 0.5F, -direction.x(), -direction.z());
                    }
                }
            }
        }
    }

    /** 枪身击退附魔（对齐 SCG applyKnockback：0.4 + 击退等级 × 0.5）。 */
    private static void applyKnockback(LivingEntity attacker, LivingEntity target, ItemStack gun) {
        int knockbackLevel = EnchantmentHelper.getItemEnchantmentLevel(Enchantments.KNOCKBACK, gun);
        Vec3 direction = target.position().subtract(attacker.position()).normalize();
        target.knockback(0.4F + knockbackLevel * 0.5F, -direction.x(), -direction.z());
    }
}