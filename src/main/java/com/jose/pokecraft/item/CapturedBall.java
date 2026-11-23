package com.jose.pokecraft.item;

import com.jose.pokecraft.PokeCraftMod;
import com.jose.pokecraft.ai.MobOrderManager;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import org.jetbrains.annotations.Nullable;

/**
 * Única fuente de verdad sobre QUÉ bola (tier) contiene un mob guardado (bola, tarro o mob liberado en el mundo).
 *
 * La bola de origen vive en DOS sitios del NBT de la entidad guardada:
 *  - clave de primer nivel "PokeballRegistryName" dentro de "CapturedEntity": la escribe la captura y es la que lee
 *    el tarro y el tooltip. NO es un dato de la entidad, así que {@code Entity.save()} nunca la serializa;
 *  - "NeoForgeData.PokeballRegistryName" (datos persistentes del mob): la escribe la liberación y es la que viaja con
 *    el mob vivo.
 * Al recoger un mob del mundo se construye un NBT nuevo con {@code target.save()}: solo sobrevive la copia de
 * NeoForgeData. Si luego el tarro solo miraba la de primer nivel, la bola caía a la Poké Ball básica.
 *
 * Reglas para que no vuelva a pasar:
 *  - Lectura ({@link #resolve}): primero primer nivel, después NeoForgeData, y solo si no hay ninguna, el valor de
 *    reserva. Así también se recuperan las bolas ya "degradadas" de mundos existentes.
 *  - Escritura ({@link #createFilled}, {@link #stamp}): se graba SIEMPRE la de primer nivel al construir una bola
 *    llena o al guardar en un tarro. La de NeoForgeData la reescribe la liberación ({@link #stamp} sobre los datos
 *    persistentes).
 */
public final class CapturedBall {
    /** Clave dentro del CUSTOM_DATA de la bola / del tarro: NBT de la entidad capturada. */
    public static final String CAPTURED_KEY = "CapturedEntity";
    /** Id registrado de la bola de origen (p. ej. "pokecraft:masterball"). */
    public static final String BALL_KEY = "PokeballRegistryName";

    private CapturedBall() {}

    /** Bola de origen del NBT de una entidad guardada; si no consta ninguna válida, la Poké Ball básica. */
    public static Item resolve(CompoundTag entityTag) {
        return resolve(entityTag, PokeCraftMod.POKEBALL.get());
    }

    /**
     * Igual que {@link #resolve(CompoundTag)} pero con una reserva propia: al meter una bola en un tarro, la que el
     * jugador tiene en la mano es la mejor pista si el NBT no dice nada. Lo que conste en el NBT siempre manda.
     */
    public static Item resolve(CompoundTag entityTag, Item fallback) {
        Item found = read(entityTag);
        if (found == null && entityTag.contains(MobOrderManager.ENTITY_PERSISTENT_TAG, Tag.TAG_COMPOUND)) {
            found = read(entityTag.getCompound(MobOrderManager.ENTITY_PERSISTENT_TAG));
        }
        if (found != null) {
            return found;
        }
        return fallback instanceof PokeballItem ? fallback : PokeCraftMod.POKEBALL.get();
    }

    /**
     * Ojo: {@code BuiltInRegistries.ITEM.get(id)} NUNCA devuelve null (devuelve el aire si el id no existe) y
     * {@code ResourceLocation.parse} lanza excepción con un id mal formado. Un id corrupto o de un mod que ya no
     * está daba una bola vacía y el mob se perdía. Aquí se valida y se exige que sea una {@link PokeballItem}.
     */
    @Nullable
    private static Item read(CompoundTag tag) {
        if (!tag.contains(BALL_KEY, Tag.TAG_STRING)) {
            return null;
        }
        ResourceLocation id = ResourceLocation.tryParse(tag.getString(BALL_KEY));
        if (id == null) {
            return null;
        }
        return BuiltInRegistries.ITEM.getOptional(id).filter(item -> item instanceof PokeballItem).orElse(null);
    }

    /** Graba la bola en el compuesto dado (NBT de una entidad guardada o datos persistentes de una entidad viva). */
    public static void stamp(CompoundTag tag, Item ball) {
        tag.putString(BALL_KEY, BuiltInRegistries.ITEM.getKey(ball).toString());
    }

    /**
     * Crea una bola llena. Punto único de construcción para la captura, la recogida con Shift + clic derecho y la
     * extracción del tarro, de modo que el NBT siempre sale con la bola grabada. No modifica el tag recibido.
     */
    public static ItemStack createFilled(Item ball, CompoundTag entityTag, @Nullable Component customName) {
        Item safeBall = ball instanceof PokeballItem ? ball : PokeCraftMod.POKEBALL.get();

        CompoundTag captured = entityTag.copy();
        stamp(captured, safeBall);

        ItemStack stack = new ItemStack(safeBall);
        CompoundTag data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        data.put(CAPTURED_KEY, captured);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(data));

        if (customName != null) {
            stack.set(DataComponents.CUSTOM_NAME, customName);
        }
        return stack;
    }
}