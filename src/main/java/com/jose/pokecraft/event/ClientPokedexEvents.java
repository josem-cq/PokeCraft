package com.jose.pokecraft.event;
 
import com.jose.pokecraft.PokeCraftMod;
import com.jose.pokecraft.client.pokedex.ClientPokedexData;
import com.jose.pokecraft.client.pokedex.MobCatalog;
import com.jose.pokecraft.client.pokedex.PokedexButton;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
 
/** Eventos de cliente de la PokéDex: el botón del inventario y la limpieza al desconectar. */
@EventBusSubscriber(modid = PokeCraftMod.MOD_ID, value = Dist.CLIENT)
public class ClientPokedexEvents {
 
    /** Solo el inventario de supervivencia (InventoryScreen); el creativo es otra pantalla distinta. */
    @SubscribeEvent
    public static void onScreenInit(ScreenEvent.Init.Post event) {
        if (event.getScreen() instanceof InventoryScreen inventory) {
            event.addListener(new PokedexButton(inventory));
        }
    }
 
    /** Al salir del mundo se olvidan los descubrimientos y el catálogo (otro servidor puede tener otros mods). */
    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientPokedexData.clear();
        MobCatalog.invalidate();
    }
}
 