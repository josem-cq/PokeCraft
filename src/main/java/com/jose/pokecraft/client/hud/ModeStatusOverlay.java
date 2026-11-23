package com.jose.pokecraft.client.hud;

import net.minecraft.Util;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * Indicador de dos líneas sobre la barra de objetos: el modo del aliado arriba y, justo debajo, su estado PVP/PVE.
 * Cada línea llega ya construida desde el servidor, así que son totalmente independientes entre sí.
 *
 * Se muestra unos segundos y se desvanece. Solo se invoca desde el cliente (ModNetwork lo llama dentro de una lambda
 * y ClientModEvents lo registra como capa de HUD), así que puede usar clases de cliente libremente.
 */
public final class ModeStatusOverlay {
    private static final long HOLD_MS = 2500L;
    private static final long FADE_MS = 500L;
    private static final int LINE_HEIGHT = 11;
    /** Distancia desde el borde inferior de la pantalla; deja libre la barra de acción vanilla (y - 68). */
    private static final int BOTTOM_OFFSET = 94;

    @Nullable
    private static Component modeLine;
    @Nullable
    private static Component pvpLine;
    private static long expiresAt = 0L;

    private ModeStatusOverlay() {}

    public static void show(Component mode, Component pvp) {
        modeLine = mode;
        pvpLine = pvp;
        expiresAt = Util.getMillis() + HOLD_MS;
    }

    public static void clear() {
        modeLine = null;
        pvpLine = null;
        expiresAt = 0L;
    }

    /** Firma de {@code LayeredDraw.Layer}: se registra con {@code ModeStatusOverlay::render}. */
    public static void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
        Component mode = modeLine;
        Component pvp = pvpLine;
        if (mode == null || pvp == null) return;

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.options.hideGui) return;

        long remaining = expiresAt - Util.getMillis();
        if (remaining <= 0L) {
            clear();
            return;
        }
        int alpha = remaining >= FADE_MS ? 255 : (int) (255L * remaining / FADE_MS);
        if (alpha < 8) return; // con alfa < 4 el texto se dibujaría opaco

        int color = (alpha << 24) | 0xFFFFFF;
        Font font = minecraft.font;
        int centerX = graphics.guiWidth() / 2;
        int y = graphics.guiHeight() - BOTTOM_OFFSET;

        graphics.drawCenteredString(font, mode, centerX, y, color);
        graphics.drawCenteredString(font, pvp, centerX, y + LINE_HEIGHT, color);
    }
}