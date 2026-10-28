package com.jose.pokecraft.item;

import com.jose.pokecraft.entity.PokeballProjectileEntity;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;

public class PokeballItem extends Item {
    public enum Tier {
        POKEBALL, GREATBALL, SUPERBALL, ULTRABALL, MASTERBALL
    }

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

        // No capturar jugadores ni jefes si se desea, o validar salud
        float maxHealth = target.getMaxHealth();
        float currentHealth = target.getHealth();
        float healthPercent = currentHealth / maxHealth;

        // Probabilidad base según el tier de la Poké Ball y la vida restante
        float catchRate = (1.0f - healthPercent) * 0.5f;
        
        switch (tier) {
            case GREATBALL -> catchRate += 0.15f;
            case SUPERBALL -> catchRate += 0.30f;
            case ULTRABALL -> catchRate += 0.50f;
            case MASTERBALL -> catchRate = 1.0f; // Infalible
            default -> {}
        }

        if (target.level().getRandom().nextFloat() <= catchRate) {
            // Captura exitosa: guardar entidad en la Poké Ball
            CompoundTag entityTag = new CompoundTag();
            target.save(entityTag);

            ItemStack filledStack = new ItemStack(com.jose.pokecraft.PokeCraftMod.POKEBALL.get());
            
            // Reemplazado getOrCreateTag() por CustomData en la 1.21
            CompoundTag tag = filledStack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
            tag.put("CapturedEntity", entityTag);
            filledStack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));

            if (!player.getInventory().add(filledStack)) {
                player.drop(filledStack, false);
            }

            target.discard(); // Eliminar la entidad del mundo
            return true;
        }

        return false;
    }

    public static void releaseEntity(ServerLevel level, CompoundTag entityTag, net.minecraft.core.BlockPos pos, Player player) {
        net.minecraft.world.entity.Entity entity = net.minecraft.world.entity.EntityType.loadEntityRecursive(entityTag, level, e -> {
            e.moveTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, level.getRandom().nextFloat() * 360.0F, 0.0F);
            return e;
        });

        if (entity != null) {
            level.addFreshEntity(entity);
        }
    }
}