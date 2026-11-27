package com.jose.pokecraft.item;

import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/**
 * Golosina de experiencia. No es comida vanilla (sin FoodProperties) para que no la gaste la curación con comida:
 * la entrega el manejador de interacción de MobLevelEvents (clic derecho sobre un aliado propio), que suma la XP de
 * 1 nivel a la barra del mob sin subirle el nivel directamente.
 */
public class XpTreatItem extends Item {
    public XpTreatItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        super.appendHoverText(stack, context, tooltipComponents, tooltipFlag);
        tooltipComponents.add(Component.translatable("tooltip.pokecraft.xp_treat.1").withStyle(ChatFormatting.GRAY));
        tooltipComponents.add(Component.translatable("tooltip.pokecraft.xp_treat.2").withStyle(ChatFormatting.GREEN));
        tooltipComponents.add(Component.translatable("tooltip.pokecraft.xp_treat.3").withStyle(ChatFormatting.DARK_GRAY));
    }
}
