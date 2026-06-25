package com.scg2tlm.elmomod;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(ExampleMod.MODID)
public class ExampleMod {
    public static final String MODID = "scg2_maid_compat";

    public ExampleMod(FMLJavaModLoadingContext context) {
        context.registerConfig(ModConfig.Type.COMMON, SCG2TLMConfig.SPEC);
        MinecraftForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void onLivingHurt(LivingHurtEvent event) {
        if (event.getEntity() instanceof Player
                && event.getSource().getDirectEntity() instanceof top.ribs.scguns.entity.projectile.ProjectileEntity
                && event.getSource().getEntity() instanceof EntityMaid) {
            event.setCanceled(true);
        }
    }
}
