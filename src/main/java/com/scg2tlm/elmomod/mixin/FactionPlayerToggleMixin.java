package com.scg2tlm.elmomod.mixin;

import com.github.tartaricacid.touhoulittlemaid.init.InitEntities;
import com.scg2tlm.elmomod.SCGExtraCompatHelper;
import com.scg2tlm.elmomod.SCG2TLMConfig;
import net.minecraft.world.entity.EntityType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// scgextra 是可选依赖。该 mixin 放在独立配置 scg2_maid_compat_scgextra.mixins.json（required:false），
// 且仅在安装了 scgextra 时才由 ExampleMod 注册该配置；此处 @Pseudo + 字符串 target + 反射辅助类，
// 确保类文件里不出现任何对 scgextra 的直接引用，缺失时静默跳过、绝不崩溃。
@Pseudo
@Mixin(targets = "net.zincstudios.scgextra.entity.Faction")
public class FactionPlayerToggleMixin {
    // 玩家/女仆阵营 "player" 的可选开关：关闭时让玩家和女仆返回 NO_FACTION（不参与阵营敌对）。
    // 该配置只在装了 scgextra 时才被注册，此时 Faction 类必然存在，完整描述符可安全解析。
    @Inject(method = "getFaction(Lnet/minecraft/world/entity/EntityType;)Lnet/zincstudios/scgextra/entity/Faction;",
            at = @At("HEAD"), cancellable = true, remap = false)
    private static void scg2tlm$togglePlayerFaction(EntityType<?> entityType, CallbackInfoReturnable<Object> cir) {
        if (SCG2TLMConfig.ENABLE_PLAYER_FACTION.get()) {
            return;
        }
        if (entityType == EntityType.PLAYER || entityType == InitEntities.MAID.get()) {
            cir.setReturnValue(SCGExtraCompatHelper.getNoFaction());
        }
    }
}
