package com.scg2tlm.elmomod;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import java.util.Optional;

@Mod(ExampleMod.MODID)
public class ExampleMod {
    public static final String MODID = "scg2_maid_compat";

    public ExampleMod(IEventBus modEventBus, ModContainer modContainer) {
        modContainer.registerConfig(ModConfig.Type.COMMON, SCG2TLMConfig.SPEC);
        NeoForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void onLivingIncomingDamage(LivingIncomingDamageEvent event) {
        if (!(event.getSource().getDirectEntity() instanceof top.ribs.scguns.entity.projectile.ProjectileEntity)) {
            return;
        }
        if (!(event.getSource().getEntity() instanceof EntityMaid shooter)) {
            return;
        }

        if (event.getEntity() instanceof Player
                && shooter.getOwnerUUID() != null && shooter.getOwnerUUID().equals(event.getEntity().getUUID())) {
            event.setCanceled(true);
            return;
        }

        if (event.getEntity() instanceof EntityMaid victim) {
            Optional<? extends LivingEntity> a = shooter.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET);
            Optional<? extends LivingEntity> b = victim.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET);
            if (a.isPresent() && b.isPresent() && a.get() == b.get()) {
                event.setCanceled(true);
            }
        }
    }
}
