package com.scg2tlm.elmomod.compat;

import com.scg2tlm.elmomod.ExampleMod;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import top.ribs.scguns.item.GunItem;

import java.lang.reflect.Method;

@EventBusSubscriber(modid = ExampleMod.MODID, value = Dist.CLIENT)
public class BetterCombatHandler {
    private static final String BC_MOD_ID = "bettercombat";
    private static Boolean hasBC;
    private static Class<?> animatableClass;
    private static Method stopAttackAnimation;
    private static ItemStack lastMainHand = ItemStack.EMPTY;

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        ItemStack current = mc.player.getMainHandItem();
        if (ItemStack.matches(lastMainHand, current)) return;
        lastMainHand = current.copy();

        if (!(current.getItem() instanceof GunItem)) return;
        if (!SC2GunCompat.isSC2Gun(current)) return;

        if (hasBC == null) {
            hasBC = ModList.get().isLoaded(BC_MOD_ID);
        }
        if (!hasBC) return;

        stopBCAttackAnimation(mc.player);
    }

    private static void stopBCAttackAnimation(Object player) {
        try {
            if (animatableClass == null) {
                animatableClass = Class.forName("net.bettercombat.client.animation.PlayerAttackAnimatable");
                stopAttackAnimation = animatableClass.getMethod("stopAttackAnimation", float.class);
            }
            if (animatableClass.isInstance(player)) {
                stopAttackAnimation.invoke(player, 0.0f);
            }
        } catch (Exception e) {
            hasBC = false;
        }
    }
}