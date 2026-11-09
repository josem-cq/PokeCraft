package com.jose.pokecraft.event;
 
import com.jose.pokecraft.PokeCraftMod;
import com.jose.pokecraft.ai.MobOrderManager;
import com.jose.pokecraft.pokedex.PokedexManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
 
import java.util.UUID;
 
/** Desbloqueos de la PokéDex por eliminación. (Las capturas se registran en PokeballItem y MobJarBlock.) */
@EventBusSubscriber(modid = PokeCraftMod.MOD_ID)
public class PokedexEventHandlers {
 
    /**
     * Un mob muerto se desbloquea para quien lo eliminó: el jugador (también con flechas, tridentes...) o un mob
     * aliado suyo capturado, que acredita a su dueño. Prioridad mínima para ver el evento ya cancelado o no por
     * otros mods y no registrar muertes que en realidad no ocurren.
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingDeath(LivingDeathEvent event) {
        LivingEntity dead = event.getEntity();
        if (dead.level().isClientSide() || !(dead instanceof Mob)) return;
 
        ServerPlayer credited = null;
        Entity killer = event.getSource().getEntity();
        if (killer instanceof ServerPlayer player) {
            credited = player;
        } else if (killer instanceof Mob ally && MobOrderManager.isFriendly(ally)) {
            UUID ownerId = MobOrderManager.getOwnerId(ally);
            if (ownerId != null && dead.getServer() != null) {
                credited = dead.getServer().getPlayerList().getPlayer(ownerId);
            }
        }
 
        if (credited != null) {
            PokedexManager.unlock(credited, dead);
        }
    }
}