package com.scg2tlm.elmomod.compat;

import com.scg2tlm.elmomod.compat.task.TaskSC2GrenadeAttack;
import com.scg2tlm.elmomod.compat.task.TaskSC2GunAttack;
import com.scg2tlm.elmomod.compat.task.TaskSC2MedicHeal;
import com.github.tartaricacid.touhoulittlemaid.api.ILittleMaid;
import com.github.tartaricacid.touhoulittlemaid.api.LittleMaidExtension;
import com.github.tartaricacid.touhoulittlemaid.entity.task.TaskManager;

@LittleMaidExtension
public class LittleMaidCompat implements ILittleMaid {

    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();

    public LittleMaidCompat() {
    }

    @Override
    public void addMaidTask(TaskManager manager) {
        // 这三个任务是否真的注册上，日志里要能直接看出来。
        // 排查「工作模式消失」时，这一行是「没注册」与「注册了但没显示」的分界证据：
        //   - 有这行日志 → 注册成功，问题在 TLM 的显示侧
        //   - 没这行日志 → 本方法没被调用（扩展扫描 / 类加载问题）
        LOGGER.info("[scg2_maid_compat] addMaidTask 被调用，当前任务数={}", TaskManager.getTaskIndex().size());
        try {
            manager.add(new TaskSC2GunAttack());
            manager.add(new TaskSC2GrenadeAttack());
            // 医疗兵：用十字军与治疗绷带治疗受伤的主人/友方女仆/同阵营实体
            manager.add(new TaskSC2MedicHeal());
            LOGGER.info("[scg2_maid_compat] 已注册 3 个任务，注册后任务数={}", TaskManager.getTaskIndex().size());
        } catch (Throwable t) {
            // 逐个注册，避免某一个失败把后面的一起带走（医疗兵曾经排在最后）
            LOGGER.error("[scg2_maid_compat] 注册任务失败", t);
            throw t;
        }
    }
}
