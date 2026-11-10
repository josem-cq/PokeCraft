package com.jose.pokecraft.pokedex;
 
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.SpawnEggItem;
import net.neoforged.fml.loading.FMLPaths;
import org.slf4j.Logger;
 
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
 
/**
 * Qué tipos de entidad están "activos": se pueden capturar, meter en frascos, aparecen en la PokéDex y cuentan en sus
 * totales. Es la única fuente de verdad (CaptureRules solo delega aquí).
 *
 * - Valor por defecto: true si la entidad tiene huevo de aparición, o si es el Ender Dragon o el Wither; false para el
 *   resto (armor stand, proyectiles, partes de jefes...).
 * - Se guarda en config/pokecraft-entities.json (id -> true/false). Los operadores lo editan en vivo desde la PokéDex
 *   de creativo; los cambios se aplican al momento y se escriben al archivo con el botón "Guardar".
 * - El servidor es la autoridad; los clientes reciben una copia (EntityConfigSyncPayload) para filtrar su PokéDex.
 */
public final class EntityConfig {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Set<EntityType<?>> DEFAULT_TRUE_WITHOUT_EGG = Set.of(EntityType.ENDER_DRAGON, EntityType.WITHER);
 
    /** Solo valores explícitos (leídos del archivo o editados); lo que falte usa el valor por defecto. */
    private static final Map<ResourceLocation, Boolean> VALUES = new ConcurrentHashMap<>();
 
    private EntityConfig() {}
 
    public static Path file() {
        return FMLPaths.CONFIGDIR.get().resolve("pokecraft-entities.json");
    }
 
    public static boolean defaultValue(EntityType<?> type) {
        return DEFAULT_TRUE_WITHOUT_EGG.contains(type) || SpawnEggItem.byId(type) != null;
    }
 
    public static boolean isEnabled(EntityType<?> type) {
        if (type == EntityType.PLAYER) return false;
        Boolean explicit = VALUES.get(BuiltInRegistries.ENTITY_TYPE.getKey(type));
        return explicit != null ? explicit : defaultValue(type);
    }
 
    public static boolean isEnabled(ResourceLocation id) {
        return BuiltInRegistries.ENTITY_TYPE.getOptional(id).map(EntityConfig::isEnabled).orElse(false);
    }
 
    public static void set(ResourceLocation id, boolean value) {
        VALUES.put(id, value);
    }
 
    public static void replaceAll(Map<ResourceLocation, Boolean> values) {
        VALUES.clear();
        VALUES.putAll(values);
    }
 
    public static void clear() {
        VALUES.clear();
    }
 
    public static Map<ResourceLocation, Boolean> snapshot() {
        return new HashMap<>(VALUES);
    }
 
    /** Lee el archivo (servidor). Si no existe lo crea con los valores por defecto. */
    public static void load() {
        VALUES.clear();
        Path file = file();
        if (!Files.exists(file)) {
            save();
            return;
        }
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            JsonElement root = JsonParser.parseReader(reader);
            if (root.isJsonObject()) {
                for (Map.Entry<String, JsonElement> entry : root.getAsJsonObject().entrySet()) {
                    ResourceLocation id = ResourceLocation.tryParse(entry.getKey());
                    if (id == null) continue;
                    try {
                        VALUES.put(id, entry.getValue().getAsBoolean());
                    } catch (RuntimeException ignored) {
                        // valor que no es true/false: se ignora y esa entidad usa su valor por defecto
                    }
                }
            }
        } catch (IOException | RuntimeException e) {
            LOGGER.error("No se pudo leer {}: se usan los valores por defecto", file, e);
        }
    }
 
    /** Escribe TODAS las entidades registradas con su valor actual, ordenadas por id, para que sea fácil de editar a mano. */
    public static synchronized boolean save() {
        Map<String, Boolean> out = new TreeMap<>();
        for (EntityType<?> type : BuiltInRegistries.ENTITY_TYPE) {
            if (type == EntityType.PLAYER) continue;
            out.put(BuiltInRegistries.ENTITY_TYPE.getKey(type).toString(), isEnabled(type));
        }
        // Ids que ya no existen (mod quitado) se conservan para no perder lo que el usuario había decidido.
        VALUES.forEach((id, value) -> out.putIfAbsent(id.toString(), value));
 
        JsonObject json = new JsonObject();
        out.forEach(json::addProperty);
        Path file = file();
        try {
            Files.createDirectories(file.getParent());
            try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                GSON.toJson(json, writer);
            }
            return true;
        } catch (IOException e) {
            LOGGER.error("No se pudo guardar {}", file, e);
            return false;
        }
    }
}
