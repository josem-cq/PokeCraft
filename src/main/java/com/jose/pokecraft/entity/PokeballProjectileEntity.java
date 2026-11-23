package com.jose.pokecraft.entity;

import com.jose.pokecraft.item.PokeballItem;
import com.jose.pokecraft.pokedex.CaptureRules;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import org.jetbrains.annotations.Nullable;

public class PokeballProjectileEntity extends ThrowableItemProjectile {
    private final PokeballItem.Tier tier;

    public PokeballProjectileEntity(EntityType<? extends ThrowableItemProjectile> entityType, Level level) {
        super(entityType, level);
        this.tier = PokeballItem.Tier.POKEBALL;
    }

    public PokeballProjectileEntity(Level level, LivingEntity shooter, PokeballItem.Tier tier) {
        super(EntityType.SNOWBALL, shooter, level);
        this.tier = tier;
    }

    @Override
    protected Item getDefaultItem() {
        return com.jose.pokecraft.PokeCraftMod.POKEBALL.get();
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        if (this.level().isClientSide) {
            return;
        }

        ItemStack stack = this.getItem();
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        CompoundTag tag = customData != null ? customData.copyTag() : null;
        
        Player player = this.getOwner() instanceof Player p ? p : null;

        if (tag != null && tag.contains("CapturedEntity")) {
            CompoundTag entityTag = tag.getCompound("CapturedEntity");
            releaseOrReturn(entityTag, this.blockPosition(), stack, player);
        } else {
            // Los mobs multi-parte (Ender Dragon, Ice Spider de Iron's Spells...) reciben el impacto en una parte
            // (PartEntity), que no es un LivingEntity: antes aquí no pasaba nada y la bola atravesaba la hitbox.
            LivingEntity target = CaptureRules.resolveTarget(result.getEntity());
            if (target == null) {
                return;
            }

            // EVITAR ESTRICTAMENTE CAPTURAR JUGADORES
            if (target instanceof Player) {
                handleEmptyBreakOrDrop(stack);
                return;
            }

            // Intentar captura
            if (player != null) {
                boolean wasSuccessful = PokeballItem.attemptCatch(target, player, tier, stack);
                if (wasSuccessful) {
                    this.discard();
                    return;
                }
            }
            
            // IMPORTANTE: Al omitir super.onHitEntity(...), evitamos que mobs con 
            // física de rebote o defensas contra proyectiles (como el Breeze) devuelvan la Poké Ball.
            handleEmptyBreakOrDrop(stack);
        }
    }

    @Override
    protected void onHitBlock(BlockHitResult result) {
        super.onHitBlock(result);
        if (this.level().isClientSide) return;

        ItemStack stack = this.getItem();
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        CompoundTag tag = customData != null ? customData.copyTag() : null;
        
        Player player = this.getOwner() instanceof Player p ? p : null;

        if (tag != null && tag.contains("CapturedEntity")) {
            CompoundTag entityTag = tag.getCompound("CapturedEntity");
            BlockPos spawnPos = result.getBlockPos().relative(result.getDirection());
            releaseOrReturn(entityTag, spawnPos, stack, player);
        } else {
            handleEmptyBreakOrDrop(stack);
        }
    }

    /**
     * Libera el mob guardado. Si no se puede (el dueño se desconectó con la bola en el aire, tipo de entidad
     * desconocido, NBT que un mod no logra cargar...) la bola llena cae al suelo intacta en lugar de destruirse con
     * el mob dentro o tumbar el servidor.
     */
    private void releaseOrReturn(CompoundTag entityTag, BlockPos pos, ItemStack stack, @Nullable Player owner) {
        boolean released = this.level() instanceof ServerLevel serverLevel
                && PokeballItem.releaseEntity(serverLevel, entityTag, pos, owner);
        if (!released) {
            this.spawnAtLocation(stack);
        }
        this.discard();
    }

    private void handleEmptyBreakOrDrop(ItemStack stack) {
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        boolean hasMob = customData != null && customData.copyTag().contains("CapturedEntity");

        if (!hasMob && this.random.nextFloat() < 0.20f) {
            this.level().playSound(null, this.getX(), this.getY(), this.getZ(), 
                SoundEvents.ITEM_BREAK, SoundSource.PLAYERS, 0.8f, 0.8f);
            this.discard();
        } else {
            this.spawnAtLocation(stack);
            this.discard();
        }
    }
}