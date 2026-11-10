package com.jose.pokecraft.client.pokedex;
 
import com.jose.pokecraft.PokeCraftMod;
import com.jose.pokecraft.network.PokedexRequestPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.Slot;
import net.neoforged.neoforge.network.PacketDistributor;
 
import java.lang.reflect.Method;
 
/**
 * Botón de la PokéDex.
 *  - Supervivencia (InventoryScreen): a la derecha del libro de recetas vanilla. Pide los descubrimientos al servidor;
 *    la pantalla se abre cuando llegan (ClientPokedexHandler).
 *  - Creativo (CreativeModeInventoryScreen): solo en la pestaña "Inventario de supervivencia", a la derecha del
 *    personaje junto a los slots de pantalones y botas. Abre la PokéDex de creativo directamente (todo desbloqueado,
 *    no necesita datos del servidor).
 */
public class PokedexButton extends Button {
    /**
     * Icono del botón: PNG propio de 16x16 en src/main/resources/assets/pokecraft/textures/gui/pokedex_button.png
     * (ruta en el juego: pokecraft:textures/gui/pokedex_button.png). Si usas otro tamaño, cambia TEXTURE_SIZE.
     */
    private static final ResourceLocation ICON_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(PokeCraftMod.MOD_ID, "textures/gui/pokedex_button.png");
    private static final int ICON_SIZE = 16;
    private static final int TEXTURE_SIZE = 16;
 
    private final AbstractContainerScreen<?> screen;
    private final boolean creative;
 
    public PokedexButton(AbstractContainerScreen<?> screen) {
        // El mensaje va vacío para que el botón vanilla no pinte texto encima del icono.
        super(0, 0, 20, 18, Component.empty(), button -> open(screen), DEFAULT_NARRATION);
        this.screen = screen;
        this.creative = screen instanceof CreativeModeInventoryScreen;
        reposition();
        setTooltip(Tooltip.create(Component.translatable("gui.pokecraft.pokedex")));
    }
 
    private static void open(AbstractContainerScreen<?> screen) {
        if (screen instanceof CreativeModeInventoryScreen) {
            Minecraft.getInstance().setScreen(new PokedexScreen(screen, true));
        } else {
            PacketDistributor.sendToServer(PokedexRequestPayload.INSTANCE);
        }
    }
 
    private void reposition() {
        if (creative) {
            // Slots de armadura de la pestaña de inventario creativo: pantalones/botas en x=108 (y=6 y y=33);
            // el personaje queda entre x=73..105. El botón va justo a la derecha de esos slots, centrado entre ambos.
            setPosition(screen.getGuiLeft() + 129, screen.getGuiTop() + 19);
        } else {
            setPosition(screen.getGuiLeft() + 126, screen.height / 2 - 22);
        }
    }
 
    /**
     * En creativo el botón solo existe en la pestaña de inventario (cambiar de pestaña no re-inicializa la pantalla).
     * AbstractWidget.render es final, así que la visibilidad se actualiza cada frame desde ClientPokedexEvents
     * (ScreenEvent.Render.Pre); con visible=false el widget ni se dibuja ni recibe clics ni muestra tooltip.
     */
    public void updateVisibility() {
        if (creative) visible = isCreativeInventoryTab();
    }
 
    @Override
    public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // El libro de recetas desplaza la pantalla al abrirse sin reinicializarla: se recoloca cada frame.
        reposition();
        super.renderWidget(graphics, mouseX, mouseY, partialTick);
        graphics.blit(ICON_TEXTURE, getX() + 2, getY() + 1, 0.0F, 0.0F, ICON_SIZE, ICON_SIZE, TEXTURE_SIZE, TEXTURE_SIZE);
    }
 
    private static Method inventoryTabMethod;
    private static boolean inventoryTabMethodLookedUp;
 
    /** ¿Está abierta la pestaña "Inventario de supervivencia" del inventario creativo? */
    private boolean isCreativeInventoryTab() {
        // 1) Si la pantalla expone isInventoryOpen() (privado o público según versión) se usa tal cual.
        if (!inventoryTabMethodLookedUp) {
            inventoryTabMethodLookedUp = true;
            try {
                inventoryTabMethod = CreativeModeInventoryScreen.class.getDeclaredMethod("isInventoryOpen");
                inventoryTabMethod.setAccessible(true);
            } catch (ReflectiveOperationException | RuntimeException e) {
                inventoryTabMethod = null;
            }
        }
        if (inventoryTabMethod != null) {
            try {
                return (boolean) inventoryTabMethod.invoke(screen);
            } catch (ReflectiveOperationException | RuntimeException e) {
                inventoryTabMethod = null;
            }
        }
        // 2) Respaldo: solo esta pestaña tiene el slot de "tirar ítem" en (173, 112).
        for (Slot slot : screen.getMenu().slots) {
            if (slot.x == 173 && slot.y == 112) return true;
        }
        return false;
    }
}