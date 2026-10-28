package com.jose.pokecraft.block;

import com.jose.pokecraft.item.PokeballItem;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import java.util.Properties;

import org.jetbrains.annotations.Nullable;

public class MobJarBlock extends Block implements EntityBlock {
    public MobJarBlock(Properties properties) {
        super(properties);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new MobJarBlockEntity(pos, state);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, net.minecraft.world.InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide) return InteractionResult.SUCCESS;

        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof MobJarBlockEntity jar) {
            ItemStack held = player.getItemInHand(hand);

            // Introduce el mob desde la Poké Ball hacia el tarro
            if (isPokeballWithEntity(held) && jar.getStoredEntityTag() == null) {
                CompoundTag entityData = held.getTag().getCompound("CapturedEntity");
                jar.setStoredEntityTag(entityData);
                held.removeTag("CapturedEntity"); // Vacía la pokebola
                jar.setChanged();
                level.sendBlockUpdated(pos, state, state, 3);
                return InteractionResult.CONSUME;
            } 
            // Extrae el mob del tarro de vuelta a una Poké Ball vacía (o mano)
            else if (jar.getStoredEntityTag() != null && held.isEmpty()) {
                ItemStack emptyBall = new ItemStack(com.jose.pokecraft.PokeCraftMod.POKEBALL.get());
                emptyBall.getOrCreateTag().put("CapturedEntity", jar.getStoredEntityTag());
                
                if (!player.addItem(emptyBall)) {
                    player.drop(emptyBall, false);
                }

                jar.setStoredEntityTag(null);
                jar.setChanged();
                level.sendBlockUpdated(pos, state, state, 3);
                return InteractionResult.CONSUME;
            }
        }
        return InteractionResult.PASS;
    }

    private boolean isPokeballWithEntity(ItemStack stack) {
        return stack.getItem() instanceof PokeballItem && stack.hasTag() && stack.getTag().contains("CapturedEntity");
    }
}