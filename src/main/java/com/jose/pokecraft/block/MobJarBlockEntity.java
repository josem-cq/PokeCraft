package com.jose.pokecraft.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class MobJarBlockEntity extends BlockEntity {
    private CompoundTag storedEntityTag;
    private Component customName;
    private Entity cachedEntity;
    private float cachedScale = -1.0f; // Escala guardada para evitar recalcularla cada frame

    public MobJarBlockEntity(BlockPos pos, BlockState state) {
        super(com.jose.pokecraft.PokeCraftMod.MOB_JAR_BE.get(), pos, state);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (storedEntityTag != null) {
            tag.put("StoredEntity", storedEntityTag);
        }
        if (customName != null) {
            tag.putString("CustomName", Component.Serializer.toJson(customName, registries));
        }
    }

    @Override
    public void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("StoredEntity")) {
            this.storedEntityTag = tag.getCompound("StoredEntity");
            this.cachedEntity = null;
            this.cachedScale = -1.0f;
        }
        if (tag.contains("CustomName")) {
            try {
                this.customName = Component.Serializer.fromJson(tag.getString("CustomName"), registries);
            } catch (Exception ignored) {}
        }
    }

    public boolean hasStoredEntity() {
        return storedEntityTag != null;
    }

    public CompoundTag getStoredEntityTag() {
        return storedEntityTag;
    }

    public void setStoredEntityTag(CompoundTag tag) {
        this.storedEntityTag = tag;
        this.cachedEntity = null;
        this.cachedScale = -1.0f;
        setChanged();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    public boolean hasCustomName() {
        return customName != null;
    }

    public Component getCustomName() {
        return customName;
    }

    public void setCustomName(Component customName) {
        this.customName = customName;
        setChanged();
    }

    public Entity getRenderEntity() {
        if (cachedEntity == null && storedEntityTag != null && level != null) {
            cachedEntity = EntityType.loadEntityRecursive(storedEntityTag, level, e -> e);
            if (cachedEntity instanceof Mob mob) {
                mob.setNoAi(true);
            }
        }
        return cachedEntity;
    }

    // Calcula la escala una sola vez y la reutiliza para evitar temblores
    public float getScale() {
        if (cachedScale < 0.0f) {
            Entity entity = getRenderEntity();
            if (entity != null) {
                float height = entity.getBbHeight();
                if (height > 0.0f) {
                    cachedScale = Math.min(0.7f / height, 0.6f);
                } else {
                    cachedScale = 0.4f;
                }
            } else {
                cachedScale = 0.4f;
            }
        }
        return cachedScale;
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        saveAdditional(tag, registries);
        return tag;
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}