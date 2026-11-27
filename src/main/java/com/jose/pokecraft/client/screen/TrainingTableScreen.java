package com.jose.pokecraft.client.screen;

import com.jose.pokecraft.PokeCraftMod;
import com.jose.pokecraft.item.CapturedMobStats;
import com.jose.pokecraft.level.MobLevelData;
import com.jose.pokecraft.level.MobLevelManager;
import com.jose.pokecraft.level.MobSlot;
import com.jose.pokecraft.menu.TrainingTableMenu;
import com.jose.pokecraft.pokedex.MobStrengthManager;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.locale.Language;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Pantalla de la Mesa de Entrenamiento, con el aspecto de las interfaces vanilla (paneles biselados dibujados en
 * código, sin texturas propias):
 *  - arriba: título, "Nivel X / 100", barra de XP del nivel actual, niveles disponibles y botón +;
 *  - izquierda: slot de la bola con mob; centro: panel con nombre, tag, salud, defensa y ataque;
 *  - derecha: 2 columnas x 4 filas (armadura | mano principal, mano secundaria, poción, bola de reserva);
 *  - los slots bloqueados llevan un candado; si hay desbloqueos pendientes se resaltan en verde y un clic los abre.
 */
public class TrainingTableScreen extends AbstractContainerScreen<TrainingTableMenu> {
    private static final int PANEL_FILL = 0xFFC6C6C6;
    private static final int SLOT_FILL = 0xFF8B8B8B;
    private static final int TEXT_DARK = 0xFF404040;

    // Barra de experiencia con los sprites vanilla del HUD del jugador (182 x 5).
    private static final ResourceLocation XP_BG = ResourceLocation.withDefaultNamespace("hud/experience_bar_background");
    private static final ResourceLocation XP_FG = ResourceLocation.withDefaultNamespace("hud/experience_bar_progress");
    private static final int BAR_X = 8;
    private static final int BAR_Y = 19;
    private static final int BAR_W = 182;
    private static final int BAR_H = 5;

    /** Iconos "fantasma" en gris de cada slot vacío: se generan una vez a partir de las texturas reales de los ítems. */
    private record Ghost(ResourceLocation texture, int width, int height) {}
    private static final Map<String, Ghost> GHOSTS = new HashMap<>();
    private static final int INFO_X = 34;
    private static final int INFO_Y = 44;
    private static final int INFO_W = 126;
    private static final int INFO_H = 88;

    private Button levelUpButton;

    public TrainingTableScreen(TrainingTableMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = 256;
        this.imageHeight = 228;
        this.titleLabelX = 8;
        this.titleLabelY = 6;
        this.inventoryLabelX = 47;
        this.inventoryLabelY = 134;
    }

    @Override
    protected void init() {
        super.init();
        levelUpButton = addRenderableWidget(Button.builder(Component.literal("+"), button -> sendButton(TrainingTableMenu.BTN_LEVEL_UP))
                .bounds(leftPos + 198, topPos + 15, 50, 14)
                .tooltip(Tooltip.create(Component.translatable("gui.pokecraft.training.level_up")))
                .build());
    }

    private void sendButton(int id) {
        if (minecraft != null && minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
        }
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        levelUpButton.active = menu.hasMob()
                && menu.getData(TrainingTableMenu.D_AVAILABLE) > 0
                && menu.getData(TrainingTableMenu.D_LEVEL) < MobLevelData.MAX_LEVEL;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        renderTooltip(g, mouseX, mouseY);
    }

    // ---------------------------------------------------------------------------------------------
    // Fondo
    // ---------------------------------------------------------------------------------------------

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int x = leftPos;
        int y = topPos;
        raised(g, x, y, imageWidth, imageHeight, PANEL_FILL);

        // Barra de experiencia (estilo HUD vanilla) con una división cada vez que "cabe" la XP de matar a un mob tan
        // fuerte como el propio mob (ver TrainingTableMenu.D_STEP).
        int bx = x + BAR_X;
        int by = y + BAR_Y;
        g.blitSprite(XP_BG, bx, by, BAR_W, BAR_H);
        if (menu.hasMob()) {
            int fill = BAR_W * menu.getData(TrainingTableMenu.D_PROGRESS) / 1000;
            if (fill > 0) {
                g.blitSprite(XP_FG, BAR_W, BAR_H, 0, 0, bx, by, fill, BAR_H);
            }
            int step = menu.getData(TrainingTableMenu.D_STEP);
            if (step > 0 && step < 1000) {
                for (int mark = step; mark < 1000; mark += step) {
                    int px = bx + BAR_W * mark / 1000;
                    g.fill(px, by, px + 1, by + BAR_H, 0xB0000000);
                }
            }
        }

        // Panel de detalles
        inset(g, x + INFO_X, y + INFO_Y, INFO_W, INFO_H, 0xFF2B2B2B);
        drawDetails(g, x + INFO_X + 6, y + INFO_Y + 6);

        // Huecos de todos los slots y, en los vacíos (también los bloqueados), el icono del ítem que admiten en gris.
        for (Slot slot : menu.slots) {
            inset(g, x + slot.x - 1, y + slot.y - 1, 18, 18, SLOT_FILL);
        }
        for (Slot slot : menu.slots) {
            if (slot.hasItem()) continue;
            Ghost ghost = null;
            if (slot.index == 0) {
                ghost = ghostFor(null);
            } else if (slot instanceof TrainingTableMenu.GearSlot gear) {
                ghost = ghostFor(gear.type());
            }
            if (ghost != null) {
                RenderSystem.enableBlend();
                g.setColor(1.0F, 1.0F, 1.0F, 0.65F);
                g.blit(ghost.texture(), x + slot.x, y + slot.y, 16, 16, 0.0F, 0.0F,
                        ghost.width(), ghost.height(), ghost.width(), ghost.height());
                g.setColor(1.0F, 1.0F, 1.0F, 1.0F);
            }
        }
        g.drawString(font, Component.translatable("gui.pokecraft.training.ball_slot"),
                x + TrainingTableMenu.BALL_X - 1, y + TrainingTableMenu.BALL_Y + 21, TEXT_DARK, false);
    }

    // ---------------------------------------------------------------------------------------------
    // Iconos en gris de los slots vacíos
    // ---------------------------------------------------------------------------------------------

    private static List<ResourceLocation> ghostSources(@Nullable MobSlot type) {
        if (type == null || type == MobSlot.RESERVE_BALL) {
            return List.of(ResourceLocation.fromNamespaceAndPath(PokeCraftMod.MOD_ID, "item/pokeball"));
        }
        return switch (type) {
            case HEAD -> List.of(ResourceLocation.withDefaultNamespace("item/iron_helmet"));
            case CHEST -> List.of(ResourceLocation.withDefaultNamespace("item/iron_chestplate"));
            case LEGS -> List.of(ResourceLocation.withDefaultNamespace("item/iron_leggings"));
            case FEET -> List.of(ResourceLocation.withDefaultNamespace("item/iron_boots"));
            case MAIN_HAND -> List.of(ResourceLocation.withDefaultNamespace("item/iron_sword"));
            case OFF_HAND -> List.of(ResourceLocation.withDefaultNamespace("item/totem_of_undying"));
            case POTION -> List.of(ResourceLocation.withDefaultNamespace("item/potion_overlay"),
                    ResourceLocation.withDefaultNamespace("item/potion"));
            case RESERVE_BALL -> List.of(ResourceLocation.fromNamespaceAndPath(PokeCraftMod.MOD_ID, "item/pokeball"));
        };
    }

    /** null = slot de la bola del mob. Devuelve null si no se pudo generar (el slot queda simplemente sin icono). */
    @Nullable
    private Ghost ghostFor(@Nullable MobSlot type) {
        String key = type == null ? "ball" : type.name().toLowerCase(Locale.ROOT);
        if (GHOSTS.containsKey(key)) return GHOSTS.get(key);
        Ghost ghost = null;
        try {
            ghost = buildGhost(key, ghostSources(type));
        } catch (IOException | RuntimeException ignored) {
            // sin icono: nunca debe romper la pantalla
        }
        GHOSTS.put(key, ghost);
        return ghost;
    }

    /** Lee las texturas reales, las pasa a escala de grises (conservando la transparencia) y las apila en una sola. */
    @Nullable
    private Ghost buildGhost(String key, List<ResourceLocation> sources) throws IOException {
        if (minecraft == null) return null;
        NativeImage out = null;
        try {
            for (ResourceLocation id : sources) {
                Optional<Resource> resource = minecraft.getResourceManager().getResource(id.withPrefix("textures/").withSuffix(".png"));
                if (resource.isEmpty()) continue;
                try (InputStream in = resource.get().open(); NativeImage layer = NativeImage.read(in)) {
                    if (out == null) out = new NativeImage(layer.getWidth(), layer.getHeight(), true);
                    if (layer.getWidth() != out.getWidth() || layer.getHeight() != out.getHeight()) continue;
                    for (int py = 0; py < layer.getHeight(); py++) {
                        for (int px = 0; px < layer.getWidth(); px++) {
                            int pixel = layer.getPixelRGBA(px, py); // ABGR
                            int alpha = (pixel >>> 24) & 0xFF;
                            if (alpha == 0) continue;
                            int r = pixel & 0xFF;
                            int gr = (pixel >> 8) & 0xFF;
                            int b = (pixel >> 16) & 0xFF;
                            int luma = (int) (0.299D * r + 0.587D * gr + 0.114D * b);
                            int gray = 60 + luma * 150 / 255; // gris medio: se ve "apagado" sobre el slot
                            out.setPixelRGBA(px, py, (alpha << 24) | (gray << 16) | (gray << 8) | gray);
                        }
                    }
                }
            }
            if (out == null) return null;
            ResourceLocation texture = ResourceLocation.fromNamespaceAndPath(PokeCraftMod.MOD_ID, "gui/ghost/" + key);
            Ghost ghost = new Ghost(texture, out.getWidth(), out.getHeight());
            minecraft.getTextureManager().register(texture, new DynamicTexture(out));
            out = null; // ahora es de la textura
            return ghost;
        } finally {
            if (out != null) out.close();
        }
    }

    private void drawDetails(GuiGraphics g, int px, int py) {
        ItemStack ball = menu.getSlot(0).getItem();
        CompoundTag entity = menu.hasMob() ? MobLevelManager.capturedEntityOf(ball) : null;
        if (entity == null || minecraft == null || minecraft.level == null) {
            g.drawWordWrap(font, Component.translatable("gui.pokecraft.training.no_mob"), px, py + 30, INFO_W - 12, 0xFFAAAAAA);
            return;
        }

        var registries = minecraft.level.registryAccess();
        EntityType<?> type = CapturedMobStats.getEntityType(entity);
        CapturedMobStats.Stats stats = CapturedMobStats.compute(entity, registries);
        int textWidth = INFO_W - 12;

        Component name = CapturedMobStats.getDisplayName(entity, type, registries, minecraft.level);
        g.drawString(font, Language.getInstance().getVisualOrder(font.substrByWidth(name, textWidth)), px, py, 0xFFFFFFFF, true);

        if (type != null) {
            Component tag = Component.translatable("gui.pokecraft.training.tag",
                    MobStrengthManager.baseStats(type, minecraft.level).kind().label(),
                    MobStrengthManager.tierOf(type, minecraft.level).label());
            g.drawString(font, tag, px, py + 13, 0xFFFFFFFF, false);
        }

        g.drawString(font, Component.translatable("gui.pokecraft.training.health",
                (int) Math.ceil(stats.health()), (int) Math.ceil(stats.maxHealth())).withStyle(ChatFormatting.RED),
                px, py + 32, 0xFFFFFFFF, false);
        g.drawString(font, Component.translatable("gui.pokecraft.training.defense", number(stats.armor()))
                .withStyle(ChatFormatting.AQUA), px, py + 45, 0xFFFFFFFF, false);
        g.drawString(font, Component.translatable("gui.pokecraft.training.attack",
                number(stats.hasAttack() ? stats.attackDamage() : 0.0D)).withStyle(ChatFormatting.GOLD),
                px, py + 58, 0xFFFFFFFF, false);
    }

    // ---------------------------------------------------------------------------------------------
    // Etiquetas y candados (se dibujan por ENCIMA de los slots)
    // ---------------------------------------------------------------------------------------------

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(font, title, titleLabelX, titleLabelY, TEXT_DARK, false);
        g.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, TEXT_DARK, false);

        if (menu.hasMob()) {
            Component level = Component.translatable("gui.pokecraft.training.level",
                    menu.getData(TrainingTableMenu.D_LEVEL), MobLevelData.MAX_LEVEL);
            g.drawString(font, level, imageWidth - 8 - font.width(level), titleLabelY, TEXT_DARK, false);
            g.drawString(font, Component.translatable("gui.pokecraft.training.available",
                    menu.getData(TrainingTableMenu.D_AVAILABLE)), BAR_X, BAR_Y + BAR_H + 4, TEXT_DARK, false);
        }

        boolean pending = menu.hasMob() && menu.getData(TrainingTableMenu.D_PENDING) > 0;
        for (Slot slot : menu.slots) {
            if (menu.hasMob() && slot instanceof TrainingTableMenu.GearSlot gear && gear.isLocked()) {
                drawLock(g, slot.x, slot.y, pending);
            }
        }
    }

    /** Candado pequeño en la esquina inferior derecha del slot (el icono en gris queda debajo). */
    private static void drawLock(GuiGraphics g, int x, int y, boolean unlockable) {
        g.fill(x + 10, y + 6, x + 14, y + 7, 0xFFC8C8C8);   // arco
        g.fill(x + 10, y + 6, x + 11, y + 10, 0xFFC8C8C8);
        g.fill(x + 13, y + 6, x + 14, y + 10, 0xFFC8C8C8);
        g.fill(x + 8, y + 9, x + 16, y + 16, 0xFF202020);   // borde del cuerpo
        g.fill(x + 9, y + 10, x + 15, y + 15, 0xFFE0B030);  // cuerpo
        g.fill(x + 11, y + 11, x + 13, y + 13, 0xFF503C10); // ojo de la cerradura
        if (unlockable) {
            g.renderOutline(x - 1, y - 1, 18, 18, 0xFF55FF55);
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Interacción y tooltips
    // ---------------------------------------------------------------------------------------------

    /** Clic en un slot bloqueado: con desbloqueos pendientes lo desbloquea (lo valida el servidor); si no, no hace nada. */
    @Override
    protected void slotClicked(@Nullable Slot slot, int slotId, int mouseButton, ClickType type) {
        if (slot instanceof TrainingTableMenu.GearSlot gear && gear.isLocked()) {
            if (mouseButton == 0 && menu.hasMob() && menu.getData(TrainingTableMenu.D_PENDING) > 0) {
                sendButton(gear.type().ordinal());
            }
            return;
        }
        super.slotClicked(slot, slotId, mouseButton, type);
    }

    @Override
    protected void renderTooltip(GuiGraphics g, int mouseX, int mouseY) {
        if (menu.hasMob() && menu.getCarried().isEmpty() && hoveredSlot == null
                && mouseX >= leftPos + BAR_X && mouseX < leftPos + BAR_X + BAR_W
                && mouseY >= topPos + BAR_Y - 1 && mouseY < topPos + BAR_Y + BAR_H + 1) {
            int percent = Math.min(100, Math.round(menu.getData(TrainingTableMenu.D_STEP) / 10.0F));
            g.renderComponentTooltip(font, List.of(Component.translatable("gui.pokecraft.training.bar_tooltip", percent)
                    .withStyle(ChatFormatting.GRAY)), mouseX, mouseY);
            return;
        }
        if (menu.getCarried().isEmpty() && hoveredSlot != null && !hoveredSlot.hasItem()) {
            if (hoveredSlot instanceof TrainingTableMenu.GearSlot gear) {
                g.renderComponentTooltip(font, gearTooltip(gear), mouseX, mouseY);
                return;
            }
            if (hoveredSlot.index == 0) {
                g.renderComponentTooltip(font, List.of(Component.translatable("gui.pokecraft.training.no_mob")
                        .withStyle(ChatFormatting.GRAY)), mouseX, mouseY);
                return;
            }
        }
        super.renderTooltip(g, mouseX, mouseY);
    }

    private List<Component> gearTooltip(TrainingTableMenu.GearSlot gear) {
        List<Component> lines = new ArrayList<>();
        MobSlot type = gear.type();
        if (!menu.hasMob()) {
            lines.add(Component.translatable("gui.pokecraft.training.no_mob").withStyle(ChatFormatting.GRAY));
        } else if (gear.isLocked()) {
            int pending = menu.getData(TrainingTableMenu.D_PENDING);
            if (pending > 0) {
                lines.add(Component.translatable("gui.pokecraft.training.unlock_slot", type.label()).withStyle(ChatFormatting.GREEN));
                lines.add(Component.translatable("gui.pokecraft.training.unlock_hint", pending).withStyle(ChatFormatting.GRAY));
            } else {
                lines.add(Component.translatable("gui.pokecraft.training.locked", type.label()).withStyle(ChatFormatting.GRAY));
                int next = nextMilestone(menu.getData(TrainingTableMenu.D_LEVEL));
                if (next > 0) {
                    lines.add(Component.translatable("gui.pokecraft.training.next_milestone", next).withStyle(ChatFormatting.DARK_GRAY));
                }
            }
        } else {
            lines.add(type.label().copy().withStyle(ChatFormatting.WHITE));
            lines.add(type.hint().copy().withStyle(ChatFormatting.GRAY));
        }
        return lines;
    }

    private static int nextMilestone(int level) {
        for (int milestone : MobLevelData.MILESTONES) {
            if (milestone > level) return milestone;
        }
        return -1;
    }

    // ---------------------------------------------------------------------------------------------
    // Utilidades de dibujo
    // ---------------------------------------------------------------------------------------------

    /** Panel en relieve: borde negro, luz arriba-izquierda, sombra abajo-derecha. */
    private static void raised(GuiGraphics g, int x, int y, int w, int h, int fill) {
        g.fill(x, y, x + w, y + h, 0xFF000000);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, 0xFFFFFFFF);
        g.fill(x + 2, y + 2, x + w - 1, y + h - 1, 0xFF555555);
        g.fill(x + 2, y + 2, x + w - 2, y + h - 2, fill);
    }

    /** Zona hundida (como los huecos de ranura): sombra arriba-izquierda, luz abajo-derecha. */
    private static void inset(GuiGraphics g, int x, int y, int w, int h, int fill) {
        g.fill(x, y, x + w, y + h, 0xFF373737);
        g.fill(x + 1, y + 1, x + w, y + h, 0xFFFFFFFF);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, fill);
    }

    /** 4 -> "4", 2.5 -> "2.5". */
    private static String number(double value) {
        double rounded = Math.rint(value);
        if (Math.abs(value - rounded) < 0.05D) return Long.toString((long) rounded);
        return String.format(Locale.ROOT, "%.1f", value);
    }
}