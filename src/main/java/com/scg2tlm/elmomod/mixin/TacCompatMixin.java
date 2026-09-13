package com.scg2tlm.elmomod.mixin;

import com.github.tartaricacid.touhoulittlemaid.api.entity.IMaid;
import com.github.tartaricacid.touhoulittlemaid.client.animation.gecko.condition.ConditionManager;
import com.github.tartaricacid.touhoulittlemaid.client.animation.gecko.condition.ConditionTAC;
import com.github.tartaricacid.touhoulittlemaid.client.animation.script.ModelRendererWrapper;
import com.github.tartaricacid.touhoulittlemaid.client.entity.GeckoMaidEntity;
import com.github.tartaricacid.touhoulittlemaid.compat.gun.tacz.TacCompat;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.core.PlayState;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.core.builder.ILoopType;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.core.event.predicate.AnimationEvent;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.file.AnimationFile;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.resource.GeckoLibCache;
import com.scg2tlm.elmomod.compat.SC2GunCompat;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import top.ribs.scguns.item.GunItem;
import org.apache.commons.lang3.StringUtils;
import org.spongepowered.asm.mixin.Mixin;

import java.util.Map;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(TacCompat.class)
public class TacCompatMixin {

    private static boolean hasCustomTacAnim(IMaid maid) {
        if (maid == null) return false;
        if (!(maid.asEntity() instanceof EntityMaid entity)) return false;
        String modelIdStr = entity.getModelId();
        if (modelIdStr == null || !modelIdStr.contains(":")) return false;
        ResourceLocation modelId = ResourceLocation.tryParse(modelIdStr);
        if (modelId == null) return false;

        if (ConditionManager.getTAC(modelId) != null) return true;

        String ns = modelId.getNamespace();
        String path = modelId.getPath();
        Map<ResourceLocation, AnimationFile> allAnims = GeckoLibCache.getInstance().getAnimations();

        // Try multiple possible animation file path patterns
        String[] possiblePaths = {
                ns + ":animation/" + path + ".animation.json",
                ns + ":animations/" + path + ".animation.json",
                ns + ":animations/" + path + "/" + path + ".animation.json",
                ns + ":animations/entity/" + path + ".animation.json"
        };
        for (String p : possiblePaths) {
            ResourceLocation loc = ResourceLocation.tryParse(p);
            if (loc == null) continue;
            if (ConditionManager.getTAC(loc) != null) return true;
            AnimationFile af = allAnims.get(loc);
            if (af != null && af.animations().keySet().stream().anyMatch(k -> k.startsWith("tac:"))) {
                return true;
            }
        }

        if (allAnims == null) return false;
        for (Map.Entry<ResourceLocation, AnimationFile> entry : allAnims.entrySet()) {
            if (entry.getKey().getNamespace().equals(ns)
                    && entry.getKey().getPath().contains(path)
                    && entry.getValue().animations().keySet().stream().anyMatch(k -> k.startsWith("tac:"))) {
                return true;
            }
        }
        return false;
    }

    @Inject(method = "isGun", at = @At("HEAD"), cancellable = true, remap = false)
    private static void onIsGun(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (SC2GunCompat.isSC2Gun(stack) && stack.getItem() instanceof GunItem) {
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
            if (id != null) {
                cir.setReturnValue(id);
            }
        }
    }

    @Inject(method = "onHoldGun", at = @At("HEAD"), cancellable = true, remap = false)
    private static void onOnHoldGun(IMaid maid, ModelRendererWrapper armLeft, ModelRendererWrapper armRight, CallbackInfoReturnable<Boolean> cir) {
        if (maid == null) return;
        ItemStack stack = maid.asEntity().getMainHandItem();
        if (!SC2GunCompat.isSC2Gun(stack) || !(stack.getItem() instanceof GunItem)) return;

        if (hasCustomTacAnim(maid)) {
            cir.setReturnValue(false);
            return;
        }

        boolean isTwoHanded = "rifle".equals(SC2GunCompat.getAnimationGunType(stack));
        if (armLeft != null) {
            if (isTwoHanded) {
                armLeft.setRotateAngleX(-1.75f);
                armLeft.setRotateAngleY(0.5f);
            } else {
                armLeft.setRotateAngleX(-0.3f);
                armLeft.setRotateAngleY(0.3f);
            }
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
        Mob entity = maid.asEntity();
        ItemStack stack = entity.getMainHandItem();
        if (!SC2GunCompat.isSC2Gun(stack) || !(stack.getItem() instanceof GunItem)) return;
        if (entity.isSleeping() || entity instanceof EntityMaid maidEntity && (maidEntity.isMaidInSittingPose() || entity.isPassenger())) {
            return;
        }
        // Models with custom tac:* animations (winefox/GF2) have their own
        // tac:walk/tac:idle for gun-holding lower body stance — let original run.
        // Models without: STOP prevents default tac:walk from conflicting with hold controller.
        if (!hasCustomTacAnim(maid)) {
            cir.setReturnValue(PlayState.STOP);
        }
    }

    @Inject(method = "playGunHoldAnimation", at = @At("HEAD"), cancellable = true, remap = false)
    private static void onPlayGunHoldAnimation(ItemStack mainHandItem, AnimationEvent<GeckoMaidEntity<?>> event, CallbackInfoReturnable<PlayState> cir) {
        if (!SC2GunCompat.isSC2Gun(mainHandItem) || !(mainHandItem.getItem() instanceof GunItem)) return;

        GeckoMaidEntity<?> animatable = event.getAnimatableEntity();
        IMaid maid = animatable.getMaid();
        if (maid == null) {
            cir.setReturnValue(PlayState.STOP);
            return;
        }

        Mob entity = maid.asEntity();

        String animType = SC2GunCompat.getAnimationGunType(mainHandItem);

        boolean isReloading = mainHandItem.getTag() != null && mainHandItem.getTag().contains("scg2tlm:reloading");
        if (isReloading && !SC2GunCompat.isMinigunGun(mainHandItem)) {
            playGunAnim(event, animatable, mainHandItem, animType, "tac:reload:");
            cir.setReturnValue(PlayState.CONTINUE);
            return;
        }

        if (entity.isSleeping() || entity.isPassenger() || entity.isSwimming()) {
            return;
        }

        if (entity.onGround() && entity.isSprinting()) {
            playGunAnim(event, animatable, mainHandItem, animType, "tac:run:");
            cir.setReturnValue(PlayState.CONTINUE);
            return;
        }

        if (entity.swinging) {
            playGunAnim(event, animatable, mainHandItem, animType, "tac:hold:fire:");
            cir.setReturnValue(PlayState.CONTINUE);
            return;
        }

        if (entity.onGround() && entity.getTarget() != null) {
            playGunAnim(event, animatable, mainHandItem, animType, "tac:aim:");
            cir.setReturnValue(PlayState.CONTINUE);
            return;
        }

        playGunAnim(event, animatable, mainHandItem, animType, "tac:hold:");
        cir.setReturnValue(PlayState.CONTINUE);
    }

    private static void playGunAnim(AnimationEvent<GeckoMaidEntity<?>> event, GeckoMaidEntity<?> animatable, ItemStack stack, String animType, String prefix) {
        ResourceLocation modelId = animatable.getAnimationFileLocation();
        AnimationFile animationFile = GeckoLibCache.getInstance().getAnimations().get(modelId);

        String fullName = prefix + animType;
        if (prefix.equals("tac:reload:") && (animationFile == null || !animationFile.animations().containsKey(fullName))) {
            fullName = "tac:hold:" + animType;
            prefix = "tac:hold:";
        }

        ILoopType loopType = ILoopType.EDefaultLoopTypes.LOOP;

        ConditionTAC conditionTAC = ConditionManager.getTAC(modelId);
        if (conditionTAC != null) {
            String name = conditionTAC.doTest(stack, prefix);
            if (StringUtils.isNoneBlank(name)) {
                SC2GunCompat.setAnimOnce(event.getController(), name, loopType);
                return;
            }
        }

        if (SC2GunCompat.isMinigunGun(stack)) {
            String minigunName = prefix.substring(0, prefix.length() - 1) + "$tacz:minigun";
            if (animationFile != null && animationFile.animations().containsKey(minigunName)) {
                SC2GunCompat.setAnimOnce(event.getController(), minigunName, loopType);
                return;
            }
        }

        SC2GunCompat.setAnimOnce(event.getController(), fullName, loopType);
    }
}
