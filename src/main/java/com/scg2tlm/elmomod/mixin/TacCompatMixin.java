package com.scg2tlm.elmomod.mixin;

import com.github.tartaricacid.touhoulittlemaid.api.entity.IMaid;
import com.github.tartaricacid.touhoulittlemaid.client.animation.gecko.condition.ConditionManager;
import com.github.tartaricacid.touhoulittlemaid.client.animation.gecko.condition.ConditionTAC;
import com.github.tartaricacid.touhoulittlemaid.client.animation.script.ModelRendererWrapper;
import com.github.tartaricacid.touhoulittlemaid.client.entity.GeckoMaidEntity;
import com.github.tartaricacid.touhoulittlemaid.compat.tacz.TacCompat;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.core.PlayState;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.core.builder.ILoopType;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.core.event.predicate.AnimationEvent;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.file.AnimationFile;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.resource.GeckoLibCache;
import com.scg2tlm.elmomod.compat.SC2GunCompat;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.item.ItemStack;
import top.ribs.scguns.item.GunItem;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(TacCompat.class)
public class TacCompatMixin {
    private static final Logger LOGGER = LoggerFactory.getLogger("SCG2TLM");

    private static boolean hasCustomTacAnim(IMaid maid) {
        if (maid == null) return false;
        EntityMaid entity = (EntityMaid) maid.asEntity();
        if (entity == null) return false;
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

    @Inject(method = "isGun", at = @At("HEAD"), cancellable = true, remap = false)
    private static void onIsGun(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (SC2GunCompat.isSC2Gun(stack) && stack.getItem() instanceof GunItem) {
            LOGGER.debug("isGun -> true for {}", stack.getItem());
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "getGunId", at = @At("HEAD"), cancellable = true, remap = false)
    private static void onGetGunId(ItemStack stack, CallbackInfoReturnable<ResourceLocation> cir) {
        if (SC2GunCompat.isSC2Gun(stack) && stack.getItem() instanceof GunItem) {
            ResourceLocation id;
            if (SC2GunCompat.isMinigunGun(stack)) {
                id = ResourceLocation.fromNamespaceAndPath("tacz", "minigun");
            } else if (SC2GunCompat.isRpgGun(stack)) {
                id = ResourceLocation.fromNamespaceAndPath("tacz", "rpg");
            } else {
                id = SC2GunCompat.getGunId(stack);
            }
            LOGGER.info("[SC2TLM] getGunId -> {} for {}", id, stack.getItem());
            if (id != null) {
                cir.setReturnValue(id);
            }
        }
    }

    @Inject(method = "onHoldGun", at = @At("HEAD"), cancellable = true, remap = false)
    private static void onOnHoldGun(IMaid maid, ModelRendererWrapper armLeft, ModelRendererWrapper armRight, CallbackInfoReturnable<Boolean> cir) {
        ItemStack stack = maid.asEntity().getMainHandItem();
        if (!(SC2GunCompat.isSC2Gun(stack) && stack.getItem() instanceof GunItem)) return;

        LOGGER.debug("onHoldGun for {}", stack.getItem());

        if (hasCustomTacAnim(maid)) {
            cir.setReturnValue(false);
            return;
        }

        if (armLeft != null) {
            armLeft.setRotateAngleX(-1.75f);
            armLeft.setRotateAngleY(0.5f);
        }
        if (armRight != null) {
            armRight.setRotateAngleX(-1.65f);
            armRight.setRotateAngleY(-0.174f);
        }
        cir.setReturnValue(true);
    }

    @Inject(method = "playGunMainAnimation", at = @At("HEAD"), cancellable = true, remap = false)
    private static void onPlayGunMainAnimation(IMaid maid, AnimationEvent<GeckoMaidEntity<?>> event, String animationName, ILoopType loopType, CallbackInfoReturnable<PlayState> cir) {
        if (maid == null) return;
        ItemStack stack = maid.asEntity().getMainHandItem();
        if (!(SC2GunCompat.isSC2Gun(stack) && stack.getItem() instanceof GunItem)) return;

        LOGGER.info("[SC2TLM] playGunMainAnimation: not cancelling for SC2 gun, letting original predicateMain handle idle");
    }

    @Inject(method = "playGunHoldAnimation", at = @At("HEAD"), cancellable = true, remap = false)
    private static void onPlayGunHoldAnimation(ItemStack mainHandItem, AnimationEvent<GeckoMaidEntity<?>> event, CallbackInfoReturnable<PlayState> cir) {
        LOGGER.info("[SC2TLM] playGunHoldAnimation called for {}", mainHandItem.isEmpty() ? "empty" : mainHandItem.getItem());
        if (!(SC2GunCompat.isSC2Gun(mainHandItem) && mainHandItem.getItem() instanceof GunItem)) return;

        LOGGER.debug("playGunHoldAnimation: IS SC2 GUN! Playing animation...");

        GeckoMaidEntity<?> animatable = event.getAnimatableEntity();
        IMaid maid = animatable.getMaid();
        if (maid == null) {
            LOGGER.debug("maid is null, STOP");
            cir.setReturnValue(PlayState.STOP);
            return;
        }

        Mob entity = maid.asEntity();
        if (((EntityMaid) entity).isMaidInSittingPose()) {
            cir.setReturnValue(PlayState.CONTINUE);
            return;
        }
        String animType = SC2GunCompat.getAnimationGunType(mainHandItem);
        LOGGER.debug("animType={} for {}", animType, mainHandItem.getItem());

        boolean isReloading = mainHandItem.getTag() != null && mainHandItem.getTag().contains("scg2tlm:reloading");
        if (isReloading && !SC2GunCompat.isMinigunGun(mainHandItem)) {
            LOGGER.debug("reloading, prefix=tac:reload:");
            playGunAnim(event, animatable, mainHandItem, animType, "tac:reload:");
            cir.setReturnValue(PlayState.CONTINUE);
            return;
        }

        if (entity.isSwimming() || entity.getPose() == Pose.SWIMMING) {
            float limbSwing = Math.abs(event.getLimbSwingAmount());
            String climbPrefix = limbSwing > 0.05 ? "tac:climb:" : "tac:climbing:";
            LOGGER.debug("swimming, prefix={}", climbPrefix);
            playGunAnim(event, animatable, mainHandItem, animType, climbPrefix);
            cir.setReturnValue(PlayState.CONTINUE);
            return;
        }

        if (entity.onGround() && entity.isSprinting()) {
            LOGGER.debug("sprinting, prefix=tac:run:");
            playGunAnim(event, animatable, mainHandItem, animType, "tac:run:");
            cir.setReturnValue(PlayState.CONTINUE);
            return;
        }

        if (entity.swinging) {
            LOGGER.debug("swinging, prefix=tac:hold:fire:");
            playGunAnim(event, animatable, mainHandItem, animType, "tac:hold:fire:");
            cir.setReturnValue(PlayState.CONTINUE);
            return;
        }

        if (entity.onGround() && entity.getTarget() != null) {
            LOGGER.debug("in combat, prefix=tac:aim:");
            playGunAnim(event, animatable, mainHandItem, animType, "tac:aim:");
            cir.setReturnValue(PlayState.CONTINUE);
            return;
        }

        LOGGER.debug("standing, prefix=tac:hold:");
        playGunAnim(event, animatable, mainHandItem, animType, "tac:hold:");
        cir.setReturnValue(PlayState.CONTINUE);
    }

    private static void playGunAnim(AnimationEvent<GeckoMaidEntity<?>> event, GeckoMaidEntity<?> animatable, ItemStack stack, String animType, String prefix) {
        ILoopType loopType = prefix.equals("tac:reload:")
                ? ILoopType.EDefaultLoopTypes.HOLD_ON_LAST_FRAME
                : ILoopType.EDefaultLoopTypes.LOOP;
        ResourceLocation modelId = animatable.getAnimationFileLocation();
        LOGGER.info("[SC2TLM] playGunAnim: modelId={}, prefix={}, animType={}", modelId, prefix, animType);
        ConditionTAC conditionTAC = ConditionManager.getTAC(modelId);
        if (conditionTAC != null) {
            LOGGER.info("[SC2TLM] ConditionTAC found for model {}", modelId);
            String name = conditionTAC.doTest(stack, prefix);
            if (StringUtils.isNoneBlank(name)) {
                LOGGER.info("[SC2TLM] ConditionTAC returned name={}", name);
                SC2GunCompat.setAnimOnce(event.getController(), name, loopType);
                return;
            }
            LOGGER.info("[SC2TLM] ConditionTAC returned blank, falling through");
        } else {
            LOGGER.info("[SC2TLM] No ConditionTAC for model {}", modelId);
        }

        AnimationFile animationFile = GeckoLibCache.getInstance().getAnimations().get(modelId);
        if (SC2GunCompat.isMinigunGun(stack)) {
            String minigunName = prefix.substring(0, prefix.length() - 1) + "$tacz:minigun";
            LOGGER.info("[SC2TLM] Trying minigun animation: {}", minigunName);
            if (animationFile != null && animationFile.animations().containsKey(minigunName)) {
                SC2GunCompat.setAnimOnce(event.getController(), minigunName, loopType);
                return;
            }
            LOGGER.info("[SC2TLM] Minigun animation '{}' not found, falling back to rifle", minigunName);
        }

        String fullName = prefix + animType;
        LOGGER.info("[SC2TLM] Playing animation: {}", fullName);
        SC2GunCompat.setAnimOnce(event.getController(), fullName, loopType);
    }
}
