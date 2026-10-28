package com.tuusuario.pokecraft.item;

import com.tuusuario.pokecraft.entity.PokeballProjectileEntity;
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
import net.minecraft.world.level.Level;

public class PokeballItem extends Item {
    public enum Tier {
        POKEBALL(1.0),
        GREATBALL(1.5),
        SUPERBALL(1.75),
        ULTRABALL(2.0),
        MASTERBALL(100.0); // 100% garantizado (Endgame con Estrella del Nether)

        private final double multiplier;
        Tier(double multiplier) { this.multiplier = multiplier; }
        public double getMultiplier() { return multiplier; }
    }

    private final Tier tier;

    public PokeballItem(Properties properties, Tier tier) {
        super(properties);
        this.tier = tier;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, net.minecraft.world.InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        
        // Sonido clásico al lanzar un proyectil esférico
        level.playSound(null, player.getX(), player.getY(), player.getZ(), 
            SoundEvents.SNOWBALL_THROW, SoundSource.PLAYERS, 0.5f, 0.4f / (level.getRandom().nextFloat() * 0.4f + 0.8f));

        if (!level.isClientSide) {
            PokeballProjectileEntity projectile = new PokeballProjectileEntity(level, player, this.tier);
            projectile.setItem(stack);
            projectile.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0f, 1.5f, 1.0f);
            level.addFreshEntity(projectile);
        }

        player.awardStat(Stats.ITEM_USED.get(this));
        
        if (!player.getAbilities().instabuild) {
            stack.shrink(1);
        }

        // Aplica cooldown idéntico al de una Ender Pearl (20 ticks = 1 segundo)
        player.getCooldowns().addCooldown(this, 20);

        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    // Algoritmo de captura con reglas de salud y tipos de mobs
    public static boolean attemptCatch(LivingEntity target, Player player, Tier tier, ItemStack pokeballStack) {
        if (tier == Tier.MASTERBALL) {
            storeEntityInPokeball(pokeballStack, target, player);
            return true;
        }

        float healthPercent = target.getHealth() / target.getMaxHealth();
        double baseChance = 0.0;

        if (target instanceof Animal) {
            baseChance = 1.0; // Pasivos: 100% de base
        } else if (isNeutralMob(target)) {
            // Neutrales: hasta 88% cuando están por debajo del 50% de vida
            baseChance = (healthPercent <= 0.5f) ? 0.88 * (1.0 - (healthPercent / 0.5f) * 0.2) : 0.20;
        } else if (target instanceof Monster) {
            // Agresivos: hasta 30% cuando están a 2 corazones o menos (<= 4 HP)
            if (target.getHealth() <= 4.0f) {
                baseChance = 0.30;
            } else if (healthPercent <= 0.5f) {
                baseChance = 0.10;
            } else {
                baseChance = 0.02;
            }
        }

        double finalChance = baseChance * tier.getMultiplier();

        if (Math.random() <= finalChance) {
            storeEntityInPokeball(pokeballStack, target, player);
            return true;
        }
        return false;
    }

    private static boolean isNeutralMob(LivingEntity target) {
        return target instanceof net.minecraft.world.entity.animal.Wolf || 
               target instanceof net.minecraft.world.entity.monster.EnderMan;
    }

    // Almacena NBT completo y entrega la Poké Ball llena al jugador
    public static void storeEntityInPokeball(ItemStack pokeball, LivingEntity target, Player player) {
        CompoundTag entityTag = new CompoundTag();
        target.save(entityTag);
        entityTag.remove("UUID"); // Limpia la UUID para evitar errores al re-spawnear

        ItemStack filledStack = new ItemStack(pokeball.getItem());
        CompoundTag itemTag = filledStack.getOrCreateTag();
        itemTag.put("CapturedEntity", entityTag);

        if (!player.addItem(filledStack)) {
            player.drop(filledStack, false);
        }
        target.discard(); // Elimina al mob del mundo tras capturarlo con éxito
    }

    // Libera al mob convirtiéndolo en aliado leal del jugador
    public static void releaseEntity(ServerLevel level, CompoundTag entityTag, net.minecraft.core.BlockPos pos, Player player) {
        if (entityTag == null || entityTag.isEmpty()) return;

        net.minecraft.world.entity.Entity entity = net.minecraft.world.entity.EntityType.loadEntityRecursive(entityTag, level, e -> {
            e.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
            return e;
        });

        if (entity instanceof LivingEntity living) {
            CompoundTag persistent = living.getPersistentData();
            persistent.putBoolean("PokeCraftFriendly", true);
            persistent.putUUID("OwnerUUID", player.getUUID());

            if (living instanceof net.minecraft.world.entity.Mob mob) {
                mob.setTarget(null);
            }
        }
        level.addFreshEntity(entity);
    }
}