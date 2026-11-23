package com.jose.pokecraft.pokedex;

import com.mojang.logging.LogUtils;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.DefaultAttributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.Level;
import net.minecraft.core.Holder;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Punto único (común a cliente y servidor) de estadísticas base, puntaje y categoría de fortaleza.
 *
 * - Las estadísticas base de cada tipo se calculan una vez y se cachean.
 * - La clasificación ({@link StrengthClassifier.Snapshot}) se calcula sobre los tipos ACTIVOS de {@link EntityConfig}
 *   y se recalcula sola cuando cambia su versión (carga del servidor, sincronización, edición en vivo...).
 *
 * No hace falta sincronizarla por red: solo depende del registro y de EntityConfig (que ya se sincroniza), así que
 * cliente y servidor obtienen el mismo resultado. En un mundo integrado comparten esta caché, lo cual es inocuo.
 */
public final class MobStrengthManager {
    private static final Logger LOGGER = LogUtils.getLogger();

    private static final Map<EntityType<?>, StrengthClassifier.BaseStats> BASE_CACHE = new ConcurrentHashMap<>();

    private static StrengthClassifier.Snapshot snapshot = StrengthClassifier.Snapshot.EMPTY;
    private static int snapshotVersion = -1;

    private MobStrengthManager() {}

    // ---------------------------------------------------------------------------------------------
    // Estadísticas base
    // ---------------------------------------------------------------------------------------------

    /**
     * Estadísticas base del tipo (AttributeSupplier por defecto). Se instancia una entidad temporal (sin añadirla al
     * mundo) solo para saber si es NeutralMob / Enemy; sin nivel se clasifica por categoría y NO se cachea.
     */
    @SuppressWarnings("unchecked")
    public static StrengthClassifier.BaseStats baseStats(EntityType<?> type, @Nullable Level level) {
        StrengthClassifier.BaseStats cached = BASE_CACHE.get(type);
        if (cached != null) return cached;

        boolean living = DefaultAttributes.hasSupplier(type);
        Entity instance = null;
        if (living && level != null) {
            try {
                instance = type.create(level);
            } catch (Exception ignored) {
                // se clasifica por categoría más abajo
            }
        }

        float health = 0, armor = 0, damage = 0;
        if (living) {
            AttributeSupplier supplier = DefaultAttributes.getSupplier((EntityType<? extends LivingEntity>) type);
            health = base(supplier, Attributes.MAX_HEALTH);
            armor = base(supplier, Attributes.ARMOR);
            damage = base(supplier, Attributes.ATTACK_DAMAGE);
        }

        StrengthClassifier.BaseStats stats = new StrengthClassifier.BaseStats(kindOf(instance, type), health, armor, damage);
        if (!living || instance != null) {
            BASE_CACHE.put(type, stats); // resultado fiable: ya se vio la instancia real (o no la necesita)
        }
        return stats;
    }

    /** Neutral > Hostil > Pasivo: el Enderman o el Piglin zombificado son Monster pero se comportan como neutrales. */
    private static MobKind kindOf(@Nullable Entity instance, EntityType<?> type) {
        if (instance instanceof NeutralMob) return MobKind.NEUTRAL;
        if (instance instanceof Enemy || type.getCategory() == MobCategory.MONSTER) return MobKind.HOSTILE;
        return MobKind.PASSIVE;
    }

    private static float base(AttributeSupplier supplier, Holder<Attribute> attribute) {
        return supplier.hasAttribute(attribute) ? (float) supplier.getBaseValue(attribute) : 0.0F;
    }

    // ---------------------------------------------------------------------------------------------
    // Clasificación
    // ---------------------------------------------------------------------------------------------

    /**
     * Clasificación vigente. Se recalcula si cambió {@link EntityConfig#version()}. Sin nivel no se puede
     * instanciar nada, así que se devuelve la última calculada (o la vacía) sin tocar la caché.
     */
    public static synchronized StrengthClassifier.Snapshot snapshot(@Nullable Level level) {
        int version = EntityConfig.version(); // se lee antes: si cambia a mitad de cálculo, el siguiente acceso recalcula
        if (snapshotVersion == version || level == null) return snapshot;

        List<StrengthClassifier.BaseStats> population = new ArrayList<>();
        for (EntityType<?> type : BuiltInRegistries.ENTITY_TYPE) {
            if (!EntityConfig.isEnabled(type)) continue; // solo la lista de capturables válidos
            try {
                population.add(baseStats(type, level));
            } catch (RuntimeException ignored) {
                // una entidad de un mod problemática no debe impedir la clasificación
            }
        }

        double[] scores = population.stream().mapToDouble(StrengthClassifier.BaseStats::score).toArray();
        StrengthClassifier.Snapshot built = StrengthClassifier.buildSnapshot(scores);
        snapshot = built;
        snapshotVersion = version;

        if (LOGGER.isDebugEnabled()) {
            int[] counts = new int[MobStrength.values().length];
            for (StrengthClassifier.BaseStats stats : population) {
                counts[built.tierOf(stats.score(), stats.forcedLowest()).ordinal()]++;
            }
            LOGGER.debug("PokéDex: fortaleza reclasificada ({} mobs), umbrales={}, reparto={}",
                    population.size(), Arrays.toString(built.thresholds()), Arrays.toString(counts));
        }
        return built;
    }

    public static MobStrength tierOf(EntityType<?> type, @Nullable Level level) {
        StrengthClassifier.BaseStats stats = baseStats(type, level);
        return snapshot(level).tierOf(stats.score(), stats.forcedLowest());
    }

    public static double scoreOf(EntityType<?> type, @Nullable Level level) {
        return baseStats(type, level).score();
    }

    /** Olvida todo (al salir del mundo: otro servidor puede tener otros mods). */
    public static synchronized void invalidate() {
        BASE_CACHE.clear();
        snapshot = StrengthClassifier.Snapshot.EMPTY;
        snapshotVersion = -1;
    }
}