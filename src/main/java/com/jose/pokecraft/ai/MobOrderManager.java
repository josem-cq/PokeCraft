package com.jose.pokecraft.ai;
 
import com.jose.pokecraft.ai.goal.FollowOwnerMobGoal;
import com.jose.pokecraft.ai.goal.ManagedGoal;
import com.jose.pokecraft.ai.goal.ProtectTargetGoal;
import com.jose.pokecraft.ai.goal.StayPutGoal;
import com.jose.pokecraft.ai.goal.TargetGuardGoal;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;
 
import java.util.UUID;
 
/**
 * Gestor central de órdenes. Es el ÚNICO sitio que toca goalSelector / targetSelector de un mob de PokeCraft.
 *
 * Persistencia: dueño, bandera de aliado y modo viven en {@link Entity#getPersistentData()} (NeoForge lo serializa
 * con la entidad, y por eso también viaja dentro del NBT "CapturedEntity" de la Poké Ball). Las metas NO se
 * serializan, así que {@link #apply} se vuelve a llamar en EntityJoinLevelEvent (carga de chunk, reinicio, dimensión,
 * liberación desde la bola).
 */
public final class MobOrderManager {
    public static final String KEY_FRIENDLY = "PokeCraftFriendly";
    public static final String KEY_OWNER = "OwnerUUID";
    public static final String KEY_ORDER = "MobOrderState";
 
    /** Distancias de seguimiento (bloques). */
    private static final float FOLLOW_START = 6.0F;
    private static final float FOLLOW_STOP = 2.5F;
    private static final float FOLLOW_TELEPORT = 24.0F;
    private static final double FOLLOW_SPEED = 1.2D;
 
    /** En modo ATTACK, ¿puede atacar a jugadores que no sean el dueño? Por defecto no. */
    private static final boolean ATTACK_OTHER_PLAYERS = false;
 
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
 
    /** Guarda el modo y lo aplica de inmediato. */
    public static void setOrder(Mob mob, MobOrder order) {
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
    // Reglas de objetivos
    // ---------------------------------------------------------------------------------------------
 
    /** Aliado = el propio mob, el dueño, o cualquier cosa del mismo dueño (mobs de PokeCraft o mascotas vanilla). */
    public static boolean isAllowedTarget(Mob mob, LivingEntity target) {
        if (target == mob || !target.isAlive()) return false;
        UUID owner = getOwnerId(mob);
        if (owner == null) return true;
 
        if (owner.equals(target.getUUID())) return false;
        if (target instanceof OwnableEntity ownable && owner.equals(ownable.getOwnerUUID())) return false;
        return !(isFriendly(target) && owner.equals(getOwnerId(target)));
    }
 
    public static boolean isTargetAllowed(Mob mob, LivingEntity target, MobOrder order) {
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
 
        // 1. Parar lo que esté ejecutándose (libera las banderas MOVE/LOOK/TARGET y deja de perseguir).
        stopRunningGoals(mob.goalSelector);
        stopRunningGoals(mob.targetSelector);
 
        // 2. Quitar SOLO nuestras metas del goalSelector (la IA nativa de movimiento y combate se conserva)
        //    y VACIAR el targetSelector: así se neutraliza la IA de selección de blancos original
        //    (zombie contra aldeanos, HurtByTarget, ira de neutrales...). Se reemplaza por la del modo.
        mob.goalSelector.getAvailableGoals().removeIf(w -> w.getGoal() instanceof ManagedGoal);
        mob.targetSelector.getAvailableGoals().clear();
 
        // 3. Borrar el estado de IA arrastrado del modo anterior.
        dropTarget(mob);
        mob.setLastHurtByMob(null);
        mob.getNavigation().stop();
        clearBrainWalkTarget(mob);
 
        // 4. Instalar las metas del nuevo modo.
        mob.goalSelector.addGoal(0, new TargetGuardGoal(mob, order));
 
        switch (order) {
            case STAY -> mob.goalSelector.addGoal(0, new StayPutGoal(mob));
            case FOLLOW -> mob.goalSelector.addGoal(3, followGoal(mob));
            case PROTECT -> {
                mob.goalSelector.addGoal(3, followGoal(mob));
                mob.targetSelector.addGoal(1, new ProtectTargetGoal(mob));
            }
            case ATTACK -> mob.targetSelector.addGoal(2,
                    new NearestAttackableTargetGoal<>(mob, LivingEntity.class, 10, true, false,
                            target -> isAttackModeTarget(mob, target)));
        }
    }
 
    private static FollowOwnerMobGoal followGoal(Mob mob) {
        return new FollowOwnerMobGoal(mob, FOLLOW_SPEED, FOLLOW_START, FOLLOW_STOP, FOLLOW_TELEPORT);
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