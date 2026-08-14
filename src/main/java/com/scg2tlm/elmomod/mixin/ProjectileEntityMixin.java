package com.scg2tlm.elmomod.mixin;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.ribs.scguns.entity.projectile.ProjectileEntity;

import java.util.Optional;

@Mixin(ProjectileEntity.class)
public class ProjectileEntityMixin {

    @Inject(method = "onHitEntity", at = @At("HEAD"), cancellable = true, remap = false)
    private void scg2tlm$onHitEntity(Entity target, Vec3 hitPos, Vec3 startPos, Vec3 endPos, boolean headshot, CallbackInfo ci) {
        Entity shooter = ((ProjectileEntity) (Object) this).getOwner();

        if (target instanceof Player && shooter instanceof EntityMaid) {
            ci.cancel();
            return;
        }

        if (!(target instanceof EntityMaid victim)) return;

        if (shooter instanceof EntityMaid maid) {
            Optional<? extends LivingEntity> a = maid.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET);
            Optional<? extends LivingEntity> b = victim.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET);
            if (a.isPresent() && b.isPresent() && a.get() == b.get()) {
                ci.cancel();
            }
        } else if (shooter instanceof Player player) {
            if (player.getUUID().equals(victim.getOwnerUUID())) {
                ci.cancel();
            }
        }
    }
}
