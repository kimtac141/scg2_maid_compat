package com.scg2tlm.elmomod.client;

import com.github.tartaricacid.touhoulittlemaid.api.event.client.DefaultGeckoAnimationEvent;
import com.github.tartaricacid.touhoulittlemaid.api.event.client.DefaultGeckoAnimationEvent.AnimationType;
import com.scg2tlm.elmomod.ExampleMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = ExampleMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class DefaultGeckoAnimationInjector {
    @SubscribeEvent
    public static void onDefaultGeckoAnimation(DefaultGeckoAnimationEvent event) {
        event.addAnimation(AnimationType.TAC, ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID, "animation/melee.animation.json"));
    }
}
