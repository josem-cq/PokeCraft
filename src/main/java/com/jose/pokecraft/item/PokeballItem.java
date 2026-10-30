package com.jose.pokecraft.item;

import com.jose.pokecraft.entity.PokeballProjectileEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;

public class PokeballItem extends Item {
    public enum Tier { POKEBALL, GREATBALL, SUPERBALL, ULTRABALL, MASTERBALL }

    private final Tier tier;

    public PokeballItem(Properties properties, Tier tier) {
        super(properties);
        this.tier = tier;
    }

    public Tier getTier() {
        return tier;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, net.minecraft.world.InteractionHand hand) {
        ItemStack itemstack = player.getItemInHand(hand);
        
        level.playSound(null, player.getX(), player.getY(), player.getZ(), 
            SoundEvents.SNOWBALL_THROW, SoundSource.NEUTRAL, 0.5F, 0.4F / (level.getRandom().nextFloat() * 0.4F + 0.8F));

        // Punto 1: Cooldown de Pokéballs en la barra de acceso rápido
        player.getCooldowns().addCooldown(this, 20);

        if (!level.isClientSide()) {
            PokeballProjectileEntity projectile = new PokeballProjectileEntity(level, player, tier);
            projectile.setItem(itemstack);
            projectile.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0F, 1.5F, 1.0F);
            level.addFreshEntity(projectile);
        }

        player.awardStat(Stats.ITEM_USED.get(this));
        if (!player.getAbilities().instabuild) {
            itemstack.shrink(1);
        }

        return InteractionResultHolder.sidedSuccess(itemstack, level.isClientSide());
    }

    public static boolean attemptCatch(LivingEntity target, Player player, Tier tier, ItemStack thrownStack) {
        if (target.level().isClientSide()) return false;

        // Punto 9: Captura del Ender Dragon u otros jefes con Masterball sin restricciones
        boolean isEnderDragon = target.getType() == EntityType.ENDER_DRAGON;
        if (tier == Tier.MASTERBALL || (isEnderDragon && tier == Tier.MASTERBALL)) {
            return executeCatch(target, player, thrownStack);
        }

        float maxHealth = target.getMaxHealth();
        float currentHealth = target.getHealth();
        float healthPercent = currentHealth / maxHealth;

        float catchRate = (1.0f - healthPercent) * 0.5f;
        
        switch (tier) {
            case GREATBALL -> catchRate += 0.15f;
            case SUPERBALL -> catchRate += 0.30f;
            case ULTRABALL -> catchRate += 0.50f;
            case MASTERBALL -> catchRate = 1.0f;
            default -> {}
        }

        if (target.level().getRandom().nextFloat() <= catchRate) {
            return executeCatch(target, player, thrownStack);
        }

        return false;
    }

    private static boolean executeCatch(LivingEntity target, Player player, ItemStack thrownStack) {
        CompoundTag entityTag = new CompoundTag();
        target.save(entityTag);

        // Punto 3: Generar la Poké Ball del mismo tier exacto que fue lanzado
        ItemStack filledStack = new ItemStack(thrownStack.getItem());
        
        CompoundTag tag = filledStack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        tag.put("CapturedEntity", entityTag);
        filledStack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));

        // Punto 2: Heredar nombre personalizado del mob capturado
        if (target.hasCustomName()) {
            filledStack.set(DataComponents.CUSTOM_NAME, target.getCustomName());
        }

        // Punto 4: Posición de Drop exacta en el mundo donde estaba el mob
        ItemEntity itemEntity = new ItemEntity(target.level(), target.getX(), target.getY(), target.getZ(), filledStack);
        itemEntity.setPickUpDelay(20);
        target.level().addFreshEntity(itemEntity);

        target.discard();
        return true;
    }

    public static void releaseEntity(ServerLevel level, CompoundTag entityTag, BlockPos pos, Player player) {
        Entity entity = EntityType.loadEntityRecursive(entityTag, level, e -> {
            e.moveTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, level.getRandom().nextFloat() * 360.0F, 0.0F);
            return e;
        });

        // Punto 5: Configuración del Sistema de Aliados / Amistad
        if (entity instanceof LivingEntity living) {
            CompoundTag persistent = living.getPersistentData();
            persistent.putBoolean("PokeCraftFriendly", true);
            persistent.putUUID("OwnerUUID", player.getUUID());
            persistent.putInt("MobOrderState", 0); // 0: Siguiendo
        }

        if (entity != null) {
            level.addFreshEntity(entity);
            level.playSound(null, pos, SoundEvents.ITEM_FRAME_ADD_ITEM, SoundSource.NEUTRAL, 1.0f, 1.0f);
        }
    }
}