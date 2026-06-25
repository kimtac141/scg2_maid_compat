package com.scg2tlm.elmomod.compat;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.monster.ZombifiedPiglin;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import top.ribs.scguns.entity.monster.ZombifiedHornlinEntity;
import top.ribs.scguns.entity.projectile.ProjectileEntity;

@Mod.EventBusSubscriber(modid = com.scg2tlm.elmomod.ExampleMod.MODID)
public class MaidProjectileHandler {

    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        if (!(event.getSource().getDirectEntity() instanceof ProjectileEntity)) return;
        if (!(event.getSource().getEntity() instanceof EntityMaid maid)) return;
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
}
