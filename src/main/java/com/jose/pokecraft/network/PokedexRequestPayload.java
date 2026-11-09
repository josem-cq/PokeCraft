package com.jose.pokecraft.network;
 
import com.jose.pokecraft.PokeCraftMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
 
/** Cliente -> servidor: "voy a abrir la PokéDex, envíame mis descubrimientos". No lleva datos. */
public record PokedexRequestPayload() implements CustomPacketPayload {
    public static final Type<PokedexRequestPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(PokeCraftMod.MOD_ID, "pokedex_request"));
    public static final PokedexRequestPayload INSTANCE = new PokedexRequestPayload();
    public static final StreamCodec<ByteBuf, PokedexRequestPayload> STREAM_CODEC = StreamCodec.unit(INSTANCE);
 
    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}