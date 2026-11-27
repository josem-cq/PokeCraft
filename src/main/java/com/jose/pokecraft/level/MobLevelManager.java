package com.jose.pokecraft.level;

import com.jose.pokecraft.PokeCraftMod;
import com.jose.pokecraft.ai.MobOrderManager;
import com.jose.pokecraft.item.CapturedBall;
import com.jose.pokecraft.item.PokeballItem;
import com.jose.pokecraft.pokedex.MobStrength;
import com.jose.pokecraft.pokedex.MobStrengthManager;
import com.jose.pokecraft.pokedex.StrengthClassifier;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeMap;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * Lógica del sistema de niveles de los aliados: XP, bonificaciones de atributos, equipamiento en el NBT de una
 * entidad guardada, poción permanente y bola de reserva.
 *
 * Dos formas de acceder a los datos (ver {@link MobLevelData}):
 *  - mob VIVO: {@link #readLive} / {@link #writeLive} sobre Entity#getPersistentData();
 *  - mob GUARDADO (NBT "CapturedEntity" de una bola/tarro): {@link #persistentOf} devuelve el compuesto "NeoForgeData".
 */
public final class MobLevelManager {
    /** Radio (bloques) alrededor de la víctima dentro del cual los aliados reciben la XP. */
    public static final double XP_RADIUS = 32.0D;

    // Bonificaciones por nivel por encima del 1 (ajustables aquí).
    public static final double HEALTH_PER_LEVEL = 0.02D;   // +2 % de la vida base por nivel (ADD_MULTIPLIED_BASE)
    public static final double DAMAGE_PER_LEVEL = 0.02D;   // +2 % del ataque base por nivel (ADD_MULTIPLIED_BASE)
    public static final double ARMOR_PER_LEVEL = 0.05D;    // +0,05 de armadura por nivel (ADD_VALUE)

    /** Fracción de la vida máxima con la que el mob sale de la bola de reserva tras un golpe letal. */
    public static final float RESERVE_REVIVE_FRACTION = 0.25F;
    /** Duración (ticks) con la que se renueva cada 40 ticks el efecto de la poción permanente. */
    private static final int POTION_REFRESH_DURATION = 120;

    private static final ResourceLocation ID_HEALTH = ResourceLocation.fromNamespaceAndPath(PokeCraftMod.MOD_ID, "level_health");
    private static final ResourceLocation ID_DAMAGE = ResourceLocation.fromNamespaceAndPath(PokeCraftMod.MOD_ID, "level_damage");
    private static final ResourceLocation ID_ARMOR = ResourceLocation.fromNamespaceAndPath(PokeCraftMod.MOD_ID, "level_armor");

    private MobLevelManager() {}

    // ---------------------------------------------------------------------------------------------
    // Acceso a datos
    // ---------------------------------------------------------------------------------------------

    public static MobLevelData readLive(Mob mob) {
        return MobLevelData.read(mob.getPersistentData(), mob.registryAccess());
    }

    public static void writeLive(Mob mob, MobLevelData data) {
        data.write(mob.getPersistentData(), mob.registryAccess());
    }

    /** Datos persistentes ("NeoForgeData") de un NBT de entidad guardada; si no existen, un compuesto vacío NO enlazado. */
    public static CompoundTag persistentOf(CompoundTag entityTag) {
        return entityTag.contains(MobOrderManager.ENTITY_PERSISTENT_TAG, Tag.TAG_COMPOUND)
                ? entityTag.getCompound(MobOrderManager.ENTITY_PERSISTENT_TAG)
                : new CompoundTag();
    }

    public static boolean hasCapturedMob(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data != null && data.copyTag().contains(CapturedBall.CAPTURED_KEY, Tag.TAG_COMPOUND);
    }

    /** Copia del NBT de la entidad guardada en una bola, o null si la bola está vacía. */
    @Nullable
    public static CompoundTag capturedEntityOf(ItemStack ball) {
        CustomData data = ball.get(DataComponents.CUSTOM_DATA);
        if (data == null) return null;
        CompoundTag root = data.copyTag();
        return root.contains(CapturedBall.CAPTURED_KEY, Tag.TAG_COMPOUND) ? root.getCompound(CapturedBall.CAPTURED_KEY) : null;
    }

    // ---------------------------------------------------------------------------------------------
    // Equipamiento real en el NBT de una entidad guardada
    // ---------------------------------------------------------------------------------------------

    public static ItemStack getEquipment(CompoundTag entityTag, MobSlot slot, HolderLookup.Provider registries) {
        if (slot.equipment() == null) return ItemStack.EMPTY;
        ListTag list = entityTag.getList(listKey(slot), Tag.TAG_COMPOUND);
        if (slot.tagIndex() >= list.size()) return ItemStack.EMPTY;
        CompoundTag itemTag = list.getCompound(slot.tagIndex());
        return itemTag.isEmpty() ? ItemStack.EMPTY : ItemStack.parseOptional(registries, itemTag);
    }

    public static void setEquipment(CompoundTag entityTag, MobSlot slot, ItemStack stack, HolderLookup.Provider registries) {
        if (slot.equipment() == null) return;
        String key = listKey(slot);
        int size = slot.isHand() ? 2 : 4;
        ListTag source = entityTag.getList(key, Tag.TAG_COMPOUND);
        ListTag list = new ListTag();
        for (int i = 0; i < size; i++) {
            list.add(i < source.size() ? source.getCompound(i) : new CompoundTag());
        }
        list.set(slot.tagIndex(), stack.isEmpty() ? new CompoundTag() : stack.save(registries));
        entityTag.put(key, list);
    }

    private static String listKey(MobSlot slot) {
        return slot.isHand() ? "HandItems" : "ArmorItems";
    }

    // ---------------------------------------------------------------------------------------------
    // XP
    // ---------------------------------------------------------------------------------------------

    /**
     * Fortaleza para los cálculos de XP: Vida * (1 + Armadura) * Ataque BASE, tomando ataque 1 si es 0 (también para
     * los pasivos). Es SOLO para la XP: la fortaleza real que muestra la PokéDex no cambia.
     */
    public static double xpStrength(EntityType<?> type, Level level) {
        StrengthClassifier.BaseStats stats = MobStrengthManager.baseStats(type, level);
        double attack = stats.damage() > 0.0F ? stats.damage() : 1.0D;
        return Math.max(0.0D, stats.health()) * (1.0D + Math.max(0.0D, stats.armor())) * attack;
    }

    /** XP constante que cuesta cada nivel de un mob de este tipo, según su categoría de fortaleza. */
    public static double xpPerLevel(EntityType<?> type, Level level) {
        MobStrength tier = MobStrengthManager.tierOf(type, level);
        double multiplier = switch (tier) {
            case LEGENDARY -> 1.0D;
            case STRONG -> 1.3D;
            case NORMAL -> 1.8D;
            case WEAK -> 2.5D;
            case VERY_WEAK -> 1.0D;
        };
        return Math.max(1.0D, Math.abs(multiplier * xpStrength(type, level)));
    }

    /**
     * Una entidad murió: su fortaleza se suma como XP a TODOS los aliados del dueño que estén a menos de
     * {@link #XP_RADIUS} bloques de ella. El dueño es quien la mató (jugador) o el dueño del aliado que la mató.
     */
    public static void awardKillXp(LivingEntity dead, @Nullable Entity killer) {
        if (!(dead instanceof Mob) || !(dead.level() instanceof ServerLevel level)) return;
        if (MobOrderManager.isFriendly(dead)) return;

        UUID ownerId = null;
        if (killer instanceof ServerPlayer player) {
            ownerId = player.getUUID();
        } else if (killer instanceof Mob ally && MobOrderManager.isFriendly(ally) && !MobOrderManager.isVengeance(ally)) {
            ownerId = MobOrderManager.getOwnerId(ally);
        }
        if (ownerId == null) return;

        double xp = xpStrength(dead.getType(), level);
        if (!(xp > 0.0D)) return;

        final UUID owner = ownerId;
        double radiusSqr = XP_RADIUS * XP_RADIUS;
        List<Mob> allies = level.getEntitiesOfClass(Mob.class, dead.getBoundingBox().inflate(XP_RADIUS),
                mob -> mob != dead && mob.isAlive() && MobOrderManager.isFriendly(mob) && !MobOrderManager.isVengeance(mob)
                        && owner.equals(MobOrderManager.getOwnerId(mob)) && mob.distanceToSqr(dead) <= radiusSqr);
        for (Mob ally : allies) {
            MobLevelData data = readLive(ally);
            data.addXp(xp, xpPerLevel(ally.getType(), level));
            writeLive(ally, data);
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Atributos
    // ---------------------------------------------------------------------------------------------

    /**
     * Aplica las bonificaciones de nivel a un mapa de atributos (quitando antes las que hubiera, para no acumular).
     * {@code permanent = true} para un mob vivo (se guardan en su NBT y se reponen solas); false para calcular
     * estadísticas de un mob guardado sin tocar su NBT (tooltips).
     */
    public static void applyLevelBonuses(AttributeMap attributes, int level, boolean permanent) {
        int extra = Math.max(0, level - 1);
        setBonus(attributes, Attributes.MAX_HEALTH, ID_HEALTH, extra * HEALTH_PER_LEVEL,
                AttributeModifier.Operation.ADD_MULTIPLIED_BASE, permanent);
        setBonus(attributes, Attributes.ATTACK_DAMAGE, ID_DAMAGE, extra * DAMAGE_PER_LEVEL,
                AttributeModifier.Operation.ADD_MULTIPLIED_BASE, permanent);
        setBonus(attributes, Attributes.ARMOR, ID_ARMOR, extra * ARMOR_PER_LEVEL,
                AttributeModifier.Operation.ADD_VALUE, permanent);
    }

    private static void setBonus(AttributeMap map, Holder<Attribute> attribute, ResourceLocation id, double amount,
                                 AttributeModifier.Operation operation, boolean permanent) {
        AttributeInstance instance = map.getInstance(attribute);
        if (instance == null) return;
        instance.removeModifier(id);
        if (amount == 0.0D) return;
        AttributeModifier modifier = new AttributeModifier(id, amount, operation);
        if (permanent) {
            instance.addPermanentModifier(modifier);
        } else {
            instance.addTransientModifier(modifier);
        }
    }

    /**
     * Reaplica al mob vivo todo lo que depende de su nivel y slots (al cargarse o liberarse): bonificaciones de
     * atributos (conservando la proporción de vida) y probabilidad de drop del equipamiento (2.0 = siempre y sin
     * desgaste, para que el equipo colocado en la mesa no se pierda si el mob muere).
     */
    public static void refresh(Mob mob) {
        if (mob.level().isClientSide) return;
        MobLevelData data = readLive(mob);

        double oldMax = mob.getMaxHealth();
        float health = mob.getHealth();
        applyLevelBonuses(mob.getAttributes(), data.level, true);
        double newMax = mob.getMaxHealth();
        if (oldMax > 0.0D && health > 0.0F && Math.abs(newMax - oldMax) > 1.0E-6D) {
            mob.setHealth((float) (health * newMax / oldMax));
        }

        for (MobSlot slot : MobSlot.values()) {
            if (slot.equipment() != null && data.isUnlocked(slot) && !mob.getItemBySlot(slot.equipment()).isEmpty()) {
                mob.setDropChance(slot.equipment(), 2.0F);
            }
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Poción permanente
    // ---------------------------------------------------------------------------------------------

    /** ¿Es una poción persistente con al menos un efecto que dure en el tiempo? Las instantáneas se rechazan. */
    public static boolean hasPermanentEffect(ItemStack stack) {
        if (!stack.is(Items.LINGERING_POTION)) return false;
        PotionContents contents = stack.get(DataComponents.POTION_CONTENTS);
        if (contents == null) return false;
        for (MobEffectInstance effect : contents.getAllEffects()) {
            if (!effect.getEffect().value().isInstantenous()) return true;
        }
        return false;
    }

    /**
     * Cada 40 ticks renueva el efecto de la poción del slot (si está desbloqueado). Al renovarse con una duración corta,
     * si se quita la poción o el mob sale del juego el efecto simplemente caduca en unos segundos.
     */
    public static void tickPotion(Mob mob) {
        MobLevelData data = readLive(mob);
        if (!data.isUnlocked(MobSlot.POTION) || !hasPermanentEffect(data.potion)) return;
        PotionContents contents = data.potion.get(DataComponents.POTION_CONTENTS);
        if (contents == null) return;
        for (MobEffectInstance effect : contents.getAllEffects()) {
            if (effect.getEffect().value().isInstantenous()) continue;
            mob.addEffect(new MobEffectInstance(effect.getEffect(), POTION_REFRESH_DURATION,
                    effect.getAmplifier(), true, false, true));
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Bola de reserva
    // ---------------------------------------------------------------------------------------------

    /**
     * Golpe letal contra un aliado con la bola de reserva desbloqueada y cargada: en vez de morir, se guarda en esa
     * bola, que cae al mundo en su posición exacta con todo su NBT (igual que con Shift + clic derecho, pero sin
     * pasar por el inventario). Si tenía un tótem en la mano secundaria el motor ya lo consumió antes de llegar aquí.
     *
     * @return true si el mob fue guardado (el llamador debe cancelar la muerte).
     */
    public static boolean tryReserveCapture(Mob mob) {
        if (mob.level().isClientSide || !(mob.level() instanceof ServerLevel level)) return false;
        if (!MobOrderManager.isFriendly(mob) || MobOrderManager.isVengeance(mob)) return false;

        MobLevelData data = readLive(mob);
        if (!data.isUnlocked(MobSlot.RESERVE_BALL)) return false;
        ItemStack reserve = data.reserve;
        if (reserve.isEmpty() || !(reserve.getItem() instanceof PokeballItem) || hasCapturedMob(reserve)) return false;

        // La bola de reserva se gasta: no debe quedar anidada dentro del propio mob guardado.
        data.reserve = ItemStack.EMPTY;
        writeLive(mob, data);

        if (mob.isPassenger()) mob.stopRiding();
        if (mob.isLeashed()) mob.dropLeash(true, false);
        mob.removeAllEffects();
        mob.setHealth(Math.max(1.0F, mob.getMaxHealth() * RESERVE_REVIVE_FRACTION));

        CompoundTag entityTag = new CompoundTag();
        mob.save(entityTag);
        entityTag.remove("UUID");

        ItemStack filled = CapturedBall.createFilled(reserve.getItem(), entityTag,
                mob.hasCustomName() ? mob.getCustomName() : null);
        ItemEntity drop = new ItemEntity(level, mob.getX(), mob.getY(), mob.getZ(), filled);
        drop.setPickUpDelay(20);
        level.addFreshEntity(drop);

        level.playSound(null, mob.blockPosition(), SoundEvents.ITEM_PICKUP, SoundSource.NEUTRAL, 1.0F, 0.8F);
        mob.discard();
        return true;
    }

    /** Dueño conectado de un mob aliado (para mensajes). */
    @Nullable
    public static Player ownerOf(Mob mob) {
        return MobOrderManager.getOwner(mob);
    }
}
