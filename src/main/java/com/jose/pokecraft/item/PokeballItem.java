package com.jose.pokecraft.item;
 
import com.jose.pokecraft.entity.PokeballProjectileEntity;
import com.jose.pokecraft.pokedex.PokedexManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.WaterAnimal;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
 
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
 
        switch (tier) {
            case POKEBALL -> {
                tooltipComponents.add(Component.literal("§7Tier: Poké Ball Básica (Bonus: 1.0x)"));
                tooltipComponents.add(Component.literal("§8-----------------"));
                tooltipComponents.add(Component.literal("§a• Pasivos: §f100% de probabilidad fija."));
                tooltipComponents.add(Component.literal("§e• Neutrales: §fFórmula + 20% extra."));
                tooltipComponents.add(Component.literal("§c• Hostiles: §fFórmula basada en HP max/actual."));
            }
            case GREATBALL -> {
                tooltipComponents.add(Component.literal("§7Tier: Great Ball (Bonus: 1.3x)"));
                tooltipComponents.add(Component.literal("§8-----------------"));
                tooltipComponents.add(Component.literal("§a• Pasivos: §f100% de probabilidad fija."));
                tooltipComponents.add(Component.literal("§e• Neutrales/Hostiles: §fMultiplicador 1.3x."));
            }
            case SUPERBALL -> {
                tooltipComponents.add(Component.literal("§7Tier: Super Ball (Bonus: 1.8x)"));
                tooltipComponents.add(Component.literal("§8-----------------"));
                tooltipComponents.add(Component.literal("§a• Pasivos: §f100% de probabilidad fija."));
                tooltipComponents.add(Component.literal("§e• Neutrales/Hostiles: §fMultiplicador 1.8x."));
            }
            case ULTRABALL -> {
                tooltipComponents.add(Component.literal("§7Tier: Ultra Ball (Bonus: 2.5x)"));
                tooltipComponents.add(Component.literal("§8-----------------"));
                tooltipComponents.add(Component.literal("§a• Pasivos: §f100% de probabilidad fija."));
                tooltipComponents.add(Component.literal("§e• Neutrales/Hostiles: §fMultiplicador 2.5x."));
            }
            case MASTERBALL -> {
                tooltipComponents.add(Component.literal("§6Tier: Master Ball"));
                tooltipComponents.add(Component.literal("§8-----------------"));
                tooltipComponents.add(Component.literal("§b• Todos los Mobs: §fCaptura instantánea del 100%."));
            }
        }
 
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        if (customData != null && customData.copyTag().contains("CapturedEntity")) {
            CompoundTag entityTag = customData.copyTag().getCompound("CapturedEntity");
            tooltipComponents.add(Component.literal("§8-----------------"));
            // Nombre, Salud, Armadura, Daño, efectos e ID: todo calculado de forma universal.
            CapturedMobStats.appendTooltip(tooltipComponents, entityTag, context, false);
        }
    }
 
    public static boolean attemptCatch(LivingEntity target, Player player, Tier tier, ItemStack thrownStack) {
        if (target.level().isClientSide()) return false;
 
        if (target instanceof Player || target instanceof net.minecraft.world.entity.decoration.ArmorStand) {
            return false;
        }
 
        boolean isEnderDragon = target.getType() == EntityType.ENDER_DRAGON;
        if (tier == Tier.MASTERBALL || isEnderDragon) {
            return executeCatch(target, player, thrownStack);
        }
 
        boolean isPassive = (target instanceof Animal || target instanceof Villager || target instanceof WaterAnimal) 
                && !(target instanceof NeutralMob);
        if (isPassive) {
            return executeCatch(target, player, thrownStack);
        }
 
        float hpMax = target.getMaxHealth();
        float hpCurrent = target.getHealth();
        if (hpMax <= 0.0f) hpMax = 1.0f;
 
        float ballBonus = switch (tier) {
            case POKEBALL -> 1.0f;
            case GREATBALL -> 1.3f;
            case SUPERBALL -> 1.8f;
            case ULTRABALL -> 2.5f;
            case MASTERBALL -> 10.0f;
        };
 
        boolean hasNegativeEffect = target.getActiveEffects().stream()
                .anyMatch(effect -> !effect.getEffect().value().isBeneficial());
        float statusBonus = hasNegativeEffect ? 1.25f : 1.0f;
 
        boolean isNeutral = target instanceof NeutralMob;
        float mobType = isNeutral ? 0.20f : 0.0f;
 
        float catchRate;
        double expComponent = 77.0 * (1.0 - Math.exp(-0.033 * 2.0 * Math.sqrt(hpMax)));
 
        if (hpMax >= 12.0f) {
            catchRate = (float) (((3.0f * hpMax - 2.0f * hpCurrent) / (3.0f * hpMax)) 
                        * (12.0f / expComponent) 
                        * ballBonus * statusBonus + mobType);
        } else {
            catchRate = ((5.0f * hpMax - hpCurrent) / (5.0f * hpMax)) * ballBonus * statusBonus + mobType;
        }
 
        catchRate = Math.clamp(catchRate, 0.0f, 1.0f);
 
        if (target.level().getRandom().nextFloat() <= catchRate) {
            return executeCatch(target, player, thrownStack);
        }
 
        return false;
    }
 
    private static boolean executeCatch(LivingEntity target, Player player, ItemStack thrownStack) {
        CompoundTag entityTag = new CompoundTag();
        target.save(entityTag);
 
        entityTag.putString("id", BuiltInRegistries.ENTITY_TYPE.getKey(target.getType()).toString());
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

        // PokéDex: una captura exitosa desbloquea el mob (se registra donde estaba el mob al capturarlo).
        if (player instanceof ServerPlayer serverPlayer) {
            PokedexManager.unlock(serverPlayer, target);
        }

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