package com.jose.pokecraft.entity;

import com.jose.pokecraft.item.PokeballItem;
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
        super.onHitEntity(result);
        if (this.level().isClientSide) return;

        ItemStack stack = this.getItem();
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        CompoundTag tag = customData != null ? customData.copyTag() : null;
        
        Player player = (Player) this.getOwner();

        if (tag != null && tag.contains("CapturedEntity")) {
            CompoundTag entityTag = tag.getCompound("CapturedEntity");
            PokeballItem.releaseEntity((ServerLevel) this.level(), entityTag, this.blockPosition(), player);
            this.discard();
        } else if (result.getEntity() instanceof LivingEntity target) {
            boolean wasSuccessful = PokeballItem.attemptCatch(target, player, tier, stack);
            if (wasSuccessful) {
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
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        CompoundTag tag = customData != null ? customData.copyTag() : null;
        
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