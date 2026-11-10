package com.jose.pokecraft.event;
 
import com.jose.pokecraft.PokeCraftMod;
import com.jose.pokecraft.network.EntityConfigSyncPayload;
import com.jose.pokecraft.pokedex.EntityConfig;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.neoforged.neoforge.network.PacketDistributor;
 
/** Carga la configuración de entidades al arrancar el servidor y se la envía a cada jugador que entra. */
@EventBusSubscriber(modid = PokeCraftMod.MOD_ID)
public class EntityConfigEvents {
 
    @SubscribeEvent
    public static void onServerAboutToStart(ServerAboutToStartEvent event) {
        EntityConfig.load();
    }
 
    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            PacketDistributor.sendToPlayer(player, new EntityConfigSyncPayload(true, EntityConfig.snapshot()));
        }
    }
}
