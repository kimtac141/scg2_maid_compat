package com.scg2tlm.elmomod.compat.task;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.google.common.collect.ImmutableMap;
import com.scg2tlm.elmomod.compat.SC2GunCompat;
import com.scg2tlm.elmomod.compat.SC2HealCompat;
import com.scg2tlm.elmomod.mixin.EntityMaidTaskDataInvoker;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.item.ItemStack;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.WeakHashMap;

/**
 * 医疗兵的完整治疗行为（自包含，<b>不使用攻击系统</b>）。
 *
 * <h2>为什么不用 {@code ATTACK_TARGET}</h2>
 * <p>最初实现为了复用 {@code MaidSC2GunShootTask}，把治疗目标写进了
 * {@link MemoryModuleType#ATTACK_TARGET}。这是错的：</p>
 * <ul>
 *   <li><b>TLM 设定女仆不能攻击任何玩家</b>，而主人正是最重要的治疗对象；</li>
 *   <li>原版 {@code StartAttacking} / {@code StopAttackingIfTargetInvalid} 及
 *       {@code IRangedAttackTask.TARGET_CONDITIONS} 都按<b>战斗</b>语义校验目标，
 *       对玩家/同类会判定非法，导致治疗目标根本建立不起来。</li>
 * </ul>
 * <p>治疗不是攻击，因此本行为<b>完全不碰攻击 memory</b>：自己找目标、自己走位、
 * 自己施法，只用 {@code WALK_TARGET} / {@code LOOK_TARGET} 做移动与朝向。</p>
 *
 * <h2>治疗流程</h2>
 * <ol>
 *   <li>找最近的受伤友军（主人 / 友方女仆 / 同阵营 / 自己）</li>
 *   <li>走到其身边</li>
 *   <li>进入施法距离后<b>蓄力</b>若干 tick，然后执行一次治疗：
 *     <ul>
 *       <li>手持十字军且还有弹药 → 远程发射治疗弹（弹丸命中即回血）</li>
 *       <li>否则若有治疗绷带且贴身 → 贴用绷带</li>
 *     </ul>
 *   </li>
 * </ol>
 */
public class MedicHealTask extends Behavior<EntityMaid> {

    /** 搜索半径。 */
    private static final float SEARCH_RADIUS = 24.0f;
    /** 十字军治疗的开火距离。 */
    private static final double GUN_RANGE = 14.0;
    /** 绷带贴用的距离。 */
    private static final double BANDAGE_RANGE = 3.0;
    /** 蓄力时长（tick）：到点才施放，避免瞬间回血显得突兀。 */
    private static final int CHARGE_TICKS = 40;
    /** 两次治疗之间的间隔（tick）。 */
    private static final int COOLDOWN_TICKS = 20;
    /**
     * 治疗消耗品的「使用」时长（tick）。
     *
     * <p>取 32 —— scgextra 的 {@code medkit} 注册的 {@code useDuration} 正是 32
     * （且 {@code useAnimation = UseAnim.BRUSH}）。对 SC2 的绷带/药膏而言它们自身
     * {@code useDuration} 是 0，但为了让它们也能播放使用动画，这里统一走这段手感时长。</p>
     */
    private static final int ITEM_USE_TICKS = 32;

    /** 每个女仆的施法进度（弱引用，实体卸载即回收）。服务端逻辑，无并发问题。 */
    private static final WeakHashMap<EntityMaid, Integer> CHARGE = new WeakHashMap<>();
    private static final WeakHashMap<EntityMaid, Long> LAST_HEAL = new WeakHashMap<>();

    /**
     * 正在「使用治疗消耗品」的女仆 → 剩余刻数。
     *
     * <p>存在期间女仆保持 {@code isUsingItem()} 为真，于是 TLM 的
     * {@code AnimationManager.predicateUse} 会播放模型的 {@code use_mainhand} 动画
     * （实测 GF2 模型包里 88 个动画文件中有 29 个含该动画）。
     * 倒计时归零时施加治疗效果并消耗物品。</p>
     *
     * <p>同样按女仆静态存放，理由见 {@link #RELOAD_TIMER}。</p>
     */
    private static final WeakHashMap<EntityMaid, Integer> ITEM_USE = new WeakHashMap<>();

    /** 上次发「开始治疗」气泡的时刻。 */
    private static final WeakHashMap<EntityMaid, Long> LAST_CHAT = new WeakHashMap<>();
    private static final int CHAT_COOLDOWN_TICKS = 600;

    /**
     * 上次发「治疗完成」气泡的时刻，与 {@link #LAST_CHAT} <b>分开</b>。
     *
     * <p>分开的原因见 {@link #chat}：共用一张表时，完成气泡会被开始气泡的 600 刻限流吞掉。</p>
     */
    private static final WeakHashMap<EntityMaid, Long> LAST_CHAT_DONE = new WeakHashMap<>();
    /** 完成气泡的限流：只需盖过「一次治疗周期」，不必 600 刻。 */
    private static final int DONE_CHAT_COOLDOWN_TICKS = 40;

    /**
     * 主手被临时换走时，原来的物品（通常是枪）以及「治疗品原来所在的槽位」。
     *
     * <p><b>为什么连槽位一起记</b>：还原时要精确换回原处。早期版本只用
     * {@code ItemHandlerHelper.insertItem} 把原主手物品「找地方塞进去」，
     * 而那个 API <b>会做同类合并</b>，导致原主手物品同时出现在物品栏与主手
     * —— 表现为「换枪操作把使用的物品复制成了主手物品」。现在改为记下槽位、做精确换回，
     * 全程只用裸槽位读写（{@code getStackInSlot} / {@code setStackInSlot}），
     * 不做任何合并。</p>
     *
     * <p>恢复用 {@code x = -1} 表示「消耗品原本就在主手」（无需换回）。</p>
     */
    private static final class SwapBackup {
        final ItemStack mainhand;
        final int consumableSlot;

        SwapBackup(ItemStack mainhand, int consumableSlot) {
            this.mainhand = mainhand;
            this.consumableSlot = consumableSlot;
        }
    }

    private static final WeakHashMap<EntityMaid, SwapBackup> MAINHAND_BACKUP = new WeakHashMap<>();

    /**
     * 女仆可用物品栏里「主手」的槽位索引。
     *
     * <p>依据 {@code EntityMaid.getAvailableInv(true)} 的实现：它构造
     * {@code MaidInvWrapper(handsInvWrapper, 背包RangedWrapper)}，
     * 而 {@code EntityHandsInvWrapper} 的槽位顺序是 {@code [主手, 副手]}，
     * 所以合并后的 0 号槽就是主手。</p>
     */
    private static final int INV_SLOT_MAINHAND = 0;

    /**
     * 换弹状态。
     *
     * <p><b>为什么用静态表而不是实例字段</b>：TLM 会为女仆重建行为实例
     * （{@code createBrainTasks(maid)} 每只女仆调用一次），实例字段在行为被替换时会丢失，
     * 换弹就会中途作废。{@code MaidSC2GunShootTask} 用的是实例字段，这里改成按女仆存放，
     * 保证换弹能跨 tick 走完。</p>
     */
    private static final WeakHashMap<EntityMaid, Integer> RELOAD_TIMER = new WeakHashMap<>();

    /**
     * 「本 tick 刚开火」的动画计时。
     *
     * <p>医疗兵原来<b>完全不上报动画状态</b>：客户端的 {@code AnimationManagerMixin} 是读
     * 女仆的 <b>syncTaskData</b>（{@code scg2tlm:firing} / {@code scg2tlm:reloading} /
     * {@code scg2tlm:melee}）来决定 {@code tac:reload:} / {@code tac:aim:fire:} 这些动画前缀的，
     * 而本任务过去只往<b>枪械物品的 NBT</b> 里写 {@code scg2tlm:reloading} —— 客户端根本不读那个，
     * 所以医疗兵既没有换弹动画也没有开火动画。</p>
     */
    private static final WeakHashMap<EntityMaid, Integer> FIRE_ANIM_TICKS = new WeakHashMap<>();

    /** 开火动画持续刻数（与 {@code MaidSC2GunShootTask} 的 firingTicks 保持一致）。 */
    private static final int FIRE_ANIM_DURATION = 5;

    /** 上一次同步给客户端的动画状态，用于去重（避免每 tick 都写 syncTaskData）。 */
    private static final WeakHashMap<EntityMaid, Boolean> SYNCED_RELOAD = new WeakHashMap<>();
    private static final WeakHashMap<EntityMaid, Boolean> SYNCED_FIRE = new WeakHashMap<>();

    public MedicHealTask() {
        super(ImmutableMap.of(
                MemoryModuleType.WALK_TARGET, MemoryStatus.REGISTERED,
                MemoryModuleType.LOOK_TARGET, MemoryStatus.REGISTERED
        ), 200);
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, EntityMaid maid) {
        if (maid.isMaidInSittingPose() || maid.isPassenger()) return false;
        return findTarget(maid).isPresent();
    }

    @Override
    protected boolean canStillUse(ServerLevel level, EntityMaid maid, long gameTime) {
        if (findTarget(maid).isEmpty()) {
            CHARGE.remove(maid);
            return false;
        }
        return true;
    }

    @Override
    protected void start(ServerLevel level, EntityMaid maid, long gameTime) {
        CHARGE.put(maid, 0);
    }

    @Override
    protected void stop(ServerLevel level, EntityMaid maid, long gameTime) {
        CHARGE.remove(maid);
        // 行为结束时必须收掉「正在使用物品」状态：否则女仆会一直保持 isUsingItem()，
        // 表现是卡在 use_mainhand 动画上且无法射击。
        if (ITEM_USE.remove(maid) != null) {
            maid.stopUsingItem();
        }
        // 兜底：把换手期间挪走的原主手物品还回去，避免枪滞留在物品栏里。
        restoreMainhand(maid);
        maid.getNavigation().stop();
        maid.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        // 收掉本任务上报的动画状态与举枪姿势，避免换任务后卡在换弹/开火动画上
        FIRE_ANIM_TICKS.remove(maid);
        syncAnimFlags(maid, false, false);
        maid.setSwingingArms(false);
    }

    @Override
    protected void tick(ServerLevel level, EntityMaid maid, long gameTime) {
        tickFireAnim(maid);

        // ---- 「使用治疗消耗品」阶段：播放使用动画，倒计时结束才结算 ----
        Integer using = ITEM_USE.get(maid);
        if (using != null) {
            // 使用期间站定不动
            maid.getNavigation().stop();
            maid.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);

            Optional<LivingEntity> usingTarget = findTarget(maid);
            if (usingTarget.isPresent()) {
                BehaviorUtils.lookAtEntity(maid, usingTarget.get());
            }

            int left = using - 1;
            if (left > 0) {
                ITEM_USE.put(maid, left);
                // 每个 tick 都重新 startUsingItem（<b>不能</b>用 `if (!isUsingItem())` 保护）。
                //
                // 原因：这些治疗品的 getUseDuration() 对女仆而言是 0 ——
                // SC2 的绷带/药膏根本没设 useDuration（默认 0），而原版
                // LivingEntity.updateUsingItem 在「已用刻数 >= useDuration」时会<b>立刻</b>
                // 结束使用状态。于是 isUsingItem() 每 tick 都被原版清掉，若加保护就只在
                // 第 1 刻开始过一次，动画一闪而过。
                //
                // 无条件重新开始即可：startUsingItem 会把 useItemRemaining 重置为 duration，
                // 从而让 isUsingItem() 在整个 ITEM_USE_TICKS 期间保持为真，
                // TLM 的 AnimationManager.predicateUse 就会持续播 use_mainhand 动画。
                maid.startUsingItem(net.minecraft.world.InteractionHand.MAIN_HAND);
                return;
            }

            // 倒计时结束：结算治疗效果，然后立刻把主手还原成换手前的物品（通常是枪）。
            ITEM_USE.remove(maid);
            maid.stopUsingItem();
            if (usingTarget.isPresent()) {
                LivingEntity target = usingTarget.get();
                ItemStack stack = maid.getMainHandItem();
                if (SC2HealCompat.applyHealingConsumable(maid, target, stack)) {
                    LAST_HEAL.put(maid, gameTime);
                    // 用真正被治疗的那个目标判断是不是自疗（这里是重新 findTarget 出来的目标，
                    // 可能与起手时不同）—— 气泡必须和实际结算对象一致
                    chatHeal(maid, gameTime, target == maid, true);
                }
            }
            restoreMainhand(maid);
            return;
        }

        Optional<LivingEntity> targetOpt = findTarget(maid);
        if (targetOpt.isEmpty()) return;
        LivingEntity target = targetOpt.get();

        // 始终看着治疗对象
        BehaviorUtils.lookAtEntity(maid, target);

        ItemStack main = maid.getMainHandItem();

        // ---- 换弹优先：十字军打空后必须先换弹（用注射器），否则永远治不了 ----
        if (scg2tlm$tickReload(maid, main)) {
            // 换弹中：站定不动，放弃本次施法蓄力
            CHARGE.put(maid, 0);
            maid.getNavigation().stop();
            maid.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
            maid.setSwingingArms(true);   // 举枪换弹，避免换弹动画期间手臂放下
            return;
        }

        boolean hasBandage = SC2HealCompat.findBandage(maid) != null;
        double dist = maid.distanceTo(target);

        // 手段选择：十字军优先（远程）；没弹药或太近时用绷带
        boolean useGun = canShoot(maid, main) && dist <= GUN_RANGE;
        boolean useBandage = !useGun && hasBandage && dist <= BANDAGE_RANGE;

        if (!useGun && !useBandage) {
            // 还没到有效距离：走过去（不举枪）
            CHARGE.put(maid, 0);
            maid.setSwingingArms(false);
            if (dist > BANDAGE_RANGE) {
                BehaviorUtils.setWalkAndLookTargetMemories(maid, target, 0.4f, 2);
            } else {
                maid.getNavigation().stop();
                maid.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
            }
            return;
        }

        // 到位：停止移动，开始蓄力
        maid.getNavigation().stop();
        maid.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        // 用枪治疗时举枪瞄准（客户端靠 isSwingingArms() 选 tac:aim: 前缀）
        maid.setSwingingArms(useGun);

        Long last = LAST_HEAL.get(maid);
        if (last != null && gameTime - last < COOLDOWN_TICKS) return;

        int charge = CHARGE.merge(maid, 1, Integer::sum);
        if (charge < CHARGE_TICKS) return;
        CHARGE.put(maid, 0);

        if (useGun) {
            ItemStack gun = maid.getMainHandItem();
            if (gun.getItem() instanceof top.ribs.scguns.item.GunItem gunItem) {
                if (SC2HealCompat.fireHealingShot(maid, target, gun, gunItem)) {
                    LAST_HEAL.put(maid, gameTime);
                    // 开火音效 + 开火动画（这两样原来都没有：fireHealingShot 直接调 AIGunEvent，
                    // 绕过了 SC2GunCompat.performGunAttack 里的音效，动画状态也没上报）
                    SC2GunCompat.playGunFireSound(maid, gun);
                    FIRE_ANIM_TICKS.put(maid, FIRE_ANIM_DURATION);
                    syncAnimFlags(maid, false, true);
                    // 自疗时给一句「给自己来一针」的气泡（枪这一路是瞬间结算，所以直接播完成语）
                    if (target == maid) {
                        chatHeal(maid, gameTime, true, true);
                    }
                }
            }
        } else {
            // 治疗消耗品：换到主手 → 进入「使用」阶段（播模型的 use_mainhand 动画）
            // → 倒计时结束结算治疗并还原主手。
            //
            // 参考 TLM 自己的 MaidFeedOwnerTask（女仆喂主人）：它同样是
            //「从 getAvailableInv 取物品 → 处理 → setStackInSlot 放回」，
            // 区别只是它不需要物品真的在手（直接施加食物效果），而我们需要 ——
            // 因为使用动画由原版的 isUsingItem() 驱动。
            ItemStack consumable = SC2HealCompat.findHealingConsumable(maid);
            if (consumable != null && mainhandTry(maid, consumable)) {
                ITEM_USE.put(maid, ITEM_USE_TICKS);
                maid.startUsingItem(net.minecraft.world.InteractionHand.MAIN_HAND);
                BehaviorUtils.lookAtEntity(maid, target);
                chatHeal(maid, gameTime, target == maid, false);
            }
        }
    }

    /**
     * 把治疗消耗品换到主手，原来的主手物品（通常是枪）与消耗品原槽位一并备份。
     *
     * <p><b>精确交换，不做合并</b>：全程只用 {@code getStackInSlot} / {@code setStackInSlot}
     * 读写裸槽位，避免 {@code ItemHandlerHelper.insertItem} 的同类合并把物品复制成两份。</p>
     *
     * @return true 表示物品已在主手（可能本来就拿着它）
     */
    private static boolean mainhandTry(EntityMaid maid, ItemStack consumable) {
        var inv = maid.getAvailableInv(true);
        if (inv == null || inv.getSlots() <= INV_SLOT_MAINHAND) return false;

        ItemStack main = inv.getStackInSlot(INV_SLOT_MAINHAND);

        // 情形一：消耗品已经在主手 —— 无需交换
        // 用引用比较：consumable 可能是主手栈，也可能是副手/背包里的某个槽。
        // 只有当它确实<b>就是</b>主手那个栈时才算「已在主手」。
        if (main == consumable) {
            MAINHAND_BACKUP.put(maid, new SwapBackup(ItemStack.EMPTY, -1));
            return true;
        }

        // 情形二：消耗品在别的槽 —— 做精确交换
        int slot = -1;
        for (int i = 1; i < inv.getSlots(); i++) {
            // 引用比较是刻意的：consumable 就是 findHealingConsumable 返回的那个栈对象
            if (inv.getStackInSlot(i) == consumable) {
                slot = i;
                break;
            }
        }
        if (slot < 0) return false;   // 状态已变，放弃（此时主手未受影响）

        ItemStack taken = inv.getStackInSlot(slot);
        if (taken.isEmpty()) return false;

        // 交换：主手 ← 消耗品，原槽 ← 原主手物品（即使原主手为空也照写，保持对称）
        MAINHAND_BACKUP.put(maid, new SwapBackup(main.copy(), slot));
        inv.setStackInSlot(INV_SLOT_MAINHAND, taken);
        inv.setStackInSlot(slot, main);
        return true;
    }

    /**
     * 还原主手与消耗品原槽位。治疗成功、目标消失、行为结束都会走到这里，属兜底路径。
     *
     * <p>同样只用裸槽位操作，不引入任何合并。</p>
     */
    private static void restoreMainhand(EntityMaid maid) {
        SwapBackup backup = MAINHAND_BACKUP.remove(maid);
        if (backup == null) return;

        var inv = maid.getAvailableInv(true);
        if (inv == null || inv.getSlots() <= INV_SLOT_MAINHAND) return;

        ItemStack current = inv.getStackInSlot(INV_SLOT_MAINHAND);

        if (backup.consumableSlot < 0) {
            // 消耗品本来就在主手：只需把用剩的部分留在原处，无需换回
            return;
        }
        if (backup.consumableSlot >= inv.getSlots()) return;

        // 反向交换：消耗品（可能已用掉一部分）回原槽，原主手物品回主手
        inv.setStackInSlot(backup.consumableSlot, current);
        inv.setStackInSlot(INV_SLOT_MAINHAND, backup.mainhand);
    }

    /**
     * 治疗专用的气泡分派：<b>自疗与治疗别人用两套文案</b>。
     *
     * <p>原来只有 {@code heal_use} / {@code heal_done} 一套，文案都是对着<b>别人</b>说的
     * （「别动，我给你包扎一下~」）—— 医疗兵给自己治的时候也照发，读起来就不对了。</p>
     *
     * @param self true = 治疗对象是女仆自己（{@code heal_self_use} / {@code heal_self_done}）
     * @param done true = 「完成」文案，false = 「起手」文案
     */
    private static void chatHeal(EntityMaid maid, long gameTime, boolean self, boolean done) {
        if (self) {
            chat(maid, gameTime, done ? "heal_self_done" : "heal_self_use", done ? 2 : 3);
        } else {
            chat(maid, gameTime, done ? "heal_done" : "heal_use", done ? 2 : 4);
        }
    }

    /**
     * 发一条聊天气泡（沿用本模组既有的 {@code chat_bubble.scg2_maid_compat.*} 体系）。
     *
     * <h3>限流：开始与完成用<b>不同的表</b></h3>
     * <p>曾经两者共用一张 {@code LAST_CHAT}（每 600 刻一条），结果是「开始包扎」的气泡刚发出，
     * 32 刻后的「包扎完成」气泡就被限流吞掉——表现就是<b>回血完成没有气泡</b>。
     * 现在分开存放：完成后很短（{@link #DONE_CHAT_COOLDOWN_TICKS}）即可再发，
     * 但「开始」仍按 {@link #CHAT_COOLDOWN_TICKS} 限流，避免反复起手刷屏。</p>
     *
     * @param keyPrefix 语言键前缀，如 {@code "heal_use"} → {@code chat_bubble.scg2_maid_compat.heal_use.N}
     * @param variants  该组的文案数量（取 {@code .0} 到 {@code .(variants-1)}）
     */
    private static void chat(EntityMaid maid, long gameTime, String keyPrefix, int variants) {
        if (variants <= 0) return;
        // 判据用后缀而不是等值比较：否则 heal_self_done 会被当成「起手」文案，
        // 落回 600 刻那张表，又被下一次起手的气泡吞掉（就是之前修过的那个 bug）
        boolean done = keyPrefix.endsWith("_done");
        var table = done ? LAST_CHAT_DONE : LAST_CHAT;
        int cooldown = done ? DONE_CHAT_COOLDOWN_TICKS : CHAT_COOLDOWN_TICKS;
        if (gameTime - table.getOrDefault(maid, Long.MIN_VALUE / 2) < cooldown) return;
        table.put(maid, gameTime);
        int idx = maid.getRandom().nextInt(variants);
        String key = "chat_bubble.scg2_maid_compat." + keyPrefix + "." + idx;
        maid.getChatBubbleManager().addChatBubble(
                com.github.tartaricacid.touhoulittlemaid.entity.chatbubble.implement.TextChatBubbleData.create(
                        600,
                        net.minecraft.network.chat.Component.translatable(key),
                        com.github.tartaricacid.touhoulittlemaid.entity.chatbubble.IChatBubbleData.TYPE_2,
                        com.github.tartaricacid.touhoulittlemaid.entity.chatbubble.IChatBubbleData.DEFAULT_PRIORITY));
    }

    /** 目标是否还需要治疗（缺血或带撕裂伤）。 */
    private static boolean needsHeal(LivingEntity e) {
        return SC2HealCompat.isWounded(e) || SC2HealCompat.hasLacerated(e);
    }

    /**
     * 每 tick 维护十字军的换弹。
     *
     * <p>复刻 {@code MaidSC2GunShootTask} 的换弹序列：</p>
     * <pre>
     *   startReload: playPreReloadSound + playReloadSound
     *                timer = SC2GunCompat.getReloadTime(gun)
     *                写入 "scg2tlm:reloading" 标签（供动画同步读取）
     *   tick:        倒计时；归零时 SC2GunCompat.reloadFromInventory(maid, gun)
     * </pre>
     *
     * @return true 表示本 tick 处于换弹中（调用方应放弃施法）
     */
    private static boolean scg2tlm$tickReload(EntityMaid maid, ItemStack gun) {
        if (!SC2HealCompat.isCrusader(gun)) return false;

        Integer timer = RELOAD_TIMER.get(maid);

        // 换弹进行中：倒计时
        if (timer != null) {
            if (timer > 0) {
                RELOAD_TIMER.put(maid, timer - 1);
                // 上报换弹动画（客户端读 syncTaskData，不读枪械 NBT）
                syncAnimFlags(maid, true, FIRE_ANIM_TICKS.containsKey(maid));
                return true;
            }
            // 倒计时结束 → 真正装填
            SC2GunCompat.reloadFromInventory(maid, gun);
            RELOAD_TIMER.remove(maid);
            gun.getOrCreateTag().remove("scg2tlm:reloading");
            syncAnimFlags(maid, false, FIRE_ANIM_TICKS.containsKey(maid));
            // 换弹完成音效：用配置的 reload_end_sound（默认 scguns:item.reload_end.reload_end）
            SC2GunCompat.playReloadEndSound(maid, gun);
            return false;
        }

        // 没在换弹：判断是否需要开始
        if (!SC2GunCompat.needsReload(gun)) return false;
        if (!SC2GunCompat.hasAmmoInInventory(maid, gun)) return false;   // 没注射器可换

        // 开始换弹
        SC2GunCompat.playPreReloadSound(maid, gun);
        SC2GunCompat.playReloadSound(maid, gun);
        int reloadTime = SC2GunCompat.getReloadTime(gun);
        RELOAD_TIMER.put(maid, reloadTime);
        gun.getOrCreateTag().putInt("scg2tlm:reloading", reloadTime);
        syncAnimFlags(maid, true, FIRE_ANIM_TICKS.containsKey(maid));
        return true;
    }

    /**
     * 把医疗兵的动画状态上报给客户端。
     *
     * <p>客户端 {@code AnimationManagerMixin} 读的是女仆的 syncTaskData
     * （{@code scg2tlm:reloading} / {@code scg2tlm:firing}），所以用枪治疗时也必须写这里，
     * 否则换弹/开火都没有动画。带状态去重，避免每 tick 都触发一次数据同步。</p>
     */
    private static void syncAnimFlags(EntityMaid maid, boolean reloading, boolean firing) {
        if (Boolean.valueOf(reloading).equals(SYNCED_RELOAD.get(maid))
                && Boolean.valueOf(firing).equals(SYNCED_FIRE.get(maid))) {
            return;
        }
        SYNCED_RELOAD.put(maid, reloading);
        SYNCED_FIRE.put(maid, firing);

        CompoundTag tag = ((EntityMaidTaskDataInvoker) maid).invokeGetSyncTaskData();
        if (tag == null) {
            tag = new CompoundTag();
        }
        if (reloading) {
            tag.putBoolean("scg2tlm:reloading", true);
        } else {
            tag.remove("scg2tlm:reloading");
        }
        if (firing) {
            tag.putBoolean("scg2tlm:firing", true);
        } else {
            tag.remove("scg2tlm:firing");
        }
        ((EntityMaidTaskDataInvoker) maid).invokeSetSyncTaskData(tag);
    }

    /** 开火动画倒计时：到点收掉 firing 标记（换弹标记按当前换弹状态重算，不受影响）。 */
    private static void tickFireAnim(EntityMaid maid) {
        Integer ticks = FIRE_ANIM_TICKS.get(maid);
        if (ticks == null) return;
        if (ticks > 1) {
            FIRE_ANIM_TICKS.put(maid, ticks - 1);
            return;
        }
        FIRE_ANIM_TICKS.remove(maid);
        syncAnimFlags(maid, RELOAD_TIMER.containsKey(maid), false);
    }

    /** 十字军是否还能开火（有弹药，或背包里有注射器可换弹）。 */
    private static boolean canShoot(EntityMaid maid, ItemStack gun) {
        if (!SC2HealCompat.isCrusader(gun)) return false;
        if (RELOAD_TIMER.containsKey(maid)) return false;   // 换弹中不能开火
        if (!SC2GunCompat.needsReload(gun)) return true;
        return SC2GunCompat.hasAmmoInInventory(maid, gun);
    }

    /**
     * 找最近的受伤友军。
     *
     * <p>优先级：主人 → 其他女仆 → 同阵营 → 自己；同级取最近。</p>
     */
    public static Optional<LivingEntity> findTarget(EntityMaid medic) {
        LivingEntity owner = medic.getOwner();
        if (owner != null && owner.isAlive() && needsHeal(owner) && SC2HealCompat.isFriendly(medic, owner)) {
            return Optional.of(owner);
        }

        List<LivingEntity> candidates = medic.level().getEntitiesOfClass(
                LivingEntity.class,
                medic.getBoundingBox().inflate(SEARCH_RADIUS),
                e -> e.isAlive() && !e.isRemoved()
                        && needsHeal(e)
                        && SC2HealCompat.isFriendly(medic, e));

        Optional<LivingEntity> otherMaid = candidates.stream()
                .filter(e -> e instanceof EntityMaid)
                .min(Comparator.comparingDouble(medic::distanceToSqr));
        if (otherMaid.isPresent()) return otherMaid;

        Optional<LivingEntity> any = candidates.stream()
                .min(Comparator.comparingDouble(medic::distanceToSqr));
        if (any.isPresent()) return any;

        if (needsHeal(medic)) return Optional.of(medic);
        return Optional.empty();
    }
}
