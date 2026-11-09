package com.jose.pokecraft.client.pokedex;
 
import com.jose.pokecraft.network.PokedexSyncPayload;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
 
import java.util.HashMap;
import java.util.List;
import java.util.Map;
 
/**
 * Copia en el cliente de los descubrimientos del jugador, recibida al abrir la PokéDex. Todo el filtrado y
 * ordenamiento consulta este mapa en memoria, sin preguntar nada más al servidor.
 */
public final class ClientPokedexData {
    public record Discovery(long tick, long dayTime, ResourceLocation dimension, int x, int y, int z) {}
 
    private static Map<ResourceLocation, Discovery> discoveries = Map.of();
 
    private ClientPokedexData() {}
 
    public static void set(List<PokedexSyncPayload.Sync> list) {
        Map<ResourceLocation, Discovery> map = new HashMap<>();
        for (PokedexSyncPayload.Sync s : list) {
            map.put(s.id(), new Discovery(s.tick(), s.dayTime(), s.dimension(), s.x(), s.y(), s.z()));
        }
        discoveries = map;
    }
 
    public static boolean isUnlocked(ResourceLocation id) {
        return discoveries.containsKey(id);
    }
 
    @Nullable
    public static Discovery get(ResourceLocation id) {
        return discoveries.get(id);
    }
 
    public static void clear() {
        discoveries = Map.of();
    }
}