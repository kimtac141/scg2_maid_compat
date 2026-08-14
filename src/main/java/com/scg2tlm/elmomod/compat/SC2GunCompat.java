package com.scg2tlm.elmomod.compat;

import com.scg2tlm.elmomod.SCG2TLMConfig;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.IItemHandlerModifiable;
import net.minecraftforge.items.ItemHandlerHelper;
import net.minecraftforge.items.wrapper.RangedWrapper;
import net.minecraftforge.registries.ForgeRegistries;

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
import top.ribs.scguns.entity.ai.AIGunEvent;
import top.ribs.scguns.entity.throwable.ThrowableGrenadeEntity;
import top.ribs.scguns.item.BeaconGrenadeItem;
import top.ribs.scguns.item.GasGrenadeItem;
import top.ribs.scguns.item.GrenadeItem;
import top.ribs.scguns.item.AmmoBoxItem;
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

    public static boolean isSC2Gun(ItemStack stack) {
        if (stack.isEmpty()) return false;
        ResourceLocation key = ForgeRegistries.ITEMS.getKey(stack.getItem());
        if (key == null) return false;
        String ns = key.getNamespace();
        return SCGUNS_NS.equals(ns) || SCCAVES_NS.equals(ns) || OREGUNIZED_NS.equals(ns);
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

    public static boolean hasAmmoInInventory(EntityMaid maid, ItemStack gun) {
        if (SCG2TLMConfig.RELOAD_FREE_AMMO.get()) {
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

        // One-handed carbine (pistol/magnum) with stock -> dynamically two-handed -> rifle pose
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
}