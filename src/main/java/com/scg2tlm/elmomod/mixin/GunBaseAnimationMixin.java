package com.scg2tlm.elmomod.mixin;

import com.github.tartaricacid.touhoulittlemaid.api.entity.IMaid;
import com.github.tartaricacid.touhoulittlemaid.client.animation.gecko.condition.ConditionManager;
import com.github.tartaricacid.touhoulittlemaid.client.animation.script.ModelRendererWrapper;
import com.github.tartaricacid.touhoulittlemaid.compat.gun.tacz.client.GunBaseAnimation;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.file.AnimationFile;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.resource.GeckoLibCache;
import com.scg2tlm.elmomod.compat.SC2GunCompat;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GunBaseAnimation.class)
public class GunBaseAnimationMixin {
    private static boolean hasCustomTacAnim(IMaid maid) {
        if (maid == null) return false;
        if (!(maid.asEntity() instanceof EntityMaid entity)) return false;
        String modelIdStr = entity.getModelId();
        if (modelIdStr == null || !modelIdStr.contains(":")) return false;
        ResourceLocation modelId = ResourceLocation.tryParse(modelIdStr);
        if (modelId == null) return false;
        ResourceLocation animLoc = ResourceLocation.tryParse(
                modelId.getNamespace() + ":animation/" + modelId.getPath() + ".animation.json");
        if (animLoc != null && ConditionManager.getTAC(animLoc) != null) return true;
        if (ConditionManager.getTAC(modelId) != null) return true;
        AnimationFile animFile = GeckoLibCache.getInstance().getAnimations().get(animLoc);
        if (animFile != null) {
            return animFile.animations().keySet().stream().anyMatch(k -> k.startsWith("tac:"));
        }
        return false;
    }

    @Inject(method = "onHoldGun", at = @At("HEAD"), cancellable = true, remap = false)
    private static void onOnHoldGun(IMaid maid, ModelRendererWrapper arm1, ModelRendererWrapper arm2, CallbackInfoReturnable<Boolean> cir) {
        if (maid == null) return;
        if (maid.asEntity().isSleeping()) { cir.setReturnValue(false); return; }
        ItemStack stack = maid.asEntity().getMainHandItem();
        if (!SC2GunCompat.isSC2Gun(stack)) return;

        if (hasCustomTacAnim(maid)) {
            cir.setReturnValue(false);
            return;
        }

        if (arm1 != null) {
            arm1.setRotateAngleX(-1.75f);
            arm1.setRotateAngleY(0.5f);
        }
        if (arm2 != null) {
            arm2.setRotateAngleX(-1.65f);
            arm2.setRotateAngleY(-0.174f);
        }
        cir.setReturnValue(true);
    }
}
