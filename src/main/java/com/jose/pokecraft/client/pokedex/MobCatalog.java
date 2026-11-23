package com.jose.pokecraft.client.pokedex;
 
import com.jose.pokecraft.pokedex.EntityConfig;
import com.jose.pokecraft.pokedex.MobStrength;
import com.jose.pokecraft.pokedex.MobStrengthManager;
import com.jose.pokecraft.pokedex.StrengthClassifier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.neoforged.fml.ModList;
import org.jetbrains.annotations.Nullable;
 
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
 
/**
 * Catálogo de TODOS los tipos de entidad del registro (vanilla y de otros mods), construido una vez en el cliente la
 * primera vez que se abre la PokéDex. Se reconstruye si cambia el idioma o al reconectar a otro mundo.
 *
 * Qué entidades se MUESTRAN (activas) lo decide cada pantalla con EntityConfig; aquí entran todas salvo el jugador,
 * porque la PokéDex de creativo de un operador debe poder verlas y activarlas.
 *
 * Fortaleza: las estadísticas base y la categoría de cada entrada vienen de {@link MobStrengthManager}. La categoría
 * se reasigna automáticamente (sin reconstruir el catálogo) cada vez que cambia {@link EntityConfig#version()}.
 *
 * Cada entrada se agrupa por la pestaña del inventario creativo donde está su huevo de aparición (p. ej. "Huevos de
 * aparición" en vanilla). Las que no tienen huevo se agrupan con las demás del mismo mod.
 */
public final class MobCatalog {
 
    /**
     * Pestaña de la PokéDex = pestaña de creativo.
     *
     * @param key       id de la pestaña de creativo, o "mod:&lt;namespace&gt;" si no hay pestaña conocida
     * @param namespace mod dueño de la pestaña
     * @param name      nombre de la pestaña de creativo
     * @param icon      icono (el que muestra el inventario creativo)
     */
    public record TabInfo(String key, String namespace, Component name, ItemStack icon, int order) {}
 
    private static List<MobEntry> entries = List.of();
    private static List<TabInfo> tabs = List.of();
    private static boolean built = false;
    private static String builtForLanguage = "";
    /** Versión de EntityConfig con la que se asignaron las categorías actuales (-1 = pendiente de clasificar). */
    private static int classifiedVersion = -1;
    private static final Map<String, String> MOD_NAMES = new HashMap<>();
 
    private MobCatalog() {}
 
    public static List<MobEntry> entries() {
        ensureBuilt();
        return entries;
    }
 
    /** Pestañas que usan las entradas del catálogo, ya ordenadas (vanilla primero, luego por mod y orden de creativo). */
    public static List<TabInfo> tabs() {
        ensureBuilt();
        return tabs;
    }
 
    private static void ensureBuilt() {
        String language = Minecraft.getInstance().getLanguageManager().getSelected();
        if (!built || !language.equals(builtForLanguage)) {
            build();
            builtForLanguage = language;
            built = true;
        }
        reclassify(); // barato: solo trabaja si cambió la configuración de entidades activas
    }
 
    public static void invalidate() {
        built = false;
        entries = List.of();
        tabs = List.of();
        classifiedVersion = -1;
        MOD_NAMES.clear();
        MobStrengthManager.invalidate();
    }
 
    /** Nombre legible del mod dueño de un namespace ("minecraft" -> "Minecraft"). */
    public static String modName(String namespace) {
        return MOD_NAMES.computeIfAbsent(namespace, ns -> ModList.get().getModContainerById(ns)
                .map(container -> container.getModInfo().getDisplayName())
                .orElse(ns));
    }
 
    // ---------------------------------------------------------------------------------------------
    // Fortaleza
    // ---------------------------------------------------------------------------------------------
 
    /**
     * Reasigna la categoría de fortaleza de todas las entradas si la lista de entidades activas cambió desde la
     * última vez. Los puntajes no cambian (son base); solo cambian los umbrales y, con ellos, la categoría.
     */
    private static void reclassify() {
        int version = EntityConfig.version();
        if (version == classifiedVersion || entries.isEmpty()) return;
 
        Level level = Minecraft.getInstance().level;
        if (level == null) return; // sin mundo no se pueden instanciar mobs; se reintenta en la próxima consulta
 
        StrengthClassifier.Snapshot snapshot = MobStrengthManager.snapshot(level);
        List<MobEntry> updated = new ArrayList<>(entries.size());
        for (MobEntry entry : entries) {
            updated.add(entry.withStrength(snapshot.tierOf(entry.strengthScore(), entry.forcedLowestStrength())));
        }
        entries = updated;
        classifiedVersion = version;
    }
 
    // ---------------------------------------------------------------------------------------------
 
    private static void build() {
        Level level = Minecraft.getInstance().level;
        classifiedVersion = -1; // las entradas nuevas llevan una categoría provisional hasta reclassify()
 
        // 1) Qué pestaña de creativo contiene el huevo de cada tipo.
        Map<Item, String> eggTab = new HashMap<>();
        Map<String, TabInfo> tabInfos = new LinkedHashMap<>();
        scanCreativeTabs(level, eggTab, tabInfos);
 
        // 2) Entradas.
        List<MobEntry> raw = new ArrayList<>();
        for (EntityType<?> type : BuiltInRegistries.ENTITY_TYPE) {
            ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(type);
            try {
                MobEntry entry = describe(type, id, level);
                if (entry != null) raw.add(entry);
            } catch (Exception ignored) {
                // Una entidad de un mod con constructor problemático no debe impedir abrir la PokéDex.
            }
        }
 
        // 3) Pestaña de cada entrada: la del huevo; sin huevo, la de su mod (donde estén los huevos de ese mod).
        Map<String, String> namespaceTab = new HashMap<>();
        for (MobEntry entry : raw) {
            if (!entry.hasEgg()) continue;
            String key = eggTab.get(entry.icon().getItem());
            if (key != null) namespaceTab.putIfAbsent(entry.namespace(), key);
        }
        List<MobEntry> result = new ArrayList<>(raw.size());
        Map<String, TabInfo> used = new LinkedHashMap<>();
        for (MobEntry entry : raw) {
            String key = entry.hasEgg() ? eggTab.get(entry.icon().getItem()) : null;
            if (key == null) key = namespaceTab.get(entry.namespace());
            if (key == null) key = firstTabOfNamespace(tabInfos, entry.namespace());
            if (key == null) key = "mod:" + entry.namespace();
 
            TabInfo info = tabInfos.get(key);
            if (info == null) { // sin pestaña de creativo conocida: una genérica con el nombre del mod
                info = new TabInfo(key, entry.namespace(), Component.literal(modName(entry.namespace())),
                        new ItemStack(Items.PAPER), Integer.MAX_VALUE);
                tabInfos.put(key, info);
            }
            used.putIfAbsent(key, info);
            result.add(entry.withTabKey(key));
        }
 
        List<TabInfo> ordered = new ArrayList<>(used.values());
        ordered.sort(Comparator.comparing((TabInfo t) -> !t.namespace().equals("minecraft"))
                .thenComparing(t -> modName(t.namespace()), String.CASE_INSENSITIVE_ORDER)
                .thenComparingInt(TabInfo::order));
 
        entries = result;
        tabs = ordered;
    }
 
    @Nullable
    private static String firstTabOfNamespace(Map<String, TabInfo> tabInfos, String namespace) {
        for (TabInfo info : tabInfos.values()) {
            if (info.namespace().equals(namespace)) return info.key();
        }
        return null;
    }
 
    /** Recorre las pestañas de creativo: apunta en qué pestaña está cada huevo y guarda su nombre e icono. */
    private static void scanCreativeTabs(@Nullable Level level, Map<Item, String> eggTab, Map<String, TabInfo> tabInfos) {
        if (level == null) return;
        try {
            var player = Minecraft.getInstance().player;
            // Es lo mismo que hace el inventario creativo al abrirse; si nada cambió, vuelve de inmediato.
            CreativeModeTabs.tryRebuildTabContents(level.enabledFeatures(),
                    player != null && player.canUseGameMasterBlocks(), level.registryAccess());
 
            int order = 0;
            for (Map.Entry<ResourceKey<CreativeModeTab>, CreativeModeTab> e : BuiltInRegistries.CREATIVE_MODE_TAB.entrySet()) {
                CreativeModeTab tab = e.getValue();
                order++;
                if (tab.getType() != CreativeModeTab.Type.CATEGORY) continue;
                ResourceLocation tabId = e.getKey().location();
                String key = tabId.toString();
                for (ItemStack stack : tab.getDisplayItems()) {
                    if (stack.getItem() instanceof SpawnEggItem && eggTab.putIfAbsent(stack.getItem(), key) == null) {
                        tabInfos.computeIfAbsent(key, k -> new TabInfo(k, tabId.getNamespace(), tab.getDisplayName(),
                                tab.getIconItem().copy(), 0));
                    }
                }
            }
            // El orden real de cada pestaña (el recorrido anterior solo conocía las que tienen huevos).
            Map<String, Integer> orderByKey = new HashMap<>();
            int index = 0;
            for (ResourceKey<CreativeModeTab> key : BuiltInRegistries.CREATIVE_MODE_TAB.registryKeySet()) {
                orderByKey.put(key.location().toString(), index++);
            }
            int unknownOrder = order;
            tabInfos.replaceAll((k, t) -> new TabInfo(t.key(), t.namespace(), t.name(), t.icon(), orderByKey.getOrDefault(k, unknownOrder)));
        } catch (Exception ignored) {
            // Sin información de pestañas: la PokéDex agrupa por mod.
        }
    }
 
    @Nullable
    private static MobEntry describe(EntityType<?> type, ResourceLocation id, @Nullable Level level) {
        if (type == EntityType.PLAYER) return null;
 
        // Tipo (neutral/hostil/pasivo), vida, armadura, ataque y puntaje BASE: una única fuente compartida con el servidor.
        StrengthClassifier.BaseStats stats = MobStrengthManager.baseStats(type, level);
 
        Component name = type.getDescription();
        SpawnEggItem egg = SpawnEggItem.byId(type);
        ItemStack icon = egg != null ? new ItemStack(egg) : ItemStack.EMPTY;
 
        // La categoría es provisional (la más baja): reclassify() la fija con los umbrales de la población activa.
        return new MobEntry(id, id.getNamespace(), name, name.getString(), stats.kind(),
                stats.health(), stats.armor(), stats.damage(), stats.score(), MobStrength.VERY_WEAK,
                icon, debugEgg(type, id), "");
    }
 
    /** Huevo de gallina que genera cualquier entidad (componente entity_data) con el nombre "Generar [entidad]". */
    private static ItemStack debugEgg(EntityType<?> type, ResourceLocation id) {
        ItemStack stack = new ItemStack(Items.CHICKEN_SPAWN_EGG);
        CompoundTag data = new CompoundTag();
        data.putString("id", id.toString());
        stack.set(DataComponents.ENTITY_DATA, CustomData.of(data));
        Component shown = I18n.exists(type.getDescriptionId()) ? type.getDescription() : Component.literal(id.toString());
        stack.set(DataComponents.ITEM_NAME, Component.translatable("pokecraft.debug.spawn", shown));
        return stack;
    }
}