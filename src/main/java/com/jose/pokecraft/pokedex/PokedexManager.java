package com.jose.pokecraft.pokedex;
 
import com.jose.pokecraft.PokeCraftMod;
import com.jose.pokecraft.network.PokedexSyncPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;
 
import java.util.ArrayList;
import java.util.List;
 
/** Único punto de entrada para desbloquear mobs y sincronizar la PokéDex de un jugador. */
public final class PokedexManager {
    private PokedexManager() {}
 
    public static PokedexData get(ServerPlayer player) {
        return player.getData(PokeCraftMod.POKEDEX);
    }
 
    /** Desbloquea el tipo de la entidad dada, registrando su posición y momento actuales. */
    public static boolean unlock(ServerPlayer player, Entity entity) {
        return unlock(player, entity.getType(), entity.level(), entity.blockPosition());
    }
 
    /**
     * Desbloquea un tipo de mob de forma permanente. Si ya estaba desbloqueado no hace nada (el primer registro
     * conserva su lugar y fecha originales).
     *
     * @return true si fue un desbloqueo nuevo.
     */
    public static boolean unlock(ServerPlayer player, EntityType<?> type, Level level, BlockPos pos) {
        if (!CaptureRules.isCapturable(type)) return false; // mismo criterio que el catálogo y la captura
 
        ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(type);
        PokedexData data = get(player);
        PokedexData.Entry entry = new PokedexData.Entry(
                level.getGameTime(), level.getDayTime(), level.dimension().location(),
                pos.getX(), pos.getY(), pos.getZ());
 
        if (!data.unlock(id, entry)) return false;
 
        player.displayClientMessage(Component.translatable("message.pokecraft.pokedex_registered", type.getDescription()), true);
        return true;
    }
 
    /** Envía al jugador el mapa completo de descubrimientos (ids + lugar y fecha). */
    public static void syncTo(ServerPlayer player) {
        List<PokedexSyncPayload.Sync> list = new ArrayList<>();
        get(player).entries().forEach((id, e) -> list.add(new PokedexSyncPayload.Sync(
                id, e.discoveredTick(), e.dayTime(), e.dimension(), e.x(), e.y(), e.z())));
        PacketDistributor.sendToPlayer(player, new PokedexSyncPayload(list));
    }
}