package com.scg2tlm.elmomod.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.ribs.scguns.client.GunItemStackRenderer;

@Mixin(GunItemStackRenderer.class)
public class GunItemStackRendererMixin {
    @Inject(method = "renderByItem", at = @At("HEAD"), cancellable = true)
    private void onRenderByItem(ItemStack stack, ItemDisplayContext context, PoseStack pose, MultiBufferSource buffer, int light, int overlay, CallbackInfo ci) {
        if (context != ItemDisplayContext.GUI && context != ItemDisplayContext.FIXED) return;

        Minecraft mc = Minecraft.getInstance();
        BakedModel model = mc.getItemRenderer().getItemModelShaper().getItemModel(stack);
        TextureAtlasSprite sprite = model.getParticleIcon();

        VertexConsumer consumer = ItemRenderer.getFoilBufferDirect(buffer, RenderType.cutout(), true, stack.hasFoil());

        Matrix4f matrix = pose.last().pose();
        consumer.vertex(matrix, 0, 0, 0).color(255, 255, 255, 255).uv(sprite.getU0(), sprite.getV1()).overlayCoords(overlay).uv2(light).normal(pose.last().normal(), 0, 0, 1).endVertex();
        consumer.vertex(matrix, 1, 0, 0).color(255, 255, 255, 255).uv(sprite.getU1(), sprite.getV1()).overlayCoords(overlay).uv2(light).normal(pose.last().normal(), 0, 0, 1).endVertex();
        consumer.vertex(matrix, 1, 1, 0).color(255, 255, 255, 255).uv(sprite.getU1(), sprite.getV0()).overlayCoords(overlay).uv2(light).normal(pose.last().normal(), 0, 0, 1).endVertex();
        consumer.vertex(matrix, 0, 1, 0).color(255, 255, 255, 255).uv(sprite.getU0(), sprite.getV0()).overlayCoords(overlay).uv2(light).normal(pose.last().normal(), 0, 0, 1).endVertex();

        ci.cancel();
    }
}
