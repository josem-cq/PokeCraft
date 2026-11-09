package com.jose.pokecraft.pokedex;
 
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.common.util.INBTSerializable;
 
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
 
/**
 * Progreso de la PokéDex de UN jugador. Se guarda como Data Attachment del jugador (ver PokeCraftMod.POKEDEX),
 * por lo que se persiste con sus datos, sobrevive a reinicios y se copia al morir.
 *
 * Clave = id del tipo de entidad (p. ej. "minecraft:zombie"). La presencia de la clave significa "desbloqueado".
 *
 * Se serializa a NBT implementando {@link INBTSerializable} (y no con un Codec directo en el AttachmentType) porque
 * esa API es estable entre versiones de NeoForge; el formato en disco lo define el Codec interno.
 */
public class PokedexData implements INBTSerializable<CompoundTag> {
 
    /** Metadatos del primer registro de un mob. */
    public record Entry(long discoveredTick, long dayTime, ResourceLocation dimension, int x, int y, int z) {
        public static final Codec<Entry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.LONG.fieldOf("tick").forGetter(Entry::discoveredTick),
                Codec.LONG.fieldOf("day_time").forGetter(Entry::dayTime),
                ResourceLocation.CODEC.fieldOf("dimension").forGetter(Entry::dimension),
                Codec.INT.fieldOf("x").forGetter(Entry::x),
                Codec.INT.fieldOf("y").forGetter(Entry::y),
                Codec.INT.fieldOf("z").forGetter(Entry::z)
        ).apply(instance, Entry::new));
    }
 
    private static final Codec<Map<ResourceLocation, Entry>> ENTRIES_CODEC =
            Codec.unboundedMap(ResourceLocation.CODEC, Entry.CODEC);
 
    private final Map<ResourceLocation, Entry> entries = new LinkedHashMap<>();
 
    public boolean isUnlocked(ResourceLocation id) {
        return entries.containsKey(id);
    }
 
    /** @return true si es un desbloqueo nuevo; el primer registro nunca se sobrescribe. */
    public boolean unlock(ResourceLocation id, Entry entry) {
        return entries.putIfAbsent(id, entry) == null;
    }
 
    public Map<ResourceLocation, Entry> entries() {
        return Collections.unmodifiableMap(entries);
    }
 
    @Override
    public CompoundTag serializeNBT(HolderLookup.Provider provider) {
        Tag tag = ENTRIES_CODEC.encodeStart(NbtOps.INSTANCE, entries).result().orElse(null);
        return tag instanceof CompoundTag compound ? compound : new CompoundTag();
    }
 
    @Override
    public void deserializeNBT(HolderLookup.Provider provider, CompoundTag nbt) {
        entries.clear();
        // resultOrPartial: si una entrada estuviera corrupta se conserva el resto en vez de perder toda la PokéDex.
        ENTRIES_CODEC.parse(NbtOps.INSTANCE, nbt).resultOrPartial(error -> {}).ifPresent(entries::putAll);
    }
}