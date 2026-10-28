package com.jose.pokecraft.event;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

@Mod.EventBusSubscriber(modid = com.jose.pokecraft.PokeCraftMod.MOD_ID)
public class ModEventHandlers {

    @SubscribeEvent
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (event.getLevel().isClientSide()) return;
        Player player = event.getEntity();
        ItemStack heldItem = player.getItemInHand(event.getHand());

        if (event.getTarget() instanceof LivingEntity target) {
            CompoundTag persistent = target.getPersistentData();

            // 1. RECUPERAR MOB CON MANO VACÍA (Únicamente el dueño)
            if (heldItem.isEmpty() && persistent.contains("PokeCraftFriendly")) {
                if (player.getUUID().equals(persistent.getUUID("OwnerUUID"))) {
                    ItemStack filledPokeball = new ItemStack(com.jose.pokecraft.PokeCraftMod.POKEBALL.get());
                    CompoundTag entityTag = new CompoundTag();
                    target.save(entityTag);
                    entityTag.remove("UUID");
                    filledPokeball.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA, net.minecraft.world.item.component.CustomData.of(entityTag));

                    if (!player.addItem(filledPokeball)) {
                        player.drop(filledPokeball, false);
                    }

                    event.getLevel().playSound(null, target.blockPosition(), SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 1.0f, 1.0f);
                    target.discard();

                    event.setCancellationResult(InteractionResult.SUCCESS);
                    event.setCanceled(true);
                    return;
                }
            }

            // 2. DAR ÓRDENES TÁCTICAS CON EL PALO (Quieto, Siguiendo, Protegiendo)
            if (heldItem.is(Items.STICK) && persistent.contains("PokeCraftFriendly")) {
                if (player.getUUID().equals(persistent.getUUID("OwnerUUID"))) {
                    int state = persistent.getInt("MobOrderState");
                    state = (state + 1) % 3;
                    persistent.putInt("MobOrderState", state);

                    String msg = switch (state) {
                        case 0 -> "§bModo: Siguiendo";
                        case 1 -> "§eModo: Quieto";
                        case 2 -> "§cModo: Protegiendo (Comportamiento de Lobo)";
                        default -> "";
                    };
                    player.sendSystemMessage(Component.literal(msg));

                    event.setCancellationResult(InteractionResult.SUCCESS);
                    event.setCanceled(true);
                    return;
                }
            }

            // 3. CURAR AL MOB CON CUALQUIER TIPO DE COMIDA
            FoodProperties food = heldItem.getFoodProperties(target);
            if (food != null && target.getHealth() < target.getMaxHealth() && persistent.contains("PokeCraftFriendly")) {
                target.heal(food.nutrition() * 2);
                if (!player.getAbilities().instabuild) {
                    heldItem.shrink(1);
                }
                event.getLevel().playSound(null, target.blockPosition(), SoundEvents.GENERIC_EAT, SoundSource.NEUTRAL, 1.0f, 1.0f);
                event.setCancellationResult(InteractionResult.SUCCESS);
                event.setCanceled(true);
            }
        }
    }
}