package com.jose.pokecraft.client.renderer;

import com.jose.pokecraft.block.MobJarBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;

public class MobJarRenderer implements BlockEntityRenderer<MobJarBlockEntity> {

    public MobJarRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(MobJarBlockEntity pBlockEntity, float pPartialTick, PoseStack pPoseStack, MultiBufferSource pBufferSource, int pPackedLight, int pPackedOverlay) {
        CompoundTag entityTag = pBlockEntity.getStoredEntityTag();
        if (entityTag == null || entityTag.isEmpty()) {
            return;
        }

        Level level = pBlockEntity.getLevel();
        if (level == null) return;

        pPoseStack.pushPose();
        // Centramos la entidad dentro del bloque (0.5 en X y Z, 0.1 en Y para que repose en la base del tarro)
        pPoseStack.translate(0.5D, 0.1D, 0.5D);

        // Instanciamos la entidad desde su NBT guardado
        Entity entity = net.minecraft.world.entity.EntityType.loadEntityRecursive(entityTag, level, e -> e);

        if (entity != null) {
            // Escalamos la entidad para que su altura máxima sea de aproximadamente 0.7 bloques
            float height = entity.getBbHeight();
            if (height > 0.0f) {
                float scale = 0.7f / height;
                // Limitamos la escala para que entidades extremadamente pequeñas o grandes no se rompan visualmente
                scale = Math.min(scale, 0.6f);
                pPoseStack.scale(scale, scale, scale);
            }

            // Si es un mob con IA, desactivamos su movimiento para que se quede estático
            if (entity instanceof Mob mob) {
                mob.setNoAi(true);
            }

            entity.setYRot(0.0f);
            entity.yRotO = 0.0f;
            entity.setXRot(0.0f);
            entity.xRotO = 0.0f;

            // Renderizamos la entidad dentro del mundo usando el despachador de render de Minecraft
            int packedLight = 15728880; // Luz máxima para que el mob dentro del tarro no se vea oscuro
            Minecraft.getInstance().getEntityRenderDispatcher().render(
                entity, 
                0.0D, 0.0D, 0.0D, 
                0.0F, 
                pPartialTick, 
                pPoseStack, 
                pBufferSource, 
                packedLight
            );
        }

        pPoseStack.popPose();
    }
}