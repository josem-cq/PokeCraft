package com.jose.pokecraft.level;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Progreso de nivel de UN mob aliado. Vive en los datos persistentes de la entidad (Entity#getPersistentData), que
 * NeoForge guarda bajo "NeoForgeData": por eso viaja solo con el mob dentro de la Poké Ball, del tarro y de la mesa.
 *
 * Reglas:
 *  - Niveles 1..{@link #MAX_LEVEL}. La XP llena la barra; cada barra llena suma 1 a {@link #available}.
 *  - Subir de nivel (botón + de la mesa) consume 1 disponible y suma 1 al nivel. Nivel + disponibles nunca pasa de 100.
 *  - Los slots empiezan bloqueados; cada hito de {@link #MILESTONES} da UN desbloqueo a elegir.
 */
public final class MobLevelData {
    public static final int MAX_LEVEL = 100;
    public static final int[] MILESTONES = {1, 5, 10, 20, 35, 50, 75, 100};

    public static final String KEY_LEVEL = "PokeCraftLevel";
    public static final String KEY_XP = "PokeCraftXp";
    public static final String KEY_AVAILABLE = "PokeCraftAvailable";
    public static final String KEY_UNLOCKED = "PokeCraftUnlockedSlots";
    public static final String KEY_POTION = "PokeCraftPotionSlot";
    public static final String KEY_RESERVE = "PokeCraftReserveSlot";

    public int level = 1;
    /** XP acumulada dentro de la barra actual (siempre menor que la XP por nivel). */
    public double xp = 0.0D;
    /** Niveles ya ganados que el jugador aún no ha aplicado con el botón +. */
    public int available = 0;
    public int unlockedMask = 0;
    public ItemStack potion = ItemStack.EMPTY;
    public ItemStack reserve = ItemStack.EMPTY;

    /** Lee los datos de un compuesto persistente. Sin registros (null) no se interpretan los ítems de los slots. */
    public static MobLevelData read(CompoundTag persistent, @Nullable HolderLookup.Provider registries) {
        MobLevelData data = new MobLevelData();
        if (persistent.contains(KEY_LEVEL, Tag.TAG_ANY_NUMERIC)) {
            data.level = Math.max(1, Math.min(MAX_LEVEL, persistent.getInt(KEY_LEVEL)));
        }
        data.xp = Math.max(0.0D, persistent.getDouble(KEY_XP));
        data.available = Math.max(0, Math.min(MAX_LEVEL - data.level, persistent.getInt(KEY_AVAILABLE)));
        data.unlockedMask = persistent.getInt(KEY_UNLOCKED) & 0xFF;
        if (registries != null) {
            data.potion = readStack(persistent, KEY_POTION, registries);
            data.reserve = readStack(persistent, KEY_RESERVE, registries);
        }
        return data;
    }

    private static ItemStack readStack(CompoundTag persistent, String key, HolderLookup.Provider registries) {
        if (!persistent.contains(key, Tag.TAG_COMPOUND)) return ItemStack.EMPTY;
        return ItemStack.parseOptional(registries, persistent.getCompound(key));
    }

    public void write(CompoundTag persistent, @Nullable HolderLookup.Provider registries) {
        persistent.putInt(KEY_LEVEL, level);
        persistent.putDouble(KEY_XP, xp);
        persistent.putInt(KEY_AVAILABLE, available);
        persistent.putInt(KEY_UNLOCKED, unlockedMask);
        if (registries != null) {
            writeStack(persistent, KEY_POTION, potion, registries);
            writeStack(persistent, KEY_RESERVE, reserve, registries);
        }
    }

    private static void writeStack(CompoundTag persistent, String key, ItemStack stack, HolderLookup.Provider registries) {
        if (stack.isEmpty()) {
            persistent.remove(key);
        } else {
            persistent.put(key, stack.save(registries));
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Slots
    // ---------------------------------------------------------------------------------------------

    public boolean isUnlocked(MobSlot slot) {
        return (unlockedMask & slot.bit()) != 0;
    }

    public int milestonesReached() {
        int count = 0;
        for (int milestone : MILESTONES) {
            if (level >= milestone) count++;
        }
        return count;
    }

    /** Desbloqueos que el jugador todavía puede repartir entre los slots bloqueados. */
    public int pendingUnlocks() {
        return Math.max(0, milestonesReached() - Integer.bitCount(unlockedMask));
    }

    /** Siguiente hito por encima del nivel actual, o -1 si ya pasó el último. */
    public int nextMilestone() {
        for (int milestone : MILESTONES) {
            if (milestone > level) return milestone;
        }
        return -1;
    }

    public boolean unlock(MobSlot slot) {
        if (pendingUnlocks() <= 0 || isUnlocked(slot)) return false;
        unlockedMask |= slot.bit();
        return true;
    }

    // ---------------------------------------------------------------------------------------------
    // XP y niveles
    // ---------------------------------------------------------------------------------------------

    /** Niveles que aún se pueden ganar antes del tope (cuenta los ya disponibles). */
    public int remainingCapacity() {
        return Math.max(0, MAX_LEVEL - level - available);
    }

    /** Suma XP a la barra; cada vez que alcanza {@code xpPerLevel} se reinicia y suma 1 nivel disponible. */
    public void addXp(double amount, double xpPerLevel) {
        if (!(amount > 0.0D) || !(xpPerLevel > 0.0D)) return;
        if (remainingCapacity() <= 0) {
            xp = 0.0D;
            return;
        }
        xp += amount;
        while (xp >= xpPerLevel && remainingCapacity() > 0) {
            xp -= xpPerLevel;
            available++;
        }
        if (remainingCapacity() <= 0) xp = 0.0D;
    }

    /** Comida de XP: equivale a llenar la barra una vez. false = ya no caben más niveles. */
    public boolean giveLevelOfXp(double xpPerLevel) {
        if (remainingCapacity() <= 0) return false;
        addXp(xpPerLevel, xpPerLevel);
        return true;
    }

    /** Botón +: consume 1 nivel disponible y sube 1 el nivel actual. */
    public boolean levelUp() {
        if (available <= 0 || level >= MAX_LEVEL) return false;
        available--;
        level++;
        return true;
    }

    public float progress(double xpPerLevel) {
        if (!(xpPerLevel > 0.0D)) return 0.0F;
        return (float) Math.max(0.0D, Math.min(1.0D, xp / xpPerLevel));
    }
}
