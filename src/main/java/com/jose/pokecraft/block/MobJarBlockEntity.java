package com.jose.pokecraft.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class MobJarBlockEntity extends BlockEntity {
    private CompoundTag storedEntityTag;
    private Entity cachedEntity;

    public MobJarBlockEntity(BlockPos pos, BlockState state) {
        super(com.jose.pokecraft.PokeCraftMod.MOB_JAR_BE.get(), pos, state);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (storedEntityTag != null) {
            tag.put("StoredEntity", storedEntityTag);
        }
    }

    @Override
    public void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("StoredEntity")) {
            storedEntityTag = tag.getCompound("StoredEntity");
            cachedEntity = null;
        }
    }

    public boolean hasStoredEntity() {
        return storedEntityTag != null;
    }

    public CompoundTag getStoredEntityTag() {
        return storedEntityTag;
    }

    public boolean hasCustomName() {
        return storedEntityTag != null && storedEntityTag.contains("CustomName");
    }

    public Component getCustomName() {
        if (hasCustomName()) {
            try {
                return Component.Serializer.fromJson(storedEntityTag.getString("CustomName"), level != null ? level.registryAccess() : null);
            } catch (Exception ignored) {}
        }
        return null;
    }

    public Entity getRenderEntity() {
        if (cachedEntity == null && storedEntityTag != null && level != null) {
            cachedEntity = EntityType.loadEntityRecursive(storedEntityTag, level, e -> e);
        }
        return cachedEntity;
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