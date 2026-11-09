package com.jose.pokecraft.network;
 
import com.jose.pokecraft.PokeCraftMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
 
import java.util.ArrayList;
import java.util.List;
 
/**
 * Servidor -> cliente: mapa completo de descubrimientos del jugador (id del mob + lugar y momento del primer registro).
 * Se envía una vez al abrir la PokéDex; el filtrado y el ordenamiento se hacen después en local.
 * Se codifica a mano porque cada entrada tiene más campos de los que admite StreamCodec.composite.
 */
public record PokedexSyncPayload(List<Sync> entries) implements CustomPacketPayload {
 
    public record Sync(ResourceLocation id, long tick, long dayTime, ResourceLocation dimension, int x, int y, int z) {}
 
    public static final Type<PokedexSyncPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(PokeCraftMod.MOD_ID, "pokedex_sync"));
 
    public static final StreamCodec<FriendlyByteBuf, PokedexSyncPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public PokedexSyncPayload decode(FriendlyByteBuf buf) {
            int size = buf.readVarInt();
            List<Sync> list = new ArrayList<>(Math.min(size, 4096));
            for (int i = 0; i < size; i++) {
                list.add(new Sync(
                        buf.readResourceLocation(),
                        buf.readVarLong(),
                        buf.readVarLong(),
                        buf.readResourceLocation(),
                        buf.readInt(), buf.readInt(), buf.readInt()));
            }
            return new PokedexSyncPayload(list);
        }
 
        @Override
        public void encode(FriendlyByteBuf buf, PokedexSyncPayload payload) {
            buf.writeVarInt(payload.entries().size());
            for (Sync s : payload.entries()) {
                buf.writeResourceLocation(s.id());
                buf.writeVarLong(s.tick());
                buf.writeVarLong(s.dayTime());
                buf.writeResourceLocation(s.dimension());
                buf.writeInt(s.x());
                buf.writeInt(s.y());
                buf.writeInt(s.z());
            }
        }
    };
 
    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}