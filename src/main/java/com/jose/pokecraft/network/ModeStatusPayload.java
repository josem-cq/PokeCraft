package com.jose.pokecraft.network;

import com.jose.pokecraft.PokeCraftMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Servidor -> cliente: las dos líneas del indicador de un aliado (modo arriba, estado PVP/PVE debajo). Se envían como
 * componentes ya construidos (traducibles), así que cada cliente los muestra en su idioma. La barra de acción vanilla
 * solo admite una línea, por eso este indicador se dibuja con su propia capa de HUD (ModeStatusOverlay).
 */
public record ModeStatusPayload(Component mode, Component pvp) implements CustomPacketPayload {

    public static final Type<ModeStatusPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(PokeCraftMod.MOD_ID, "mode_status"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ModeStatusPayload> STREAM_CODEC = StreamCodec.composite(
            ComponentSerialization.STREAM_CODEC, ModeStatusPayload::mode,
            ComponentSerialization.STREAM_CODEC, ModeStatusPayload::pvp,
            ModeStatusPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}