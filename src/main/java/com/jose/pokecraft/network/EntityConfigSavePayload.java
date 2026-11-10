package com.jose.pokecraft.network;
 
import com.jose.pokecraft.PokeCraftMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
 
/** Cliente -> servidor: "Guardar". El servidor (si el jugador es operador) escribe config/pokecraft-entities.json. */
public record EntityConfigSavePayload() implements CustomPacketPayload {
    public static final Type<EntityConfigSavePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(PokeCraftMod.MOD_ID, "entity_config_save"));
    public static final EntityConfigSavePayload INSTANCE = new EntityConfigSavePayload();
    public static final StreamCodec<ByteBuf, EntityConfigSavePayload> STREAM_CODEC = StreamCodec.unit(INSTANCE);
 
    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
