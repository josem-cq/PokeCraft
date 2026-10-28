package com.jose.pokecraft.item;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.ItemStackHandler;

public class QuiverItem extends Item {
    public QuiverItem(Properties properties) {
        super(properties);
    }

    // Crea y deserializa el contenedor interno de 5 slots de la aljaba
    public static ItemStackHandler getQuiverInventory(ItemStack quiverStack) {
        ItemStackHandler handler = new ItemStackHandler(5) {
            @Override
            public boolean isItemValid(int slot, ItemStack stack) {
                // Filtra para permitir únicamente flechas o proyectiles de tipo ArrowItem
                return stack.getItem() instanceof net.minecraft.world.item.ArrowItem;
            }
        };
        CompoundTag tag = quiverStack.getTag();
        if (tag != null && tag.contains("Inventory")) {
            handler.deserializeNBT(tag.getCompound("Inventory"));
        }
        return handler;
    }

    public static void saveQuiverInventory(ItemStack quiverStack, ItemStackHandler handler) {
        CompoundTag tag = quiverStack.getOrCreateTag();
        tag.put("Inventory", handler.serializeNBT());
    }
}