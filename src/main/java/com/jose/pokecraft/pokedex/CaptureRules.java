package com.jose.pokecraft.pokedex;
 
import net.minecraft.world.entity.EntityType;
 
/**
 * Regla única de qué tipos de entidad se pueden capturar, meter en frascos y aparecen en la PokéDex.
 * Delegada en {@link EntityConfig}: por defecto los que tienen huevo de aparición (más Ender Dragon y Wither),
 * y lo que el operador marque en la PokéDex de creativo.
 */
public final class CaptureRules {
    private CaptureRules() {}
 
    public static boolean isCapturable(EntityType<?> type) {
        return EntityConfig.isEnabled(type);
    }
}
