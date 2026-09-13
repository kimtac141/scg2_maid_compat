package com.scg2tlm.elmomod.mixin;

import com.github.tartaricacid.touhoulittlemaid.init.InitEntities;
import com.scg2tlm.elmomod.SCGExtraCompatHelper;
import com.scg2tlm.elmomod.SCG2TLMConfig;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Projectile.class)
public abstract class ProjectileFactionFriendlyFireMixin {
    @Shadow
    public abstract Entity getOwner();

    // 同一 SCG Extra 阵营的怪互相射击时，子弹直接穿过友军，不造成友伤。
    // 放行玩家/女仆（"player" 阵营）的弹体，保留原有的玩家打怪/打女仆行为。
    // scgextra 通过 SCGExtraCompatHelper 反射访问，未安装时不生效也不崩溃。
    @Inject(method = "canHitEntity", at = @At("HEAD"), cancellable = true)
    private void scg2tlm$skipFactionFriendlyFire(Entity target, CallbackInfoReturnable<Boolean> cir) {
        if (!SCG2TLMConfig.PREVENT_FACTION_FRIENDLY_FIRE.get()) {
            return;
        }
        // scgextra 未安装时直接跳过。ModList 的检查刻意留在这里而不是
        // SCGExtraCompatHelper 里：那个类要保持「零依赖、纯反射」，才能安全降级。
        if (!net.minecraftforge.fml.ModList.get().isLoaded("scgextra")) {
            return;
        }
        Entity owner = this.getOwner();
        if (!(owner instanceof LivingEntity ownerLiving) || !(target instanceof LivingEntity)) {
            return;
        }
        EntityType<?> ownerType = ownerLiving.getType();
        if (ownerType == EntityType.PLAYER || ownerType == InitEntities.MAID.get()) {
            return;
        }
        if (SCGExtraCompatHelper.isFriendlies(ownerLiving, (LivingEntity) target)) {
            cir.setReturnValue(false);
        }
    }
}
