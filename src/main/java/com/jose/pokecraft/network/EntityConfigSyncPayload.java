package com.jose.pokecraft.network;
 
import com.jose.pokecraft.PokeCraftMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
 
import java.util.HashMap;
import java.util.Map;
 
/**
 * Servidor -> cliente: valores de la configuración de entidades (id -> activa). Con replace=true sustituye todo lo que
 * el cliente tenía (al entrar al mundo); con replace=false aplica solo los cambios recibidos (edición en vivo).
 */
public record EntityConfigSyncPayload(boolean replace, Map<ResourceLocation, Boolean> values) implements CustomPacketPayload {
 
    public static final Type<EntityConfigSyncPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(PokeCraftMod.MOD_ID, "entity_config_sync"));
 
    public static final StreamCodec<FriendlyByteBuf, EntityConfigSyncPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public EntityConfigSyncPayload decode(FriendlyByteBuf buf) {
            boolean replace = buf.readBoolean();
            int size = buf.readVarInt();
            Map<ResourceLocation, Boolean> map = new HashMap<>(Math.min(size, 8192));
            for (int i = 0; i < size; i++) {
                map.put(buf.readResourceLocation(), buf.readBoolean());
            }
            return new EntityConfigSyncPayload(replace, map);
        }
 
        @Override
        public void encode(FriendlyByteBuf buf, EntityConfigSyncPayload payload) {
            buf.writeBoolean(payload.replace());
            buf.writeVarInt(payload.values().size());
            payload.values().forEach((id, value) -> {
                buf.writeResourceLocation(id);
                buf.writeBoolean(value);
            });
        }
    };
 
    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
