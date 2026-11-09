package com.jose.pokecraft.client.pokedex;
 
import com.jose.pokecraft.network.PokedexRequestPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.network.PacketDistributor;
 
/**
 * Botón de la PokéDex en el inventario de supervivencia, a la derecha del libro de recetas vanilla.
 * Al pulsarlo pide los descubrimientos al servidor; la pantalla se abre cuando llegan (ClientPokedexHandler).
 */
public class PokedexButton extends Button {
    /** Icono provisional: la perla de ender (minecraft:item/ender_pearl). Cámbialo aquí por tu propia textura. */
    private final ItemStack icon = new ItemStack(Items.ENDER_PEARL);
 
    private final InventoryScreen screen;
 
    public PokedexButton(InventoryScreen screen) {
        // El mensaje va vacío para que el botón vanilla no pinte texto encima del icono.
        super(screen.getGuiLeft() + 126, screen.height / 2 - 22, 20, 18, Component.empty(),
                button -> PacketDistributor.sendToServer(PokedexRequestPayload.INSTANCE), DEFAULT_NARRATION);
        this.screen = screen;
        setTooltip(Tooltip.create(Component.translatable("gui.pokecraft.pokedex")));
    }
 
    @Override
    public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // El libro de recetas desplaza la pantalla al abrirse sin reinicializarla: se recoloca cada frame.
        setPosition(screen.getGuiLeft() + 126, screen.height / 2 - 22);
        super.renderWidget(graphics, mouseX, mouseY, partialTick);
        graphics.renderItem(icon, getX() + 2, getY() + 1);
    }
}