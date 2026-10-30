package com.jose.pokecraft.event;

import com.jose.pokecraft.PokeCraftMod;
import com.jose.pokecraft.item.PokeballItem;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AnvilUpdateEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import java.util.UUID;

@EventBusSubscriber(modid = PokeCraftMod.MOD_ID)
public class ModEventHandlers {

    @SubscribeEvent
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (event.getLevel().isClientSide()) return;
        Player player = event.getEntity();
        ItemStack heldItem = player.getItemInHand(event.getHand());

        if (event.getTarget() instanceof LivingEntity target) {
            CompoundTag persistent = target.getPersistentData();

            // 1. RECUPERAR MOB CON MANO VACÍA (Únicamente el dueño) - Infinito y conserva el Tier exacto
            if (heldItem.isEmpty() && persistent.contains("PokeCraftFriendly")) {
                if (player.getUUID().equals(persistent.getUUID("OwnerUUID"))) {
                    Item pokeballItem = PokeCraftMod.POKEBALL.get();
                    if (persistent.contains("PokeballRegistryName")) {
                        Item foundItem = BuiltInRegistries.ITEM.get(net.minecraft.resources.ResourceLocation.parse(persistent.getString("PokeballRegistryName")));
                        if (foundItem != null) {
                            pokeballItem = foundItem;
                        }
                    }

                    ItemStack filledPokeball = new ItemStack(pokeballItem);
                    CompoundTag entityTag = new CompoundTag();
                    target.save(entityTag);
                    entityTag.remove("UUID");
                    
                    CompoundTag tag = filledPokeball.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
                    tag.put("CapturedEntity", entityTag);
                    filledPokeball.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));

                    if (target.hasCustomName()) {
                        filledPokeball.set(DataComponents.CUSTOM_NAME, target.getCustomName());
                    }

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

            // 2. DAR ÓRDENES TÁCTICAS EXCLUSIVAMENTE CON EL PALO
            if (heldItem.is(Items.STICK) && persistent.contains("PokeCraftFriendly")) {
                if (player.getUUID().equals(persistent.getUUID("OwnerUUID"))) {
                    int state = persistent.getInt("MobOrderState");
                    state = (state + 1) % 3;
                    persistent.putInt("MobOrderState", state);

                    String stateStr = switch (state) {
                        case 0 -> "siguiendo";
                        case 1 -> "quieto";
                        case 2 -> "protegiendo";
                        default -> "siguiendo";
                    };

                    Component mobName = target.hasCustomName() ? target.getCustomName() : target.getDisplayName();
                    Component message = Component.literal("[").append(mobName).append("] ahora está " + stateStr);
                    
                    player.displayClientMessage(message, true);

                    event.getLevel().playSound(null, target.blockPosition(), SoundEvents.NOTE_BLOCK_PLING.value(), SoundSource.PLAYERS, 0.8f, 1.2f);
                    event.setCancellationResult(InteractionResult.SUCCESS);
                    event.setCanceled(true);
                    return;
                }
            }

            // 3. CURAR AL MOB CON COMIDA
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

    // Prevenir que los mobs aliados ataquen a su dueño y limpiar su objetivo hostil
    @SubscribeEvent
    public static void onLivingIncomingDamage(LivingIncomingDamageEvent event) {
        if (event.getSource().getEntity() instanceof LivingEntity attacker && event.getEntity() instanceof Player player) {
            if (attacker.getPersistentData().contains("PokeCraftFriendly")) {
                UUID ownerUUID = attacker.getPersistentData().getUUID("OwnerUUID");
                if (player.getUUID().equals(ownerUUID)) {
                    event.setCanceled(true);
                    if (attacker instanceof Mob mob) {
                        mob.setTarget(null);
                    }
                }
            }
        }
    }

    // Sincronización de nombre en Yunque
    @SubscribeEvent
    public static void onAnvilUpdate(AnvilUpdateEvent event) {
        ItemStack left = event.getLeft();
        if (left.getItem() instanceof PokeballItem) {
            CustomData customData = left.get(DataComponents.CUSTOM_DATA);
            if (customData != null) {
                CompoundTag tag = customData.copyTag();
                if (tag.contains("CapturedEntity") && event.getName() != null && !event.getName().isEmpty()) {
                    ItemStack output = left.copy();
                    Component newName = Component.literal(event.getName());
                    output.set(DataComponents.CUSTOM_NAME, newName);

                    CompoundTag entityTag = tag.getCompound("CapturedEntity");
                    var registries = ServerLifecycleHooks.getCurrentServer() != null ? 
                        ServerLifecycleHooks.getCurrentServer().registryAccess() : null;
                    if (registries != null) {
                        entityTag.putString("CustomName", Component.Serializer.toJson(newName, registries));
                    }
                    tag.put("CapturedEntity", entityTag);
                    output.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));

                    event.setOutput(output);
                    event.setCost(5);
                    event.setMaterialCost(1);
                }
            }
        }
    }
}