package com.scg2tlm.elmomod.mixin;

import com.github.tartaricacid.touhoulittlemaid.client.resource.GeckoModelLoader;
import com.scg2tlm.elmomod.ExampleMod;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;

/**
 * 兜底注入：无论 DefaultGeckoAnimationEvent 事件订阅是否生效，都在
 * GeckoModelLoader.loadDefaultAnimation() 末尾把 scg2_maid_compat:animation/melee.animation.json
 * 直接合并进 DEFAULT_TAC_ANIMATION_FILE，随后 registerMaidAnimations 会把它合并进所有 gecko 模型。
 * 幂等（putAnimation 覆盖同名键），与事件注入并存无害。
 */
@Mixin(GeckoModelLoader.class)
public class GeckoModelLoaderMixin {
    private static final Logger LOGGER = LogUtils.getLogger();

    @Inject(method = "loadDefaultAnimation", at = @At("TAIL"), remap = false)
    private static void scg2tlm$forceMergeMeleeAnimation(CallbackInfo ci) {
        try {
            ResourceLocation meleeLoc = ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID, "animation/melee.animation.json");
            Optional<Resource> res = Minecraft.getInstance().getResourceManager().getResource(meleeLoc);
            if (res.isPresent()) {
                try (InputStream in = res.get().open()) {
                    GeckoModelLoader.mergeAnimationFile(in, GeckoModelLoader.DEFAULT_TAC_ANIMATION_FILE);
                }
                LOGGER.debug("[scg2_maid_compat] Injected melee animation into TLM default TAC animation file");
            } else {
                LOGGER.warn("[scg2_maid_compat] melee.animation.json resource not found for injection");
            }
        } catch (IOException e) {
            LOGGER.error("[scg2_maid_compat] Failed to inject melee animation into TLM default TAC file", e);
        }
    }
}
