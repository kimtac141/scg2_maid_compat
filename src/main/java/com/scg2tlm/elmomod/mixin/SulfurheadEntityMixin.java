package com.scg2tlm.elmomod.mixin;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import top.ribs.scguns.entity.monster.SulfurheadEntity;

@Mixin(SulfurheadEntity.class)
public abstract class SulfurheadEntityMixin extends Monster {

    protected SulfurheadEntityMixin(EntityType<? extends Monster> entityType, Level level) {
        super(entityType, level);
    }

    /**
     * @author ELMOSYG
     * @reason Allow non-player entities (such as the maid's gunfire) to damage Sulfurheads.
     * The vanilla SCG2 implementation only lets Player sources deal damage.
     */
    @Overwrite(remap = false)
    public boolean hurt(DamageSource source, float amount) {
        return super.hurt(source, amount);
    }
}
