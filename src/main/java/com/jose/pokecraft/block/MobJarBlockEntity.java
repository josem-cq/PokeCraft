package com.jose.pokecraft.block;
 
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
 
import java.util.Objects;
 
public class MobJarBlockEntity extends BlockEntity {
    @Nullable
    private CompoundTag storedEntityTag;
    @Nullable
    private Component customName;
 
    // Cachés solo usadas por el renderer (cliente). Se invalidan cada vez que cambia el contenido.
    @Nullable
    private Entity cachedEntity;
    private float cachedScale = -1.0f;
 
    public MobJarBlockEntity(BlockPos pos, BlockState state) {
        super(com.jose.pokecraft.PokeCraftMod.MOB_JAR_BE.get(), pos, state);
    }
 
    // ---------------------------------------------------------------------------------------------
    // Persistencia y sincronización
    // ---------------------------------------------------------------------------------------------
 
    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (storedEntityTag != null) {
            tag.put("StoredEntity", storedEntityTag);
        }
        if (customName != null) {
            tag.putString("CustomName", Component.Serializer.toJson(customName, registries));
        }
    }
 
    /**
     * Se usa tanto al cargar el chunk (servidor) como al recibir un paquete de actualización (cliente).
     * Un tag SIN "StoredEntity" / "CustomName" significa "vacío", por lo que hay que limpiar el estado
     * anterior; de lo contrario el cliente conserva el mob extraído y lo sigue renderizando.
     */
    @Override
    public void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
 
        CompoundTag newEntityTag = tag.contains("StoredEntity", Tag.TAG_COMPOUND) ? tag.getCompound("StoredEntity") : null;
        // Solo se invalida la caché si el contenido realmente cambió (evita reinstanciar la entidad
        // cuando llega un paquete que solo cambia, por ejemplo, el nombre).
        if (!Objects.equals(this.storedEntityTag, newEntityTag)) {
            this.storedEntityTag = newEntityTag;
            invalidateRenderCache();
        }
 
        Component newName = null;
        if (tag.contains("CustomName", Tag.TAG_STRING)) {
            try {
                newName = Component.Serializer.fromJson(tag.getString("CustomName"), registries);
            } catch (Exception ignored) {}
        }
        this.customName = newName;
    }
 
    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        saveAdditional(tag, registries);
        return tag;
    }
 
    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
 
    /** Marca el chunk como modificado y envía el paquete de actualización a los clientes (solo en servidor). */
    private void syncToClients() {
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }
 
    private void invalidateRenderCache() {
        this.cachedEntity = null;
        this.cachedScale = -1.0f;
    }
 
    // ---------------------------------------------------------------------------------------------
    // Acceso a datos
    // ---------------------------------------------------------------------------------------------
 
    public boolean hasStoredEntity() {
        return storedEntityTag != null;
    }
 
    @Nullable
    public CompoundTag getStoredEntityTag() {
        return storedEntityTag;
    }
 
    public void setStoredEntityTag(@Nullable CompoundTag tag) {
        if (Objects.equals(this.storedEntityTag, tag)) return;
        this.storedEntityTag = tag;
        invalidateRenderCache();
        syncToClients();
    }
 
    public boolean hasCustomName() {
        return customName != null;
    }
 
    @Nullable
    public Component getCustomName() {
        return customName;
    }
 
    public void setCustomName(@Nullable Component customName) {
        if (Objects.equals(this.customName, customName)) return;
        this.customName = customName;
        syncToClients();
    }
 
    // ---------------------------------------------------------------------------------------------
    // Renderizado (cliente)
    // ---------------------------------------------------------------------------------------------
 
    @Nullable
    public Entity getRenderEntity() {
        if (storedEntityTag == null) {
            // Defensa extra: si no hay contenido, no puede quedar ninguna entidad cacheada.
            cachedEntity = null;
            return null;
        }
        if (cachedEntity == null && level != null) {
            cachedEntity = EntityType.loadEntityRecursive(storedEntityTag, level, e -> e);
            if (cachedEntity instanceof Mob mob) {
                mob.setNoAi(true);
            }
        }
        return cachedEntity;
    }
 
    // Calcula la escala una sola vez y la reutiliza para evitar temblores
    public float getScale() {
        if (cachedScale < 0.0f) {
            Entity entity = getRenderEntity();
            if (entity != null) {
                float height = entity.getBbHeight();
                cachedScale = height > 0.0f ? Math.min(0.7f / height, 0.6f) : 0.4f;
            } else {
                cachedScale = 0.4f;
            }
        }
        return cachedScale;
    }
}