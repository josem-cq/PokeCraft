package com.jose.pokecraft.item;
 
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeMap;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.DefaultAttributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentEffectComponents;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import org.jetbrains.annotations.Nullable;
 
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
 
/**
 * Cálculo universal de estadísticas (Salud, Armadura, Daño) de un mob capturado a partir de su NBT,
 * para usarse en los tooltips de las Poké Balls y los Mob Jars.
 *
 * Principios:
 *  - Los atributos se reconstruyen con el propio {@link AttributeMap} del motor: se parte del suministrador de
 *    atributos por defecto del tipo de entidad (vanilla o mod) y se carga el NBT "attributes". Así el motor aplica
 *    sus fórmulas reales (ADD_VALUE, ADD_MULTIPLIED_BASE, ADD_MULTIPLIED_TOTAL y el clamp del atributo).
 *    Los modificadores permanentes guardados (pociones, buffs de mods, etc.) entran sin conocer ningún ID.
 *  - El equipamiento (HandItems / ArmorItems / body_armor_item) NO se guarda como modificador en el NBT del mob
 *    (es transitorio), así que se lee y se aplica con {@link ItemStack#forEachModifier}, exactamente igual que
 *    hace LivingEntity al equipar. Funciona con cualquier ítem de vanilla o de mods.
 *  - Los encantamientos se evalúan con sus componentes de efecto data-driven (damage_protection y damage).
 */
public final class CapturedMobStats {
    private static final EquipmentSlot[] ARMOR_SLOTS = {
            EquipmentSlot.FEET, EquipmentSlot.LEGS, EquipmentSlot.CHEST, EquipmentSlot.HEAD
    };
    /** El motor limita el total de puntos de protección de encantamientos a 20 (reducción máxima del 80%). */
    private static final float MAX_ENCHANT_PROTECTION = 20.0f;
    private static final RandomSource ENCHANT_RANDOM = RandomSource.create(0L);
 
    private CapturedMobStats() {}
 
    public record Stats(float health, double maxHealth, float absorption, double armor, boolean hasAttack, double attackDamage) {}
 
    private record Equipped(EquipmentSlot slot, ItemStack stack) {}
 
    private record CacheEntry(CompoundTag tag, @Nullable HolderLookup.Provider registries, Stats stats) {}
 
    // Los tooltips se reconstruyen cada frame mientras el ratón está encima: se cachea el último resultado.
    private static volatile CacheEntry cache;
 
    // ------------------------------------------------------------------------------------------------
    // Cálculo
    // ------------------------------------------------------------------------------------------------
 
    public static Stats compute(CompoundTag entityTag, @Nullable HolderLookup.Provider registries) {
        CacheEntry cached = cache;
        if (cached != null && cached.registries() == registries && cached.tag().equals(entityTag)) {
            return cached.stats();
        }
 
        Stats stats;
        try {
            stats = computeUncached(entityTag, registries);
        } catch (RuntimeException e) {
            // Un tooltip nunca debe crashear el cliente (p. ej. por un mob de un mod con datos raros).
            float health = entityTag.contains("Health", Tag.TAG_ANY_NUMERIC) ? entityTag.getFloat("Health") : 20f;
            stats = new Stats(health, Math.max(health, 1.0f), 0f, 0.0, false, 0.0);
        }
 
        cache = new CacheEntry(entityTag.copy(), registries, stats);
        return stats;
    }
 
    private static Stats computeUncached(CompoundTag entityTag, @Nullable HolderLookup.Provider registries) {
        EntityType<?> type = getEntityType(entityTag);
 
        // 1. Atributos: valores base del tipo + atributos y modificadores permanentes guardados en el NBT.
        AttributeMap attributes = new AttributeMap(supplierFor(type));
        ListTag saved = entityTag.getList("attributes", Tag.TAG_COMPOUND);
        if (saved.isEmpty()) {
            saved = entityTag.getList("Attributes", Tag.TAG_COMPOUND);
        }
        attributes.load(saved);
 
        // 2. Equipamiento: sus modificadores de atributo (armadura, daño, salud, etc.) como en LivingEntity.
        List<Equipped> equipment = registries != null ? readEquipment(entityTag, registries) : List.of();
        for (Equipped equipped : equipment) {
            equipped.stack().forEachModifier(equipped.slot(), (attribute, modifier) -> {
                AttributeInstance instance = attributes.getInstance(attribute);
                if (instance != null) {
                    instance.removeModifier(modifier.id());
                    instance.addTransientModifier(modifier);
                }
            });
        }
 
        // 3. Salud
        double maxHealth = valueOf(attributes, Attributes.MAX_HEALTH, 20.0);
        float health = entityTag.contains("Health", Tag.TAG_ANY_NUMERIC) ? entityTag.getFloat("Health") : (float) maxHealth;
        float absorption = entityTag.contains("AbsorptionAmount", Tag.TAG_ANY_NUMERIC) ? entityTag.getFloat("AbsorptionAmount") : 0f;
 
        // 4. Armadura: atributo final + puntos de protección de encantamientos de las piezas equipadas.
        //    (1 punto de protección de encantamiento = 1/25 de reducción, igual que 1 punto de armadura.)
        double armor = valueOf(attributes, Attributes.ARMOR, 0.0) + enchantProtection(equipment);
 
        // 5. Daño: atributo final (incluye el arma de la mano principal) + bonus de encantamientos del arma.
        boolean hasAttack = attributes.hasAttribute(Attributes.ATTACK_DAMAGE);
        double attack = 0.0;
        if (hasAttack) {
            attack = attributes.getValue(Attributes.ATTACK_DAMAGE);
            for (Equipped equipped : equipment) {
                if (equipped.slot() == EquipmentSlot.MAINHAND) {
                    attack += enchantDamageBonus(equipped.stack(), (float) attack);
                }
            }
        }
 
        return new Stats(health, maxHealth, absorption, armor, hasAttack, attack);
    }
 
    @SuppressWarnings("unchecked")
    private static AttributeSupplier supplierFor(@Nullable EntityType<?> type) {
        if (type != null && DefaultAttributes.hasSupplier(type)) {
            return DefaultAttributes.getSupplier((EntityType<? extends LivingEntity>) type);
        }
        return AttributeSupplier.builder().build();
    }
 
    private static double valueOf(AttributeMap map, Holder<Attribute> attribute, double fallback) {
        return map.hasAttribute(attribute) ? map.getValue(attribute) : fallback;
    }
 
    // ------------------------------------------------------------------------------------------------
    // Equipamiento
    // ------------------------------------------------------------------------------------------------
 
    private static List<Equipped> readEquipment(CompoundTag entityTag, HolderLookup.Provider registries) {
        List<Equipped> result = new ArrayList<>();
 
        ListTag hand = entityTag.getList("HandItems", Tag.TAG_COMPOUND);
        for (int i = 0; i < hand.size() && i < 2; i++) {
            addIfPresent(result, i == 0 ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND, hand.getCompound(i), registries);
        }
 
        ListTag armor = entityTag.getList("ArmorItems", Tag.TAG_COMPOUND);
        for (int i = 0; i < armor.size() && i < ARMOR_SLOTS.length; i++) {
            addIfPresent(result, ARMOR_SLOTS[i], armor.getCompound(i), registries);
        }
 
        // Armadura corporal (caballos, lobos...)
        if (entityTag.contains("body_armor_item", Tag.TAG_COMPOUND)) {
            addIfPresent(result, EquipmentSlot.BODY, entityTag.getCompound("body_armor_item"), registries);
        }
        return result;
    }
 
    private static void addIfPresent(List<Equipped> list, EquipmentSlot slot, CompoundTag itemTag, HolderLookup.Provider registries) {
        if (itemTag.isEmpty()) return;
        ItemStack stack = ItemStack.parseOptional(registries, itemTag);
        if (!stack.isEmpty()) {
            list.add(new Equipped(slot, stack));
        }
    }
 
    // ------------------------------------------------------------------------------------------------
    // Encantamientos (efectos data-driven, sin listas de nombres)
    //
    // Nota: los efectos de encantamiento llevan condiciones (p. ej. Fuego, Explosión o Smite solo se aplican
    // ante cierto daño/objetivo) que requieren un contexto de combate real para evaluarse. En un tooltip no existe,
    // así que por pieza/arma se toma el mayor efecto individual (los encantamientos de protección son
    // mutuamente exclusivos en el juego base) en lugar de sumarlos todos.
    // ------------------------------------------------------------------------------------------------
 
    private static double enchantProtection(List<Equipped> equipment) {
        float total = 0f;
        for (Equipped equipped : equipment) {
            if (equipped.slot().getType() == EquipmentSlot.Type.HAND) continue;
            total += bestProtection(equipped.stack());
        }
        return Math.min(total, MAX_ENCHANT_PROTECTION);
    }
 
    private static float bestProtection(ItemStack stack) {
        ItemEnchantments enchantments = stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
        float best = 0f;
        for (var entry : enchantments.entrySet()) {
            Enchantment enchantment = entry.getKey().value();
            int level = entry.getIntValue();
            var effects = enchantment.effects().get(EnchantmentEffectComponents.DAMAGE_PROTECTION);
            if (effects == null) continue;
            float value = 0f;
            for (var conditional : effects) {
                value = conditional.effect().process(level, ENCHANT_RANDOM, value);
            }
            best = Math.max(best, value);
        }
        return best;
    }
 
    private static float enchantDamageBonus(ItemStack weapon, float baseDamage) {
        ItemEnchantments enchantments = weapon.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
        float best = 0f;
        for (var entry : enchantments.entrySet()) {
            Enchantment enchantment = entry.getKey().value();
            int level = entry.getIntValue();
            var effects = enchantment.effects().get(EnchantmentEffectComponents.DAMAGE);
            if (effects == null) continue;
            float value = baseDamage;
            for (var conditional : effects) {
                value = conditional.effect().process(level, ENCHANT_RANDOM, value);
            }
            best = Math.max(best, value - baseDamage);
        }
        return best;
    }
 
    // ------------------------------------------------------------------------------------------------
    // Utilidades de tooltip
    // ------------------------------------------------------------------------------------------------
 
    /** Devuelve el tipo de entidad del NBT, o null si no existe (no cae al valor por defecto del registro). */
    @Nullable
    public static EntityType<?> getEntityType(CompoundTag entityTag) {
        if (!entityTag.contains("id", Tag.TAG_STRING)) return null;
        ResourceLocation location = ResourceLocation.tryParse(entityTag.getString("id"));
        if (location == null) return null;
        return BuiltInRegistries.ENTITY_TYPE.getOptional(location).orElse(null);
    }
 
    public static Component getDisplayName(CompoundTag entityTag, @Nullable EntityType<?> type, @Nullable HolderLookup.Provider registries) {
        if (entityTag.contains("CustomName", Tag.TAG_STRING)) {
            String raw = entityTag.getString("CustomName");
            if (registries != null) {
                try {
                    Component parsed = Component.Serializer.fromJson(raw, registries);
                    if (parsed != null) return parsed;
                } catch (Exception ignored) {}
            }
            return Component.literal(raw);
        }
        return type != null ? type.getDescription() : Component.literal("Desconocido");
    }
 
    private static Component getBallName(CompoundTag entityTag) {
        Component ballName = Component.literal("Poké Ball");
        if (entityTag.contains("PokeballRegistryName", Tag.TAG_STRING)) {
            ResourceLocation location = ResourceLocation.tryParse(entityTag.getString("PokeballRegistryName"));
            if (location != null) {
                Item ball = BuiltInRegistries.ITEM.getOptional(location).orElse(null);
                if (ball != null) {
                    ballName = ball.getDescription();
                }
            }
        }
        return ballName;
    }
 
    /**
     * Añade al tooltip: nombre, (opcionalmente) Poké Ball de origen, Salud, Armadura, Daño, efectos activos e ID técnico.
     */
    public static void appendTooltip(List<Component> tooltip, CompoundTag entityTag, Item.TooltipContext context, boolean includeBall) {
        HolderLookup.Provider registries = context.registries();
        EntityType<?> type = getEntityType(entityTag);
 
        tooltip.add(Component.literal("§aContiene: §f").append(getDisplayName(entityTag, type, registries)));
        if (includeBall) {
            tooltip.add(Component.literal("§7Bola: §f").append(getBallName(entityTag)));
        }
 
        appendStatLines(tooltip, compute(entityTag, registries));
        appendActiveEffects(tooltip, entityTag);
 
        if (entityTag.contains("id", Tag.TAG_STRING)) {
            tooltip.add(Component.literal("§7ID: " + entityTag.getString("id")));
        }
    }
 
    private static void appendStatLines(List<Component> tooltip, Stats stats) {
        String healthStr = "§7Salud: §f" + (int) Math.ceil(stats.health()) + " / " + (int) Math.ceil(stats.maxHealth());
        if (stats.absorption() > 0f) {
            healthStr += " (+" + (int) Math.ceil(stats.absorption()) + " 💛)";
        }
        healthStr += " §c❤";
        tooltip.add(Component.literal(healthStr));
 
        if (stats.armor() > 0.0) {
            tooltip.add(Component.literal("§7Armadura: §f" + format(stats.armor()) + " 🛡"));
        }
        if (stats.hasAttack() && stats.attackDamage() > 0.0) {
            tooltip.add(Component.literal("§7Daño: §f" + format(stats.attackDamage()) + " ⚔"));
        }
    }
 
    private static void appendActiveEffects(List<Component> tooltip, CompoundTag entityTag) {
        ListTag effects = null;
        if (entityTag.contains("active_effects", Tag.TAG_LIST)) {
            effects = entityTag.getList("active_effects", Tag.TAG_COMPOUND);
        } else if (entityTag.contains("ActiveEffects", Tag.TAG_LIST)) {
            effects = entityTag.getList("ActiveEffects", Tag.TAG_COMPOUND);
        }
        if (effects == null || effects.isEmpty()) return;
 
        tooltip.add(Component.literal("§7Efectos activos:"));
        for (int i = 0; i < effects.size(); i++) {
            CompoundTag effectTag = effects.getCompound(i);
            String effectId = effectTag.getString("id");
            int amplifier = effectTag.getInt("amplifier");
 
            Component effectName = Component.literal(effectId);
            ResourceLocation location = ResourceLocation.tryParse(effectId);
            if (location != null) {
                MobEffect mobEffect = BuiltInRegistries.MOB_EFFECT.getOptional(location).orElse(null);
                if (mobEffect != null) {
                    effectName = Component.translatable(mobEffect.getDescriptionId());
                }
            }
            tooltip.add(Component.literal(" §7- ").append(effectName).append(" (Nivel " + (amplifier + 1) + ")"));
        }
    }
 
    private static String format(double value) {
        double rounded = Math.rint(value);
        if (Math.abs(value - rounded) < 0.05) {
            return String.valueOf((long) rounded);
        }
        return String.format(Locale.ROOT, "%.1f", value);
    }
}