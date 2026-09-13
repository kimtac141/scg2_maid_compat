package com.scg2tlm.elmomod.mixin;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.scg2tlm.elmomod.SCG2TLMConfig;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import top.ribs.scguns.common.Gun;
import top.ribs.scguns.entity.ai.AIGunEvent;
import top.ribs.scguns.item.GunItem;

@Mixin(AIGunEvent.class)
public class AIGunEventLeadMixin {
    // AIGunEvent 被所有持枪 AI 共用，这里记录当前射击者是否是女仆，只对女仆生效
    @Unique
    private static boolean scg2tlm$isMaid = false;

    @Inject(method = "getDirection", at = @At("HEAD"), remap = false)
    private static void scg2tlm$captureShooter(LivingEntity shooter, LivingEntity target, ItemStack weapon,
                                               GunItem item, Gun modifiedGun, float accuracyModifier,
                                               CallbackInfoReturnable<?> cir) {
        scg2tlm$isMaid = shooter instanceof EntityMaid;
    }

    // SCG 只在困难难度才计算提前量。女仆强制让提前量的难度门控（getDirection 里第二次 getDifficulty 调用）通过。
    @Redirect(method = "getDirection",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;getDifficulty()Lnet/minecraft/world/Difficulty;", ordinal = 1))
    private static Difficulty scg2tlm$forceHardForLead(Level level) {
        return scg2tlm$isMaid && SCG2TLMConfig.LEAD_ALWAYS.get() ? Difficulty.HARD : level.getDifficulty();
    }

    // SCG 只把提前量按 0.3 权重混合进瞄准方向。女仆加强为可配置权重（默认 1.0 全预判）。
    @ModifyArg(method = "getDirection",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/phys/Vec3;scale(D)Lnet/minecraft/world/phys/Vec3;", ordinal = 0))
    private static double scg2tlm$increaseLeadWeight(double original) {
        return scg2tlm$isMaid ? SCG2TLMConfig.LEAD_WEIGHT.get() : original;
    }
}
