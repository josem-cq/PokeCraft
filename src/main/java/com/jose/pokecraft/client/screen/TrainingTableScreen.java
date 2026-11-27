package com.jose.pokecraft.client.screen;

import com.jose.pokecraft.PokeCraftMod;
import com.jose.pokecraft.item.CapturedMobStats;
import com.jose.pokecraft.level.MobLevelData;
import com.jose.pokecraft.level.MobLevelManager;
import com.jose.pokecraft.level.MobSlot;
import com.jose.pokecraft.menu.TrainingTableMenu;
import com.jose.pokecraft.pokedex.MobStrengthManager;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.locale.Language;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

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

    private static final int BAR_X = 8;
    private static final int BAR_Y = 19;
    private static final int BAR_W = 196;
    private static final int BAR_H = 10;
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
                .bounds(leftPos + 212, topPos + 15, 36, 14)
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

        // Barra de progreso del nivel actual
        inset(g, x + BAR_X - 1, y + BAR_Y - 1, BAR_W + 2, BAR_H + 2, 0xFF2B2B2B);
        if (menu.hasMob()) {
            int fill = BAR_W * menu.getData(TrainingTableMenu.D_PROGRESS) / 1000;
            if (fill > 0) {
                g.fill(x + BAR_X, y + BAR_Y, x + BAR_X + fill, y + BAR_Y + BAR_H, 0xFF3FA83F);
                g.fill(x + BAR_X, y + BAR_Y, x + BAR_X + fill, y + BAR_Y + 3, 0xFF7CE07C);
            }
        }

        // Panel de detalles
        inset(g, x + INFO_X, y + INFO_Y, INFO_W, INFO_H, 0xFF2B2B2B);
        drawDetails(g, x + INFO_X + 6, y + INFO_Y + 6);

        // Huecos de todos los slots y, en los vacíos y desbloqueados, el icono "fantasma" de lo que admiten.
        for (Slot slot : menu.slots) {
            inset(g, x + slot.x - 1, y + slot.y - 1, 18, 18, SLOT_FILL);
        }
        for (Slot slot : menu.slots) {
            ItemStack ghost = ItemStack.EMPTY;
            if (slot.index == 0) {
                ghost = new ItemStack(PokeCraftMod.POKEBALL.get());
            } else if (slot instanceof TrainingTableMenu.GearSlot gear && !gear.isLocked()) {
                ghost = ghostFor(gear.type());
            }
            if (!ghost.isEmpty() && !slot.hasItem()) {
                g.renderFakeItem(ghost, x + slot.x, y + slot.y);
                g.fill(x + slot.x, y + slot.y, x + slot.x + 16, y + slot.y + 16, 0xB08B8B8B);
            }
        }
        g.drawString(font, Component.translatable("gui.pokecraft.training.ball_slot"),
                x + TrainingTableMenu.BALL_X - 1, y + TrainingTableMenu.BALL_Y + 21, TEXT_DARK, false);
    }

    private static ItemStack ghostFor(MobSlot type) {
        return new ItemStack(switch (type) {
            case HEAD -> Items.IRON_HELMET;
            case CHEST -> Items.IRON_CHESTPLATE;
            case LEGS -> Items.IRON_LEGGINGS;
            case FEET -> Items.IRON_BOOTS;
            case MAIN_HAND -> Items.IRON_SWORD;
            case OFF_HAND -> Items.TOTEM_OF_UNDYING;
            case POTION -> Items.LINGERING_POTION;
            case RESERVE_BALL -> PokeCraftMod.POKEBALL.get();
        });
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
            if (slot instanceof TrainingTableMenu.GearSlot gear && gear.isLocked()) {
                drawLock(g, slot.x, slot.y, pending);
            }
        }
    }

    private static void drawLock(GuiGraphics g, int x, int y, boolean unlockable) {
        g.fill(x, y, x + 16, y + 16, 0xD0373737);
        g.fill(x + 5, y + 3, x + 11, y + 5, 0xFFB0B0B0);   // arco del candado
        g.fill(x + 5, y + 3, x + 7, y + 8, 0xFFB0B0B0);
        g.fill(x + 9, y + 3, x + 11, y + 8, 0xFFB0B0B0);
        g.fill(x + 4, y + 8, x + 12, y + 14, 0xFFD8A82A);  // cuerpo
        g.fill(x + 7, y + 10, x + 9, y + 12, 0xFF5A4410);  // ojo de la cerradura
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
