package com.jose.pokecraft.block;

import com.jose.pokecraft.menu.TrainingTableMenu;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * Mesa de Entrenamiento: inspecciona el nivel de un mob guardado en una bola, desbloquea slots y gestiona su equipo.
 *
 * Funciona como la mesa de crafteo: el bloque no guarda nada. Cada jugador abre su propio menú (varios a la vez) con
 * su propia bola, y al cerrarlo la bola vuelve a su inventario.
 */
public class TrainingTableBlock extends Block {
    public static final MapCodec<TrainingTableBlock> CODEC = simpleCodec(TrainingTableBlock::new);

    private static final Component TITLE = Component.translatable("container.pokecraft.training_table");

    public TrainingTableBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Nullable
    @Override
    protected MenuProvider getMenuProvider(BlockState state, Level level, BlockPos pos) {
        return new SimpleMenuProvider((containerId, inventory, player) ->
                new TrainingTableMenu(containerId, inventory, ContainerLevelAccess.create(level, pos)), TITLE);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (level.isClientSide) return InteractionResult.SUCCESS;
        player.openMenu(state.getMenuProvider(level, pos));
        return InteractionResult.CONSUME;
    }
}