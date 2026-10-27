package com.scg2tlm.elmomod.mixin;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.scg2tlm.elmomod.compat.SC2HealCompat;
import net.minecraft.resources.ResourceLocation;
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

    /**
     * 治療弾の判定: impactEffect == minecraft:instant_health。
     * 例外時は false(従来どおりオーナー保護を優先し、友軍を誤って傷つけない側に倒す)。
     */
    private boolean scg2tlm$isHealingProjectile() {
        try {
            ProjectileEntity self = (ProjectileEntity) (Object) this;
            var projectile = self.getProjectile();
            if (projectile == null) return false;
            ResourceLocation effect = projectile.getImpactEffect();
            return effect != null && SC2HealCompat.INSTANT_HEALTH.equals(effect);
        } catch (Throwable t) {
            return false;
        }
    }

    /** 候補が発射者本人か(身分比較)。getOwner / getShooter の両方を見る。 */
    private boolean scg2tlm$isShooter(Entity candidate) {
        if (candidate == null) return false;
        try {
            ProjectileEntity self = (ProjectileEntity) (Object) this;
            return self.getOwner() == candidate || self.getShooter() == candidate;
        } catch (Throwable t) {
            return false;
        }
    }

    @Inject(method = "onHitEntity", at = @At("HEAD"), cancellable = true, remap = false)
    private void scg2tlm$onHitEntity(Entity target, Vec3 hitPos, Vec3 startPos, Vec3 endPos, boolean headshot, CallbackInfo ci) {
        boolean healing = scg2tlm$isHealingProjectile();

        if (healing) {
            // 治療弾は発射者自身には作用させず(弾薬の無駄)、それ以外(オーナー含む)には必ず通す
            if (scg2tlm$isShooter(target)) {
                ci.cancel();
            }
            return;
        }

        if (target instanceof Player player) {
            Entity owner = ((ProjectileEntity) (Object) this).getOwner();
            if (owner instanceof EntityMaid maid && player.getUUID().equals(maid.getOwnerUUID())) {
                ci.cancel();
            }
        }
    }
}
