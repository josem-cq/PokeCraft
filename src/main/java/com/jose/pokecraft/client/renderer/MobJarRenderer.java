package com.jose.pokecraft.client.renderer;

import com.jose.pokecraft.block.MobJarBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.entity.Entity;

public class MobJarRenderer implements BlockEntityRenderer<MobJarBlockEntity> {
    public MobJarRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(MobJarBlockEntity blockEntity, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        Entity entity = blockEntity.getRenderEntity();
        if (entity != null) {
            poseStack.pushPose();
            // Centrar perfectamente el mob dentro del tarro
            poseStack.translate(0.5D, 0.15D, 0.5D);
            
            // Escalar para ajustar al tamaño del tarro
            float scale = 0.40F;
            poseStack.scale(scale, scale, scale);

            // Congelar rotaciones y animaciones para evitar temblores
            entity.setXRot(0.0F);
            entity.setYRot(0.0F);
            entity.yRotO = 0.0F;
            entity.xRotO = 0.0F;

            Minecraft.getInstance().getEntityRenderDispatcher().render(
                entity, 
                0.0D, 0.0D, 0.0D, 
                0.0F, 0.0F, 
                poseStack, bufferSource, packedLight
            );
            
            poseStack.popPose();
        }
    }
}