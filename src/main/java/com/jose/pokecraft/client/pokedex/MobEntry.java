package com.jose.pokecraft.client.pokedex;
 
import com.jose.pokecraft.pokedex.MobKind;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
 
/**
 * Ficha estática de un tipo de entidad del registro (no depende del progreso del jugador).
 * Las estadísticas son las BASE del tipo (AttributeSupplier por defecto), sin modificadores de mods de dificultad.
 *
 * @param health   salud base en puntos (1 corazón = 2 puntos)
 * @param armor    armadura base en puntos (1 escudo = 2 puntos)
 * @param damage   daño de ataque base en puntos (0 si no tiene atributo de ataque)
 * @param icon     huevo de aparición REAL del tipo, o vacío si no tiene
 * @param debugEgg huevo de gallina que genera esta entidad (solo para depuración en creativo); siempre presente
 * @param tabKey   pestaña de creativo (o mod) a la que pertenece en la PokéDex, ver {@link MobCatalog.TabInfo}
 */
public record MobEntry(ResourceLocation id, String namespace, Component displayName, String sortName,
                       MobKind kind, float health, float armor, float damage, ItemStack icon,
                       ItemStack debugEgg, String tabKey) {
 
    public float hearts() {
        return health / 2.0F;
    }
 
    public float shields() {
        return armor / 2.0F;
    }
 
    public boolean hasEgg() {
        return !icon.isEmpty();
    }
 
    /** Lo que se entrega en creativo al hacer clic: el huevo real o, si no existe, el de depuración. */
    public ItemStack giveStack() {
        return hasEgg() ? icon : debugEgg;
    }
 
    public MobEntry withTabKey(String key) {
        return new MobEntry(id, namespace, displayName, sortName, kind, health, armor, damage, icon, debugEgg, key);
    }
}
