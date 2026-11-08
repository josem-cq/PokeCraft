package com.jose.pokecraft.network;
 
import com.jose.pokecraft.PokeCraftMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
 
/**
 * Cliente -> servidor: "Shift + clic izquierdo con el palo al aire". El evento LeftClickEmpty solo existe en el
 * cliente, así que hace falta avisar al servidor para cancelar el ataque directo. No lleva datos.
 */
public record CancelDirectAttackPayload() implements CustomPacketPayload {
    public static final Type<CancelDirectAttackPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(PokeCraftMod.MOD_ID, "cancel_direct_attack"));
    public static final CancelDirectAttackPayload INSTANCE = new CancelDirectAttackPayload();
    public static final StreamCodec<ByteBuf, CancelDirectAttackPayload> STREAM_CODEC = StreamCodec.unit(INSTANCE);
 
    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
 