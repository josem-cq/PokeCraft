package com.jose.pokecraft.event;
 
import com.jose.pokecraft.PokeCraftMod;
import com.jose.pokecraft.network.CancelDirectAttackPayload;
import net.minecraft.world.item.Items;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.network.PacketDistributor;
 
/** Eventos de juego que solo existen en el cliente. */
@EventBusSubscriber(modid = PokeCraftMod.MOD_ID, value = Dist.CLIENT)
public class ClientForgeEvents {
 
    /**
     * Clic izquierdo al aire: el servidor no recibe ningún evento. Con Shift + palo en la mano principal se le avisa
     * para cancelar el ataque directo. El golpe/animación vanilla no se toca.
     */
    @SubscribeEvent
    public static void onLeftClickEmpty(PlayerInteractEvent.LeftClickEmpty event) {
        if (event.getEntity().isShiftKeyDown() && event.getEntity().getMainHandItem().is(Items.STICK)) {
            PacketDistributor.sendToServer(CancelDirectAttackPayload.INSTANCE);
        }
    }
}
 