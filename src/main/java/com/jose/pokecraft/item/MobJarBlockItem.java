package com.jose.pokecraft.item;
 
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.Block;
 
import java.util.List;
 
public class MobJarBlockItem extends BlockItem {
    public MobJarBlockItem(Block block, Properties properties) {
        super(block, properties);
    }
 
    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        super.appendHoverText(stack, context, tooltipComponents, tooltipFlag);
 
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        if (customData == null) return;
 
        CompoundTag tag = customData.copyTag();
        CompoundTag entityTag = tag.contains("CapturedEntity") ? tag.getCompound("CapturedEntity") : tag;
        if (!entityTag.contains("id")) return;
 
        tooltipComponents.add(Component.literal("§8-----------------"));
        // Nombre, bola de origen, Salud, Armadura, Daño, efectos e ID: todo calculado de forma universal.
        CapturedMobStats.appendTooltip(tooltipComponents, entityTag, context, true);
    }
}
 