package com.jose.pokecraft.block;

import com.jose.pokecraft.item.PokeballItem;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
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
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        ItemStack stack = player.getMainHandItem();
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);

        if (stack.getItem() instanceof PokeballItem && customData != null && customData.copyTag().contains("CapturedEntity")) {
            if (level.getBlockEntity(pos) instanceof MobJarBlockEntity jarBE) {
                if (jarBE.getStoredEntityTag() == null) {
                    jarBE.setStoredEntityTag(customData.copyTag().getCompound("CapturedEntity"));
                    if (!player.isCreative()) {
                        stack.shrink(1);
                    }
                    return InteractionResult.SUCCESS;
                }
            }
        }
        return InteractionResult.PASS;
    }
}