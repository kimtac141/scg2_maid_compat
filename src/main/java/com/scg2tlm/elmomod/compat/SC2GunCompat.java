package com.scg2tlm.elmomod.compat;

import com.scg2tlm.elmomod.SCG2TLMConfig;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.phys.Vec3;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import net.neoforged.neoforge.items.wrapper.RangedWrapper;

import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import com.mojang.logging.LogUtils;
import top.ribs.scguns.common.Gun;
import top.ribs.scguns.common.Gun.WeaponType;
import top.ribs.scguns.common.GripType;
import top.ribs.scguns.common.ProjectileManager;
import top.ribs.scguns.init.ModTags;
import top.ribs.scguns.interfaces.IProjectileFactory;
import top.ribs.scguns.entity.projectile.ProjectileEntity;
import top.ribs.scguns.entity.throwable.ThrowableGrenadeEntity;
import top.ribs.scguns.item.BeaconGrenadeItem;
import top.ribs.scguns.item.GasGrenadeItem;
import top.ribs.scguns.item.GrenadeItem;
import top.ribs.scguns.item.AmmoBoxItem;
import top.ribs.scguns.item.GunItem;
import top.ribs.scguns.util.GunModifierHelper;
import top.ribs.scguns.util.GunEnchantmentHelper;

import com.github.tartaricacid.touhoulittlemaid.geckolib3.core.builder.Animation;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.core.builder.AnimationBuilder;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.core.builder.ILoopType;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.core.controller.AnimationController;

public final class SC2GunCompat {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Class<?> ENERGY_GUN_CLASS = initEnergyGunClass();

    private static Class<?> initEnergyGunClass() {
        String[] candidates = {
                "top.ribs.scguns.interfaces.IEnergyGun",
                "top.ribs.scguns.common.item.gun.RechargeableEnergyGunItem"
        };
        for (String name : candidates) {
            try {
                return Class.forName(name);
            } catch (ClassNotFoundException ignored) {
            }
        }
        return null;
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
    public static final String WW1_NS = "scgunsww1";

    public static boolean isSC2Gun(ItemStack stack) {
        if (stack.isEmpty()) return false;
        ResourceLocation key = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (key == null) return false;
        String ns = key.getNamespace();
        return SCGUNS_NS.equals(ns) || SCCAVES_NS.equals(ns) || OREGUNIZED_NS.equals(ns) || WW1_NS.equals(ns);
    }

    public static boolean isCCGun(ItemStack stack) {
        if (stack.isEmpty()) return false;
        ResourceLocation key = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return key != null && SCCAVES_NS.equals(key.getNamespace());
    }

    public static boolean isOregunizedGun(ItemStack stack) {
        if (stack.isEmpty()) return false;
        ResourceLocation key = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return key != null && OREGUNIZED_NS.equals(key.getNamespace());
    }

    public static ResourceLocation getGunId(ItemStack stack) {
        if (isSC2Gun(stack)) {
            return BuiltInRegistries.ITEM.getKey(stack.getItem());
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
    private static final int DEFAULT_ENERGY_RECHARGE_RATE = 50;

    public static void tickEnergyRecharge(ItemStack stack) {
        if (stack.isEmpty()) return;
        if (!isEnergyGun(stack)) return;
        ResourceLocation key = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (key == null) return;
        int rate = ENERGY_RECHARGE_RATES.getOrDefault(key.getPath(), DEFAULT_ENERGY_RECHARGE_RATE);
        if (rate <= 0) return;
        IEnergyStorage storage = stack.getCapability(Capabilities.EnergyStorage.ITEM);
        if (storage == null) return;
        if (storage.getEnergyStored() < storage.getMaxEnergyStored()) {
            storage.receiveEnergy(rate, false);
        }
    }

    public static int performGunAttack(LivingEntity maid, LivingEntity target, ItemStack gun) {
        return performGunAttack(maid, target, gun, true);
    }

    /**
     * @param hostile false = 治療などの非攻撃射撃。標的の setLastHurtByMob / setTarget(仇恨) を触らない。
     *                target == maid の場合は呼び出し側が設定した向き(自分の足元など)をそのまま使う。
     */
    public static int performGunAttack(LivingEntity maid, LivingEntity target, ItemStack gun, boolean hostile) {
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

        if (target != maid) rotateToFace(maid, target);

        Gun.Projectile projectileConfig = Gun.getLoadedProjectile(gun, modifiedGun);
        if (projectileConfig == null) {
            projectileConfig = modifiedGun.getProjectile();
        }

        int projectileAmount = general.getProjectileAmount();
        if (projectileAmount <= 0) projectileAmount = 1;

        // AI damage scaling
        float difficultyMultiplier = switch (maid.level().getDifficulty()) {
            case PEACEFUL -> 0.05f;
            case EASY -> 0.35f;
            case NORMAL -> 0.50f;
            case HARD -> 0.65f;
        };
        float totalDamageScale = difficultyMultiplier;

        // Calculate projectile speed with enchantment modifiers
        double speedModifier = GunEnchantmentHelper.getProjectileSpeedModifier(gun);

        if (projectileConfig != null && projectileConfig.getItem() != null) {
            Item ammoItem = projectileConfig.getItem();
            ResourceLocation ammoId = BuiltInRegistries.ITEM.getKey(ammoItem);
            if (ammoId != null) {
                IProjectileFactory factory = ProjectileManager.getInstance().getFactory(ammoId);
                if (factory != null) {
                    double baseSpeed = projectileConfig.getSpeed();
                    double speed = GunModifierHelper.getModifiedProjectileSpeed(gun, baseSpeed * speedModifier);
                    float accuracyModifier = SCG2TLMConfig.GUN_ACCURACY.get().floatValue();
                    float baseSpread = projectileConfig.getSpread();

                    for (int i = 0; i < projectileAmount; i++) {
                        ProjectileEntity projectile = factory.create(maid.level(), maid, gun, gunItem, modifiedGun);
                        if (projectile != null) {
                            projectile.setWeapon(gun);
                            projectile.setAdditionalDamage(Gun.getAdditionalDamage(gun) * totalDamageScale);
                            projectile.getPersistentData().putFloat("AIDamageScale", totalDamageScale);

                            Vec3 direction = getAimDirection(maid, target, gun, gunItem, modifiedGun, baseSpread, baseSpeed, accuracyModifier);
                            projectile.setDeltaMovement(direction.x * speed, direction.y * speed, direction.z * speed);
                            projectile.setPos(maid.getX(), maid.getEyeY(), maid.getZ());

                            maid.level().addFreshEntity(projectile);
                            projectile.tick();
                        }
                    }
                }
            }
        }

        if (hostile && target instanceof Mob mobTarget) {
            mobTarget.setLastHurtByMob(maid);
            mobTarget.setTarget(mob);
        }

        playFireSound(maid, gun, gunItem);

        if (!isEnergyGun(gun)) {
            int ammoCount = Gun.getAmmoCount(gun);
            if (ammoCount > 0) {
                getOrCreateTag(gun).putInt("AmmoCount", ammoCount - 1);
                Gun.Projectile projectile = getProjectile(gun, gunItem);
                if (projectile != null && projectile.ejectsCasing() && !projectile.ejectDuringReload()
                        && maid.getRandom().nextFloat() < 0.8f) {
                    spawnCasing(maid, gun, gunItem, 1);
                }
            }
        }

        int bursts = general.getBurstAmount();
        if (bursts > 1) {
            int shotInBurst = getOrCreateTag(gun).getInt("scg2tlm:burst_shot");
            shotInBurst++;
            if (shotInBurst >= bursts) {
                getOrCreateTag(gun).putInt("scg2tlm:burst_shot", 0);
                return Math.max(general.getBurstCooldown(), rate * 2);
            }
            getOrCreateTag(gun).putInt("scg2tlm:burst_shot", shotInBurst);
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
            SoundEvent soundEvent = BuiltInRegistries.SOUND_EVENT.get(soundId);
            if (soundEvent != null) {
                float volume = GunModifierHelper.getFireSoundVolume(gun) * 4.0f;
                entity.level().playSound(null, entity.getX(), entity.getY(), entity.getZ(),
                        soundEvent, SoundSource.NEUTRAL, volume, 0.9f + entity.getRandom().nextFloat() * 0.2f);
            }
        }
    }

    public static void playGunSound(LivingEntity entity, ItemStack gun, ResourceLocation soundId, float volume) {
        if (soundId == null) return;
        SoundEvent soundEvent = BuiltInRegistries.SOUND_EVENT.get(soundId);
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

    public static void playGunFireSound(LivingEntity entity, ItemStack gun) {
        if (!(gun.getItem() instanceof GunItem gunItem)) return;
        playFireSound(entity, gun, gunItem);
    }

    /** 換弹完了音(設定 reload_end_sound、空文字=無音)。 */
    public static void playReloadEndSound(LivingEntity entity, ItemStack gun) {
        String configured = SCG2TLMConfig.RELOAD_END_SOUND.get();
        if (configured == null || configured.isBlank()) return;
        ResourceLocation id = ResourceLocation.tryParse(configured.trim());
        if (id == null) return;
        playGunSound(entity, gun, id, 1.0f);
    }

    public static void playCockSound(LivingEntity entity, ItemStack gun) {
        if (!(gun.getItem() instanceof GunItem gunItem)) return;
        playGunSound(entity, gun, getSounds(gun, gunItem).getCock(), 1.0f);
    }

    public static boolean isGrenade(ItemStack stack) {
        if (stack.isEmpty()) return false;
        Item item = stack.getItem();
        return item instanceof GrenadeItem || item instanceof GasGrenadeItem || item instanceof BeaconGrenadeItem;
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

    private static Vec3 getAimDirection(LivingEntity shooter, LivingEntity target, ItemStack gunStack, GunItem gunItem, Gun gun, float baseSpread, double projectileSpeed, float accuracyModifier) {
        float spread = GunModifierHelper.getModifiedSpread(gunStack, baseSpread);
        float baseDifficultyMod = 5.0f;
        float difficultyMod = switch (shooter.level().getDifficulty()) {
            case PEACEFUL -> 3.0f;
            case EASY -> 2.0f;
            case NORMAL -> 1.5f;
            case HARD -> 1.0f;
        };
        float spreadMod = Math.min((baseDifficultyMod * difficultyMod) / accuracyModifier, 25.0f);

        Vec3 aimDir = getLookVector(shooter.getXRot(), shooter.getYRot());

        if (target != shooter && shooter.level().getDifficulty() == Difficulty.HARD && target.getDeltaMovement().lengthSqr() > 0.01) {
            Vec3 leadDir = getLeadingDirection(shooter, target, projectileSpeed);
            aimDir = aimDir.add(leadDir.scale(0.3)).normalize();
        }

        float spreadRad = spreadMod * 0.017453292f;
        float theta = shooter.level().random.nextFloat() * 2 * (float) Math.PI;
        float magnitude = (float)(Mth.fastInvSqrt(shooter.level().random.nextFloat()) * Math.tan(spreadRad));

        Vec3 right = getLookVector(shooter.getXRot() + 90, shooter.getYRot());
        Vec3 up = aimDir.cross(right);

        float hOffset = Mth.cos(theta) * magnitude;
        float vOffset = Mth.sin(theta) * magnitude;

        Vec3 spreadDir = aimDir.add(up.scale(hOffset)).add(right.scale(vOffset)).normalize();

        if (spread != 0.0f) {
            spread = Math.min(spread, 170.0f) * 0.5f * 0.017453292f;
            Vec3 right2 = getLookVector(shooter.getXRot() + 90, shooter.getYRot());
            Vec3 up2 = spreadDir.cross(right2);

            float theta2 = shooter.level().random.nextFloat() * 2 * (float) Math.PI;
            float magnitude2 = (float)(Mth.fastInvSqrt(shooter.level().random.nextFloat()) * Math.tan(spread));

            float hOffset2 = Mth.cos(theta2) * magnitude2;
            float vOffset2 = Mth.sin(theta2) * magnitude2;

            spreadDir = spreadDir.add(up2.scale(hOffset2)).add(right2.scale(vOffset2)).normalize();
        }

        return spreadDir;
    }

    private static Vec3 getLeadingDirection(LivingEntity shooter, LivingEntity target, double projectileSpeed) {
        Vec3 targetPos = target.position().add(0, target.getEyeHeight() * 0.8, 0);
        Vec3 targetMotion = target.getDeltaMovement();
        Vec3 shooterPos = shooter.position().add(0, shooter.getEyeHeight(), 0);
        Vec3 diff = targetPos.subtract(shooterPos);
        double distance = diff.length();
        double time = distance / projectileSpeed;
        Vec3 leadTarget = targetPos.add(targetMotion.scale(time));
        return leadTarget.subtract(shooterPos).normalize();
    }

    private static Vec3 getLookVector(float pitch, float yaw) {
        float f = Mth.cos(-yaw * 0.017453292f - (float) Math.PI);
        float f1 = Mth.sin(-yaw * 0.017453292f - (float) Math.PI);
        float f2 = -Mth.cos(-pitch * 0.017453292f);
        float f3 = Mth.sin(-pitch * 0.017453292f);
        return new Vec3(f1 * f2, f3, f * f2);
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
        LOGGER.debug("getMaxAmmo: capacity={}, stack={}", capacity, stack);
        return capacity;
    }

    public static void setFullAmmo(ItemStack stack) {
        if (stack.isEmpty() || !(stack.getItem() instanceof GunItem)) {
            return;
        }
        int maxAmmo = getMaxAmmo(stack);
        getOrCreateTag(stack).putInt("AmmoCount", maxAmmo);
    }

    public static boolean hasAmmoInInventory(EntityMaid maid, ItemStack gun) {
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
        int maxAmmo = getMaxAmmo(gun);
        Item ammoType = getProjectileItem(gun, gunItem);
        if (ammoType == null) {
            LOGGER.warn("reloadFromInventory: ammoType is null, cannot reload");
            return false;
        }

        ResourceLocation ammoId = BuiltInRegistries.ITEM.getKey(ammoType);
        boolean isSingleReload = isSingleReloadAmmo(ammoType);
        boolean isBlazeFuel = ammoId != null && "scguns".equals(ammoId.getNamespace())
                && "blaze_fuel".equals(ammoId.getPath());

        IItemHandler inv = maid.getAvailableInv(true);
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
                LOGGER.debug("reloadFromInventory(single): maxAmmo={}, found={}", maxAmmo, found);
                getOrCreateTag(gun).putInt("AmmoCount", maxAmmo);
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
                CompoundTag tag = getOrCreateTag(gun);
                int prevAmmo = tag.getInt("AmmoCount");

                int casingCount = tag.contains("PrevAmmoCount") ? tag.getInt("PrevAmmoCount") : 0;
                tag.putInt("AmmoCount", newCount);
                tag.putInt("PrevAmmoCount", newCount);
                LOGGER.debug("reloadFromInventory: maxAmmo={}, found={}, newCount={}, prevAmmo={}, ammoType={}", maxAmmo, found, newCount, prevAmmo, ammoType);
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
            int taken = removeFromBoxTag(maid, boxStack, ammoType, toExtract);
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

    private static int removeFromBoxTag(EntityMaid maid, ItemStack boxStack, Item ammoType, int maxRemove) {
        CompoundTag tag = getOrCreateTag(boxStack);
        if (!tag.contains("Items", Tag.TAG_LIST)) return 0;

        ListTag items = tag.getList("Items", Tag.TAG_COMPOUND);
        int remaining = maxRemove;
        var registries = maid.level().registryAccess();

        for (int j = 0; j < items.size() && remaining > 0; j++) {
            CompoundTag itemTag = items.getCompound(j);
            ItemStack itemStack = ItemStack.parse(registries, itemTag).orElse(ItemStack.EMPTY);
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
        Item depletedCore = BuiltInRegistries.ITEM.get(
                ResourceLocation.fromNamespaceAndPath("scguns", "depleted_energy_core"));
        if (depletedCore == null) return;
        giveOrDrop(maid, new ItemStack(depletedCore));
    }

    private static boolean isSingleReloadAmmo(Item item) {
        if (item == null) return false;
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
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
        Item depleted = BuiltInRegistries.ITEM.get(
                ResourceLocation.fromNamespaceAndPath(namespace, depletedItemPath));
        if (depleted == null) {
            depleted = BuiltInRegistries.ITEM.get(
                    ResourceLocation.fromNamespaceAndPath(namespace, defaultDepletedPath));
        }
        if (depleted == null) return;
        giveOrDrop(maid, new ItemStack(depleted));
    }

    private static void spawnEmptyTank(EntityMaid maid) {
        Item emptyTank = BuiltInRegistries.ITEM.get(
                ResourceLocation.fromNamespaceAndPath("scguns", "empty_tank"));
        if (emptyTank == null) return;
        giveOrDrop(maid, new ItemStack(emptyTank));
    }

    private static void giveOrDrop(EntityMaid maid, ItemStack stack) {
        IItemHandler inv = maid.getAvailableInv(false);
        ItemStack remaining = ItemHandlerHelper.insertItemStacked(inv, stack, false);
        if (!remaining.isEmpty()) {
            ItemEntity entity = new ItemEntity(maid.level(),
                    maid.getX(), maid.getY() + 1.0, maid.getZ(), remaining);
            entity.setPickUpDelay(10);
            maid.level().addFreshEntity(entity);
        }
    }

    private static void spawnCasing(LivingEntity entity, ItemStack gun, GunItem gunItem, int count) {
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

        Item casingItem = BuiltInRegistries.ITEM.get(casingId);
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
        return SCG2TLMConfig.GUN_RANGE.get().floatValue();
    }

    public static WeaponType getWeaponType(ItemStack stack) {
        if (stack.isEmpty() || !(stack.getItem() instanceof GunItem gunItem)) return null;
        return getGeneral(stack, gunItem).getWeaponType();
    }

    public static boolean isMinigunGun(ItemStack stack) {
        ResourceLocation key = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (key == null) return false;
        return SC2_MINIGUN_GUNS.contains(key.getPath())
                || CC_MINIGUN_GUNS.contains(key.getPath())
                || OREG_MINIGUN_GUNS.contains(key.getPath());
    }

    public static boolean isRpgGun(ItemStack stack) {
        ResourceLocation key = BuiltInRegistries.ITEM.getKey(stack.getItem());
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

    public static double getIdealRange(ItemStack stack) {
        WeaponType type = getWeaponType(stack);
        double baseRange = SCG2TLMConfig.GUN_RANGE.get();
        if (type == null) return baseRange * 0.375;
        if (type == WeaponType.sniper) return Math.min(baseRange * 1.25, SCG2TLMConfig.SNIPER_SEARCH_RADIUS.get() * 0.85);
        if (type == WeaponType.rifle) return baseRange * 0.5;
        if (type == WeaponType.magnum) return baseRange * 0.4375;
        if (type == WeaponType.smg) return baseRange * 0.25;
        if (type == WeaponType.shotgun) return baseRange * 0.21875;
        if (type == WeaponType.pistol) return baseRange * 0.1875;
        if (type == WeaponType.heavy) return baseRange * 0.5;
        if (type == WeaponType.lmg) return baseRange * 0.5625;
        return baseRange * 0.375;
    }

    public static double getMinRange(ItemStack stack) {
        WeaponType type = getWeaponType(stack);
        double baseRange = SCG2TLMConfig.GUN_RANGE.get();
        if (type == null) return baseRange * 0.078125;
        if (type == WeaponType.sniper) return baseRange * 0.4375;
        if (type == WeaponType.rifle) return baseRange * 0.125;
        if (type == WeaponType.magnum) return baseRange * 0.125;
        if (type == WeaponType.smg) return baseRange * 0.0625;
        if (type == WeaponType.shotgun) return baseRange * 0.046875;
        if (type == WeaponType.pistol) return baseRange * 0.046875;
        if (type == WeaponType.heavy) return baseRange * 0.25;
        if (type == WeaponType.lmg) return baseRange * 0.15625;
        return baseRange * 0.078125;
    }

    public static String getAnimationGunType(ItemStack stack) {
        if (stack.isEmpty() || !(stack.getItem() instanceof GunItem)) return "";
        if (isMinigunGun(stack)) return "rifle";
        if (isRpgGun(stack)) return "rpg";
        WeaponType type = getWeaponType(stack);
        if (type == null) return "rifle";

        if ((type == WeaponType.pistol || type == WeaponType.magnum)
                && stack.getItem() instanceof GunItem gi) {
            Gun modifiedGun = gi.getModifiedGun(stack);
            if (modifiedGun != null && modifiedGun.getGeneral() != null) {
                GripType grip = modifiedGun.getGeneral().getGripType(stack);
                if (grip == GripType.TWO_HANDED) {
                    return "rifle";
                }
            }
        }

        if (type == WeaponType.pistol || type == WeaponType.magnum) return "pistol";
        return "rifle";
    }

    public static boolean hasBayonet(ItemStack stack) {
        if (!(stack.getItem() instanceof GunItem gunItem)) return false;
        return gunItem.hasBayonet(stack) || stack.is(ModTags.Items.BUILT_IN_BAYONET);
    }

    public static float getMeleeReach(ItemStack stack) {
        if (!(stack.getItem() instanceof GunItem gunItem)) return 2.5f;
        Gun modified = gunItem.getModifiedGun(stack);
        if (modified == null || modified.getGeneral() == null) return 2.5f;
        return modified.getGeneral().getMeleeReach();
    }

    public static int getMeleeCooldownTicks(ItemStack stack) {
        if (!(stack.getItem() instanceof GunItem gunItem)) return 10;
        Gun modified = gunItem.getModifiedGun(stack);
        if (modified == null || modified.getGeneral() == null) return 10;
        return modified.getGeneral().getMeleeCooldownTicks();
    }

    public static float getMeleeDamage(ItemStack stack) {
        if (!(stack.getItem() instanceof GunItem gunItem)) return 1.0f;
        Gun modified = gunItem.getModifiedGun(stack);
        if (modified == null || modified.getGeneral() == null) return 1.0f;
        return modified.getGeneral().getMeleeDamage();
    }

    public static int performMeleeAttack(EntityMaid maid, LivingEntity target, ItemStack gun) {
        if (!(gun.getItem() instanceof GunItem gunItem)) return 10;
        float damage = getMeleeDamage(gun);

        if (gunItem.hasBayonet(gun)) {
            damage += GunModifierHelper.getAdditionalDamage(gun, true);
        }

        target.hurt(maid.damageSources().mobAttack(maid), damage);
        maid.playSound(SoundEvents.PLAYER_ATTACK_WEAK, 1.0f, 0.9f + maid.getRandom().nextFloat() * 0.2f);
        return getMeleeCooldownTicks(gun);
    }

    public static CompoundTag getTag(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data != null ? data.copyTag() : null;
    }

    public static CompoundTag getOrCreateTag(ItemStack stack) {
        CustomData data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        CompoundTag tag = data.copyTag();
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        return tag;
    }

    public static void setTag(ItemStack stack, CompoundTag tag) {
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }
}