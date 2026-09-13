package com.scg2tlm.elmomod.compat.task;

import com.scg2tlm.elmomod.compat.SC2GunCompat;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.google.common.collect.ImmutableMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.item.ItemStack;
import net.minecraft.util.Mth;

import java.util.Optional;

/**
 * 射击时的环绕走位。
 *
 * <h3>为什么现在以「视线」为第一约束</h3>
 * <p>旧实现是纯随机的：每约 1 秒有 30% 概率反向、30% 概率改前进/后退，走位时完全不看
 * 自己还能不能看见目标。于是会出现一条死循环：</p>
 * <pre>
 *   发现敌人 → 寻路进射程 → 走位 → 走位把自己绕到掩体后面 → 看不见目标 → 停火
 *   → 3 秒后开火任务走向最后目击点 → 又看见敌人 → 继续走位 → 又绕到掩体后 …
 * </pre>
 * <p>根因不在「跟丢后怎么办」，而在<b>走位本身会主动把自己走出视线</b>。
 * 现在的规则：</p>
 * <ol>
 *   <li>丢视线的第一刻就判定「刚才那个方向是坏方向」→ 反向，并朝目标方向靠近
 *       （这是最可靠的走回视线的动作），最多自救 {@value #LOS_RECOVERY_TICKS} 刻；</li>
 *   <li>自救成功后先<b>站定射击</b> {@value #HOLD_AFTER_COVER_TICKS} 刻，不马上又走出去
 *       —— 此时走位方向已经翻转，之后再走位是「走回刚才看得见的那一侧」；</li>
 *   <li>自救失败（{@value #LOS_RECOVERY_TICKS} 刻还没恢复视线）就退出，把局面交给开火任务的
 *       重获视线 / 放弃目标逻辑，绝不再继续盲走；</li>
 *   <li>随机换向的周期拉长到 {@value #STRAFE_SWITCH_TICKS} 刻、概率降到 20%，
 *       减少「随机游走」把自己晃进掩体的概率。</li>
 * </ol>
 */
public class SC2GunStrafingTask extends Behavior<EntityMaid> {
    /** 丢失视线后允许继续接管、用于自救的刻数（20 刻 = 1 秒）。 */
    private static final int LOS_RECOVERY_TICKS = 20;
    /** 从掩体后重新看见目标后，先站定射击的刻数（40 刻 = 2 秒）。 */
    private static final int HOLD_AFTER_COVER_TICKS = 40;
    /** 同方向连续走位多少刻后才考虑换向。 */
    private static final int STRAFE_SWITCH_TICKS = 40;
    /** 目标进入这个距离内就改为一面向后退一面绕。 */
    private static final double BACKOFF_DISTANCE = 6.0;
    /** 超过这个距离就不再环绕（交给走位任务去拉近）。 */
    private static final double MAX_STRAFE_DISTANCE = 8.0;

    private boolean strafingClockwise;
    private boolean strafingBackwards;
    private int strafingTime = -1;
    /** 连续丢失视线的刻数。 */
    private int losLostTicks = 0;
    /** 「刚从掩体后回来」的站定射击计时，跨 start/stop 保留。 */
    private int holdTicks = 0;

    public SC2GunStrafingTask() {
        super(ImmutableMap.of(
                MemoryModuleType.WALK_TARGET, MemoryStatus.VALUE_ABSENT,
                MemoryModuleType.LOOK_TARGET, MemoryStatus.REGISTERED,
                MemoryModuleType.ATTACK_TARGET, MemoryStatus.VALUE_PRESENT,
                MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES, MemoryStatus.VALUE_PRESENT
        ), 200);
    }

    /**
     * 除「能不能看见目标」以外的全部前置条件。
     *
     * <p>抽出来是为了让 {@link #canStillUse} 故意比 {@link #checkExtraStartConditions} 宽松：
     * 丢视线的那一刻本任务必须还能运行，才有机会把自己走回视线。</p>
     */
    private Optional<LivingEntity> baseTarget(EntityMaid maid) {
        if (maid.isMaidInSittingPose() || maid.isPassenger()) return Optional.empty();
        ItemStack gun = maid.getMainHandItem();
        if (!SC2GunCompat.isSC2Gun(gun)) return Optional.empty();
        if (SC2GunCompat.isLineInfantryGun(gun)) return Optional.empty();
        // 刺刀模式（打空备弹 + 有刺刀）：禁止环绕走位，由走位任务贴脸近战
        if (SC2GunCompat.needsReload(gun) && !SC2GunCompat.hasAmmoInInventory(maid, gun)
                && SC2GunCompat.hasBayonet(gun)) {
            return Optional.empty();
        }
        // 走位任务有行走目标时（需要贴近/调整距离/搜索）暂停环绕，避免覆盖移动
        if (maid.getBrain().hasMemoryValue(MemoryModuleType.WALK_TARGET)) {
            return Optional.empty();
        }
        Optional<LivingEntity> target = maid.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET);
        if (target.isEmpty() || !target.get().isAlive() || target.get().isRemoved()) {
            if (target.isPresent()) {
                maid.getBrain().eraseMemory(MemoryModuleType.ATTACK_TARGET);
                maid.setTarget(null);
            }
            return Optional.empty();
        }
        return target;
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, EntityMaid maid) {
        Optional<LivingEntity> target = baseTarget(maid);
        return target.isPresent() && maid.canSee(target.get());
    }

    @Override
    protected boolean canStillUse(ServerLevel level, EntityMaid maid, long gameTime) {
        Optional<LivingEntity> target = baseTarget(maid);
        if (target.isEmpty()) return false;
        // 能看见 → 正常；看不见 → 只给 LOS_RECOVERY_TICKS 刻自救时间，之后交还给开火任务
        return maid.canSee(target.get()) || losLostTicks < LOS_RECOVERY_TICKS;
    }

    @Override
    protected void start(ServerLevel level, EntityMaid maid, long gameTime) {
        if (maid.isMaidInSittingPose()) return;
        maid.setSwingingArms(true);
        strafingTime = -1;
        losLostTicks = 0;
    }

    @Override
    protected void stop(ServerLevel level, EntityMaid maid, long gameTime) {
        maid.setSwingingArms(false);
        maid.getMoveControl().strafe(0, 0);
        losLostTicks = 0;
    }

    @Override
    protected void tick(ServerLevel level, EntityMaid maid, long gameTime) {
        if (maid.isMaidInSittingPose() || maid.isPassenger()) return;
        ItemStack gun = maid.getMainHandItem();
        if (!SC2GunCompat.isSC2Gun(gun)) return;

        Optional<LivingEntity> targetOpt = baseTarget(maid);
        if (targetOpt.isEmpty()) return;
        LivingEntity target = targetOpt.get();
        maid.setTarget(target);

        // ---- 第一优先级：视线。走位绝不能把自己走进掩体 ----
        if (!maid.canSee(target)) {
            if (losLostTicks == 0) {
                // 第一次丢视线：把当前方向判为「坏方向」并翻转，
                // 这样恢复视线后再走位就是走回刚才看得见的那一侧
                strafingClockwise = !strafingClockwise;
                strafingBackwards = false;
            }
            losLostTicks++;
            strafingTime = -1;
            holdTicks = HOLD_AFTER_COVER_TICKS;

            if (losLostTicks > LOS_RECOVERY_TICKS) {
                // 自救失败：站定，交给开火任务（走向最后目击点 / 放弃目标）
                maid.getMoveControl().strafe(0, 0);
                return;
            }
            // 一边朝目标靠近（最可靠的重新看见目标的方式）一边反向平移
            maid.getMoveControl().strafe(0.35f, strafingClockwise ? 0.3f : -0.3f);
            maid.setYRot(Mth.rotateIfNecessary(maid.getYRot(), maid.yHeadRot, 0));
            BehaviorUtils.lookAtEntity(maid, target);
            return;
        }

        losLostTicks = 0;

        // ---- 刚从掩体后回来：先站定射击，别立刻又走出去 ----
        if (holdTicks > 0) {
            holdTicks--;
            maid.getMoveControl().strafe(0, 0);
            BehaviorUtils.lookAtEntity(maid, target);
            return;
        }

        double dist = maid.distanceTo(target);

        if (dist < MAX_STRAFE_DISTANCE) {
            strafingTime++;
        } else {
            strafingTime = -1;
        }

        if (strafingTime >= STRAFE_SWITCH_TICKS) {
            if (maid.getRandom().nextFloat() < 0.2f) {
                strafingClockwise = !strafingClockwise;
            }
            strafingTime = 0;
        }

        if (strafingTime > -1) {
            strafingBackwards = dist < BACKOFF_DISTANCE;
            maid.getMoveControl().strafe(
                    strafingBackwards ? -0.4f : 0.0f,
                    strafingClockwise ? 0.3f : -0.3f
            );
            maid.setYRot(Mth.rotateIfNecessary(maid.getYRot(), maid.yHeadRot, 0));
        } else {
            maid.getMoveControl().strafe(0, 0);
        }

        BehaviorUtils.lookAtEntity(maid, target);
    }
}
