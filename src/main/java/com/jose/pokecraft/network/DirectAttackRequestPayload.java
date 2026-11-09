package com.jose.pokecraft.network;
 
import com.jose.pokecraft.PokeCraftMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
 
/**
 * Cliente -> servidor: "clic izquierdo con el palo que no golpeó a ninguna entidad" (al aire o a un bloque).
 * El servidor traza por su cuenta el rayo de hasta 256 bloques hacia donde mira el jugador, así que el paquete no
 * lleva datos y el cliente no puede falsear el objetivo.
 */
public record DirectAttackRequestPayload() implements CustomPacketPayload {
    public static final Type<DirectAttackRequestPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(PokeCraftMod.MOD_ID, "direct_attack_request"));
    public static final DirectAttackRequestPayload INSTANCE = new DirectAttackRequestPayload();
    public static final StreamCodec<ByteBuf, DirectAttackRequestPayload> STREAM_CODEC = StreamCodec.unit(INSTANCE);
 
    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}