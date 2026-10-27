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
import net.minecraft.world.item.ItemStack;
import top.ribs.scguns.item.GunItem;
import org.apache.commons.lang3.StringUtils;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AnimationManager.class)
public class AnimationManagerMixin {

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
        if (maid.asEntity().isUsingItem() && maid.asEntity().getUseItem().getItem() instanceof GunItem) {
            cir.setReturnValue(PlayState.STOP);
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

        CompoundTag taskData = ((EntityMaidTaskDataInvoker) entity).invokeGetSyncTaskData();
        boolean isReloading = taskData.getBoolean("scg2tlm:reloading");

        if (entity.isSwimming()) {
            return;
        }

        ResourceLocation modelId = animatable.getAnimationFileLocation();
        AnimationFile animationFile = GeckoLibCache.getInstance().getAnimations().get(modelId);

        String prefix;
        if (isReloading && !SC2GunCompat.isMinigunGun(stack)) {
            prefix = "tac:reload:";
            String reloadAnim = prefix + animType;
            if (animationFile == null || !animationFile.animations().containsKey(reloadAnim)) {
                prefix = "tac:hold:";
            }
        } else if (isFiring) {
            prefix = "tac:hold:fire:";
        } else if (entity.onGround() && entity.isSprinting()) {
            prefix = "tac:run:";
        } else if (entity.onGround() && isInCombat && !isFiring) {
            prefix = "tac:aim:";
        } else {
            prefix = "tac:hold:";
        }

        ILoopType loopType = ILoopType.EDefaultLoopTypes.LOOP;

        ConditionTAC conditionTAC = ConditionManager.getTAC(modelId);
        if (conditionTAC != null) {
            String name = conditionTAC.doTest(stack, prefix);
            if (StringUtils.isNoneBlank(name)) {
                SC2GunCompat.setAnimOnce(event.getController(), name, loopType);
                cir.setReturnValue(PlayState.CONTINUE);
                return;
            }
        }

        String animName;
        if (SC2GunCompat.isMinigunGun(stack)) {
            animName = prefix.substring(0, prefix.length() - 1) + "$tacz:minigun";
            if (animationFile != null && animationFile.animations().containsKey(animName)) {
                SC2GunCompat.setAnimOnce(event.getController(), animName, loopType);
                cir.setReturnValue(PlayState.CONTINUE);
                return;
            }
        }
        animName = prefix + animType;
        if (animationFile == null || !animationFile.animations().containsKey(animName)) {
            if (prefix.equals("tac:aim:")) {
                animName = "tac:hold:" + animType;
                if (animationFile != null && animationFile.animations().containsKey(animName)) {
                    SC2GunCompat.setAnimOnce(event.getController(), animName, loopType);
                    cir.setReturnValue(PlayState.CONTINUE);
                }
            }
            return;
        }
        SC2GunCompat.setAnimOnce(event.getController(), animName, loopType);
        cir.setReturnValue(PlayState.CONTINUE);
    }
}
