package com.jose.pokecraft.network;
 
import com.jose.pokecraft.PokeCraftMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
 
/** Cliente -> servidor: un operador activa/desactiva una entidad (se aplica en vivo; el servidor valida el permiso). */
public record EntityConfigEditPayload(ResourceLocation id, boolean value) implements CustomPacketPayload {
 
    public static final Type<EntityConfigEditPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(PokeCraftMod.MOD_ID, "entity_config_edit"));
 
    public static final StreamCodec<ByteBuf, EntityConfigEditPayload> STREAM_CODEC = StreamCodec.composite(
            ResourceLocation.STREAM_CODEC, EntityConfigEditPayload::id,
            ByteBufCodecs.BOOL, EntityConfigEditPayload::value,
            EntityConfigEditPayload::new);
 
    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
