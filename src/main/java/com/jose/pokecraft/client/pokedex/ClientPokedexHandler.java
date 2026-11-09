package com.jose.pokecraft.client.pokedex;
 
import com.jose.pokecraft.network.PokedexSyncPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
 
/**
 * Recibe en el cliente los descubrimientos enviados por el servidor. Solo se invoca desde el cliente
 * (ModNetwork lo llama dentro de una lambda), así que puede usar clases de cliente libremente.
 */
public final class ClientPokedexHandler {
    private ClientPokedexHandler() {}
 
    public static void handleSync(PokedexSyncPayload payload) {
        ClientPokedexData.set(payload.entries());
 
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof PokedexScreen screen) {
            screen.onDataUpdated();
        } else if (minecraft.screen instanceof InventoryScreen inventory) {
            // El botón del inventario pidió los datos: al llegar, se abre la PokéDex con ellos ya cargados.
            minecraft.setScreen(new PokedexScreen(inventory));
        }
    }
}
 