package com.scg2tlm.elmomod.compat;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.monster.AbstractIllager;
import net.minecraft.world.entity.monster.ZombifiedPiglin;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import top.ribs.scguns.entity.monster.ZombifiedHornlinEntity;
import top.ribs.scguns.entity.projectile.ProjectileEntity;

@EventBusSubscriber(modid = com.scg2tlm.elmomod.ExampleMod.MODID)
public class MaidProjectileHandler {
    private static final String LASER_MUSKET_COUNT_KEY = "scg2tlm:laser_musket_illager_kills";
    private static final int LASER_MUSKET_GOAL = 100;
    private static final String TOTAL_KILL_COUNT_KEY = "scg2tlm:total_kills";
    private static final int TOTAL_KILL_GOAL = 1000;
    private static Item laserMusketItem;

    private static Item getLaserMusketItem() {
        if (laserMusketItem == null) {
            laserMusketItem = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(
                ResourceLocation.fromNamespaceAndPath("scguns", "laser_musket"));
        }
        return laserMusketItem;
    }

    @SubscribeEvent
    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (!(event.getEntity() instanceof ProjectileEntity projectile)) return;
        if (!(projectile.getOwner() instanceof EntityMaid)) return;
        if (!projectile.getPersistentData().contains("AIDamageScale")) return;
        projectile.getPersistentData().putFloat("AIDamageScale", 1.0f);
    }

    @SubscribeEvent
    public static void onLivingIncomingDamage(LivingIncomingDamageEvent event) {
        if (!(event.getSource().getDirectEntity() instanceof ProjectileEntity)) return;

        Entity shooter = event.getSource().getEntity();

        if (shooter instanceof Player player && event.getEntity() instanceof EntityMaid maid) {
            if (player.getUUID().equals(maid.getOwnerUUID())) {
                event.setCanceled(true);
                return;
            }
        }

        if (!(shooter instanceof EntityMaid maid)) return;
        if (event.getEntity() instanceof Player) return;

        event.getEntity().invulnerableTime = 0;

        if (event.getEntity() instanceof Mob mob) {
            mob.setLastHurtByMob(maid);
            mob.setTarget(maid);

            var brain = mob.getBrain();
            if (brain != null) {
                brain.setMemory(MemoryModuleType.ATTACK_TARGET, maid);
                brain.setMemory(MemoryModuleType.HURT_BY_ENTITY, maid);
                brain.setMemory(MemoryModuleType.HURT_BY, event.getSource());
            }

            if (mob instanceof ZombifiedPiglin && !mob.level().isClientSide) {
                Vec3 pos = mob.position();
                AABB searchBox = AABB.ofSize(pos, 52.0, 20.0, 52.0);
                mob.level().getEntitiesOfClass(ZombifiedHornlinEntity.class, searchBox, EntitySelector.NO_SPECTATORS)
                    .stream()
                    .filter(h -> h.getTarget() == null)
                    .forEach(h -> {
                        h.setTarget(maid);
                        h.stopBeingAngry();
                        h.setLastHurtByMob(maid);
                        h.setPersistentAngerTarget(maid.getUUID());
                    });
            }
        }
    }

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        if (!(event.getSource().getDirectEntity() instanceof ProjectileEntity)) return;
        if (!(event.getSource().getEntity() instanceof EntityMaid maid)) return;
        if (event.getEntity() instanceof Player) return;

        SC2AdvancementTriggers.triggerForMaidOwner(maid, SC2AdvancementTriggers.KILL_MOB);

        if (!maid.level().isClientSide) {
            CompoundTag data = maid.getPersistentData();
            int total = data.getInt(TOTAL_KILL_COUNT_KEY) + 1;
            data.putInt(TOTAL_KILL_COUNT_KEY, total);
            if (total >= TOTAL_KILL_GOAL) {
                SC2AdvancementTriggers.triggerForMaidOwner(maid, SC2AdvancementTriggers.KILL_1000);
            }
        }

        if (event.getEntity() instanceof AbstractIllager && !maid.level().isClientSide) {
            ItemStack held = maid.getMainHandItem();
            if (held.getItem() == getLaserMusketItem()) {
                CompoundTag data = maid.getPersistentData();
                int count = data.getInt(LASER_MUSKET_COUNT_KEY) + 1;
                data.putInt(LASER_MUSKET_COUNT_KEY, count);
                if (count >= LASER_MUSKET_GOAL) {
                    SC2AdvancementTriggers.triggerForMaidOwner(maid, SC2AdvancementTriggers.LASER_MUSKET_100_ILLAGER);
                }
            }
        }
    }
}
