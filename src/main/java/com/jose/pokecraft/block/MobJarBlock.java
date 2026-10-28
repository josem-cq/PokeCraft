package com.jose.pokecraft.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class MobJarBlockEntity extends BlockEntity {
    private CompoundTag storedEntityTag;

    public MobJarBlockEntity(BlockPos pos, BlockState state) {
        super(com.jose.pokecraft.PokeCraftMod.MOB_JAR_BE.get(), pos, state);
    }

    @Override
    public void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("StoredEntity")) {
            this.storedEntityTag = tag.getCompound("StoredEntity");
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (this.storedEntityTag != null) {
            tag.put("StoredEntity", this.storedEntityTag);
        }
    }

    public CompoundTag getStoredEntityTag() { return storedEntityTag; }
    public void setStoredEntityTag(CompoundTag tag) { this.storedEntityTag = tag; setChanged(); }
}