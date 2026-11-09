package com.jose.pokecraft.client.pokedex;
 
import com.jose.pokecraft.pokedex.MobKind;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
 
/**
 * Ficha estática de un tipo de mob del registro (no depende del progreso del jugador).
 * Las estadísticas son las BASE del tipo (AttributeSupplier por defecto), sin modificadores de mods de dificultad.
 *
 * @param health salud base en puntos (1 corazón = 2 puntos)
 * @param armor  armadura base en puntos (1 escudo = 2 puntos)
 * @param damage daño de ataque base en puntos (0 si el mob no tiene atributo de ataque)
 * @param icon   huevo de aparición del mob, o vacío si no tiene (solo se muestra si está desbloqueado)
 */
public record MobEntry(ResourceLocation id, String namespace, Component displayName, String sortName,
                       MobKind kind, float health, float armor, float damage, ItemStack icon) {
 
    public float hearts() {
        return health / 2.0F;
    }
 
    public float shields() {
        return armor / 2.0F;
    }
}