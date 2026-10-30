package com.jose.pokecraft.block;

import com.jose.pokecraft.item.PokeballItem;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
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
        if (level.isClientSide) return InteractionResult.SUCCESS;

        if (level.getBlockEntity(pos) instanceof MobJarBlockEntity jarBE) {
            ItemStack heldItem = player.getItemInHand(player.getUsedItemHand());

            // 1. Si el tarro está vacío y el jugador usa una Pokéball llena -> INSERTAR MOB AL TARRO
            if (!jarBE.hasStoredEntity() && heldItem.getItem() instanceof PokeballItem) {
                CustomData customData = heldItem.get(DataComponents.CUSTOM_DATA);
                if (customData != null && customData.copyTag().contains("CapturedEntity")) {
                    CompoundTag entityTag = customData.copyTag().getCompound("CapturedEntity");
                    jarBE.setStoredEntityTag(entityTag);

                    if (heldItem.has(DataComponents.CUSTOM_NAME)) {
                        jarBE.setCustomName(heldItem.get(DataComponents.CUSTOM_NAME));
                    }

                    if (!player.getAbilities().instabuild) {
                        heldItem.shrink(1);
                    }

                    level.playSound(null, pos, SoundEvents.ITEM_FRAME_ADD_ITEM, SoundSource.BLOCKS, 1.0f, 1.0f);
                    return InteractionResult.SUCCESS;
                }
            }

            // 2. Si el tarro tiene un mob y el jugador usa la MANO VACÍA -> EXTRAER MOB A UNA POKÉBALL
            if (jarBE.hasStoredEntity() && heldItem.isEmpty()) {
                CompoundTag entityTag = jarBE.getStoredEntityTag();
                
                Item pokeballItem = com.jose.pokecraft.PokeCraftMod.POKEBALL.get();
                if (entityTag.contains("PokeballRegistryName")) {
                    Item foundItem = BuiltInRegistries.ITEM.get(net.minecraft.resources.ResourceLocation.parse(entityTag.getString("PokeballRegistryName")));
                    if (foundItem != null) {
                        pokeballItem = foundItem;
                    }
                }

                ItemStack filledPokeball = new ItemStack(pokeballItem);
                CompoundTag tag = filledPokeball.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
                tag.put("CapturedEntity", entityTag);
                filledPokeball.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));

                if (jarBE.hasCustomName()) {
                    filledPokeball.set(DataComponents.CUSTOM_NAME, jarBE.getCustomName());
                }

                if (!player.addItem(filledPokeball)) {
                    player.drop(filledPokeball, false);
                }

                jarBE.setStoredEntityTag(null);
                jarBE.setCustomName(null);

                level.playSound(null, pos, SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS, 1.0f, 1.0f);
                return InteractionResult.SUCCESS;
            }
        }
        return InteractionResult.PASS;
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