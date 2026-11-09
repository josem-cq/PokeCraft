package com.jose.pokecraft.event;
 
import com.jose.pokecraft.PokeCraftMod;
import com.jose.pokecraft.network.CancelDirectAttackPayload;
import com.jose.pokecraft.network.DirectAttackRequestPayload;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.network.PacketDistributor;
 
/** Eventos de juego que solo existen en el cliente. El golpe/animación vanilla nunca se toca. */
@EventBusSubscriber(modid = PokeCraftMod.MOD_ID, value = Dist.CLIENT)
public class ClientForgeEvents {
 
    /**
     * Clic izquierdo al aire con el palo en la mano principal (el servidor no recibe ningún evento):
     *  - Con Shift: cancela el ataque directo.
     *  - Sin Shift: pide un ataque directo a lo que haya en la mira, hasta 256 bloques (lo resuelve el servidor).
     * Si el clic golpea una entidad al alcance normal, este evento no se dispara y lo atiende AttackEntityEvent.
     */
    @SubscribeEvent
    public static void onLeftClickEmpty(PlayerInteractEvent.LeftClickEmpty event) {
        Player player = event.getEntity();
        if (!player.getMainHandItem().is(Items.STICK)) return;
 
        if (player.isShiftKeyDown()) {
            PacketDistributor.sendToServer(CancelDirectAttackPayload.INSTANCE);
        } else {
            PacketDistributor.sendToServer(DirectAttackRequestPayload.INSTANCE);
        }
    }
 
    /**
     * Clic izquierdo con el palo apuntando a un bloque cercano: el rayo del servidor decide si hay una entidad
     * visible antes de ese bloque (el bloque la tapa si está delante). Con Shift ya lo cancela el servidor.
     * LeftClickBlock también se publica en el hilo del servidor integrado, por eso se filtra por lado cliente.
     */
    @SubscribeEvent
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (!event.getLevel().isClientSide()) return;
        if (event.getAction() != PlayerInteractEvent.LeftClickBlock.Action.START) return;
 
        Player player = event.getEntity();
        if (player.isShiftKeyDown() || !player.getMainHandItem().is(Items.STICK)) return;
        PacketDistributor.sendToServer(DirectAttackRequestPayload.INSTANCE);
    }
}