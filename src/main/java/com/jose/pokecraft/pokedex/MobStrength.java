package com.jose.pokecraft.pokedex;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * Categoría de fortaleza de un mob. El orden del enum es de menor a mayor fortaleza (el ordinal es el índice de
 * tier que usa {@link StrengthClassifier}). No se persiste en disco, así que reordenar o ampliar es seguro.
 */
public enum MobStrength {
    VERY_WEAK("very_weak", ChatFormatting.GRAY),
    WEAK("weak", ChatFormatting.GREEN),
    NORMAL("normal", ChatFormatting.BLUE),
    // LIGHT_PURPLE (y no DARK_PURPLE): el morado oscuro apenas se lee sobre el panel gris y los tooltips negros.
    STRONG("strong", ChatFormatting.LIGHT_PURPLE),
    LEGENDARY("legendary", ChatFormatting.GOLD);

    private final String key;
    private final ChatFormatting color;

    MobStrength(String key, ChatFormatting color) {
        this.key = key;
        this.color = color;
    }

    public ChatFormatting color() {
        return color;
    }

    /** Nombre de la categoría ya coloreado. */
    public MutableComponent label() {
        return Component.translatable("pokecraft.pokedex.strength." + key).withStyle(color);
    }

    /** Nombre de la categoría sin color. */
    public MutableComponent plainLabel() {
        return Component.translatable("pokecraft.pokedex.strength." + key);
    }

    /**
     * Copia del componente dado pintada con el color de la categoría (no modifica el original).
     *
     * El color se aplica a TODO el árbol (raíz y siblings), no solo a la raíz: así un nombre personalizado que ya
     * traía colores propios también queda con el color de su categoría. Se conserva el texto, el resto de estilos
     * (negrita, cursiva, hover...) y los componentes traducibles (se siguen traduciendo en el idioma del jugador).
     */
    public MutableComponent colorize(Component name) {
        return paint(name, color);
    }

    private static MutableComponent paint(Component source, ChatFormatting color) {
        MutableComponent copy = MutableComponent.create(source.getContents())
                .setStyle(source.getStyle().withColor(color));
        for (Component sibling : source.getSiblings()) {
            copy.append(paint(sibling, color));
        }
        return copy;
    }

    public static MobStrength byIndex(int index) {
        MobStrength[] all = values();
        return all[Math.max(0, Math.min(index, all.length - 1))];
    }
}