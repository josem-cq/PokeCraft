package com.jose.pokecraft.event;
 
import com.jose.pokecraft.PokeCraftMod;
import com.jose.pokecraft.client.pokedex.ClientPokedexData;
import com.jose.pokecraft.client.pokedex.MobCatalog;
import com.jose.pokecraft.client.pokedex.PokedexButton;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
 
/** Eventos de cliente de la PokéDex: el botón del inventario y la limpieza al desconectar. */
@EventBusSubscriber(modid = PokeCraftMod.MOD_ID, value = Dist.CLIENT)
public class ClientPokedexEvents {
 
    /**
     * Inventario de supervivencia (InventoryScreen) e inventario creativo (CreativeModeInventoryScreen, otra pantalla
     * distinta): el botón decide solo qué variante de la PokéDex abre y, en creativo, cuándo es visible.
     */
    @SubscribeEvent
    public static void onScreenInit(ScreenEvent.Init.Post event) {
        if (event.getScreen() instanceof InventoryScreen inventory) {
            event.addListener(new PokedexButton(inventory));
        } else if (event.getScreen() instanceof CreativeModeInventoryScreen creativeInventory) {
            event.addListener(new PokedexButton(creativeInventory));
        }
    }
 
    /** Muestra/oculta el botón del inventario creativo según la pestaña activa (se evalúa antes de dibujar). */
    @SubscribeEvent
    public static void onScreenRender(ScreenEvent.Render.Pre event) {
        if (event.getScreen() instanceof CreativeModeInventoryScreen creativeInventory) {
            for (GuiEventListener child : creativeInventory.children()) {
                if (child instanceof PokedexButton button) button.updateVisibility();
            }
        }
    }
 
    /** Al salir del mundo se olvidan los descubrimientos y el catálogo (otro servidor puede tener otros mods). */
    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientPokedexData.clear();
        MobCatalog.invalidate();
    }
}