package com.jose.pokecraft.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

public class MobJarBlock extends BaseEntityBlock {
    public static final MapCodec<MobJarBlock> CODEC = simpleCodec(MobJarBlock::new);

    public MobJarBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new MobJarBlockEntity(pos, state);
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide && !player.getAbilities().instabuild) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof MobJarBlockEntity jarBe && jarBe.hasStoredEntity()) {
                ItemStack jarStack = new ItemStack(this);
                jarStack.set(DataComponents.CUSTOM_DATA, CustomData.of(jarBe.getStoredEntityTag()));
                if (jarBe.hasCustomName()) {
                    jarStack.set(DataComponents.CUSTOM_NAME, jarBe.getCustomName());
                }
                Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), jarStack);
            }
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    public ItemStack getCloneItemStack(net.minecraft.world.level.LevelReader level, BlockPos pos, BlockState state) {
        ItemStack stack = super.getCloneItemStack(level, pos, state);
        if (level.getBlockEntity(pos) instanceof MobJarBlockEntity jarBe && jarBe.hasStoredEntity()) {
            stack.set(DataComponents.CUSTOM_DATA, CustomData.of(jarBe.getStoredEntityTag()));
            if (jarBe.hasCustomName()) {
                stack.set(DataComponents.CUSTOM_NAME, jarBe.getCustomName());
            }
        }
        return stack;
    }
}