package com.scg2tlm.elmomod.mixin;

import net.minecraft.world.effect.MobEffectInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import top.ribs.scguns.item.HealingBandageItem;

import java.util.List;

/**
 * SC2 の HealingBandageItem のパッケージプライベートな治療パラメータを読む。
 *
 * <p>HealingBandageItem.finishUsingItem は Player 専用で、メイドが使っても何も起きない。
 * そのため効果は本MOD側で再現する(SC2HealCompat.applyBandage)。その際に
 * healingAmount / potionEffects が必要になる。</p>
 */
@Mixin(HealingBandageItem.class)
public interface HealingBandageAccessor {

    @Accessor(value = "healingAmount", remap = false)
    int scg2tlm$getHealingAmount();

    @Accessor(value = "potionEffects", remap = false)
    List<MobEffectInstance> scg2tlm$getPotionEffects();
}
