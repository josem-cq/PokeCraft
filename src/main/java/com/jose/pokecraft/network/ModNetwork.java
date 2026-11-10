package com.jose.pokecraft.network;
 
import com.jose.pokecraft.PokeCraftMod;
import com.jose.pokecraft.ai.DirectAttackManager;
import com.jose.pokecraft.client.pokedex.ClientPokedexHandler;
import com.jose.pokecraft.pokedex.EntityConfig;
import com.jose.pokecraft.pokedex.PokedexManager;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import java.util.Map;
 
// Sin "bus": NeoForge detecta solo que RegisterPayloadHandlersEvent es un evento del bus de mod.
@EventBusSubscriber(modid = PokeCraftMod.MOD_ID)
public class ModNetwork {
 
    @SubscribeEvent
    public static void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToServer(CancelDirectAttackPayload.TYPE, CancelDirectAttackPayload.STREAM_CODEC, ModNetwork::handleCancel);
        registrar.playToServer(DirectAttackRequestPayload.TYPE, DirectAttackRequestPayload.STREAM_CODEC, ModNetwork::handleDirectAttack);
 
        // PokéDex: el cliente pide sus datos al abrirla y el servidor responde con el mapa completo.
        registrar.playToServer(PokedexRequestPayload.TYPE, PokedexRequestPayload.STREAM_CODEC, ModNetwork::handlePokedexRequest);
        // La lambda (y no una referencia a método) evita cargar la clase de cliente en un servidor dedicado.
        registrar.playToClient(PokedexSyncPayload.TYPE, PokedexSyncPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> ClientPokedexHandler.handleSync(payload)));
 
        // Configuración de entidades activas: edición (solo operadores), guardado y copia hacia los clientes.
        registrar.playToServer(EntityConfigEditPayload.TYPE, EntityConfigEditPayload.STREAM_CODEC, ModNetwork::handleConfigEdit);
        registrar.playToServer(EntityConfigSavePayload.TYPE, EntityConfigSavePayload.STREAM_CODEC, ModNetwork::handleConfigSave);
        registrar.playToClient(EntityConfigSyncPayload.TYPE, EntityConfigSyncPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> ClientPokedexHandler.handleConfigSync(payload)));
    }
 
    /** Nivel de permisos mínimo para editar la configuración (operador). */
    private static final int EDIT_PERMISSION_LEVEL = 2;
 
    private static void handleConfigEdit(EntityConfigEditPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player) || !player.hasPermissions(EDIT_PERMISSION_LEVEL)) return;
            if (BuiltInRegistries.ENTITY_TYPE.getOptional(payload.id()).isEmpty()) return;
            EntityConfig.set(payload.id(), payload.value());
            PacketDistributor.sendToAllPlayers(new EntityConfigSyncPayload(false, Map.of(payload.id(), payload.value())));
        });
    }
 
    private static void handleConfigSave(EntityConfigSavePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player) || !player.hasPermissions(EDIT_PERMISSION_LEVEL)) return;
            boolean saved = EntityConfig.save();
            player.displayClientMessage(Component.translatable(
                    saved ? "message.pokecraft.config_saved" : "message.pokecraft.config_save_failed"), true);
        });
    }
 
    private static void handlePokedexRequest(PokedexRequestPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                PokedexManager.syncTo(player);
            }
        });
    }
 
    private static void handleDirectAttack(DirectAttackRequestPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            // Se revalida en el servidor: palo en la mano principal y sin Shift (con Shift es "cancelar").
            if (context.player() instanceof ServerPlayer player
                    && !player.isShiftKeyDown()
                    && player.getMainHandItem().is(Items.STICK)) {
                DirectAttackManager.startDirectAttackFromLook(player);
            }
        });
    }
 
    private static void handleCancel(CancelDirectAttackPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            // Se revalida en el servidor: nunca se confía solo en lo que dice el cliente.
            if (context.player() instanceof ServerPlayer player
                    && player.isShiftKeyDown()
                    && player.getMainHandItem().is(Items.STICK)) {
                DirectAttackManager.cancelDirectAttack(player);
            }
        });
    }
}