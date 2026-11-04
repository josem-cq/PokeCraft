package com.jose.pokecraft.item;

import com.jose.pokecraft.entity.PokeballProjectileEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.WaterAnimal;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

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

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        super.appendHoverText(stack, context, tooltipComponents, tooltipFlag);

        // Descripciones y multiplicadores según el Tier de la Poké Ball
        switch (tier) {
            case POKEBALL -> {
                tooltipComponents.add(Component.literal("§7Tier: Poké Ball Básica"));
                tooltipComponents.add(Component.literal("§eProbabilidad: Pasivos (100%), Hostiles (Vida faltante), Neutrales (+20%)."));
            }
            case GREATBALL -> {
                tooltipComponents.add(Component.literal("§7Tier: Great Ball"));
                tooltipComponents.add(Component.literal("§eProbabilidad: +15% extra de captura base en hostiles/neutrales."));
            }
            case SUPERBALL -> {
                tooltipComponents.add(Component.literal("§7Tier: Super Ball"));
                tooltipComponents.add(Component.literal("§eProbabilidad: +30% extra de captura base en hostiles/neutrales."));
            }
            case ULTRABALL -> {
                tooltipComponents.add(Component.literal("§7Tier: Ultra Ball"));
                tooltipComponents.add(Component.literal("§eProbabilidad: +50% extra de captura base en hostiles/neutrales."));
            }
            case MASTERBALL -> {
                tooltipComponents.add(Component.literal("§6Tier: Master Ball"));
                tooltipComponents.add(Component.literal("§bCaptura instantánea del 100%."));
            }
        }

        // Muestra el mob que contiene adentro de forma garantizada
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        if (customData != null && customData.copyTag().contains("CapturedEntity")) {
            CompoundTag entityTag = customData.copyTag().getCompound("CapturedEntity");
            if (entityTag.contains("id")) {
                String entityId = entityTag.getString("id");
                tooltipComponents.add(Component.literal("§aContiene: §f" + entityId));
            }
        }
    }

    public static boolean attemptCatch(LivingEntity target, Player player, Tier tier, ItemStack thrownStack) {
        if (target.level().isClientSide()) return false;

        boolean isEnderDragon = target.getType() == EntityType.ENDER_DRAGON;
        if (tier == Tier.MASTERBALL || isEnderDragon) {
            return executeCatch(target, player, thrownStack);
        }

        // 1. Mobs Pasivos: Tienen 100% de probabilidad independiente del tipo de bola
        boolean isPassive = (target instanceof Animal || target instanceof Villager || target instanceof WaterAnimal) 
                && !(target instanceof NeutralMob);
        if (isPassive) {
            return executeCatch(target, player, thrownStack);
        }

        float maxHealth = target.getMaxHealth();
        float currentHealth = target.getHealth();
        float healthPercent = currentHealth / maxHealth;

        // Cálculo base para hostiles basado en vida faltante
        float catchRate = (1.0f - healthPercent) * 0.5f;

        // 2. Mobs Neutrales: Aumentan un 20% (+0.20) la probabilidad base
        boolean isNeutral = target instanceof NeutralMob;
        if (isNeutral) {
            catchRate += 0.20f;
        }

        // 3. Bonificador por Tier de Poké Ball (aplica para Hostiles y Neutrales)
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

        // Guardar explícitamente el ID del tipo de entidad para que el tooltip lo lea correctamente
        entityTag.putString("id", BuiltInRegistries.ENTITY_TYPE.getKey(target.getType()).toString());

        // Guardar el registro del item exacto de la Poké Ball en los datos de la entidad
        entityTag.putString("PokeballRegistryName", BuiltInRegistries.ITEM.getKey(thrownStack.getItem()).toString());

        ItemStack filledStack = new ItemStack(thrownStack.getItem());
        
        CompoundTag tag = filledStack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        tag.put("CapturedEntity", entityTag);
        filledStack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));

        if (target.hasCustomName()) {
            filledStack.set(DataComponents.CUSTOM_NAME, target.getCustomName());
        }

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

        if (entity instanceof LivingEntity living) {
            CompoundTag persistent = living.getPersistentData();
            persistent.putBoolean("PokeCraftFriendly", true);
            persistent.putUUID("OwnerUUID", player.getUUID());
            persistent.putInt("MobOrderState", 0);
            if (entityTag.contains("PokeballRegistryName")) {
                persistent.putString("PokeballRegistryName", entityTag.getString("PokeballRegistryName"));
            }
        }

        if (entity != null) {
            level.addFreshEntity(entity);
            level.playSound(null, pos, SoundEvents.ITEM_FRAME_ADD_ITEM, SoundSource.NEUTRAL, 1.0f, 1.0f);
        }
    }
}