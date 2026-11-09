package com.jose.pokecraft.pokedex;
 
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
 
/** Clasificación de un mob en la PokéDex. El orden del enum es el que usa el ordenamiento "Tipo". */
public enum MobKind {
    PASSIVE("passive", ChatFormatting.GREEN),
    NEUTRAL("neutral", ChatFormatting.YELLOW),
    HOSTILE("hostile", ChatFormatting.RED);
 
    private final String key;
    private final ChatFormatting color;
 
    MobKind(String key, ChatFormatting color) {
        this.key = key;
        this.color = color;
    }
 
    /** Nombre del tipo ya coloreado (verde / amarillo / rojo). */
    public Component label() {
        return Component.translatable("pokecraft.pokedex.kind." + key).withStyle(color);
    }
 
    /** Nombre del tipo sin color, para menús y filtros. */
    public Component plainLabel() {
        return Component.translatable("pokecraft.pokedex.kind." + key);
    }
}