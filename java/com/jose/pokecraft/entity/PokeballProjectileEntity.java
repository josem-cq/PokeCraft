package com.tuusuario.pokecraft.entity;

import com.tuusuario.pokecraft.item.PokeballItem;
import net.minecraft.core.BlockPos;
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
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;

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
        return com.tuusuario.pokecraft.PokeCraftMod.POKEBALL.get();
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        super.onHitEntity(result);
        if (this.level().isClientSide) return;

        ItemStack stack = this.getItem();
        CompoundTag tag = stack.getTag();
        Player player = (Player) this.getOwner();

        if (tag != null && tag.contains("CapturedEntity")) {
            CompoundTag entityTag = tag.getCompound("CapturedEntity");
            PokeballItem.releaseEntity((ServerLevel) this.level(), entityTag, this.blockPosition(), player);
            this.discard(); // La Poké Ball se destruye al desplegar el mob
        } else if (result.getEntity() instanceof LivingEntity target) {
            boolean success = PokeballItem.attemptCatch(target, player, tier, stack);
            if (success) {
                this.discard();
            } else {
                handleEmptyBreakOrDrop(stack);
            }
        }
    }

    @Override
    protected void onHitBlock(BlockHitResult result) {
        super.onHitBlock(result);
        if (this.level().isClientSide) return;

        ItemStack stack = this.getItem();
        CompoundTag tag = stack.getTag();
        Player player = (Player) this.getOwner();

        if (tag != null && tag.contains("CapturedEntity")) {
            CompoundTag entityTag = tag.getCompound("CapturedEntity");
            BlockPos spawnPos = result.getBlockPos().relative(result.getDirection());
            PokeballItem.releaseEntity((ServerLevel) this.level(), entityTag, spawnPos, player);
            this.discard();
        } else {
            handleEmptyBreakOrDrop(stack);
        }
    }

    // Simula la probabilidad de rotura del Ojo de Ender (20% de destruirse, 80% cae al suelo)
    private void handleEmptyBreakOrDrop(ItemStack stack) {
        if (this.random.nextFloat() < 0.20f) {
            this.level().playSound(null, this.getX(), this.getY(), this.getZ(), 
                SoundEvents.ITEM_BREAK, SoundSource.PLAYERS, 0.8f, 0.8f);
            this.discard();
        } else {
            this.spawnAtLocation(stack);
            this.discard();
        }
    }
}