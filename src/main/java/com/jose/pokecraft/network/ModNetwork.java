package com.jose.pokecraft.network;
 
import com.jose.pokecraft.PokeCraftMod;
import com.jose.pokecraft.ai.DirectAttackManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
 
// Sin "bus": NeoForge detecta solo que RegisterPayloadHandlersEvent es un evento del bus de mod.
@EventBusSubscriber(modid = PokeCraftMod.MOD_ID)
public class ModNetwork {
 
    @SubscribeEvent
    public static void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToServer(CancelDirectAttackPayload.TYPE, CancelDirectAttackPayload.STREAM_CODEC, ModNetwork::handleCancel);
        registrar.playToServer(DirectAttackRequestPayload.TYPE, DirectAttackRequestPayload.STREAM_CODEC, ModNetwork::handleDirectAttack);
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