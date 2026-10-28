package com.jose.pokecraft.item;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.neoforge.items.ItemStackHandler;

public class QuiverItem extends Item {
    public QuiverItem(Properties properties) {
        super(properties);
    }

    // Crea y deserializa el contenedor interno de 5 slots de la aljaba
    public static ItemStackHandler getQuiverInventory(ItemStack quiverStack) {
        ItemStackHandler handler = new ItemStackHandler(5) {
            @Override
            public boolean isItemValid(int slot, ItemStack stack) {
                return stack.getItem() instanceof net.minecraft.world.item.ArrowItem;
            }
        };
        CustomData customData = quiverStack.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
        if (customData != null) {
            CompoundTag tag = customData.copyTag();
            if (tag.contains("Inventory")) {
                handler.deserializeNBT(null, tag.getCompound("Inventory"));
            }
        }
        return handler;
    }

    public static void saveQuiverInventory(ItemStack quiverStack, ItemStackHandler handler) {
        CompoundTag tag = quiverStack.getOrDefault(net.minecraft.core.component.DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        tag.put("Inventory", handler.serializeNBT(null));
        quiverStack.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }
}