package com.scg2tlm.elmomod.mixin;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.nbt.CompoundTag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(EntityMaid.class)
public interface EntityMaidTaskDataInvoker {

    @Invoker(value = "getSyncTaskData", remap = false)
    CompoundTag invokeGetSyncTaskData();

    @Invoker(value = "setSyncTaskData", remap = false)
    void invokeSetSyncTaskData(CompoundTag compoundTag);
}
