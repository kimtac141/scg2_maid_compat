package com.scg2tlm.elmomod.client;

import com.github.tartaricacid.touhoulittlemaid.api.event.client.AddClothConfigEvent;
import com.scg2tlm.elmomod.ExampleMod;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = ExampleMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class AddClothConfigEventListener {
    @SubscribeEvent
    public static void onAddClothConfig(AddClothConfigEvent event) {
        SCG2TLMClothConfig.addEntries(event.getRoot(), event.getEntryBuilder());
    }
}
