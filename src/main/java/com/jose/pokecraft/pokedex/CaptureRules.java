package com.jose.pokecraft.pokedex;
 
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.entity.PartEntity;
import org.jetbrains.annotations.Nullable;
 
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

    /**
     * Ser vivo al que se refiere un impacto o una interacción. Los mobs multi-parte (Ender Dragon, Ice Spider de
     * Iron's Spells...) no reciben el impacto ni el clic en la entidad principal sino en una {@link PartEntity}, que
     * NO es un LivingEntity (la Ice Spider ni siquiera es "pickable": solo sus partes lo son). Se sube hasta el
     * padre. Devuelve null si no hay entidad o lo golpeado no es un ser vivo (bote, marco, vagoneta...).
     */
    @Nullable
    public static LivingEntity resolveTarget(@Nullable Entity hit) {
        Entity root = hit;
        if (hit instanceof PartEntity<?> part) {
            root = part.getParent();
        }
        return root instanceof LivingEntity living ? living : null;
    }
}