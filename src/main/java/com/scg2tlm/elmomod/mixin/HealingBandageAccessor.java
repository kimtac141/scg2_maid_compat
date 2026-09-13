package com.scg2tlm.elmomod.mixin;

import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import top.ribs.scguns.item.HealingBandageItem;

import java.util.List;

/**
 * 读取 SC2 {@code HealingBandageItem} 的私有治疗参数。
 *
 * <h2>为什么需要它</h2>
 * <p>{@code HealingBandageItem} 的治疗逻辑写在 {@code finishUsingItem} 里，但<b>只对玩家生效</b>：</p>
 * <pre>
 *   public ItemStack m_5922_(ItemStack stack, Level level, LivingEntity entity) {
 *       if (!(entity instanceof Player)) return stack;   // ★ 非玩家直接跳过
 *       player.heal(this.healingAmount);
 *       player.removeEffect(LACERATED);
 *       this.potionEffects.forEach(player::addEffect);
 *   }
 * </pre>
 * <p>女仆不是 {@code Player}，所以「女仆用绷带治疗别人」必须由本模组自己复刻。
 * 复刻需要真实的 {@code healingAmount} 与 {@code potionEffects}，而这两个字段是
 * <b>包私有</b>（{@code int healingAmount} / {@code List<MobEffectInstance> potionEffects}），
 * 只能通过 accessor 读取。</p>
 *
 * <p>SC2 是本模组的硬依赖（build.gradle 里是 {@code implementation}），
 * 因此这里直接用类字面量指定目标，语义清晰且不影响注解处理器。</p>
 */
@Mixin(HealingBandageItem.class)
public interface HealingBandageAccessor {

    /** 读取回血量。 */
    @Accessor(value = "healingAmount", remap = false)
    int scg2tlm$getHealingAmount();

    /** 读取附带的药水效果列表。 */
    @Accessor(value = "potionEffects", remap = false)
    List<MobEffectInstance> scg2tlm$getPotionEffects();
}
