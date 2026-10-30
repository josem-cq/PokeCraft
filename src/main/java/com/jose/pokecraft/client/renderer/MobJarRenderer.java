package com.jose.pokecraft.client.renderer;

import com.jose.pokecraft.block.MobJarBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

public class MobJarRenderer implements BlockEntityRenderer<MobJarBlockEntity> {
    public MobJarRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(MobJarBlockEntity blockEntity, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        Entity entity = blockEntity.getRenderEntity();
        if (entity != null) {
            poseStack.pushPose();
            
            // Centrar y posicionar en la base del tarro
            poseStack.translate(0.5D, 0.1D, 0.5D);
            
            // Obtener la escala fija calculada en el BlockEntity
            float scale = blockEntity.getScale();
            poseStack.scale(scale, scale, scale);

            // Congelar rotaciones y posiciones anteriores para evitar cualquier temblor
            entity.setXRot(0.0F);
            entity.setYRot(0.0F);
            entity.xRotO = 0.0F;
            entity.yRotO = 0.0F;
            entity.xo = entity.getX();
            entity.yo = entity.getY();
            entity.zo = entity.getZ();
            entity.xOld = entity.getX();
            entity.yOld = entity.getY();
            entity.zOld = entity.getZ();

            if (entity instanceof LivingEntity living) {
                living.yHeadRot = 0.0F;
                living.yHeadRotO = 0.0F;
                living.yBodyRot = 0.0F;
                living.yBodyRotO = 0.0F;
            }

            // Luz máxima para que el interior del tarro no se vea oscuro
            int maxLight = 15728880;

            // IMPORTANTE: Pasar 0.0F como partialTick evita la interpolación de animaciones por frame
            Minecraft.getInstance().getEntityRenderDispatcher().render(
                entity, 
                0.0D, 0.0D, 0.0D, 
                0.0F, 
                0.0F, 
                poseStack, 
                bufferSource, 
                maxLight
            );
            
            poseStack.popPose();
        }
    }
}