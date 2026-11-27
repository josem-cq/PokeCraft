package com.jose.pokecraft.level;

import com.jose.pokecraft.item.PokeballItem;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArrowItem;
import net.minecraft.world.item.Equipable;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.PotionItem;
import org.jetbrains.annotations.Nullable;

/**
 * Los 8 slots de un mob aliado. El ORDINAL es el índice del slot en la interfaz y el bit del desbloqueo
 * (1 << ordinal) en la máscara que se guarda en el NBT: no reordenes los valores existentes, añade nuevos al final.
 *
 * Los 6 primeros que tienen {@link #equipment()} son el equipamiento REAL del mob (ArmorItems / HandItems del NBT de la
 * entidad); la poción y la bola de reserva se guardan en los datos persistentes (ver {@link MobLevelData}).
 */
public enum MobSlot {
    HEAD("head", "armor", EquipmentSlot.HEAD, 3),
    CHEST("chest", "armor", EquipmentSlot.CHEST, 2),
    LEGS("legs", "armor", EquipmentSlot.LEGS, 1),
    FEET("feet", "armor", EquipmentSlot.FEET, 0),
    MAIN_HAND("main_hand", "main_hand", EquipmentSlot.MAINHAND, 0),
    OFF_HAND("off_hand", "off_hand", EquipmentSlot.OFFHAND, 1),
    POTION("potion", "potion", null, -1),
    RESERVE_BALL("reserve_ball", "reserve_ball", null, -1);

    private final String key;
    private final String hintKey;
    @Nullable
    private final EquipmentSlot equipment;
    private final int tagIndex;

    MobSlot(String key, String hintKey, @Nullable EquipmentSlot equipment, int tagIndex) {
        this.key = key;
        this.hintKey = hintKey;
        this.equipment = equipment;
        this.tagIndex = tagIndex;
    }

    @Nullable
    public EquipmentSlot equipment() {
        return equipment;
    }

    public boolean isArmor() {
        return equipment != null && equipment.getType() == EquipmentSlot.Type.HUMANOID_ARMOR;
    }

    public boolean isHand() {
        return equipment != null && equipment.getType() == EquipmentSlot.Type.HAND;
    }

    /** Posición dentro de la lista ArmorItems (pies 0 ... cabeza 3) o HandItems (principal 0, secundaria 1). */
    public int tagIndex() {
        return tagIndex;
    }

    public int bit() {
        return 1 << ordinal();
    }

    public Component label() {
        return Component.translatable("pokecraft.slot." + key);
    }

    public Component hint() {
        return Component.translatable("pokecraft.slot." + hintKey + ".hint");
    }

    public boolean isValid(ItemStack stack) {
        if (stack.isEmpty()) return false;
        return switch (this) {
            case HEAD, CHEST, LEGS, FEET -> stack.getItem() instanceof Equipable equipable && equipable.getEquipmentSlot() == equipment;
            case MAIN_HAND -> !(stack.getItem() instanceof PotionItem) && !(stack.getItem() instanceof ArrowItem)
                    && !(stack.getItem() instanceof PokeballItem) && !(stack.getItem() instanceof ArmorItem)
                    && !stack.is(Items.ELYTRA) && !stack.is(Items.FIREWORK_ROCKET);
            case OFF_HAND -> stack.is(Items.TOTEM_OF_UNDYING) || stack.getItem() instanceof ArrowItem || stack.is(Items.FIREWORK_ROCKET);
            case POTION -> MobLevelManager.hasTimedEffect(stack);
            case RESERVE_BALL -> stack.getItem() instanceof PokeballItem && !MobLevelManager.hasCapturedMob(stack);
        };
    }

    public static MobSlot byIndex(int index) {
        MobSlot[] all = values();
        return all[Math.max(0, Math.min(index, all.length - 1))];
    }
}