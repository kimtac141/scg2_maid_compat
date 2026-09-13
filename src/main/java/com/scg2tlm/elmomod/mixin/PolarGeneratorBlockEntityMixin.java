package com.scg2tlm.elmomod.mixin;

import com.mojang.logging.LogUtils;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.common.util.NonNullConsumer;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import top.ribs.scguns.blockentity.PolarGeneratorBlockEntity;

import java.util.ConcurrentModificationException;

@Mixin(PolarGeneratorBlockEntity.class)
public class PolarGeneratorBlockEntityMixin {
    private static final Logger LOGGER = LogUtils.getLogger();

    @Redirect(
        method = "tick",
        at = @At(value = "INVOKE", target = "Lnet/minecraftforge/common/util/LazyOptional;ifPresent(Lnet/minecraftforge/common/util/NonNullConsumer;)V", remap = false),
        remap = false
    )
    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void redirectIfPresent(LazyOptional instance, NonNullConsumer consumer) {
        try {
            instance.ifPresent(consumer);
        } catch (ConcurrentModificationException e) {
            LOGGER.warn("Caught ConcurrentModificationException in PolarGenerator energy transfer, skipping this tick");
        }
    }
}
