package com.jose.pokecraft.pokedex;
 
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.SpawnEggItem;
 
import java.util.Set;
 
/**
 * Regla única de qué tipos de entidad se pueden capturar y aparecen en la PokéDex: los que tienen huevo de aparición.
 * Así quedan fuera automáticamente (también los de otros mods) las entidades sin hitbox, las que no spawnean y las que
 * generan objetos o mobs (proyectiles, partes de jefes, colmillos, etc.).
 *
 * Excepciones: criaturas sin huevo de aparición vanilla que sí se quieren poder capturar.
 */
public final class CaptureRules {
    private static final Set<EntityType<?>> ALWAYS_ALLOWED = Set.of(EntityType.ENDER_DRAGON, EntityType.WITHER);
 
    private CaptureRules() {}
 
    public static boolean isCapturable(EntityType<?> type) {
        if (type == EntityType.PLAYER) return false;
        return ALWAYS_ALLOWED.contains(type) || SpawnEggItem.byId(type) != null;
    }
}