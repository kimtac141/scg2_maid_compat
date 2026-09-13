package com.scg2tlm.elmomod.mixin;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.world.entity.PathfinderMob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.ribs.scguns.config.GunnerMobSpawner;

@Mixin(GunnerMobSpawner.class)
public abstract class GunnerMobSpawnerMixin {

    @Inject(method = "reassessWeaponGoal", at = @At("HEAD"), cancellable = true, remap = false)
    private static void scg2tlm$skipMaidWeaponGoal(PathfinderMob mob, CallbackInfo ci) {
        if (mob instanceof EntityMaid) {
            ci.cancel();
        }
    }
}
