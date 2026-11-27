package com.jose.pokecraft.menu;

import com.jose.pokecraft.PokeCraftMod;
import com.jose.pokecraft.ai.MobOrderManager;
import com.jose.pokecraft.item.CapturedBall;
import com.jose.pokecraft.item.CapturedMobStats;
import com.jose.pokecraft.item.PokeballItem;
import com.jose.pokecraft.level.MobLevelData;
import com.jose.pokecraft.level.MobLevelManager;
import com.jose.pokecraft.level.MobSlot;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.function.BiPredicate;

/**
 * Menú de la Mesa de Entrenamiento.
 *
 * Slots del menú: 0 = bola con mob; 1..8 = slots del mob (índice = {@link MobSlot#ordinal()} + 1); 9..35 inventario del
 * jugador; 36..44 barra rápida.
 *
 * Los 8 slots del mob son un contenedor PROPIO del menú que se rellena desde el NBT de la bola colocada y se vuelve a
 * escribir en ella en cuanto el jugador cambia algo. Así nada se guarda fuera de la bola y no se puede perder ni
 * duplicar equipo: al sacar la bola, los slots se vacían al instante.
 *
 * Los números de nivel se sincronizan con un ContainerData (16 bits por valor, de sobra: nivel <= 100, barra en milésimas).
 */
public class TrainingTableMenu extends AbstractContainerMenu {
    /** Botón de menú para subir de nivel; los ids 0..7 desbloquean el slot con ese ordinal. */
    public static final int BTN_LEVEL_UP = 8;

    public static final int D_HAS_MOB = 0;
    public static final int D_LEVEL = 1;
    /** Progreso de la barra en milésimas (0..1000). */
    public static final int D_PROGRESS = 2;
    public static final int D_AVAILABLE = 3;
    public static final int D_MASK = 4;
    public static final int D_PENDING = 5;
    private static final int D_COUNT = 6;

    public static final int BALL_X = 10;
    public static final int BALL_Y = 80;
    private static final int INV_START = 9;

    private final Container ballContainer;
    private final SimpleContainer gear = new SimpleContainer(MobSlot.values().length);
    private final ContainerData data = new SimpleContainerData(D_COUNT);
    private final ContainerLevelAccess access;
    private final Level level;
    private final HolderLookup.Provider registries;

    private boolean syncing;
    private ItemStack lastBall = ItemStack.EMPTY;

    /** Constructor del cliente (lo usa el MenuType): el contenido llega por los paquetes de sincronización. */
    public TrainingTableMenu(int containerId, Inventory inventory) {
        this(containerId, inventory, new SimpleContainer(1), ContainerLevelAccess.NULL);
    }

    public TrainingTableMenu(int containerId, Inventory inventory, Container ballContainer, ContainerLevelAccess access) {
        super(PokeCraftMod.TRAINING_TABLE_MENU.get(), containerId);
        this.ballContainer = ballContainer;
        this.access = access;
        this.level = inventory.player.level();
        this.registries = level.registryAccess();

        addSlot(new BallSlot(ballContainer, 0, BALL_X, BALL_Y));
        for (MobSlot type : MobSlot.values()) {
            addSlot(new GearSlot(this, gear, type, gearX(type), gearY(type)));
        }
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inventory, col + row * 9 + 9, 47 + col * 18, 146 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(inventory, col, 47 + col * 18, 204));
        }
        addDataSlots(data);

        gear.addListener(container -> onGearChanged());
        if (!level.isClientSide) {
            loadGear();
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Geometría compartida con la pantalla (columna 0: armadura; columna 1: ítems)
    // ---------------------------------------------------------------------------------------------

    public static int gearX(MobSlot type) {
        return type.ordinal() < 4 ? 181 : 219;
    }

    public static int gearY(MobSlot type) {
        return 48 + (type.ordinal() % 4) * 22;
    }

    // ---------------------------------------------------------------------------------------------
    // Estado sincronizado
    // ---------------------------------------------------------------------------------------------

    public int getData(int index) {
        return data.get(index);
    }

    public boolean hasMob() {
        return data.get(D_HAS_MOB) == 1;
    }

    public boolean isUnlocked(MobSlot type) {
        return hasMob() && (data.get(D_MASK) & type.bit()) != 0;
    }

    private void refreshData(@Nullable MobLevelData d, @Nullable EntityType<?> type) {
        if (d == null) {
            for (int i = 0; i < D_COUNT; i++) data.set(i, 0);
            return;
        }
        double perLevel = type != null ? MobLevelManager.xpPerLevel(type, level) : 1.0D;
        data.set(D_HAS_MOB, 1);
        data.set(D_LEVEL, d.level);
        data.set(D_PROGRESS, Math.round(d.progress(perLevel) * 1000.0F));
        data.set(D_AVAILABLE, d.available);
        data.set(D_MASK, d.unlockedMask);
        data.set(D_PENDING, d.pendingUnlocks());
    }

    // ---------------------------------------------------------------------------------------------
    // Bola <-> slots del mob (solo servidor)
    // ---------------------------------------------------------------------------------------------

    /** Rellena los 8 slots y los números desde el NBT de la bola actual (o los vacía si no hay bola con mob). */
    private void loadGear() {
        syncing = true;
        try {
            ItemStack ball = ballContainer.getItem(0);
            lastBall = ball.copy();
            CompoundTag entity = MobLevelManager.capturedEntityOf(ball);
            if (entity == null) {
                gear.clearContent();
                refreshData(null, null);
                return;
            }
            MobLevelData d = MobLevelData.read(MobLevelManager.persistentOf(entity), registries);
            for (MobSlot type : MobSlot.values()) {
                ItemStack stack = ItemStack.EMPTY;
                if (d.isUnlocked(type)) {
                    stack = switch (type) {
                        case POTION -> d.potion.copy();
                        case RESERVE_BALL -> d.reserve.copy();
                        default -> MobLevelManager.getEquipment(entity, type, registries);
                    };
                }
                gear.setItem(type.ordinal(), stack);
            }
            refreshData(d, CapturedMobStats.getEntityType(entity));
        } finally {
            syncing = false;
        }
    }

    /** Si la bola cambió (entró, salió o la modificó otro), recarga los slots de inmediato. */
    private void syncBall() {
        if (!ItemStack.matches(lastBall, ballContainer.getItem(0))) {
            loadGear();
        }
    }

    private void onGearChanged() {
        if (syncing || level.isClientSide) return;
        modify((entity, d) -> {
            for (MobSlot type : MobSlot.values()) {
                if (!d.isUnlocked(type)) continue; // un slot bloqueado nunca toca el equipo que el mob ya tuviera
                ItemStack stack = gear.getItem(type.ordinal()).copy();
                switch (type) {
                    case POTION -> d.potion = stack;
                    case RESERVE_BALL -> d.reserve = stack;
                    default -> MobLevelManager.setEquipment(entity, type, stack, registries);
                }
            }
            return true;
        });
        lastBall = ballContainer.getItem(0).copy();
    }

    /**
     * Edita el NBT de la bola colocada: copia el CustomData, aplica el cambio sobre la entidad guardada y sus datos de
     * nivel y lo reescribe entero (el resto del NBT, bola de origen incluida, no se toca).
     */
    private boolean modify(BiPredicate<CompoundTag, MobLevelData> change) {
        ItemStack ball = ballContainer.getItem(0);
        CustomData custom = ball.get(DataComponents.CUSTOM_DATA);
        if (custom == null) return false;
        CompoundTag root = custom.copyTag();
        if (!root.contains(CapturedBall.CAPTURED_KEY, Tag.TAG_COMPOUND)) return false;

        CompoundTag entity = root.getCompound(CapturedBall.CAPTURED_KEY);
        CompoundTag persistent = MobLevelManager.persistentOf(entity);
        MobLevelData d = MobLevelData.read(persistent, registries);
        if (!change.test(entity, d)) return false;

        d.write(persistent, registries);
        entity.put(MobOrderManager.ENTITY_PERSISTENT_TAG, persistent);
        root.put(CapturedBall.CAPTURED_KEY, entity);
        ball.set(DataComponents.CUSTOM_DATA, CustomData.of(root));
        ballContainer.setChanged();
        return true;
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (level.isClientSide) return false;
        boolean changed;
        if (id == BTN_LEVEL_UP) {
            changed = modify((entity, d) -> d.levelUp());
            if (changed) playSound(SoundEvents.PLAYER_LEVELUP, 1.2F);
        } else if (id >= 0 && id < MobSlot.values().length) {
            MobSlot type = MobSlot.byIndex(id);
            changed = modify((entity, d) -> d.unlock(type));
            if (changed) playSound(SoundEvents.ENCHANTMENT_TABLE_USE, 1.0F);
        } else {
            return false;
        }
        if (changed) loadGear();
        return changed;
    }

    private void playSound(net.minecraft.sounds.SoundEvent sound, float pitch) {
        access.execute((lvl, pos) -> lvl.playSound(null, pos, sound, SoundSource.BLOCKS, 0.5F, pitch));
    }

    // ---------------------------------------------------------------------------------------------
    // Menú
    // ---------------------------------------------------------------------------------------------

    /** Tras cada clic se sincroniza la bola YA, para que no haya ventana entre sacarla y vaciar los slots del mob. */
    @Override
    public void clicked(int slotId, int button, ClickType clickType, Player player) {
        super.clicked(slotId, button, clickType, player);
        if (!level.isClientSide) syncBall();
    }

    @Override
    public void broadcastChanges() {
        if (!level.isClientSide) syncBall();
        super.broadcastChanges();
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (slot == null || !slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();

        if (index < INV_START) {
            if (!moveItemStackTo(stack, INV_START, slots.size(), true)) return ItemStack.EMPTY;
        } else if (!moveItemStackTo(stack, 0, INV_START, false)) {
            return ItemStack.EMPTY;
        }

        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        if (stack.getCount() == original.getCount()) return ItemStack.EMPTY;
        slot.onTake(player, stack);
        return original;
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(access, player, PokeCraftMod.TRAINING_TABLE_BLOCK.get());
    }

    // ---------------------------------------------------------------------------------------------
    // Slots
    // ---------------------------------------------------------------------------------------------

    private static class BallSlot extends Slot {
        BallSlot(Container container, int index, int x, int y) {
            super(container, index, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return stack.getItem() instanceof PokeballItem && MobLevelManager.hasCapturedMob(stack);
        }

        @Override
        public int getMaxStackSize() {
            return 1;
        }

        @Override
        public int getMaxStackSize(ItemStack stack) {
            return 1;
        }
    }

    /** Slot del mob: solo acepta/entrega ítems si está desbloqueado y el ítem es válido para ese tipo. */
    public static class GearSlot extends Slot {
        private final TrainingTableMenu menu;
        private final MobSlot type;

        GearSlot(TrainingTableMenu menu, Container container, MobSlot type, int x, int y) {
            super(container, type.ordinal(), x, y);
            this.menu = menu;
            this.type = type;
        }

        public MobSlot type() {
            return type;
        }

        public boolean isLocked() {
            return !menu.isUnlocked(type);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return !isLocked() && type.isValid(stack);
        }

        @Override
        public boolean mayPickup(Player player) {
            return !isLocked();
        }

        @Override
        public int getMaxStackSize() {
            return 1;
        }

        @Override
        public int getMaxStackSize(ItemStack stack) {
            return 1;
        }
    }
}
