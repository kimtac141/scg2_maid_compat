package com.scg2tlm.elmomod.mixin;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import top.ribs.scguns.entity.projectile.LightningProjectileEntity;

@Mixin(LightningProjectileEntity.class)
public class LightningProjectileEntityMixin {

    @Inject(method = "findNextTarget", at = @At("RETURN"), cancellable = true, remap = false)
    private void scg2tlm$findNextTarget(Entity entity, CallbackInfoReturnable<LivingEntity> cir) {
        LivingEntity result = cir.getReturnValue();
        if (result instanceof Player player) {
            Entity owner = ((LightningProjectileEntity) (Object) this).getOwner();
            if (owner instanceof EntityMaid maid && player.getUUID().equals(maid.getOwnerUUID())) {
                cir.setReturnValue(null);
            }
        }
    }
}
