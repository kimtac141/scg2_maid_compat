package com.scg2tlm.elmomod.compat;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Set;

import com.github.tartaricacid.touhoulittlemaid.geckolib3.core.builder.Animation;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.core.builder.AnimationBuilder;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.core.builder.ILoopType;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.core.controller.AnimationController;
import top.ribs.scguns.common.Gun;
import top.ribs.scguns.common.Gun.WeaponType;
import top.ribs.scguns.entity.ai.AIGunEvent;
import top.ribs.scguns.entity.throwable.ThrowableGrenadeEntity;
import top.ribs.scguns.item.BeaconGrenadeItem;
import top.ribs.scguns.item.GasGrenadeItem;
import top.ribs.scguns.item.GrenadeItem;
import top.ribs.scguns.item.AmmoBoxItem;
import top.ribs.scguns.item.GunItem;

public final class SC2GunCompat {
    private static final float DEFAULT_ACCURACY = 2.0f;
    private static final Set<String> MINIGUN_GUNS = Set.of(
            "thunderhead", "gattaler", "weevil", "hullbreaker",
            "cr4k_mining_laser", "flayed_god", "spitfire", "cyclone"
    );
    private static final Set<String> RPG_GUNS = Set.of(
            "kiln_gun", "scratches", "dozier_rl", "terra_incognita"
    );
    public static boolean isSC2Gun(ItemStack stack) {
        if (stack.isEmpty()) return false;
        ResourceLocation key = ForgeRegistries.ITEMS.getKey(stack.getItem());
        return key != null && "scguns".equals(key.getNamespace());
    }

    public static ResourceLocation getGunId(ItemStack stack) {
        if (isSC2Gun(stack)) {
            return ForgeRegistries.ITEMS.getKey(stack.getItem());
        }
        return null;
    }

    public static int performGunAttack(LivingEntity maid, LivingEntity target, ItemStack gun) {
        if (!(maid instanceof Mob mob)) return 10;
        if (maid.level().isClientSide) return 10;
        if (!(gun.getItem() instanceof GunItem gunItem)) {
            return 10;
        }

        Gun modifiedGun = gunItem.getModifiedGun(gun);
        int rate = modifiedGun.getGeneral().getRate();

        if (!Gun.hasAmmo(gun)) {
            return rate;
        }

        rotateToFace(maid, target);

        mob.swing(InteractionHand.MAIN_HAND);
        AIGunEvent.performGunAttack(mob, target, gun, modifiedGun, DEFAULT_ACCURACY);

        if (target instanceof Mob mobTarget) {
            mobTarget.setLastHurtByMob(maid);
            mobTarget.setTarget(mob);
        }

        playFireSound(maid, modifiedGun);

        int ammoCount = Gun.getAmmoCount(gun);
        if (ammoCount > 0) {
            gun.getOrCreateTag().putInt("AmmoCount", ammoCount - 1);
        }

        return rate;
    }

    public static int getAttackCooldown(ItemStack gun) {
        if (!(gun.getItem() instanceof GunItem gunItem)) return 5;
        return gunItem.getModifiedGun(gun).getGeneral().getRate();
    }

    private static void playFireSound(LivingEntity entity, Gun modifiedGun) {
        ResourceLocation soundId = modifiedGun.getSounds().getFire();
        if (soundId != null) {
            SoundEvent soundEvent = ForgeRegistries.SOUND_EVENTS.getValue(soundId);
            if (soundEvent != null) {
                entity.level().playSound(null, entity.getX(), entity.getY(), entity.getZ(),
                        soundEvent, SoundSource.NEUTRAL, 3.0f, 0.9f + entity.getRandom().nextFloat() * 0.2f);
            }
        }
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
        if (Gun.Reloads.hasInfiniteAmmo(stack)) {
            return false;
        }
        return !Gun.hasAmmo(stack);
    }

    public static int getReloadTime(ItemStack stack) {
        if (stack.isEmpty() || !(stack.getItem() instanceof GunItem gunItem)) return 20;
        Gun modifiedGun = gunItem.getModifiedGun(stack);
        return modifiedGun.getReloads().getReloadTimer() + modifiedGun.getReloads().getEmptyMagTimer();
    }

    public static int getMaxAmmo(ItemStack stack) {
        if (stack.isEmpty() || !(stack.getItem() instanceof GunItem gunItem)) return 0;
        Gun modifiedGun = gunItem.getModifiedGun(stack);
        return modifiedGun.getReloads().getMaxAmmo();
    }

    public static void setFullAmmo(ItemStack stack) {
        if (stack.isEmpty() || !(stack.getItem() instanceof GunItem)) {
            return;
        }
        int maxAmmo = getMaxAmmo(stack);
        stack.getOrCreateTag().putInt("AmmoCount", maxAmmo);
    }

    public static boolean hasAmmoInInventory(EntityMaid maid, ItemStack gun) {
        if (!(gun.getItem() instanceof GunItem gunItem)) {
            return false;
        }
        Gun modifiedGun = gunItem.getModifiedGun(gun);
        Item ammoType = modifiedGun.getProjectile().getItem();
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

    public static void reloadFromInventory(EntityMaid maid, ItemStack gun) {
        if (!(gun.getItem() instanceof GunItem gunItem)) {
            return;
        }
        Gun modifiedGun = gunItem.getModifiedGun(gun);
        int maxAmmo = modifiedGun.getReloads().getMaxAmmo();
        Item ammoType = modifiedGun.getProjectile().getItem();
        if (ammoType == null) {
            return;
        }

        ResourceLocation ammoId = ForgeRegistries.ITEMS.getKey(ammoType);
        boolean isEnergyCore = ammoId != null && "scguns".equals(ammoId.getNamespace())
                && "energy_core".equals(ammoId.getPath());
        boolean isBlazeFuel = ammoId != null && "scguns".equals(ammoId.getNamespace())
                && "blaze_fuel".equals(ammoId.getPath());
        boolean isSingleReload = isEnergyCore || isBlazeFuel;

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
                gun.getOrCreateTag().putInt("AmmoCount", maxAmmo);
                if (isBlazeFuel) {
                    spawnEmptyTank(maid);
                } else {
                    spawnDepletedCore(maid);
                }
            }
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

                int casingCount = tag.contains("PrevAmmoCount") ? tag.getInt("PrevAmmoCount") : 0;
                tag.putInt("AmmoCount", newCount);
                tag.putInt("PrevAmmoCount", newCount);
                spawnCasing(maid, modifiedGun, casingCount);
            }
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
        if (!"scguns".equals(id.getNamespace())) return false;
        return "energy_core".equals(id.getPath()) || "blaze_fuel".equals(id.getPath());
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

    private static void spawnCasing(LivingEntity entity, Gun modifiedGun, int count) {
        Item ammoType = modifiedGun.getProjectile().getItem();
        if (isSingleReloadAmmo(ammoType)) {
            return;
        }

        boolean ejects = modifiedGun.getProjectile().ejectsCasing();
        if (!ejects) return;

        ResourceLocation casingId = modifiedGun.getProjectile().getCasingType();
        if (casingId == null) {
            casingId = modifiedGun.getProjectile().casingType;
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
            IItemHandler inv = maid.getAvailableInv(true);

            // First try to insert into compatible ammo boxes (e.g. EmptyCasingPouch)
            int remaining = insertIntoAmmoBoxes(inv, casingStack.copy());
            if (remaining <= 0) return;
            casingStack = new ItemStack(casingItem, remaining);

            ItemStack leftover = ItemHandlerHelper.insertItemStacked(inv, casingStack, false);
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
        Gun modifiedGun = gunItem.getModifiedGun(stack);
        return modifiedGun.getGeneral().getWeaponType();
    }

    public static boolean isMinigunGun(ItemStack stack) {
        ResourceLocation key = ForgeRegistries.ITEMS.getKey(stack.getItem());
        return key != null && MINIGUN_GUNS.contains(key.getPath());
    }

    public static boolean isRpgGun(ItemStack stack) {
        ResourceLocation key = ForgeRegistries.ITEMS.getKey(stack.getItem());
        return key != null && RPG_GUNS.contains(key.getPath());
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
        if (type == null) return 24.0;
        return switch (type) {
            case sniper -> 80.0;
            case rifle -> 32.0;
            case magnum -> 28.0;
            case smg -> 16.0;
            case shotgun -> 14.0;
            case pistol -> 12.0;
            case heavy -> 32.0;
            case lmg -> 36.0;
            default -> 24.0;
        };
    }

    public static double getMinRange(ItemStack stack) {
        WeaponType type = getWeaponType(stack);
        if (type == null) return 5.0;
        return switch (type) {
            case sniper -> 28.0;
            case rifle -> 8.0;
            case magnum -> 8.0;
            case smg -> 4.0;
            case shotgun -> 3.0;
            case pistol -> 3.0;
            case heavy -> 16.0;
            case lmg -> 10.0;
            default -> 5.0;
        };
    }

    public static String getAnimationGunType(ItemStack stack) {
        if (isMinigunGun(stack)) return "rifle";
        if (isRpgGun(stack)) return "rpg";
        WeaponType type = getWeaponType(stack);
        if (type == null) return "rifle";
        return switch (type) {
            case pistol -> "pistol";
            case magnum -> "pistol";
            case smg -> "rifle";
            case rifle -> "rifle";
            case lmg -> "rifle";
            case shotgun -> "rifle";
            case sniper -> "rifle";
            case heavy -> "rifle";
            default -> "rifle";
        };
    }
}
