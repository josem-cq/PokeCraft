package com.jose.pokecraft.client.pokedex;
 
import com.jose.pokecraft.pokedex.MobKind;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.DefaultAttributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.level.Level;
import net.neoforged.fml.ModList;
import org.jetbrains.annotations.Nullable;
 
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
 
/**
 * Catálogo de TODOS los mobs del registro (vanilla y de otros mods), construido una vez en el cliente la primera
 * vez que se abre la PokéDex. Se reconstruye si cambia el idioma (los nombres cambian) o al reconectar a otro mundo.
 *
 * Un tipo entra al catálogo si su entidad es un {@link Mob}: así quedan fuera el jugador, los armor stands, los
 * proyectiles, los vehículos, etc.
 */
public final class MobCatalog {
    private static List<MobEntry> entries = List.of();
    private static boolean built = false;
    private static String builtForLanguage = "";
    private static final Map<String, String> MOD_NAMES = new HashMap<>();
 
    private MobCatalog() {}
 
    public static List<MobEntry> entries() {
        String language = Minecraft.getInstance().getLanguageManager().getSelected();
        if (!built || !language.equals(builtForLanguage)) {
            entries = build();
            builtForLanguage = language;
            built = true;
        }
        return entries;
    }
 
    public static void invalidate() {
        built = false;
        entries = List.of();
        MOD_NAMES.clear();
    }
 
    /** Nombre legible del mod dueño de un namespace ("minecraft" -> "Minecraft"). */
    public static String modName(String namespace) {
        return MOD_NAMES.computeIfAbsent(namespace, ns -> ModList.get().getModContainerById(ns)
                .map(container -> container.getModInfo().getDisplayName())
                .orElse(ns));
    }
 
    // ---------------------------------------------------------------------------------------------
 
    private static List<MobEntry> build() {
        Level level = Minecraft.getInstance().level;
        List<MobEntry> list = new ArrayList<>();
        for (EntityType<?> type : BuiltInRegistries.ENTITY_TYPE) {
            ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(type);
            try {
                MobEntry entry = describe(type, id, level);
                if (entry != null) list.add(entry);
            } catch (Exception ignored) {
                // Un mob de un mod con constructor problemático no debe impedir abrir la PokéDex.
            }
        }
        return list;
    }
 
    @SuppressWarnings("unchecked")
    @Nullable
    private static MobEntry describe(EntityType<?> type, ResourceLocation id, @Nullable Level level) {
        // Todos los tipos "vivos" (con atributos) tienen un AttributeSupplier registrado.
        if (type == EntityType.PLAYER || !DefaultAttributes.hasSupplier(type)) return null;
 
        // Se instancia (sin añadirla al mundo) solo para saber de qué clase es: Mob, NeutralMob, Enemy...
        Entity instance = null;
        if (level != null) {
            try {
                instance = type.create(level);
            } catch (Exception ignored) {
                // se clasifica por categoría más abajo
            }
        }
        if (instance != null && !(instance instanceof Mob)) return null;
        if (instance == null && type.getCategory() == MobCategory.MISC) return null;
 
        MobKind kind = classify(instance, type);
 
        AttributeSupplier supplier = DefaultAttributes.getSupplier((EntityType<? extends LivingEntity>) type);
        float health = base(supplier, Attributes.MAX_HEALTH);
        float armor = base(supplier, Attributes.ARMOR);
        float damage = base(supplier, Attributes.ATTACK_DAMAGE);
 
        net.minecraft.network.chat.Component name = type.getDescription();
        SpawnEggItem egg = SpawnEggItem.byId(type);
        ItemStack icon = egg != null ? new ItemStack(egg) : ItemStack.EMPTY;
 
        return new MobEntry(id, id.getNamespace(), name, name.getString(), kind, health, armor, damage, icon);
    }
 
    /** Neutral > Hostil > Pasivo: el Enderman o el Piglin zombificado son Monster pero se comportan como neutrales. */
    private static MobKind classify(@Nullable Entity instance, EntityType<?> type) {
        if (instance instanceof NeutralMob) return MobKind.NEUTRAL;
        if (instance instanceof Enemy || type.getCategory() == MobCategory.MONSTER) return MobKind.HOSTILE;
        return MobKind.PASSIVE;
    }
 
    private static float base(AttributeSupplier supplier, Holder<Attribute> attribute) {
        return supplier.hasAttribute(attribute) ? (float) supplier.getBaseValue(attribute) : 0.0F;
    }
}