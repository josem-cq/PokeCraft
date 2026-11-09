package com.jose.pokecraft.client.pokedex;
 
import net.minecraft.client.gui.GuiGraphics;
 
/** Primitivas de dibujo con aspecto de interfaz vanilla (paneles biselados), sin necesidad de texturas propias. */
final class PokedexUi {
    static final int PANEL_FILL = 0xFFC6C6C6;
    static final int BUTTON_FILL = 0xFF8B8B8B;
    static final int BUTTON_HOVER = 0xFFA6A6C8;
    static final int TEXT_DARK = 0xFF404040;
 
    private PokedexUi() {}
 
    /** Panel/botón en relieve: borde negro, luz arriba-izquierda, sombra abajo-derecha. */
    static void raised(GuiGraphics g, int x, int y, int w, int h, int fill) {
        g.fill(x, y, x + w, y + h, 0xFF000000);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, 0xFFFFFFFF);
        g.fill(x + 2, y + 2, x + w - 1, y + h - 1, 0xFF555555);
        g.fill(x + 2, y + 2, x + w - 2, y + h - 2, fill);
    }
 
    /** Zona hundida (como los huecos de ranura): sombra arriba-izquierda, luz abajo-derecha. */
    static void inset(GuiGraphics g, int x, int y, int w, int h, int fill) {
        g.fill(x, y, x + w, y + h, 0xFF373737);
        g.fill(x + 1, y + 1, x + w, y + h, 0xFFFFFFFF);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, fill);
    }
 
    /** Triángulo de 5x3 apuntando hacia abajo, con su esquina superior izquierda en (x, y). */
    static void arrowDown(GuiGraphics g, int x, int y, int color) {
        g.fill(x, y, x + 5, y + 1, color);
        g.fill(x + 1, y + 1, x + 4, y + 2, color);
        g.fill(x + 2, y + 2, x + 3, y + 3, color);
    }
}