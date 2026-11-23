package com.jose.pokecraft.block;
 
import com.jose.pokecraft.item.CapturedBall;
import com.jose.pokecraft.item.CapturedMobStats;
import com.jose.pokecraft.item.PokeballItem;
import com.jose.pokecraft.pokedex.CaptureRules;
import com.jose.pokecraft.pokedex.PokedexManager;
import net.minecraft.network.chat.Component;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;
 
public class MobJarBlock extends BaseEntityBlock {
    public static final DirectionProperty HORIZONTAL_FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final MapCodec<MobJarBlock> CODEC = simpleCodec(MobJarBlock::new);
 
    public MobJarBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(HORIZONTAL_FACING, Direction.NORTH));
    }
 
    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }
 
    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(HORIZONTAL_FACING);
    }
 
    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState().setValue(HORIZONTAL_FACING, context.getHorizontalDirection().getOpposite());
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
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof MobJarBlockEntity jarBE) {
            CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
            if (customData != null) {
                CompoundTag tag = customData.copyTag();
                if (tag.contains("CapturedEntity")) {
                    jarBE.setStoredEntityTag(tag.getCompound("CapturedEntity"));
                } else if (tag.contains("id")) {
                    jarBE.setStoredEntityTag(tag);
                }
            }
            if (stack.has(DataComponents.CUSTOM_NAME)) {
                jarBE.setCustomName(stack.get(DataComponents.CUSTOM_NAME));
            }
        }
    }
 
    /**
     * Insertar un mob: clic derecho (sin Shift) con una bola llena sobre un tarro vacío.
     *
     * Se hace aquí, y no en useWithoutItem, porque este método recibe la mano y el ítem REALES del clic. Antes se
     * leía {@code player.getItemInHand(player.getUsedItemHand())}: getUsedItemHand() no es "la mano con la que
     * estoy haciendo clic" sino la mano del último objeto que el jugador empezó a USAR (comer, beber, escudo...) y
     * el juego no la restablece al terminar. Tras Shift + clic derecho con comida en la mano secundaria (el cliente
     * pasa a "usar objeto" y empieza a comer con esa mano) la mano "usada" se quedaba en OFF_HAND, el tarro miraba
     * la mano secundaria en vez de la bola y dejaba de aceptar mobs hasta reconectar.
     */
    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hitResult) {
        if (!(stack.getItem() instanceof PokeballItem)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (!(level.getBlockEntity(pos) instanceof MobJarBlockEntity jarBE) || jarBE.hasStoredEntity()) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }

        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        if (customData == null || !customData.copyTag().contains("CapturedEntity")) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        // Cliente: solo predice el resultado; todo el cambio real ocurre en el servidor.
        if (level.isClientSide) {
            return ItemInteractionResult.SUCCESS;
        }

        CompoundTag entityTag = customData.copyTag().getCompound("CapturedEntity");

        // Solo entidades activas en la configuración (ver EntityConfig).
        EntityType<?> insertType = CapturedMobStats.getEntityType(entityTag);
        if (insertType != null && !CaptureRules.isCapturable(insertType)) {
            player.displayClientMessage(Component.translatable("message.pokecraft.not_capturable", insertType.getDescription()), true);
            return ItemInteractionResult.FAIL;
        }

        // Se guarda una copia con la bola de origen grabada en la clave que lee la extracción. Lo que conste en el NBT
        // manda; si no consta ninguna (mob recogido del mundo), vale la bola que el jugador tiene en la mano.
        CompoundTag storedTag = entityTag.copy();
        CapturedBall.stamp(storedTag, CapturedBall.resolve(entityTag, stack.getItem()));
        jarBE.setStoredEntityTag(storedTag);

        // PokéDex: guardar un mob en el frasco lo desbloquea (aunque la bola viniera de otra fuente).
        if (player instanceof ServerPlayer serverPlayer) {
            EntityType<?> storedType = CapturedMobStats.getEntityType(storedTag);
            if (storedType != null) {
                PokedexManager.unlock(serverPlayer, storedType, level, pos);
            }
        }

        if (stack.has(DataComponents.CUSTOM_NAME)) {
            jarBE.setCustomName(stack.get(DataComponents.CUSTOM_NAME));
        }

        if (!player.getAbilities().instabuild) {
            stack.shrink(1);
        }

        level.playSound(null, pos, SoundEvents.ITEM_FRAME_ADD_ITEM, SoundSource.BLOCKS, 1.0f, 1.0f);
        return ItemInteractionResult.SUCCESS;
    }

    /**
     * Extraer el mob: Shift + clic derecho con AMBAS manos vacías. (Con un objeto en cualquiera de las dos manos el
     * juego ni siquiera llama aquí cuando se pulsa Shift; el chequeo explícito cubre objetos que ignoran Shift.)
     */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (level.isClientSide) return InteractionResult.SUCCESS;
 
        if (level.getBlockEntity(pos) instanceof MobJarBlockEntity jarBE
                && jarBE.hasStoredEntity()
                && player.isShiftKeyDown()
                && player.getMainHandItem().isEmpty()
                && player.getOffhandItem().isEmpty()) {
            CompoundTag entityTag = jarBE.getStoredEntityTag();
 
            // Bola de origen: clave de primer nivel y, si falta (mob recogido del mundo, tarros antiguos), NeoForgeData.
            ItemStack filledPokeball = CapturedBall.createFilled(
                    CapturedBall.resolve(entityTag), entityTag, jarBE.hasCustomName() ? jarBE.getCustomName() : null);
 
            if (!player.addItem(filledPokeball)) {
                player.drop(filledPokeball, false);
            }
 
            jarBE.setStoredEntityTag(null);
            jarBE.setCustomName(null);
                
            level.playSound(null, pos, SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS, 1.0f, 1.0f);
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }
 
    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof MobJarBlockEntity jarBe) {
                ItemStack jarStack = new ItemStack(this);
                if (jarBe.hasStoredEntity()) {
                    CompoundTag tag = new CompoundTag();
                    tag.put("CapturedEntity", jarBe.getStoredEntityTag());
                    jarStack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
                }
                if (jarBe.hasCustomName()) {
                    jarStack.set(DataComponents.CUSTOM_NAME, jarBe.getCustomName());
                }
                if (!player.getAbilities().instabuild) {
                    Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), jarStack);
                }
            }
        }
        return super.playerWillDestroy(level, pos, state, player);
    }
 
    @Override
    public ItemStack getCloneItemStack(net.minecraft.world.level.LevelReader level, BlockPos pos, BlockState state) {
        ItemStack stack = super.getCloneItemStack(level, pos, state);
        if (level.getBlockEntity(pos) instanceof MobJarBlockEntity jarBe && jarBe.hasStoredEntity()) {
            CompoundTag tag = new CompoundTag();
            tag.put("CapturedEntity", jarBe.getStoredEntityTag());
            stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
            if (jarBe.hasCustomName()) {
                stack.set(DataComponents.CUSTOM_NAME, jarBe.getCustomName());
            }
        }
        return stack;
    }
}