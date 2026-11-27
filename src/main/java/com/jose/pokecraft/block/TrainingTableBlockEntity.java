package com.jose.pokecraft.block;

import com.jose.pokecraft.PokeCraftMod;
import com.jose.pokecraft.menu.TrainingTableMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Guarda la bola con el mob que hay en la mesa. El nivel, la XP, los slots desbloqueados y el equipo NO se guardan
 * aquí: viajan dentro del NBT de la propia bola (CapturedEntity), así que sacar la bola nunca pierde nada.
 */
public class TrainingTableBlockEntity extends BlockEntity implements MenuProvider {
    private final SimpleContainer ball = new SimpleContainer(1) {
        @Override
        public void setChanged() {
            super.setChanged();
            TrainingTableBlockEntity.this.setChanged();
        }
    };

    public TrainingTableBlockEntity(BlockPos pos, BlockState state) {
        super(PokeCraftMod.TRAINING_TABLE_BE.get(), pos, state);
    }

    public SimpleContainer getBall() {
        return ball;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("Ball", ball.getItem(0).saveOptional(registries));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        ball.setItem(0, tag.contains("Ball") ? ItemStack.parseOptional(registries, tag.getCompound("Ball")) : ItemStack.EMPTY);
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.pokecraft.training_table");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new TrainingTableMenu(containerId, inventory, ball, ContainerLevelAccess.create(level, worldPosition));
    }
}
