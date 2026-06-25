package com.scg2tlm.elmomod.mixin;

import com.github.tartaricacid.touhoulittlemaid.api.entity.IMaid;
import com.github.tartaricacid.touhoulittlemaid.client.animation.gecko.AnimationManager;
import com.github.tartaricacid.touhoulittlemaid.client.animation.gecko.condition.ConditionManager;
import com.github.tartaricacid.touhoulittlemaid.client.animation.gecko.condition.ConditionTAC;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.client.entity.GeckoMaidEntity;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.core.PlayState;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.core.builder.AnimationBuilder;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.core.builder.ILoopType;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.core.event.predicate.AnimationEvent;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.file.AnimationFile;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.resource.GeckoLibCache;
import com.scg2tlm.elmomod.compat.SC2GunCompat;
import com.scg2tlm.elmomod.mixin.EntityMaidTaskDataInvoker;
import net.minecraft.nbt.CompoundTag;
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

@Mixin(AnimationManager.class)
public class AnimationManagerMixin {
    private static final Logger LOGGER = LoggerFactory.getLogger("SCG2TLM");

    @Inject(method = "predicateMain", at = @At("HEAD"), cancellable = true, remap = false)
    private void onPredicateMain(AnimationEvent<GeckoMaidEntity<?>> event, CallbackInfoReturnable<PlayState> cir) {
        GeckoMaidEntity<?> animatable = event.getAnimatableEntity();
        if (animatable == null) return;
        IMaid maid = animatable.getMaid();
        if (maid == null) return;
        ItemStack stack = maid.asEntity().getMainHandItem();
        if (!(SC2GunCompat.isSC2Gun(stack) && stack.getItem() instanceof GunItem)) return;

        Mob entity = maid.asEntity();
        if (entity.isSleeping() || ((EntityMaid) entity).isMaidInSittingPose() || entity.isPassenger() || entity.isSwimming()) {
            return;
        }

        ResourceLocation modelId = animatable.getAnimationFileLocation();
        AnimationFile animationFile = GeckoLibCache.getInstance().getAnimations().get(modelId);
        if (animationFile == null) return;

        boolean isMoving = entity.onGround() && Math.abs(event.getLimbSwingAmount()) > 0.05f;
        String animName = isMoving ? "tac:walk" : "tac:idle";
        if (animationFile.animations().containsKey(animName)) {
            LOGGER.info("[SC2TLM] predicateMain: playing {}", animName);
            event.getController().setAnimation(new AnimationBuilder().addAnimation(animName, ILoopType.EDefaultLoopTypes.LOOP));
            cir.setReturnValue(PlayState.CONTINUE);
        }
    }

    @Inject(method = "predicateSwing", at = @At("HEAD"), cancellable = true, remap = false)
    private void onPredicateSwing(AnimationEvent<GeckoMaidEntity<?>> event, CallbackInfoReturnable<PlayState> cir) {
        GeckoMaidEntity<?> animatable = event.getAnimatableEntity();
        if (animatable == null) return;
        IMaid maid = animatable.getMaid();
        if (maid == null) return;
        if (maid.asEntity().swinging) {
            ItemStack stack = maid.asEntity().getMainHandItem();
            if (SC2GunCompat.isSC2Gun(stack) && stack.getItem() instanceof GunItem) {
                LOGGER.debug("predicateSwing: SC2 gun, STOP");
                cir.setReturnValue(PlayState.STOP);
            }
        }
    }

    @Inject(method = "predicateUse", at = @At("HEAD"), cancellable = true, remap = false)
    private void onPredicateUse(AnimationEvent<GeckoMaidEntity<?>> event, CallbackInfoReturnable<PlayState> cir) {
        GeckoMaidEntity<?> animatable = event.getAnimatableEntity();
        if (animatable == null) return;
        IMaid maid = animatable.getMaid();
        if (maid == null) return;
        if (maid.asEntity().isUsingItem()) {
            ItemStack stack = maid.asEntity().getMainHandItem();
            if (SC2GunCompat.isSC2Gun(stack) && stack.getItem() instanceof GunItem) {
                LOGGER.debug("predicateUse: SC2 gun, STOP");
                cir.setReturnValue(PlayState.STOP);
            }
        }
    }

    @Inject(method = "predicateMainhandHold", at = @At("HEAD"), cancellable = true, remap = false)
    private void onPredicateMainhandHold(AnimationEvent<GeckoMaidEntity<?>> event, CallbackInfoReturnable<PlayState> cir) {
        GeckoMaidEntity<?> animatable = event.getAnimatableEntity();
        if (animatable == null) return;
        IMaid maid = animatable.getMaid();
        if (maid == null) return;

        Mob entity = maid.asEntity();
        ItemStack stack = entity.getMainHandItem();
        if (!(SC2GunCompat.isSC2Gun(stack) && stack.getItem() instanceof GunItem)) return;
        if (((EntityMaid) entity).isMaidInSittingPose()) return;

        String animType = SC2GunCompat.getAnimationGunType(stack);
        boolean isInCombat = ((EntityMaid) entity).isSwingingArms();
        boolean isFiring = entity.swinging && entity.swingTime > 0;

        LOGGER.info("[SC2TLM] predicateMainhandHold for {}, combat={}, swinging={}, swingTime={}", stack.getItem(), isInCombat, entity.swinging, entity.swingTime);

        CompoundTag taskData = ((EntityMaidTaskDataInvoker) entity).invokeGetSyncTaskData();
        boolean isReloading = taskData.getBoolean("scg2tlm:reloading");

        String prefix;
        if (isReloading && !SC2GunCompat.isMinigunGun(stack)) {
            prefix = "tac:reload:";
        } else if (isFiring) {
            prefix = "tac:hold:fire:";
        } else if (entity.isSwimming() || entity.getPose() == Pose.SWIMMING) {
            float limbSwing = Math.abs(event.getLimbSwingAmount());
            prefix = limbSwing > 0.05 ? "tac:climb:" : "tac:climbing:";
        } else if (entity.onGround() && entity.isSprinting()) {
            prefix = "tac:run:";
        } else if (entity.onGround() && isInCombat && !isFiring) {
            prefix = "tac:aim:";
        } else {
            prefix = "tac:hold:";
        }

        ILoopType loopType = prefix.equals("tac:reload:")
                ? ILoopType.EDefaultLoopTypes.HOLD_ON_LAST_FRAME
                : ILoopType.EDefaultLoopTypes.LOOP;

        ResourceLocation modelId = animatable.getAnimationFileLocation();
        LOGGER.info("[SC2TLM] modelId={}, prefix={}, animType={}", modelId, prefix, animType);

        ConditionTAC conditionTAC = ConditionManager.getTAC(modelId);
        if (conditionTAC != null) {
            LOGGER.info("[SC2TLM] ConditionTAC FOUND for model {}", modelId);
            String name = conditionTAC.doTest(stack, prefix);
            LOGGER.info("[SC2TLM] ConditionTAC.doTest returned: '{}'", name);
            if (StringUtils.isNoneBlank(name)) {
                LOGGER.info("[SC2TLM] Playing condition animation: {}", name);
                SC2GunCompat.setAnimOnce(event.getController(), name, loopType);
                cir.setReturnValue(PlayState.CONTINUE);
                return;
            }
        } else {
            LOGGER.info("[SC2TLM] No ConditionTAC for model {}", modelId);
        }

        AnimationFile animationFile = GeckoLibCache.getInstance().getAnimations().get(modelId);
        String animName;
        if (SC2GunCompat.isMinigunGun(stack)) {
            animName = prefix.substring(0, prefix.length() - 1) + "$tacz:minigun";
            LOGGER.info("[SC2TLM] Trying minigun animation: {}", animName);
            if (animationFile != null && animationFile.animations().containsKey(animName)) {
                SC2GunCompat.setAnimOnce(event.getController(), animName, loopType);
                cir.setReturnValue(PlayState.CONTINUE);
                return;
            }
            LOGGER.info("[SC2TLM] Minigun animation '{}' not found, falling back to rifle", animName);
        }
        animName = prefix + animType;
        LOGGER.info("[SC2TLM] Fallback animation: {}", animName);
        SC2GunCompat.setAnimOnce(event.getController(), animName, loopType);
        cir.setReturnValue(PlayState.CONTINUE);
    }
}
