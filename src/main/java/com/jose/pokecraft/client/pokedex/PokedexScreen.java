package com.jose.pokecraft.client.pokedex;
 
import com.jose.pokecraft.network.EntityConfigEditPayload;
import com.jose.pokecraft.network.EntityConfigSavePayload;
import com.jose.pokecraft.pokedex.EntityConfig;
import com.jose.pokecraft.pokedex.MobKind;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ClickType;
import net.neoforged.neoforge.network.PacketDistributor;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;
 
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
 
/**
 * Pantalla de la PokéDex. Estructura:
 *  - Pestañas superiores: "Búsqueda" (todos los mods) + una por namespace, con flechas si no caben.
 *  - Buscador + orden (criterio y dirección) y filtros múltiples (Tipo, Mod solo en Búsqueda, Estado).
 *  - Cuadrícula con scroll: nombre (cifrado si está bloqueado) y estadísticas compactas debajo.
 *  - Tooltip completo al pasar el ratón sobre un mob desbloqueado.
 *
 * Todo se filtra y ordena en memoria sobre una lista cacheada ({@link #filtered}) que solo se recalcula cuando
 * cambia la pestaña, el texto, un filtro o el orden.
 */
public class PokedexScreen extends Screen {
 
    // Fuente de la mesa de encantamientos (alfabeto galáctico estándar).
    private static final ResourceLocation ALT_FONT = ResourceLocation.withDefaultNamespace("alt");
    private static final ResourceLocation HEART_SPRITE = ResourceLocation.withDefaultNamespace("hud/heart/full");
    private static final ResourceLocation ARMOR_SPRITE = ResourceLocation.withDefaultNamespace("hud/armor_full");
 
    private static final int CELL_H = 28;
    private static final int TOGGLE_SIZE = 14;
    private static final int MIN_CELL_W = 118;
    private static final int TAB_W = 28;
    /** Clave de la pestaña "Otros" (entidades sin huevo, solo creativo). */
    private static final String OTHERS_KEY = "#others";
    private static final int TAB_H = 22;
    private static final int PAD = 8;
    private static final int SCROLLBAR_W = 6;
    private static final float STATS_SCALE = 0.72F;
    // Bloque de inventario del jugador (creativo): 3 filas + hotbar, como en el inventario vanilla.
    private static final int INV_W = 9 * 18 + 14;
    private static final int INV_H = 7 + 3 * 18 + 4 + 18 + 7;
    private static final int INV_GAP = 6;
 
    public enum LockState {
        LOCKED("locked"), UNLOCKED("unlocked");
 
        private final String key;
 
        LockState(String key) {
            this.key = key;
        }
 
        Component label() {
            return Component.translatable("pokecraft.pokedex.state." + key);
        }
    }
 
    public enum SortKey {
        NAME("name", false), KIND("kind", false), MOD("mod", false), STATE("state", false),
        HEALTH("health", true), ARMOR("armor", true), DAMAGE("damage", true), DISCOVERED("discovered", true);
 
        private final String key;
        /** Los criterios que usan datos del mob dejan siempre los bloqueados al final, sin revelar su orden. */
        private final boolean lockedLast;
 
        SortKey(String key, boolean lockedLast) {
            this.key = key;
            this.lockedLast = lockedLast;
        }
 
        Component label() {
            return Component.translatable("pokecraft.pokedex.sort." + key);
        }
    }
 
    /**
     * Pestaña con el icono de la pestaña de creativo correspondiente (la Búsqueda lleva una brújula). El nombre del mod
     * (y el de la pestaña de creativo si el mod tiene varias) va en el tooltip.
     */
    private record Tab(@Nullable String key, ItemStack icon, List<Component> tooltip) {
        boolean isSearch() {
            return key == null;
        }
 
        boolean isOthers() {
            return OTHERS_KEY.equals(key);
        }
    }
 
    private final Screen parent;
    /** Variante de creativo: todo desbloqueado, sin datos de registro y los clics entregan huevos al cursor. */
    private final boolean creative;
    /** Ítem que "sostiene" el cursor en la variante de creativo (se devuelve al inventario creativo al cerrar). */
    private ItemStack carried = ItemStack.EMPTY;
    private final ItemStack swordIcon = new ItemStack(Items.IRON_SWORD);
    private final List<MobEntry> catalog;
    private final List<Tab> tabs = new ArrayList<>();
 
    private final Dropdown<MobKind> kindFilter;
    private final Dropdown<String> modFilter;
    private final Dropdown<LockState> stateFilter;
    private final Dropdown<Boolean> enabledFilter;
    private final Dropdown<SortKey> sortDropdown;
    private boolean ascending = true;
 
    private int activeTab = 0;
    private int tabScroll = 0;
 
    private EditBox searchBox;
    private Button directionButton;
    private Button saveButton;
    /** Hay cambios hechos en esta sesión que aún no se han escrito al archivo. */
    private boolean unsaved = false;
 
    /** Lista filtrada y ordenada de la pestaña actual (caché). */
    private List<MobEntry> filtered = List.of();
    private int tabTotal = 0;
    private int tabUnlocked = 0;
    private int scrollRow = 0;
    private boolean draggingScrollbar = false;
 
    // Geometría calculada en init()
    private int panelX, panelY, panelW, panelH;
    private int invX, invY;
    private int gridX, gridY, gridW, gridH;
    private int cols = 1, cellW = MIN_CELL_W, visibleRows = 1;
 
    public PokedexScreen(Screen parent) {
        this(parent, false);
    }
 
    public PokedexScreen(Screen parent, boolean creative) {
        super(Component.translatable("gui.pokecraft.pokedex"));
        this.parent = parent;
        this.creative = creative;
        this.catalog = MobCatalog.entries();
 
        // Creativo: se recoge el ítem que ya tuviera el cursor en el inventario creativo (solo en su pantalla propia,
        // porque ahí el cursor es 100% cliente; en un menú normal desincronizaría con el servidor).
        if (creative && parent instanceof CreativeModeInventoryScreen creativeScreen) {
            carried = creativeScreen.getMenu().getCarried().copy();
            creativeScreen.getMenu().setCarried(ItemStack.EMPTY);
        }
 
        // Mods presentes (para el filtro "Mod"): "minecraft" y el resto por orden alfabético.
        Set<String> namespaces = new TreeSet<>();
        for (MobEntry entry : catalog) namespaces.add(entry.namespace());
        List<String> ordered = new ArrayList<>(namespaces);
        ordered.sort(Comparator.comparing((String ns) -> !ns.equals("minecraft"))
                .thenComparing(MobCatalog::modName, String.CASE_INSENSITIVE_ORDER));
        rebuildTabs();
 
        // Filtros y orden. Cada cambio refiltra al instante.
        List<Dropdown.Option<MobKind>> kinds = new ArrayList<>();
        for (MobKind kind : MobKind.values()) kinds.add(new Dropdown.Option<>(kind, kind.plainLabel()));
        kindFilter = new Dropdown<>(Component.translatable("pokecraft.pokedex.filter.kind"), kinds, true, () -> refresh(true));
 
        List<Dropdown.Option<String>> mods = new ArrayList<>();
        for (String ns : ordered) mods.add(new Dropdown.Option<>(ns, Component.literal(MobCatalog.modName(ns))));
        modFilter = new Dropdown<>(Component.translatable("pokecraft.pokedex.filter.mod"), mods, true, () -> refresh(true));
 
        List<Dropdown.Option<LockState>> states = new ArrayList<>();
        for (LockState state : LockState.values()) states.add(new Dropdown.Option<>(state, state.label()));
        stateFilter = new Dropdown<>(Component.translatable("pokecraft.pokedex.filter.state"), states, true, () -> refresh(true));
 
        List<Dropdown.Option<Boolean>> flags = new ArrayList<>();
        flags.add(new Dropdown.Option<>(Boolean.TRUE, Component.translatable("pokecraft.pokedex.enabled.true")));
        flags.add(new Dropdown.Option<>(Boolean.FALSE, Component.translatable("pokecraft.pokedex.enabled.false")));
        enabledFilter = new Dropdown<>(Component.translatable("pokecraft.pokedex.filter.enabled"), flags, true, () -> refresh(true));
 
        List<Dropdown.Option<SortKey>> sorts = new ArrayList<>();
        for (SortKey key : SortKey.values()) {
            // Sin progreso que consultar: "Estado de bloqueo" y "Fecha de descubierto" no tienen sentido en creativo.
            if (creative && (key == SortKey.STATE || key == SortKey.DISCOVERED)) continue;
            sorts.add(new Dropdown.Option<>(key, key.label()));
        }
        sortDropdown = new Dropdown<>(Component.translatable("pokecraft.pokedex.sort"), sorts, false, () -> refresh(true));
        sortDropdown.select(SortKey.NAME);
    }
 
    // ---------------------------------------------------------------------------------------------
    // Permisos, visibilidad y pestañas
    // ---------------------------------------------------------------------------------------------
 
    /** Operador (nivel 2+) en la PokéDex de creativo: ve todas las entidades y puede activarlas/desactivarlas. */
    private boolean canEdit() {
        var player = Minecraft.getInstance().player;
        return creative && player != null && player.hasPermissions(2);
    }
 
    /** Sin permisos solo se ven las entidades activas; con permisos (creativo) se ven todas. */
    private boolean isVisible(MobEntry entry) {
        return canEdit() || EntityConfig.isEnabled(entry.id());
    }
 
    /** Reconstruye las pestañas según lo que hay que mostrar (la configuración puede cambiar en vivo). */
    private void rebuildTabs() {
        String previous = tabs.isEmpty() || activeTab >= tabs.size() ? null : tabs.get(activeTab).key();
        tabs.clear();
        tabs.add(new Tab(null, new ItemStack(Items.COMPASS),
                List.of(Component.translatable("pokecraft.pokedex.tab.search"))));
 
        Set<String> usedKeys = new HashSet<>();
        boolean anyOthers = false;
        for (MobEntry entry : catalog) {
            if (!isVisible(entry)) continue;
            if (creative && !entry.hasEgg()) anyOthers = true;
            else usedKeys.add(entry.tabKey());
        }
 
        List<MobCatalog.TabInfo> infos = new ArrayList<>();
        for (MobCatalog.TabInfo info : MobCatalog.tabs()) {
            if (usedKeys.contains(info.key())) infos.add(info);
        }
        java.util.Map<String, Integer> perMod = new java.util.HashMap<>();
        for (MobCatalog.TabInfo info : infos) perMod.merge(info.namespace(), 1, Integer::sum);
 
        for (MobCatalog.TabInfo info : infos) {
            List<Component> tooltip = new ArrayList<>();
            if (perMod.get(info.namespace()) > 1) {
                tooltip.add(info.name().copy().withStyle(ChatFormatting.WHITE));
                tooltip.add(Component.literal(MobCatalog.modName(info.namespace())).withStyle(ChatFormatting.GRAY));
            } else {
                tooltip.add(Component.literal(MobCatalog.modName(info.namespace())));
            }
            tabs.add(new Tab(info.key(), info.icon(), tooltip));
        }
 
        // "Otros": entidades sin huevo (depuración). Sin permisos solo aparece si hay alguna activa.
        if (creative && (anyOthers || canEdit())) {
            tabs.add(new Tab(OTHERS_KEY, new ItemStack(Items.BARRIER), List.of(
                    Component.translatable("pokecraft.pokedex.tab.others"),
                    Component.translatable("pokecraft.pokedex.tab.others_hint").withStyle(ChatFormatting.GRAY))));
        }
 
        activeTab = 0;
        if (previous != null) {
            for (int i = 0; i < tabs.size(); i++) {
                if (previous.equals(tabs.get(i).key())) {
                    activeTab = i;
                    break;
                }
            }
        }
    }
 
    // ---------------------------------------------------------------------------------------------
    // Ciclo de vida
    // ---------------------------------------------------------------------------------------------
 
    @Override
    protected void init() {
        panelW = Mth.clamp(width - 24, 280, 420);
        panelX = (width - panelW) / 2;
        if (creative) {
            // La PokéDex sube y deja sitio debajo para el inventario del jugador (27 slots + hotbar).
            panelH = Mth.clamp(height - TAB_H - INV_GAP - INV_H - 12, 120, 260);
            int total = TAB_H + panelH + INV_GAP + INV_H;
            panelY = Math.max(4, (height - total) / 2) + TAB_H;
            invX = (width - INV_W) / 2;
            invY = panelY + panelH + INV_GAP;
        } else {
            panelH = Mth.clamp(height - 56, 190, 260);
            panelY = Mth.clamp((height - panelH) / 2 + 10, TAB_H + 4, Math.max(TAB_H + 4, height - panelH - 4));
        }
 
        int rowA = panelY + 18;
        int rowB = rowA + 22;
        int directionW = 44;
        int sortW = 96;
        int searchW = panelW - 2 * PAD - directionW - sortW - 8;
 
        String previous = searchBox == null ? "" : searchBox.getValue();
        searchBox = new EditBox(font, panelX + PAD, rowA, searchW, 16, Component.translatable("pokecraft.pokedex.search"));
        searchBox.setHint(Component.translatable("pokecraft.pokedex.search_hint").withStyle(ChatFormatting.DARK_GRAY));
        searchBox.setMaxLength(48);
        searchBox.setValue(previous);
        searchBox.setResponder(text -> refresh(true));
        addRenderableWidget(searchBox);
        setInitialFocus(searchBox);
 
        sortDropdown.setBounds(panelX + PAD + searchW + 4, rowA, sortW, 16);
        directionButton = addRenderableWidget(Button.builder(directionLabel(), button -> {
            ascending = !ascending;
            button.setMessage(directionLabel());
            refresh(true);
        }).bounds(panelX + panelW - PAD - directionW, rowA, directionW, 16).build());
 
        layoutFilters(rowB);
 
        saveButton = null;
        if (canEdit()) {
            int saveW = 56;
            saveButton = addRenderableWidget(Button.builder(saveLabel(), button -> {
                PacketDistributor.sendToServer(EntityConfigSavePayload.INSTANCE);
                unsaved = false;
                button.setMessage(saveLabel());
            }).bounds(panelX + PAD + font.width(title) + 10, panelY + 3, saveW, 14).build());
        }
 
        gridX = panelX + PAD;
        gridY = rowB + 22;
        gridW = panelW - 2 * PAD - SCROLLBAR_W - 2;
        gridH = panelY + panelH - PAD - gridY;
        cols = Math.max(1, gridW / MIN_CELL_W);
        cellW = gridW / cols;
        visibleRows = Math.max(1, gridH / CELL_H);
 
        refresh(false);
    }
 
    /** Coloca los filtros de la segunda fila; "Mod" solo existe en la pestaña de Búsqueda. */
    private void layoutFilters(int rowB) {
        int fx = panelX + PAD;
        int fw = 78;
        kindFilter.setBounds(fx, rowB, fw, 16);
        fx += fw + 4;
        if (tabs.get(activeTab).isSearch()) {
            modFilter.setBounds(fx, rowB, fw, 16);
            fx += fw + 4;
        }
        if (!creative) stateFilter.setBounds(fx, rowB, fw, 16);
        else if (canEdit()) enabledFilter.setBounds(fx, rowB, fw, 16);
    }
 
    private List<Dropdown<?>> visibleDropdowns() {
        List<Dropdown<?>> list = new ArrayList<>();
        list.add(kindFilter);
        if (tabs.get(activeTab).isSearch()) list.add(modFilter);
        if (!creative) list.add(stateFilter);
        else if (canEdit()) list.add(enabledFilter);
        list.add(sortDropdown);
        return list;
    }
 
    @Nullable
    private Dropdown<?> openDropdown() {
        for (Dropdown<?> dropdown : visibleDropdowns()) {
            if (dropdown.isOpen()) return dropdown;
        }
        return null;
    }
 
    private Component saveLabel() {
        Component label = Component.translatable("pokecraft.pokedex.save");
        return unsaved ? Component.empty().append(label).append("*") : label;
    }
 
    private Component directionLabel() {
        return Component.translatable(ascending ? "pokecraft.pokedex.asc" : "pokecraft.pokedex.desc");
    }
 
    /** En creativo todo está desbloqueado; en supervivencia depende del progreso del jugador. */
    private boolean isUnlocked(MobEntry entry) {
        return creative || ClientPokedexData.isUnlocked(entry.id());
    }
 
    /** Llamado cuando llegan descubrimientos nuevos con la pantalla ya abierta. */
    public void onDataUpdated() {
        rebuildTabs();
        refresh(false);
    }
 
    @Override
    public void onClose() {
        minecraft.setScreen(parent);
        // Al volver al inventario creativo, el ítem del cursor vuelve con el jugador.
        if (creative && parent instanceof CreativeModeInventoryScreen creativeScreen) {
            creativeScreen.getMenu().setCarried(carried);
            carried = ItemStack.EMPTY;
        }
    }
 
    @Override
    public boolean isPauseScreen() {
        return false;
    }
 
    // ---------------------------------------------------------------------------------------------
    // Filtrado y ordenamiento (en memoria)
    // ---------------------------------------------------------------------------------------------
 
    private void refresh(boolean resetScroll) {
        Tab tab = tabs.get(activeTab);
 
        // Totales de la pestaña (sin filtros) para el contador "Registrados".
        tabTotal = 0;
        tabUnlocked = 0;
        List<MobEntry> source = new ArrayList<>();
        for (MobEntry entry : catalog) {
            if (!isVisible(entry) || !inTab(tab, entry)) continue;
            source.add(entry);
            tabTotal++;
            if (isUnlocked(entry)) tabUnlocked++;
        }
 
        String query = searchBox == null ? "" : searchBox.getValue().trim().toLowerCase(Locale.ROOT);
        boolean searching = !query.isEmpty();
 
        List<MobEntry> result = new ArrayList<>();
        for (MobEntry entry : source) {
            boolean unlocked = isUnlocked(entry);
 
            // Restricción estricta: una búsqueda con texto NUNCA encuentra ni muestra mobs bloqueados.
            if (searching) {
                if (!unlocked) continue;
                if (!entry.sortName().toLowerCase(Locale.ROOT).contains(query)) continue;
            }
            if (!kindFilter.selected().isEmpty() && !kindFilter.selected().contains(entry.kind())) continue;
            if (tab.isSearch() && !modFilter.selected().isEmpty() && !modFilter.selected().contains(entry.namespace())) continue;
            if (canEdit() && !enabledFilter.selected().isEmpty()
                    && !enabledFilter.selected().contains(EntityConfig.isEnabled(entry.id()))) continue;
            if (!stateFilter.selected().isEmpty()
                    && !stateFilter.selected().contains(unlocked ? LockState.UNLOCKED : LockState.LOCKED)) continue;
 
            result.add(entry);
        }
 
        result.sort(buildComparator());
        filtered = result;
 
        if (resetScroll) scrollRow = 0;
        scrollRow = Mth.clamp(scrollRow, 0, maxScrollRow());
    }
 
    private boolean inTab(Tab tab, MobEntry entry) {
        if (tab.isSearch()) return true;
        if (tab.isOthers()) return !entry.hasEgg();
        // En creativo las pestañas normales solo tienen entidades con huevo; las demás van a "Otros".
        return entry.tabKey().equals(tab.key()) && (!creative || entry.hasEgg());
    }
 
    private Comparator<MobEntry> buildComparator() {
        SortKey key = sortDropdown.single() == null ? SortKey.NAME : sortDropdown.single();
 
        Comparator<MobEntry> byName = Comparator.comparing(MobEntry::sortName, String.CASE_INSENSITIVE_ORDER);
 
        // Para los criterios con datos del mob, los bloqueados valen 0: así no se puede deducir nada de ellos.
        Comparator<MobEntry> inner = switch (key) {
            case NAME -> byName;
            case KIND -> Comparator.comparingInt(e -> e.kind().ordinal());
            case MOD -> Comparator.comparing((MobEntry e) -> MobCatalog.modName(e.namespace()), String.CASE_INSENSITIVE_ORDER);
            case STATE -> Comparator.comparingInt(e -> isUnlocked(e) ? 0 : 1);
            case HEALTH -> Comparator.comparingDouble(e -> isUnlocked(e) ? e.health() : 0);
            case ARMOR -> Comparator.comparingDouble(e -> isUnlocked(e) ? e.armor() : 0);
            case DAMAGE -> Comparator.comparingDouble(e -> isUnlocked(e) ? e.damage() : 0);
            case DISCOVERED -> Comparator.comparingLong(e -> {
                ClientPokedexData.Discovery d = ClientPokedexData.get(e.id());
                return d == null ? 0L : d.tick();
            });
        };
        if (!ascending) inner = inner.reversed();
 
        if (key.lockedLast) {
            Comparator<MobEntry> unlockedFirst = Comparator.comparingInt(e -> isUnlocked(e) ? 0 : 1);
            return unlockedFirst.thenComparing(inner).thenComparing(byName);
        }
        return inner.thenComparing(byName);
    }
 
    private int totalRows() {
        return (filtered.size() + cols - 1) / cols;
    }
 
    private int maxScrollRow() {
        return Math.max(0, totalRows() - visibleRows);
    }
 
    private void selectTab(int index) {
        if (index == activeTab) return;
        activeTab = index;
        scrollRow = 0;
        for (Dropdown<?> dropdown : visibleDropdowns()) dropdown.close();
        layoutFilters(panelY + 18 + 22);
        refresh(true);
    }
 
    // ---------------------------------------------------------------------------------------------
    // Dibujo
    // ---------------------------------------------------------------------------------------------
 
    private int tabAreaX() {
        return panelX + 16;
    }
 
    private int visibleTabCount() {
        return Math.max(1, (panelW - 32) / TAB_W);
    }
 
    private int tabY() {
        return panelY - TAB_H;
    }
 
    /** Fondo + panel + pestañas. Se pinta aquí para quedar DEBAJO de los widgets (Screen.render llama a esto primero). */
    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(g, mouseX, mouseY, partialTick);
        PokedexUi.raised(g, panelX, panelY, panelW, panelH, PokedexUi.PANEL_FILL);
        renderTabs(g, mouseX, mouseY);
 
        g.drawString(font, title, panelX + PAD, panelY + 6, PokedexUi.TEXT_DARK, false);
        Component counter = creative
                ? Component.translatable("pokecraft.pokedex.creative_count", tabTotal)
                : Component.translatable("pokecraft.pokedex.registered_count", tabUnlocked, tabTotal);
        g.drawString(font, counter, panelX + panelW - PAD - font.width(counter), panelY + 6, PokedexUi.TEXT_DARK, false);
    }
 
    private void renderTabs(GuiGraphics g, int mouseX, int mouseY) {
        int visible = visibleTabCount();
        tabScroll = Mth.clamp(tabScroll, 0, Math.max(0, tabs.size() - visible));
 
        for (int i = 0; i < visible; i++) {
            int index = i + tabScroll;
            if (index >= tabs.size()) break;
            int x = tabAreaX() + i * TAB_W;
            boolean active = index == activeTab;
            boolean hover = mouseX >= x && mouseX < x + TAB_W && mouseY >= tabY() && mouseY < tabY() + TAB_H;
 
            // La pestaña activa baja 2 px para tapar el borde superior del panel y fundirse con él.
            int top = active ? tabY() : tabY() + 3;
            int bottom = active ? panelY + 2 : panelY;
            g.fill(x, top, x + TAB_W, bottom, 0xFF000000);
            g.fill(x + 1, top + 1, x + TAB_W - 1, bottom, 0xFFFFFFFF);
            g.fill(x + 2, top + 2, x + TAB_W - 2, bottom, active ? PokedexUi.PANEL_FILL : (hover ? 0xFFA6A6A6 : 0xFF8B8B8B));
 
            g.renderItem(tabs.get(index).icon(), x + (TAB_W - 16) / 2, top + (active ? 4 : 2));
        }
 
        // Flechas de desplazamiento de pestañas
        boolean canLeft = tabScroll > 0;
        boolean canRight = tabScroll + visible < tabs.size();
        g.drawString(font, "<", panelX + 5, tabY() + 10, canLeft ? 0xFFFFFFFF : 0xFF606060, true);
        g.drawString(font, ">", panelX + panelW - 11, tabY() + 10, canRight ? 0xFFFFFFFF : 0xFF606060, true);
    }
 
    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick); // fondo + panel + buscador + botón de dirección
 
        for (Dropdown<?> dropdown : visibleDropdowns()) {
            dropdown.renderButton(g, font, mouseX, mouseY);
        }
 
        renderGrid(g, mouseX, mouseY);
        if (creative) renderInventory(g, mouseX, mouseY);
 
        Dropdown<?> open = openDropdown();
        if (open != null) {
            open.renderPopup(g, font, mouseX, mouseY); // con un menú abierto no se muestran tooltips por debajo
        } else {
            MobEntry hovered = entryAt(mouseX, mouseY);
            if (hovered != null && toggleAt(mouseX, mouseY) != null) {
                g.renderComponentTooltip(font, List.of(
                        Component.translatable("pokecraft.pokedex.toggle.title"),
                        Component.translatable("pokecraft.pokedex.toggle.hint").withStyle(ChatFormatting.GRAY)), mouseX, mouseY);
            } else if (hovered != null) {
                g.renderComponentTooltip(font, buildTooltip(hovered), mouseX, mouseY);
            } else {
                ItemStack hoveredItem = creative ? inventoryStackAt(mouseX, mouseY) : ItemStack.EMPTY;
                Tab tab = tabAtMouse(mouseX, mouseY);
                if (!hoveredItem.isEmpty()) g.renderTooltip(font, hoveredItem, mouseX, mouseY);
                else if (tab != null) g.renderComponentTooltip(font, tab.tooltip(), mouseX, mouseY);
            }
        }
 
        renderCarried(g, mouseX, mouseY);
    }
 
    // ---------------------------------------------------------------------------------------------
    // Inventario del jugador (solo creativo, solo lectura): muestra dónde caerán los huevos con Shift+clic
    // ---------------------------------------------------------------------------------------------
 
    /** Posición (esquina superior izquierda) de la casilla del inventario del jugador con ese índice (0-8 hotbar, 9-35). */
    private int invSlotX(int index) {
        return invX + 7 + (index % 9) * 18;
    }
 
    private int invSlotY(int index) {
        if (index < 9) return invY + 7 + 3 * 18 + 4;          // hotbar, separada 4 px
        return invY + 7 + ((index - 9) / 9) * 18;             // 3 filas principales
    }
 
    private void renderInventory(GuiGraphics g, int mouseX, int mouseY) {
        if (minecraft == null || minecraft.player == null) return;
        Inventory inventory = minecraft.player.getInventory();
        PokedexUi.raised(g, invX, invY, INV_W, INV_H, PokedexUi.PANEL_FILL);
        for (int index = 0; index < 36; index++) {
            int x = invSlotX(index);
            int y = invSlotY(index);
            PokedexUi.inset(g, x, y, 18, 18, PokedexUi.BUTTON_FILL);
            ItemStack stack = inventory.getItem(index);
            if (!stack.isEmpty()) {
                g.renderItem(stack, x + 1, y + 1);
                g.renderItemDecorations(font, stack, x + 1, y + 1);
            }
            if (mouseX >= x && mouseX < x + 18 && mouseY >= y && mouseY < y + 18 && openDropdown() == null) {
                g.fill(x + 1, y + 1, x + 17, y + 17, 0x80FFFFFF);
            }
        }
    }
 
    /** Índice de Inventory (0-8 hotbar, 9-35 principal) bajo el ratón, o -1. */
    private int inventorySlotAt(double mouseX, double mouseY) {
        for (int index = 0; index < 36; index++) {
            int x = invSlotX(index);
            int y = invSlotY(index);
            if (mouseX >= x && mouseX < x + 18 && mouseY >= y && mouseY < y + 18) return index;
        }
        return -1;
    }
 
    private ItemStack inventoryStackAt(int mouseX, int mouseY) {
        if (minecraft == null || minecraft.player == null) return ItemStack.EMPTY;
        int index = inventorySlotAt(mouseX, mouseY);
        return index < 0 ? ItemStack.EMPTY : minecraft.player.getInventory().getItem(index);
    }
 
    /**
     * Clic en una casilla del inventario con las reglas del inventario creativo (cursor local, cada cambio se
     * envía al servidor): izq. = recoger/colocar/apilar/intercambiar todo; der. = recoger la mitad / colocar 1.
     */
    private void clickInventorySlot(int index, int button) {
        if (minecraft == null || minecraft.player == null || minecraft.gameMode == null) return;
        if (!minecraft.gameMode.hasInfiniteItems() || (button != 0 && button != 1)) return;
        Inventory inventory = minecraft.player.getInventory();
        ItemStack slot = inventory.getItem(index);
        boolean same = !slot.isEmpty() && !carried.isEmpty() && ItemStack.isSameItemSameComponents(slot, carried);
 
        if (button == 0) {
            if (carried.isEmpty()) {
                if (slot.isEmpty()) return;
                carried = slot.copy();
                inventory.setItem(index, ItemStack.EMPTY);
            } else if (slot.isEmpty()) {
                inventory.setItem(index, carried);
                carried = ItemStack.EMPTY;
            } else if (same) {
                int moved = Math.min(slot.getMaxStackSize() - slot.getCount(), carried.getCount());
                if (moved <= 0) return;
                slot.grow(moved);
                carried.shrink(moved);
                if (carried.isEmpty()) carried = ItemStack.EMPTY;
            } else {
                ItemStack previous = slot.copy();
                inventory.setItem(index, carried);
                carried = previous;
            }
        } else {
            if (carried.isEmpty()) {
                if (slot.isEmpty()) return;
                carried = slot.split((slot.getCount() + 1) / 2);
                if (slot.isEmpty()) inventory.setItem(index, ItemStack.EMPTY);
            } else if (slot.isEmpty()) {
                inventory.setItem(index, carried.split(1));
                if (carried.isEmpty()) carried = ItemStack.EMPTY;
            } else if (same) {
                if (slot.getCount() >= slot.getMaxStackSize()) return;
                slot.grow(1);
                carried.shrink(1);
                if (carried.isEmpty()) carried = ItemStack.EMPTY;
            } else {
                ItemStack previous = slot.copy();
                inventory.setItem(index, carried);
                carried = previous;
            }
        }
        syncInventorySlot(inventory, index);
    }
 
    /** ¿El punto está dentro de la interfaz (panel, pestañas o bloque de inventario)? Fuera = soltar el ítem al mundo. */
    private boolean isInsideInterface(double mouseX, double mouseY) {
        if (mouseX >= panelX && mouseX < panelX + panelW && mouseY >= tabY() && mouseY < panelY + panelH) return true;
        return creative && mouseX >= invX && mouseX < invX + INV_W && mouseY >= invY && mouseY < invY + INV_H;
    }
 
    /** Clic fuera de la interfaz con un ítem en el cursor: lo suelta (izq. todo, der. una unidad), como el vanilla. */
    private void dropCursorItem(int button) {
        if (minecraft == null || minecraft.player == null || minecraft.gameMode == null) return;
        if (button != 0 && button != 1) return;
        if (creative) {
            if (carried.isEmpty() || !minecraft.gameMode.hasInfiniteItems()) return;
            ItemStack dropped = button == 0 ? carried : carried.split(1);
            minecraft.gameMode.handleCreativeModeItemDrop(dropped.copy());
            if (button == 0 || carried.isEmpty()) carried = ItemStack.EMPTY;
        } else {
            var menu = minecraft.player.containerMenu;
            if (menu.getCarried().isEmpty()) return;
            // Clic fuera (slot -999) del menú del inventario: el servidor lo suelta igual que en la pantalla vanilla.
            minecraft.gameMode.handleInventoryMouseClick(menu.containerId, -999, button, ClickType.PICKUP, minecraft.player);
        }
    }
 
    /**
     * Shift+clic: manda un stack completo del huevo al inventario del jugador en este orden: hotbar de izquierda a
     * derecha y después las 3 filas principales (fila de arriba primero, de izquierda a derecha). Primero completa los
     * stacks que ya tiene del mismo huevo y luego ocupa las casillas vacías. Se sincroniza con el servidor igual
     * que el inventario creativo vanilla (paquete de "set creative slot").
     */
    private void giveToInventory(ItemStack egg) {
        if (minecraft == null || minecraft.player == null || minecraft.gameMode == null) return;
        if (!minecraft.gameMode.hasInfiniteItems()) return;
        Inventory inventory = minecraft.player.getInventory();
        ItemStack remaining = egg.copyWithCount(egg.getMaxStackSize());
 
        // Orden de índices de Inventory: 0-8 hotbar, 9-35 principal (ya va de izquierda a derecha, fila a fila).
        for (int index = 0; index < 36 && !remaining.isEmpty(); index++) {
            ItemStack existing = inventory.getItem(index);
            if (existing.isEmpty() || !ItemStack.isSameItemSameComponents(existing, remaining)) continue;
            int room = existing.getMaxStackSize() - existing.getCount();
            if (room <= 0) continue;
            int moved = Math.min(room, remaining.getCount());
            existing.grow(moved);
            remaining.shrink(moved);
            syncInventorySlot(inventory, index);
        }
        for (int index = 0; index < 36 && !remaining.isEmpty(); index++) {
            if (!inventory.getItem(index).isEmpty()) continue;
            int moved = Math.min(remaining.getMaxStackSize(), remaining.getCount());
            inventory.setItem(index, remaining.copyWithCount(moved));
            remaining.shrink(moved);
            syncInventorySlot(inventory, index);
        }
    }
 
    /** Envía al servidor el contenido de una casilla (id de slot del menú del jugador: hotbar 36-44, principal 9-35). */
    private void syncInventorySlot(Inventory inventory, int index) {
        int menuSlot = index < 9 ? 36 + index : index;
        minecraft.gameMode.handleCreativeModeItemAdd(inventory.getItem(index).copy(), menuSlot);
    }
 
    /** Ítem pegado al cursor (creativo), por encima de todo, igual que en el inventario vanilla. */
    /** Ítem en el cursor: el local en creativo; en supervivencia el del menú del inventario del jugador. */
    private ItemStack cursorStack() {
        if (creative) return carried;
        return minecraft != null && minecraft.player != null ? minecraft.player.containerMenu.getCarried() : ItemStack.EMPTY;
    }
 
    private void renderCarried(GuiGraphics g, int mouseX, int mouseY) {
        ItemStack stack = cursorStack();
        if (stack.isEmpty()) return;
        g.pose().pushPose();
        g.pose().translate(0.0F, 0.0F, 450.0F);
        g.renderItem(stack, mouseX - 8, mouseY - 8);
        g.renderItemDecorations(font, stack, mouseX - 8, mouseY - 8);
        g.pose().popPose();
    }
 
    private void renderGrid(GuiGraphics g, int mouseX, int mouseY) {
        PokedexUi.inset(g, gridX - 1, gridY - 1, gridW + 2, gridH + 2, 0xFF2B2B2B);
 
        if (filtered.isEmpty()) {
            Component message = Component.translatable("pokecraft.pokedex.empty");
            g.drawCenteredString(font, message, gridX + gridW / 2, gridY + gridH / 2 - 8, 0xFFAAAAAA);
            if (!creative && !searchBox.getValue().isBlank()) {
                g.drawCenteredString(font, Component.translatable("pokecraft.pokedex.empty_search_hint"),
                        gridX + gridW / 2, gridY + gridH / 2 + 4, 0xFF777777);
            }
            return;
        }
 
        g.enableScissor(gridX, gridY, gridX + gridW, gridY + gridH);
        int first = scrollRow * cols;
        int last = Math.min(filtered.size(), first + visibleRows * cols);
        for (int i = first; i < last; i++) {
            int slot = i - first;
            int cx = gridX + (slot % cols) * cellW;
            int cy = gridY + (slot / cols) * CELL_H;
            boolean hover = mouseX >= cx && mouseX < cx + cellW && mouseY >= cy && mouseY < cy + CELL_H
                    && mouseX >= gridX && mouseX < gridX + gridW && mouseY >= gridY && mouseY < gridY + gridH;
            renderCell(g, filtered.get(i), cx, cy, hover);
        }
        g.disableScissor();
 
        renderScrollbar(g);
    }
 
    private void renderCell(GuiGraphics g, MobEntry entry, int x, int y, boolean hover) {
        boolean unlocked = isUnlocked(entry);
        g.fill(x + 1, y + 1, x + cellW - 1, y + CELL_H - 1, hover ? 0xFF5C5C6E : 0xFF444444);
 
        // Icono: huevo del mob si está desbloqueado (en creativo, el de depuración si no tiene); interrogación si no.
        ItemStack shownIcon = creative ? entry.giveStack() : entry.icon();
        if (unlocked && !shownIcon.isEmpty()) {
            g.renderItem(shownIcon, x + 4, y + 6);
        } else {
            g.drawString(font, unlocked ? "·" : "?", x + 10, y + 10, 0xFF777777, false);
        }
 
        int textX = x + 24;
        int maxWidth = cellW - 28 - (canEdit() ? TOGGLE_SIZE + 4 : 0);
 
        if (canEdit()) renderToggle(g, entry, x + cellW - TOGGLE_SIZE - 4, y + 4);
 
        Component name = unlocked ? entry.displayName() : censoredName(entry);
        FormattedCharSequence line = Language.getInstance().getVisualOrder(font.substrByWidth(name, maxWidth));
        g.drawString(font, line, textX, y + 5, unlocked ? 0xFFFFFFFF : 0xFFAAAAAA, true);
 
        if (unlocked) {
            drawCompactStats(g, entry, textX, y + 16);
        } else {
            drawScaled(g, Component.translatable("pokecraft.pokedex.locked"), textX, y + 16, 0xFF777777);
        }
    }
 
    private void renderToggle(GuiGraphics g, MobEntry entry, int x, int y) {
        boolean enabled = EntityConfig.isEnabled(entry.id());
        PokedexUi.inset(g, x, y, TOGGLE_SIZE, TOGGLE_SIZE, enabled ? 0xFF2E7D32 : 0xFF9E2A2A);
        String letter = enabled ? "T" : "F";
        g.drawString(font, letter, x + (TOGGLE_SIZE - font.width(letter)) / 2 + 1, y + 3, 0xFFFFFFFF, false);
    }
 
    /** Entrada cuyo selector true/false está bajo el ratón (solo con permisos), o null. */
    @Nullable
    private MobEntry toggleAt(double mouseX, double mouseY) {
        if (!canEdit()) return null;
        MobEntry entry = entryAt(mouseX, mouseY);
        if (entry == null) return null;
        int col = (int) ((mouseX - gridX) / cellW);
        int row = (int) ((mouseY - gridY) / CELL_H);
        int cellX = gridX + col * cellW;
        int cellY = gridY + row * CELL_H;
        int tx = cellX + cellW - TOGGLE_SIZE - 4;
        int ty = cellY + 4;
        return mouseX >= tx && mouseX < tx + TOGGLE_SIZE && mouseY >= ty && mouseY < ty + TOGGLE_SIZE ? entry : null;
    }
 
    /** Cambia el valor en vivo (el servidor valida el permiso y lo reenvía a todos); "Guardar" lo escribe al archivo. */
    private void toggle(MobEntry entry) {
        boolean value = !EntityConfig.isEnabled(entry.id());
        EntityConfig.set(entry.id(), value); // reflejo inmediato en esta pantalla
        PacketDistributor.sendToServer(new EntityConfigEditPayload(entry.id(), value));
        unsaved = true;
        if (saveButton != null) saveButton.setMessage(saveLabel());
        onDataUpdated();
    }
 
    /** Nombre de un mob bloqueado: su nombre real escrito con la fuente de la mesa de encantamientos. */
    private static Component censoredName(MobEntry entry) {
        return Component.literal(entry.sortName()).withStyle(Style.EMPTY.withFont(ALT_FONT));
    }
 
    private void drawScaled(GuiGraphics g, Component text, int x, int y, int color) {
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(STATS_SCALE, STATS_SCALE, 1.0F);
        g.drawString(font, text, 0, 0, color, false);
        g.pose().popPose();
    }
 
    /** "Neutral  ❤4  🛡0  ⚔2" dibujado con los iconos vanilla (corazón, armadura, espada) a escala reducida. */
    private void drawCompactStats(GuiGraphics g, MobEntry entry, int x, int y) {
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(STATS_SCALE, STATS_SCALE, 1.0F);
 
        int cx = 0;
        Component kind = entry.kind().label();
        g.drawString(font, kind, cx, 0, 0xFFFFFFFF, false);
        cx += font.width(kind) + 5;
 
        g.blitSprite(HEART_SPRITE, cx, -1, 9, 9);
        cx += 10;
        String hearts = number(entry.hearts());
        g.drawString(font, hearts, cx, 0, 0xFFFFFFFF, false);
        cx += font.width(hearts) + 5;
 
        g.blitSprite(ARMOR_SPRITE, cx, -1, 9, 9);
        cx += 10;
        String shields = number(entry.shields());
        g.drawString(font, shields, cx, 0, 0xFFFFFFFF, false);
        cx += font.width(shields) + 5;
 
        g.pose().pushPose();
        g.pose().translate(cx, -1, 0);
        g.pose().scale(9.0F / 16.0F, 9.0F / 16.0F, 1.0F);
        g.renderItem(swordIcon, 0, 0);
        g.pose().popPose();
        cx += 10;
        g.drawString(font, number(entry.damage()), cx, 0, 0xFFFFFFFF, false);
 
        g.pose().popPose();
    }
 
    private void renderScrollbar(GuiGraphics g) {
        int trackX = gridX + gridW + 2;
        g.fill(trackX, gridY, trackX + SCROLLBAR_W, gridY + gridH, 0xFF1A1A1A);
 
        int max = maxScrollRow();
        if (max <= 0) {
            g.fill(trackX, gridY, trackX + SCROLLBAR_W, gridY + gridH, 0xFF555555);
            return;
        }
        int thumbH = scrollbarThumbHeight();
        int thumbY = gridY + (int) ((gridH - thumbH) * (scrollRow / (float) max));
        g.fill(trackX, thumbY, trackX + SCROLLBAR_W, thumbY + thumbH, 0xFF000000);
        g.fill(trackX, thumbY, trackX + SCROLLBAR_W - 1, thumbY + thumbH - 1, 0xFFC6C6C6);
        g.fill(trackX + 1, thumbY + 1, trackX + SCROLLBAR_W - 1, thumbY + thumbH - 1, 0xFF8B8B8B);
    }
 
    private int scrollbarThumbHeight() {
        return Math.max(16, (int) (gridH * (visibleRows / (float) Math.max(visibleRows, totalRows()))));
    }
 
    // ---------------------------------------------------------------------------------------------
    // Tooltip detallado
    // ---------------------------------------------------------------------------------------------
 
    private List<Component> buildTooltip(MobEntry entry) {
        List<Component> lines = new ArrayList<>();
        ClientPokedexData.Discovery discovery = creative ? null : ClientPokedexData.get(entry.id());
 
        if (!creative && discovery == null) {
            // Bloqueado: no se revela nada del mob.
            lines.add(Component.translatable("pokecraft.pokedex.locked").withStyle(ChatFormatting.GRAY));
            lines.add(Component.translatable("pokecraft.pokedex.locked_hint").withStyle(ChatFormatting.DARK_GRAY));
            return lines;
        }
 
        lines.add(entry.displayName().copy().withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD));
        lines.add(Component.translatable("pokecraft.pokedex.tooltip.kind", entry.kind().label()).withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable("pokecraft.pokedex.tooltip.mod", MobCatalog.modName(entry.namespace())).withStyle(ChatFormatting.GRAY));
        lines.add(Component.empty());
        lines.add(Component.translatable("pokecraft.pokedex.tooltip.health", number(entry.health()), number(entry.hearts()))
                .withStyle(ChatFormatting.RED));
        lines.add(Component.translatable("pokecraft.pokedex.tooltip.armor", number(entry.armor()), number(entry.shields()))
                .withStyle(ChatFormatting.AQUA));
        lines.add(Component.translatable("pokecraft.pokedex.tooltip.damage", number(entry.damage()))
                .withStyle(ChatFormatting.GOLD));
        // En creativo no hay "primer registro": se omiten lugar, dimensión y fecha.
        if (creative) return lines;
 
        lines.add(Component.empty());
        lines.add(Component.translatable("pokecraft.pokedex.tooltip.registered").withStyle(ChatFormatting.YELLOW));
        lines.add(Component.translatable("pokecraft.pokedex.tooltip.position", discovery.x(), discovery.y(), discovery.z())
                .withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable("pokecraft.pokedex.tooltip.dimension", dimensionName(discovery.dimension()))
                .withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable("pokecraft.pokedex.tooltip.date", day(discovery.dayTime()), clock(discovery.dayTime()))
                .withStyle(ChatFormatting.GRAY));
        return lines;
    }
 
    private static Component dimensionName(ResourceLocation dimension) {
        String id = dimension.toString();
        return switch (id) {
            case "minecraft:overworld" -> Component.translatable("pokecraft.pokedex.dim.overworld");
            case "minecraft:the_nether" -> Component.translatable("pokecraft.pokedex.dim.the_nether");
            case "minecraft:the_end" -> Component.translatable("pokecraft.pokedex.dim.the_end");
            default -> Component.literal(id);
        };
    }
 
    /** Día de juego (empieza en 1). */
    private static long day(long dayTime) {
        return Math.max(0L, dayTime) / 24000L + 1L;
    }
 
    /** Hora del reloj del juego: el tick 0 del día es las 06:00. */
    private static String clock(long dayTime) {
        long t = Math.floorMod(dayTime, 24000L);
        long hour = (t / 1000L + 6L) % 24L;
        long minute = (t % 1000L) * 60L / 1000L;
        return String.format(Locale.ROOT, "%02d:%02d", hour, minute);
    }
 
    /** 4 -> "4", 2.5 -> "2.5". */
    private static String number(float value) {
        float rounded = Math.round(value);
        if (Math.abs(value - rounded) < 0.05F) return Integer.toString((int) rounded);
        return String.format(Locale.ROOT, "%.1f", value);
    }
 
    // ---------------------------------------------------------------------------------------------
    // Entrada
    // ---------------------------------------------------------------------------------------------
 
    @Nullable
    private MobEntry entryAt(double mouseX, double mouseY) {
        if (mouseX < gridX || mouseX >= gridX + gridW || mouseY < gridY || mouseY >= gridY + gridH) return null;
        int col = (int) ((mouseX - gridX) / cellW);
        int row = (int) ((mouseY - gridY) / CELL_H);
        if (col >= cols || row >= visibleRows) return null;
        int index = (row + scrollRow) * cols + col;
        return index >= 0 && index < filtered.size() ? filtered.get(index) : null;
    }
 
    @Nullable
    private Tab tabAtMouse(double mouseX, double mouseY) {
        if (mouseY < tabY() || mouseY >= panelY) return null;
        int slot = (int) ((mouseX - tabAreaX()) / TAB_W);
        if (mouseX < tabAreaX() || slot < 0 || slot >= visibleTabCount()) return null;
        int index = slot + tabScroll;
        return index < tabs.size() ? tabs.get(index) : null;
    }
 
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // 1. Con un menú abierto, él se queda con el clic (elige una casilla o se cierra).
        Dropdown<?> open = openDropdown();
        if (open != null) {
            open.mouseClicked(mouseX, mouseY, button);
            return true;
        }
 
        // 2. Botones de los menús desplegables.
        for (Dropdown<?> dropdown : visibleDropdowns()) {
            if (dropdown.mouseClicked(mouseX, mouseY, button)) return true;
        }
 
        // 2b. Selector true/false de una entidad (operadores en creativo).
        if (button == 0) {
            MobEntry toggled = toggleAt(mouseX, mouseY);
            if (toggled != null) {
                toggle(toggled);
                return true;
            }
        }
 
        // 2c. Creativo: clic sobre una casilla de mob (huevo al cursor, descartar, acumular) o del inventario.
        if (creative) {
            MobEntry cell = entryAt(mouseX, mouseY);
            if (cell != null) {
                handleCreativeClick(cell, button);
                return true;
            }
            int slotIndex = inventorySlotAt(mouseX, mouseY);
            if (slotIndex >= 0) {
                clickInventorySlot(slotIndex, button);
                return true;
            }
        }
 
        if (button == 0) {
            // 3. Pestañas y sus flechas.
            if (mouseY >= tabY() && mouseY < panelY) {
                if (mouseX >= panelX && mouseX < panelX + 16) {
                    tabScroll = Math.max(0, tabScroll - 1);
                    return true;
                }
                if (mouseX >= panelX + panelW - 16 && mouseX < panelX + panelW) {
                    tabScroll = Math.min(Math.max(0, tabs.size() - visibleTabCount()), tabScroll + 1);
                    return true;
                }
                int slot = (int) ((mouseX - tabAreaX()) / TAB_W);
                if (mouseX >= tabAreaX() && slot >= 0 && slot < visibleTabCount() && slot + tabScroll < tabs.size()) {
                    selectTab(slot + tabScroll);
                    return true;
                }
            }
 
            // 4. Barra de desplazamiento.
            int trackX = gridX + gridW + 2;
            if (maxScrollRow() > 0 && mouseX >= trackX && mouseX < trackX + SCROLLBAR_W
                    && mouseY >= gridY && mouseY < gridY + gridH) {
                draggingScrollbar = true;
                scrollToMouse(mouseY);
                return true;
            }
        }
 
        // Fuera de toda la interfaz: se suelta el ítem del cursor al mundo.
        if (!isInsideInterface(mouseX, mouseY)) {
            dropCursorItem(button);
            return true;
        }
 
        return super.mouseClicked(mouseX, mouseY, button);
    }
 
    /**
     * Reglas del inventario creativo vanilla sobre una casilla (huevo de aparición del mob):
     *  - Cursor vacío: clic izq. = 1 huevo; rueda = stack completo al cursor.
     *  - Shift+clic izq.: un stack completo directo al inventario del jugador (hotbar y luego filas, ver giveToInventory).
     *  - Mismo ítem en el cursor: clic izq. +1; rueda = stack completo; clic der. -1.
     *  - Otro ítem en el cursor: clic izq. lo descarta; clic der. descarta 1 unidad.
     */
    private void handleCreativeClick(MobEntry entry, int button) {
        ItemStack egg = entry.giveStack(); // huevo real, o el de depuración ("Generar [entidad]") si no tiene
        boolean hasEgg = !egg.isEmpty();
        boolean same = hasEgg && !carried.isEmpty() && ItemStack.isSameItemSameComponents(carried, egg);
 
        if (button == 1) { // clic derecho: quita 1 unidad del cursor (sea el mismo ítem u otro)
            if (!carried.isEmpty()) {
                carried.shrink(1);
                if (carried.isEmpty()) carried = ItemStack.EMPTY;
            }
            return;
        }
 
        if (button == 2) { // rueda: stack completo, solo con el cursor vacío o con ese mismo ítem
            if (hasEgg && (carried.isEmpty() || same)) {
                carried = egg.copyWithCount(egg.getMaxStackSize());
            }
            return;
        }
 
        if (button != 0) return;
 
        if (hasShiftDown()) { // Shift+clic: stack completo directo al inventario, sin tocar el cursor
            if (hasEgg) giveToInventory(egg);
            return;
        }
 
        if (same) {
            if (carried.getCount() < carried.getMaxStackSize()) {
                carried.grow(1);
            }
        } else if (!carried.isEmpty()) {
            carried = ItemStack.EMPTY; // ítem distinto: se descarta el del cursor
        } else if (hasEgg) {
            carried = egg.copyWithCount(1);
        }
    }
 
    private void scrollToMouse(double mouseY) {
        int max = maxScrollRow();
        int thumbH = scrollbarThumbHeight();
        double fraction = (mouseY - gridY - thumbH / 2.0) / Math.max(1, gridH - thumbH);
        scrollRow = Mth.clamp((int) Math.round(fraction * max), 0, max);
    }
 
    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (draggingScrollbar) {
            scrollToMouse(mouseY);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }
 
    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        draggingScrollbar = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }
 
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        Dropdown<?> open = openDropdown();
        if (open != null) {
            open.mouseScrolled(mouseX, mouseY, scrollY);
            return true;
        }
        if (mouseY >= tabY() && mouseY < panelY) {
            tabScroll = Mth.clamp(tabScroll - (int) Math.signum(scrollY), 0, Math.max(0, tabs.size() - visibleTabCount()));
            return true;
        }
        scrollRow = Mth.clamp(scrollRow - (int) Math.signum(scrollY), 0, maxScrollRow());
        return true;
    }
 
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            Dropdown<?> open = openDropdown();
            if (open != null) {
                open.close();
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}