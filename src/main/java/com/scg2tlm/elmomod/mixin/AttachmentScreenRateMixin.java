package com.scg2tlm.elmomod.mixin;

import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import top.ribs.scguns.client.screen.AttachmentScreen;
import top.ribs.scguns.common.Gun;
import top.ribs.scguns.util.GunCompositeStatHelper;
import top.ribs.scguns.util.GunEnchantmentHelper;

/**
 * 修正「改装界面显示的射速与实际射速不一致」。
 *
 * <h2>SC2 本体的 bug（实为笔误）</h2>
 * <p>{@code GunCompositeStatHelper} 里有两组语义不同的重载，其中三参数版<b>算完就把结果丢了</b>：</p>
 * <pre>
 *   // 两参数：附魔 → 配件，返回最终值   ✅
 *   public static int getCompositeRate(ItemStack weapon, Gun modifiedGun) {
 *       int a = GunEnchantmentHelper.getRate(weapon, modifiedGun);
 *       return GunModifierHelper.getModifiedRate(weapon, a);      // ireturn b
 *   }
 *
 *   // 三参数：算了 b 之后 return a，b 成了死变量；player 参数也完全未使用
 *   public static int getCompositeRate(ItemStack weapon, Gun modifiedGun, Player player) {
 *       int a = GunEnchantmentHelper.getRate(weapon, modifiedGun);
 *       int b = GunModifierHelper.getModifiedRate(weapon, a);     // istore 4，此后无引用
 *       return a;
 *   }
 * </pre>
 *
 * <h2>于是界面与实际劈开</h2>
 * <table border="1">
 *   <caption>各调用方用的重载（实测自 0.5.5 jar 的字节码）</caption>
 *   <tr><th>调用方</th><th>重载</th><th>是否含配件修正</th></tr>
 *   <tr><td>改装界面 {@code AttachmentScreen}（偏移 783）</td><td>两参数</td><td><b>含</b></td></tr>
 *   <tr><td>实际开火 {@code ShootingHandler}（偏移 808）</td><td>三参数</td><td><b>不含</b></td></tr>
 *   <tr><td>点射间隔 {@code BurstTracker}（偏移 9）</td><td>三参数</td><td><b>不含</b></td></tr>
 * </table>
 * <p>所以给枪装上提升射速的配件后，界面 RPM 会涨，实际却按未装的速率开火。</p>
 * <p>附魔侧不受影响：{@code GunEnchantmentHelper.getRate} 在<b>两个</b>重载里都被调用，
 * 因此附魔的射速加成在界面与实际中一致——真正被丢掉的只有<b>配件</b>那一层。</p>
 *
 * <h2>本 mixin 的取舍：改显示，不改逻辑</h2>
 * <p>界面改为显示<b>被丢弃的那个 {@code b}</b>——即「附魔修正 → 配件修正，
 * 各应用<b>一次</b>」的复合值，从而与实际开火一致。</p>
 *
 * <h2>实测标定（使用者提供的数字）</h2>
 * <table border="1">
 *   <caption>某枪 base {@code rate=3}（400 RPM）+ 撞火枪托</caption>
 *   <tr><th>来源</th><th>RPM</th><th>折算 rate</th></tr>
 *   <tr><td>界面（修复前）</td><td>1200</td><td>1</td></tr>
 *   <tr><td><b>实际开火</b></td><td><b>600</b></td><td><b>2</b></td></tr>
 *   <tr><td>原始（无配件）</td><td>400</td><td>3</td></tr>
 * </table>
 * <p>可见界面把配件修正算了<b>两次</b>（3→2→1），而实际只算了一次（3→2）。</p>
 *
 * <p><b>踩过的坑</b>：本 mixin 的第一版返回的是 {@code GunEnchantmentHelper.getRate(...)}
 * （只含附魔 = {@code rate 3} = 400 RPM），那样界面会低于实际，仍然不符。
 * 正确的目标是那个被丢弃的 {@code b}（= 600 RPM）。<b>不要</b>把它改回只含附魔的版本，
 * 除非同时也改了开火逻辑。</p>
 *
 * <p>若日后想让配件真正更强（作者本意可能如此），正确做法是给三参数版补上
 * {@code return b}，那时本 mixin 会变得多余——因为显示与实际<em>都</em>会用 {@code b}。</p>
 *
 * <h2>为什么用 {@code @Redirect} 而不是改 {@code GunCompositeStatHelper}</h2>
 * <p>因为「改显示」的意图只落在<b>这一个调用点</b>上。{@code AttachmentScreen} 共显示
 * 19 项属性（伤害/暴击/穿甲/射速/弹匣/换弹/握持/后坐力…），而 {@code getCompositeRate}
 * 全类<b>只被调用一次</b>（就是射速那一行）。用 {@code @Redirect} 精确替换这一处，
 * 其余 18 项与整个类的其它逻辑<b>完全不受影响</b>；改 {@code GunCompositeStatHelper}
 * 则会影响所有调用方，语义上不再等于「只改显示」。</p>
 *
 * <p>注入点锚在 SC2 <b>自己的</b>私有方法 {@code renderGunStats(GuiGraphics)} 上
 * （实测它承担了全部 19 项属性的绘制），而不是继承自原版的 {@code m_88315_}（render），
 * 这样目标稳定、且不受原版方法名映射变化影响。</p>
 */
@Mixin(AttachmentScreen.class)
public class AttachmentScreenRateMixin {

    /**
     * 界面射速那一行的取值：复刻 {@code GunCompositeStatHelper.getCompositeRate} 三参数版
     * <b>本该返回</b>的复合值（附魔一次 + 配件一次）。
     *
     * <p>这样界面数值等于 {@code ShootingHandler} 与 {@code BurstTracker} 实际使用的速率，
     * 且不改动任何开火逻辑。</p>
     */
    @Redirect(
            method = "renderGunStats",
            at = @At(
                    value = "INVOKE",
                    target = "Ltop/ribs/scguns/util/GunCompositeStatHelper;"
                            + "getCompositeRate(Lnet/minecraft/world/item/ItemStack;"
                            + "Ltop/ribs/scguns/common/Gun;)I"
            ),
            remap = false,
            require = 0
    )
    private int scg2tlm$displayedRate(ItemStack weapon, Gun modifiedGun) {
        // 与开火路径的语义对齐：附魔修正先应用，再由配件修正各应用一次。
        int enchanted = GunEnchantmentHelper.getRate(weapon, modifiedGun);
        return top.ribs.scguns.util.GunModifierHelper.getModifiedRate(weapon, enchanted);
    }
}
