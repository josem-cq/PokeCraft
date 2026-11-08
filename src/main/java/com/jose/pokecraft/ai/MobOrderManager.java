package com.jose.pokecraft.ai;
 
import com.jose.pokecraft.ai.goal.AttackTargetGoal;
import com.jose.pokecraft.ai.goal.DirectApproachGoal;
import com.jose.pokecraft.ai.goal.DirectTargetGoal;
import com.jose.pokecraft.ai.goal.FollowOwnerMobGoal;
import com.jose.pokecraft.ai.goal.ManagedGoal;
import com.jose.pokecraft.ai.goal.NativeTargetStashGoal;
import com.jose.pokecraft.ai.goal.ProtectTargetGoal;
import com.jose.pokecraft.ai.goal.StayPutGoal;
import com.jose.pokecraft.ai.goal.TargetGuardGoal;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;
 
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
 
/**
 * Gestor central de órdenes. Es el ÚNICO sitio que toca goalSelector / targetSelector de un mob de PokeCraft.
 *
 * Persistencia: dueño, bandera de aliado, modo y ataque directo viven en {@link Entity#getPersistentData()}
 * (NeoForge lo serializa con la entidad, y por eso también viaja dentro del NBT "CapturedEntity" de la Poké Ball).
 * Las metas NO se serializan, así que {@link #apply} se vuelve a llamar en EntityJoinLevelEvent (carga de chunk,
 * reinicio, dimensión, liberación desde la bola).
 *
 * Ataque directo: es una SUPERPOSICIÓN sobre el modo, no un modo nuevo. El modo guardado (MobOrderState) no se toca,
 * así que al terminar el ataque basta con volver a llamar a {@link #apply} para regresar exactamente al modo previo.
 */
public final class MobOrderManager {
    public static final String KEY_FRIENDLY = "PokeCraftFriendly";
    public static final String KEY_OWNER = "OwnerUUID";
    public static final String KEY_ORDER = "MobOrderState";
    /** Última vez (gameTime) que el mob tuvo un objetivo de ataque válido. */
    public static final String KEY_LAST_COMBAT = "PokeCraftLastCombat";
    /** UUID de la entidad marcada con el palo (ataque directo activo) y su nombre para los mensajes. */
    public static final String KEY_DIRECT_TARGET = "PokeCraftDirectTarget";
    public static final String KEY_DIRECT_NAME = "PokeCraftDirectName";
 
    /** Distancias de seguimiento (bloques). */
    private static final float FOLLOW_START = 6.0F;
    private static final float FOLLOW_STOP = 2.5F;
    private static final float FOLLOW_TELEPORT = 24.0F;
    private static final double FOLLOW_SPEED = 1.2D;
 
    /** En modo ATTACK, ¿puede atacar a jugadores que no sean el dueño? Por defecto no. */
    private static final boolean ATTACK_OTHER_PLAYERS = false;
 
    /**
     * Mobs cuyo modo hay que reaplicar al final del tick. Las metas no pueden reinstalarse desde dentro de su propio
     * tick (se modificaría el conjunto de metas mientras el GoalSelector lo recorre).
     */
    private static final List<Mob> PENDING_REFRESH = new ArrayList<>();
 
    private MobOrderManager() {}
 
    // ---------------------------------------------------------------------------------------------
    // Datos persistentes
    // ---------------------------------------------------------------------------------------------
 
    public static boolean isFriendly(Entity entity) {
        return entity.getPersistentData().getBoolean(KEY_FRIENDLY);
    }
 
    @Nullable
    public static UUID getOwnerId(Entity entity) {
        CompoundTag data = entity.getPersistentData();
        return data.hasUUID(KEY_OWNER) ? data.getUUID(KEY_OWNER) : null;
    }
 
    /** El dueño, solo si está conectado y en la misma dimensión. */
    @Nullable
    public static Player getOwner(Mob mob) {
        UUID id = getOwnerId(mob);
        return id == null ? null : mob.level().getPlayerByUUID(id);
    }
 
    public static MobOrder getOrder(Entity entity) {
        return MobOrder.byId(entity.getPersistentData().getInt(KEY_ORDER));
    }
 
    /**
     * Guarda el modo y lo aplica de inmediato. Cambiar el modo a mano termina cualquier ataque directo en curso.
     */
    public static void setOrder(Mob mob, MobOrder order) {
        clearDirectTarget(mob);
        mob.getPersistentData().putInt(KEY_ORDER, order.ordinal());
        apply(mob, order);
    }
 
    /** Pasa al siguiente modo del ciclo, lo aplica y lo devuelve. */
    public static MobOrder cycleOrder(Mob mob) {
        MobOrder next = getOrder(mob).next();
        setOrder(mob, next);
        return next;
    }
 
    // ---------------------------------------------------------------------------------------------
    // Ataque directo
    // ---------------------------------------------------------------------------------------------
 
    @Nullable
    public static UUID getDirectTargetId(Entity entity) {
        CompoundTag data = entity.getPersistentData();
        return data.hasUUID(KEY_DIRECT_TARGET) ? data.getUUID(KEY_DIRECT_TARGET) : null;
    }
 
    public static String getDirectTargetName(Entity entity) {
        return entity.getPersistentData().getString(KEY_DIRECT_NAME);
    }
 
    /** Marca a la víctima y reinstala la IA del mob con la superposición de ataque directo. */
    public static void setDirectTarget(Mob mob, LivingEntity victim) {
        CompoundTag data = mob.getPersistentData();
        data.putUUID(KEY_DIRECT_TARGET, victim.getUUID());
        data.putString(KEY_DIRECT_NAME, victim.getName().getString());
        apply(mob, getOrder(mob));
    }
 
    /** Solo borra los datos; quien llama decide cuándo reinstalar la IA. */
    public static void clearDirectTarget(Mob mob) {
        CompoundTag data = mob.getPersistentData();
        data.remove(KEY_DIRECT_TARGET);
        data.remove(KEY_DIRECT_NAME);
    }
 
    /** La víctima si sigue viva, cargada y en la misma dimensión; si no, null. */
    @Nullable
    public static LivingEntity resolveDirectTarget(Mob mob) {
        UUID id = getDirectTargetId(mob);
        if (id == null || !(mob.level() instanceof ServerLevel level)) return null;
        Entity found = level.getEntity(id);
        return found instanceof LivingEntity living && living.isAlive() && !living.isRemoved() ? living : null;
    }
 
    /**
     * La víctima murió, desapareció o se descargó: termina el ataque directo. Se llama desde dentro de las metas, así
     * que la reinstalación de la IA (vuelta al modo previo) se aplaza al final del tick.
     */
    public static void finishDirectAttack(Mob mob) {
        clearDirectTarget(mob);
        PENDING_REFRESH.add(mob);
    }
 
    /** Se llama en ServerTickEvent.Post. */
    public static void flushPendingRefresh() {
        if (PENDING_REFRESH.isEmpty()) return;
        List<Mob> mobs = new ArrayList<>(PENDING_REFRESH);
        PENDING_REFRESH.clear();
        for (Mob mob : mobs) {
            if (mob.isAlive() && !mob.isRemoved()) {
                apply(mob, getOrder(mob));
            }
        }
    }
 
    // ---------------------------------------------------------------------------------------------
    // Combate (para decidir cuándo se puede teletransportar)
    // ---------------------------------------------------------------------------------------------
 
    /** Lo llama TargetGuardGoal cada tick mientras el mob tiene un objetivo válido. */
    public static void markCombat(Mob mob) {
        mob.getPersistentData().putLong(KEY_LAST_COMBAT, mob.level().getGameTime());
    }
 
    /**
     * Ticks desde el último combate: el menor entre (a) la última vez que tuvo objetivo, (b) la última vez que
     * recibió daño de una entidad y (c) la última vez que hirió a una entidad.
     */
    public static long ticksSinceCombat(Mob mob) {
        long result = Long.MAX_VALUE;
 
        CompoundTag data = mob.getPersistentData();
        if (data.contains(KEY_LAST_COMBAT)) {
            result = Math.min(result, mob.level().getGameTime() - data.getLong(KEY_LAST_COMBAT));
        }
        if (mob.getLastHurtByMob() != null) {
            result = Math.min(result, mob.tickCount - mob.getLastHurtByMobTimestamp());
        }
        if (mob.getLastHurtMob() != null) {
            result = Math.min(result, mob.tickCount - mob.getLastHurtMobTimestamp());
        }
        return result;
    }
 
    // ---------------------------------------------------------------------------------------------
    // Reglas de objetivos
    // ---------------------------------------------------------------------------------------------
 
    /** Aliado del dueño: él mismo, mobs de PokeCraft con ese dueño o mascotas vanilla de ese dueño. */
    public static boolean isAlly(@Nullable UUID ownerId, Entity entity) {
        if (ownerId == null) return false;
        if (ownerId.equals(entity.getUUID())) return true;
        if (entity instanceof OwnableEntity ownable && ownerId.equals(ownable.getOwnerUUID())) return true;
        return isFriendly(entity) && ownerId.equals(getOwnerId(entity));
    }
 
    public static boolean isAllowedTarget(Mob mob, LivingEntity target) {
        if (target == mob || !target.isAlive()) return false;
        return !isAlly(getOwnerId(mob), target);
    }
 
    public static boolean isTargetAllowed(Mob mob, LivingEntity target, MobOrder order) {
        // La víctima marcada con el palo siempre es válida, sea cual sea el modo (excepto Quieto, que ignora la orden).
        UUID direct = getDirectTargetId(mob);
        if (direct != null && direct.equals(target.getUUID())) {
            return order != MobOrder.STAY && !isAlly(getOwnerId(mob), target);
        }
        return order.allowsTargeting() && isAllowedTarget(mob, target);
    }
 
    private static boolean isAttackModeTarget(Mob mob, LivingEntity target) {
        if (!isAllowedTarget(mob, target)) return false;
        if (target instanceof ArmorStand) return false;
        return ATTACK_OTHER_PLAYERS || !(target instanceof Player);
    }
 
    // ---------------------------------------------------------------------------------------------
    // Aplicación de un modo (el método centralizado de limpieza y reasignación)
    // ---------------------------------------------------------------------------------------------
 
    public static void apply(Mob mob, MobOrder order) {
        if (mob.level().isClientSide) return;
 
        // 0. Guardar (una sola vez) las metas de objetivo nativas, para poder restaurarlas en el modo FREE.
        NativeTargetStashGoal stash = ensureNativeStash(mob);
 
        // 1. Parar lo que esté ejecutándose (libera las banderas MOVE/LOOK/TARGET y deja de perseguir).
        stopRunningGoals(mob.goalSelector);
        stopRunningGoals(mob.targetSelector);
 
        // 2. Quitar SOLO nuestras metas del goalSelector (la IA nativa de movimiento y combate se conserva)
        //    y VACIAR el targetSelector. Así se neutraliza la IA de selección de blancos original
        //    (zombie contra aldeanos, HurtByTarget, ira de neutrales...). Cada modo pone la suya.
        mob.goalSelector.getAvailableGoals().removeIf(w -> w.getGoal() instanceof ManagedGoal);
        mob.targetSelector.getAvailableGoals().clear();
 
        // 3. Borrar el estado de IA arrastrado del modo anterior.
        dropTarget(mob);
        mob.setLastHurtByMob(null);
        mob.getNavigation().stop();
        clearBrainWalkTarget(mob);
 
        // 4. Instalar las metas.
        mob.goalSelector.addGoal(0, new TargetGuardGoal(mob, order));
 
        // 4a. Ataque directo activo (el modo Quieto lo ignora): máxima prioridad, sin metas del modo. Al no
        //     instalarse FollowOwnerMobGoal no hay teletransporte posible; se avanza con la navegación normal.
        if (order != MobOrder.STAY && getDirectTargetId(mob) != null) {
            mob.goalSelector.addGoal(1, new DirectApproachGoal(mob));
            mob.targetSelector.addGoal(0, new DirectTargetGoal(mob));
            return;
        }
 
        // 4b. Metas del modo.
        switch (order) {
            case STAY -> mob.goalSelector.addGoal(0, new StayPutGoal(mob));
            case FOLLOW -> mob.goalSelector.addGoal(3, followGoal(mob));
            case PROTECT -> {
                mob.goalSelector.addGoal(3, followGoal(mob));
                mob.targetSelector.addGoal(1, new ProtectTargetGoal(mob, ProtectTargetGoal.Role.SELF));
                mob.targetSelector.addGoal(2, new ProtectTargetGoal(mob, ProtectTargetGoal.Role.OWNER));
            }
            case ATTACK -> mob.targetSelector.addGoal(2,
                    new AttackTargetGoal(mob, target -> isAttackModeTarget(mob, target)));
            // FREE: sin metas de movimiento y con la IA de objetivos original. El TargetGuardGoal (y el
            // LivingChangeTargetEvent) impiden que elija al dueño o a sus aliados.
            case FREE -> mob.targetSelector.getAvailableGoals().addAll(stash.getStashed());
        }
    }
 
    private static FollowOwnerMobGoal followGoal(Mob mob) {
        return new FollowOwnerMobGoal(mob, FOLLOW_SPEED, FOLLOW_START, FOLLOW_STOP, FOLLOW_TELEPORT);
    }
 
    /**
     * Devuelve el guardián de metas nativas del mob; si no existe (primer apply tras crearse/cargarse el mob),
     * lo crea con las metas de objetivo que tiene ahora mismo, que todavía son las originales.
     */
    private static NativeTargetStashGoal ensureNativeStash(Mob mob) {
        for (WrappedGoal wrapped : mob.goalSelector.getAvailableGoals()) {
            if (wrapped.getGoal() instanceof NativeTargetStashGoal existing) {
                return existing;
            }
        }
        List<WrappedGoal> natives = mob.targetSelector.getAvailableGoals().stream()
                .filter(w -> !(w.getGoal() instanceof ManagedGoal))
                .toList();
        NativeTargetStashGoal stash = new NativeTargetStashGoal(natives);
        mob.goalSelector.addGoal(Integer.MAX_VALUE, stash);
        return stash;
    }
 
    /**
     * WrappedGoal.stop() deja running = false; el GoalSelector libera las banderas bloqueadas de goals
     * parados en su siguiente tick, por eso hay que parar ANTES de eliminar.
     */
    private static void stopRunningGoals(GoalSelector selector) {
        selector.getAvailableGoals().stream()
                .filter(WrappedGoal::isRunning)
                .toList()
                .forEach(WrappedGoal::stop);
    }
 
    // ---------------------------------------------------------------------------------------------
    // Utilidades de IA
    // ---------------------------------------------------------------------------------------------
 
    public static void dropTarget(Mob mob) {
        mob.setTarget(null);
        if (mob instanceof NeutralMob neutral) {
            neutral.stopBeingAngry();
        }
        mob.getBrain().eraseMemory(MemoryModuleType.ATTACK_TARGET);
    }
 
    public static void haltMovement(Mob mob) {
        mob.getNavigation().stop();
        mob.setZza(0.0F);
        mob.setXxa(0.0F);
        clearBrainWalkTarget(mob);
    }
 
    /** Mobs con Brain (aldeano, piglin, warden, cabra...) guardan su destino en memorias, no en goals. */
    private static void clearBrainWalkTarget(Mob mob) {
        mob.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
    }
}