package com.tuusuario.pokecraft.block;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class MobJarBlockEntity extends BlockEntity {
    private CompoundTag storedEntityTag;

    public MobJarBlockEntity(BlockPos pos, BlockState state) {
        super(com.tuusuario.pokecraft.PokeCraftMod.BLOCK_ENTITIES.get("mob_jar_be").get(), pos, state);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        if (tag.contains("StoredEntity")) {
            this.storedEntityTag = tag.getCompound("StoredEntity");
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        if (this.storedEntityTag != null) {
            tag.put("StoredEntity", this.storedEntityTag);
        }
    }

    public CompoundTag getStoredEntityTag() { return storedEntityTag; }
    public void setStoredEntityTag(CompoundTag tag) { this.storedEntityTag = tag; setChanged(); }
}