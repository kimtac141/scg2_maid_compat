package com.scg2tlm.elmomod.compat;

import com.scg2tlm.elmomod.compat.task.TaskSC2GrenadeAttack;
import com.scg2tlm.elmomod.compat.task.TaskSC2GunAttack;
import com.scg2tlm.elmomod.compat.task.TaskSC2MedicHeal;
import com.github.tartaricacid.touhoulittlemaid.api.ILittleMaid;
import com.github.tartaricacid.touhoulittlemaid.api.LittleMaidExtension;
import com.github.tartaricacid.touhoulittlemaid.entity.task.TaskManager;

@LittleMaidExtension
public class LittleMaidCompat implements ILittleMaid {
    public LittleMaidCompat() {
    }

    @Override
    public void addMaidTask(TaskManager manager) {
        manager.add(new TaskSC2GunAttack());
        manager.add(new TaskSC2GrenadeAttack());
        manager.add(new TaskSC2MedicHeal());
    }
}
