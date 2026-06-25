package com.scg2tlm.elmomod.mixin;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.ribs.scguns.entity.projectile.ProjectileEntity;

@Mixin(ProjectileEntity.class)
public class ProjectileEntityMixin {

    @Inject(method = "onHitEntity", at = @At("HEAD"), cancellable = true, remap = false)
    private void scg2tlm$onHitEntity(Entity target, Vec3 hitPos, Vec3 startPos, Vec3 endPos, boolean headshot, CallbackInfo ci) {
        if (target instanceof Player && ((ProjectileEntity) (Object) this).getOwner() instanceof EntityMaid) {
            ci.cancel();
        }
    }
}
