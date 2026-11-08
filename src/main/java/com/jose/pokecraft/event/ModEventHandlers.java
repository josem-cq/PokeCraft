package com.jose.pokecraft.event;
 
import com.jose.pokecraft.PokeCraftMod;
import com.jose.pokecraft.ai.DirectAttackManager;
import com.jose.pokecraft.ai.MobOrder;
import com.jose.pokecraft.ai.MobOrderManager;
import com.jose.pokecraft.item.PokeballItem;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
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
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
 
import java.util.UUID;
 
@EventBusSubscriber(modid = PokeCraftMod.MOD_ID)
public class ModEventHandlers {
 
    /**
     * Las metas (Goal) no se serializan: tras cargar un chunk, reiniciar el servidor, cambiar de dimensión o
     * liberar el mob desde la bola, el mob vuelve con su IA vanilla. Aquí se reinstala la IA del modo guardado.
     */
    @SubscribeEvent
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) return;
        if (event.getEntity() instanceof Mob mob && MobOrderManager.isFriendly(mob)) {
            MobOrderManager.apply(mob, MobOrderManager.getOrder(mob));
        }
    }
 
    /**
     * Filtro previo a cualquier cambio de objetivo de un mob de PokeCraft, venga de donde venga (metas nativas,
     * ira de neutrales, otros mods): si el modo actual no permite ese objetivo (cualquiera en Seguir/Quieto;
     * el dueño y sus aliados en el resto), se sustituye por "sin objetivo". Es lo que permite que el modo
     * Libre conserve la IA nativa sin que el mob llegue a apuntar al dueño ni un instante.
     */
    @SubscribeEvent
    public static void onLivingChangeTarget(LivingChangeTargetEvent event) {
        if (!(event.getEntity() instanceof Mob mob) || mob.level().isClientSide()) return;
        LivingEntity newTarget = event.getNewAboutToBeSetTarget();
        if (newTarget == null || !MobOrderManager.isFriendly(mob)) return;
        if (!MobOrderManager.isTargetAllowed(mob, newTarget, MobOrderManager.getOrder(mob))) {
            event.setNewAboutToBeSetTarget(null);
        }
    }
 
    // ---------------------------------------------------------------------------------------------
    // Órdenes con el palo en la mano principal
    // ---------------------------------------------------------------------------------------------
 
    /**
     * Clic izquierdo con el palo sobre una entidad: ataque directo de todos los aliados. Shift + clic izquierdo:
     * cancelarlo. El evento NO se cancela, así que el golpe y la animación vanilla del jugador se ejecutan igual.
     * (Con el palo en la mano secundaria no se hace nada: el ataque siempre usa la mano principal.)
     */
    @SubscribeEvent
    public static void onAttackEntity(AttackEntityEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!player.getMainHandItem().is(Items.STICK)) return;
 
        if (player.isShiftKeyDown()) {
            DirectAttackManager.cancelDirectAttack(player);
        } else {
            DirectAttackManager.startDirectAttack(player, event.getTarget());
        }
    }
 
    /** Shift + clic izquierdo con el palo sobre un bloque: también cancela (no hace falta apuntar a una entidad). */
    @SubscribeEvent
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (event.getAction() != PlayerInteractEvent.LeftClickBlock.Action.START) return;
        if (player.isShiftKeyDown() && player.getMainHandItem().is(Items.STICK)) {
            DirectAttackManager.cancelDirectAttack(player);
        }
    }
 
    /**
     * Shift + clic derecho con el palo: "Todos quietos". RightClickItem se dispara una vez por mano; solo se
     * procesa la principal. Se dispara tanto al aire como apuntando a un bloque o a una entidad (el cliente
     * pasa de la interacción a "usar objeto" cuando la interacción no se consume).
     */
    @SubscribeEvent
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (event.getHand() != InteractionHand.MAIN_HAND) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (player.isShiftKeyDown() && player.getMainHandItem().is(Items.STICK)) {
            DirectAttackManager.stayAll(player);
        }
    }
 
    /** Reinstala el modo de los mobs cuyo ataque directo terminó (no se puede hacer dentro del tick de sus metas). */
    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MobOrderManager.flushPendingRefresh();
    }
 
    @SubscribeEvent
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (event.getLevel().isClientSide()) return;
        Player player = event.getEntity();
        ItemStack mainHand = player.getMainHandItem();
        ItemStack offHand = player.getOffhandItem();
        ItemStack heldItem = event.getItemStack();
 
        if (event.getTarget() instanceof LivingEntity target) {
            CompoundTag persistent = target.getPersistentData();
 
            // Verificar que sea un aliado leal de este jugador
            if (!persistent.contains("PokeCraftFriendly")) return;
            if (!player.getUUID().equals(persistent.getUUID("OwnerUUID"))) return;
 
            // Shift + palo en la mano principal es la orden "Todos quietos" (se resuelve en onRightClickItem, que el
            // cliente dispara a continuación porque aquí no se consume la interacción). Tiene prioridad sobre
            // recuperar al mob; para recuperarlo hay que agacharse con cualquier otro objeto o con la mano vacía.
            if (player.isShiftKeyDown() && mainHand.is(Items.STICK)) return;
 
            // 1. RECUPERAR AL MOB: Solo si el jugador está agachado (Shift + Clic derecho)
            if (player.isShiftKeyDown()) {
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
 
            // 2. DAR ÓRDENES TÁCTICAS CON EL PALO (Mano principal o secundaria)
            boolean mainHasStick = mainHand.is(Items.STICK);
            boolean offHasStick = offHand.is(Items.STICK);
 
            if (mainHasStick || offHasStick) {
                // Vanilla envía UN paquete de interacción POR MANO por cada clic derecho (el cliente no
                // consume la interacción, así que prueba main hand y luego off hand). Hay que cambiar de modo
                // solo en el evento de la mano que realmente sostiene el palo; si no, un clic con el palo en la
                // mano principal avanzaba el ciclo DOS veces y con 4 modos solo se veían 2 de ellos.
                boolean handlesThisEvent = event.getItemStack().is(Items.STICK)
                        && (event.getHand() == InteractionHand.MAIN_HAND || !mainHasStick);
 
                if (!handlesThisEvent) {
                    // Evento de la otra mano: se absorbe sin hacer nada (evita acciones vanilla con esa mano).
                    event.setCancellationResult(InteractionResult.SUCCESS);
                    event.setCanceled(true);
                    return;
                }
 
                // Las órdenes solo tienen sentido en Mob (los que tienen IA de metas)
                if (target instanceof Mob mob) {
                    // Cambia al siguiente modo (seguir -> quieto -> proteger -> ataque), lo guarda en el NBT
                    // persistente y reinstala la IA del mob en el mismo tick.
                    MobOrder newOrder = MobOrderManager.cycleOrder(mob);
 
                    Component mobName = target.hasCustomName() ? target.getCustomName() : target.getDisplayName();
                    player.displayClientMessage(
                            Component.translatable("message.pokecraft.order_changed", mobName, newOrder.displayName()), true);
 
                    event.getLevel().playSound(null, target.blockPosition(), SoundEvents.NOTE_BLOCK_PLING.value(), SoundSource.PLAYERS, 0.8f, 1.2f);
                }
                event.setCancellationResult(InteractionResult.SUCCESS);
                event.setCanceled(true);
                return;
            }
 
            // 3. CURAR AL MOB CON COMIDA (si sostiene comida válida)
            FoodProperties food = heldItem.getFoodProperties(target);
            if (food != null && target.getHealth() < target.getMaxHealth()) {
                target.heal(food.nutrition() * 2);
                if (!player.getAbilities().instabuild) {
                    heldItem.shrink(1);
                }
                event.getLevel().playSound(null, target.blockPosition(), SoundEvents.GENERIC_EAT, SoundSource.NEUTRAL, 1.0f, 1.0f);
                event.setCancellationResult(InteractionResult.SUCCESS);
                event.setCanceled(true);
                return;
            }
 
            // 4. SI NO TIENE PALO, NO SE DA SHIFT Y NO ES COMIDA -> NO SUCEDE NADA
            //event.setCancellationResult(InteractionResult.SUCCESS);
            //event.setCanceled(true);
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