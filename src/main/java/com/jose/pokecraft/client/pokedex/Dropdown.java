package com.jose.pokecraft.client.pokedex;
 
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;
 
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
 
/**
 * Menú desplegable reutilizable: un botón que abre una lista con casillas.
 *  - Modo múltiple: cada fila es una casilla; conjunto vacío = "sin restricción".
 *  - Modo único: cada fila es una opción excluyente (como un selector de orden).
 * Cada cambio llama a {@code onChange} para que la pantalla refiltre en memoria al instante.
 *
 * No es un widget de Minecraft: la pantalla le reenvía clics y rueda, y le pide pintar el botón y, al final del
 * frame, la lista desplegada (para que quede por encima de todo lo demás).
 */
public class Dropdown<T> {
    public record Option<T>(T value, Component label) {}
 
    private static final int ROW_H = 12;
    private static final int MAX_ROWS = 9;
 
    private final Component title;
    private final List<Option<T>> options;
    private final boolean multi;
    private final Runnable onChange;
    private final Set<T> selected = new LinkedHashSet<>();
 
    private int x, y, w, h;
    private boolean open;
    private int scroll;
 
    public Dropdown(Component title, List<Option<T>> options, boolean multi, Runnable onChange) {
        this.title = title;
        this.options = options;
        this.multi = multi;
        this.onChange = onChange;
    }
 
    // ---------------------------------------------------------------------------------------------
    // Estado
    // ---------------------------------------------------------------------------------------------
 
    public void setBounds(int x, int y, int w, int h) {
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
    }
 
    public Set<T> selected() {
        return Collections.unmodifiableSet(selected);
    }
 
    /** Valor elegido en modo único (el primero si hubiera varios). */
    @Nullable
    public T single() {
        return selected.isEmpty() ? null : selected.iterator().next();
    }
 
    /** Fija la selección en modo único sin disparar onChange. */
    public void select(T value) {
        selected.clear();
        selected.add(value);
    }
 
    public boolean isOpen() {
        return open;
    }
 
    public void close() {
        open = false;
    }
 
    // ---------------------------------------------------------------------------------------------
    // Geometría
    // ---------------------------------------------------------------------------------------------
 
    private boolean overButton(double mx, double my) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }
 
    private int visibleRows() {
        return Math.min(options.size(), MAX_ROWS);
    }
 
    private int popupWidth(Font font) {
        int widest = w;
        for (Option<T> option : options) {
            widest = Math.max(widest, font.width(option.label()) + 24);
        }
        return widest;
    }
 
    private int popupHeight() {
        return visibleRows() * ROW_H + 4;
    }
 
    private int popupX(Font font) {
        int screenW = Minecraft.getInstance().getWindow().getGuiScaledWidth();
        return Math.min(x, screenW - popupWidth(font) - 2);
    }
 
    private int popupY() {
        int screenH = Minecraft.getInstance().getWindow().getGuiScaledHeight();
        int below = y + h;
        return below + popupHeight() > screenH - 2 ? y - popupHeight() : below;
    }
 
    public boolean isOverPopup(double mx, double my) {
        Font font = Minecraft.getInstance().font;
        int px = popupX(font);
        int py = popupY();
        return open && mx >= px && mx < px + popupWidth(font) && my >= py && my < py + popupHeight();
    }
 
    private int maxScroll() {
        return Math.max(0, options.size() - MAX_ROWS);
    }
 
    // ---------------------------------------------------------------------------------------------
    // Dibujo
    // ---------------------------------------------------------------------------------------------
 
    private Component buttonLabel() {
        if (multi) {
            return selected.isEmpty() ? title : Component.empty().append(title).append(" (" + selected.size() + ")");
        }
        T current = single();
        if (current != null) {
            for (Option<T> option : options) {
                if (option.value().equals(current)) {
                    return Component.empty().append(title).append(": ").append(option.label());
                }
            }
        }
        return title;
    }
 
    public void renderButton(GuiGraphics g, Font font, int mouseX, int mouseY) {
        boolean hover = overButton(mouseX, mouseY) || open;
        PokedexUi.raised(g, x, y, w, h, hover ? PokedexUi.BUTTON_HOVER : PokedexUi.BUTTON_FILL);
 
        String text = font.plainSubstrByWidth(buttonLabel().getString(), w - 16);
        g.drawString(font, text, x + 5, y + (h - 8) / 2, 0xFFFFFFFF, true);
        PokedexUi.arrowDown(g, x + w - 10, y + (h - 3) / 2, 0xFFFFFFFF);
    }
 
    public void renderPopup(GuiGraphics g, Font font, int mouseX, int mouseY) {
        if (!open) return;
        int px = popupX(font);
        int py = popupY();
        int pw = popupWidth(font);
        int ph = popupHeight();
 
        g.pose().pushPose();
        g.pose().translate(0, 0, 400);
 
        g.fill(px - 1, py - 1, px + pw + 1, py + ph + 1, 0xFF000000);
        g.fill(px, py, px + pw, py + ph, 0xFF2B2B2B);
 
        for (int i = 0; i < visibleRows(); i++) {
            int index = i + scroll;
            if (index >= options.size()) break;
            Option<T> option = options.get(index);
            int ry = py + 2 + i * ROW_H;
 
            boolean hover = mouseX >= px && mouseX < px + pw && mouseY >= ry && mouseY < ry + ROW_H;
            if (hover) g.fill(px + 1, ry, px + pw - 1, ry + ROW_H, 0xFF4A4A7A);
 
            // Casilla (o marca de opción única)
            int bx = px + 4;
            int by = ry + 1;
            g.fill(bx, by, bx + 9, by + 9, 0xFFAAAAAA);
            g.fill(bx + 1, by + 1, bx + 8, by + 8, 0xFF1A1A1A);
            if (selected.contains(option.value())) {
                g.fill(bx + 2, by + 2, bx + 7, by + 7, 0xFF55FF55);
            }
 
            String label = font.plainSubstrByWidth(option.label().getString(), pw - 22);
            g.drawString(font, label, px + 17, ry + 2, 0xFFFFFFFF, false);
        }
 
        // Indicadores de que hay más filas fuera de vista
        if (scroll > 0) PokedexUi.arrowDown(g, px + pw - 9, py + 1, 0xFF777777);
        if (scroll < maxScroll()) PokedexUi.arrowDown(g, px + pw - 9, py + ph - 4, 0xFFFFFFFF);
 
        g.pose().popPose();
    }
 
    // ---------------------------------------------------------------------------------------------
    // Entrada
    // ---------------------------------------------------------------------------------------------
 
    /**
     * @return true si el clic fue consumido (abrió/cerró el menú o eligió una opción).
     * Con el menú abierto, cualquier clic se consume: dentro elige y fuera cierra.
     */
    public boolean mouseClicked(double mx, double my, int button) {
        if (open) {
            if (button == 0 && isOverPopup(mx, my)) {
                int row = (int) ((my - popupY() - 2) / ROW_H);
                int index = row + scroll;
                if (row >= 0 && row < visibleRows() && index < options.size()) {
                    toggle(options.get(index).value());
                }
                return true; // el menú de selección múltiple se queda abierto para marcar varias casillas
            }
            open = false;
            return true;
        }
 
        if (button == 0 && overButton(mx, my)) {
            open = true;
            scroll = 0;
            return true;
        }
        return false;
    }
 
    public boolean mouseScrolled(double mx, double my, double deltaY) {
        if (!open || !isOverPopup(mx, my)) return false;
        scroll = Mth.clamp(scroll - (int) Math.signum(deltaY), 0, maxScroll());
        return true;
    }
 
    private void toggle(T value) {
        if (multi) {
            if (!selected.remove(value)) selected.add(value);
        } else {
            selected.clear();
            selected.add(value);
            open = false;
        }
        onChange.run();
    }
}