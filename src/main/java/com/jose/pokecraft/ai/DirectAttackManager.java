package com.jose.pokecraft.ai;
 
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
 
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
 
/**
 * Órdenes masivas del palo sobre todos los aliados capturados del jugador:
 *  - {@link #startDirectAttack}: clic izquierdo con el palo sobre una entidad.
 *  - {@link #cancelDirectAttack}: Shift + clic izquierdo con el palo.
 *  - {@link #stayAll}: Shift + clic derecho con el palo.
 * Solo se llama desde el servidor.
 */
public final class DirectAttackManager {
    /** Alcance máximo del ataque directo hacia donde mira el jugador (bloques). */
    public static final double LOOK_RANGE = 256.0D;
    /** Mínimo de ticks entre dos peticiones de largo alcance del mismo jugador (el rayo no es gratis). */
    private static final int REQUEST_COOLDOWN_TICKS = 4;
    private static final String KEY_LAST_REQUEST = "PokeCraftLastDirectRequest";
 
    private DirectAttackManager() {}
 
    // ---------------------------------------------------------------------------------------------
    // Ataque directo
    // ---------------------------------------------------------------------------------------------
 
    /**
     * Ataque directo de largo alcance: traza un rayo desde los ojos del jugador hasta {@link #LOOK_RANGE} bloques y
     * ataca a la primera entidad viva que encuentre. Los bloques sólidos tapan la visión. Los aliados capturados
     * se atraviesan (no estorban la mira) y los ArmorStand se ignoran.
     * Solo alcanza entidades cargadas en el servidor, así que en la práctica el límite es también la distancia
     * de renderizado/simulación del servidor.
     */
    public static void startDirectAttackFromLook(ServerPlayer player) {
        long now = player.level().getGameTime();
        var data = player.getPersistentData();
        if (now - data.getLong(KEY_LAST_REQUEST) < REQUEST_COOLDOWN_TICKS) return;
        data.putLong(KEY_LAST_REQUEST, now);
 
        LivingEntity target = findLookTarget(player);
        if (target != null) {
            startDirectAttack(player, target);
        }
    }
 
    @Nullable
    private static LivingEntity findLookTarget(ServerPlayer player) {
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getViewVector(1.0F);
        Vec3 end = eye.add(look.scale(LOOK_RANGE));
 
        // Un bloque sólido antes de la entidad la tapa: el rayo se acorta hasta él.
        HitResult blockHit = player.level().clip(new ClipContext(
                eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        double limitSqr = LOOK_RANGE * LOOK_RANGE;
        if (blockHit.getType() != HitResult.Type.MISS) {
            limitSqr = blockHit.getLocation().distanceToSqr(eye);
        }
 
        AABB search = player.getBoundingBox().expandTowards(look.scale(LOOK_RANGE)).inflate(2.0D);
        UUID ownerId = player.getUUID();
 
        LivingEntity best = null;
        double bestSqr = limitSqr;
        for (LivingEntity candidate : player.level().getEntitiesOfClass(LivingEntity.class, search,
                e -> e != player && e.isAlive() && !e.isSpectator()
                        && !(e instanceof ArmorStand) && !MobOrderManager.isAlly(ownerId, e))) {
 
            // Asistencia de puntería: a 200 bloques un mob mide un par de píxeles, así que la caja de impacto
            // crece con la distancia (+1 bloque cada 100). Cerca es prácticamente la caja real.
            double distance = Math.sqrt(candidate.distanceToSqr(eye));
            AABB box = candidate.getBoundingBox().inflate(0.25D + distance * 0.01D);
 
            double hitSqr;
            if (box.contains(eye)) {
                hitSqr = 0.0D;
            } else {
                Optional<Vec3> hit = box.clip(eye, end);
                if (hit.isEmpty()) continue;
                hitSqr = eye.distanceToSqr(hit.get());
            }
            if (hitSqr < bestSqr) {
                bestSqr = hitSqr;
                best = candidate;
            }
        }
        return best;
    }
 
    public static void startDirectAttack(ServerPlayer player, Entity clicked) {
        if (!(clicked instanceof LivingEntity victim) || victim instanceof ArmorStand || !victim.isAlive()) return;
 
        if (MobOrderManager.isAlly(player.getUUID(), victim)) {
            // Se apuntó a un mob aliado capturado: no es un objetivo válido y se ignora.
            // (Depuración, descomentar para comprobarlo en el juego:)
            // player.displayClientMessage(Component.literal("mob aliado"), true);
            return;
        }
 
        List<Mob> engaged = new ArrayList<>();
        for (Mob mob : findAllies(player.serverLevel(), player.getUUID())) {
            // El modo Quieto ignora la orden; los mobs sin ataque (vacas, ovejas...) tampoco participan.
            if (MobOrderManager.getOrder(mob) == MobOrder.STAY) continue;
            if (!mob.getAttributes().hasAttribute(Attributes.ATTACK_DAMAGE)) continue;
            // Si ya iban a por esta víctima (golpes repetidos con el palo) no se reinicia nada.
            if (victim.getUUID().equals(MobOrderManager.getDirectTargetId(mob))) continue;
 
            MobOrderManager.setDirectTarget(mob, victim);
            engaged.add(mob);
        }
 
        if (!engaged.isEmpty()) {
            player.displayClientMessage(Component.translatable(
                    engaged.size() == 1 ? "message.pokecraft.direct_attack.one" : "message.pokecraft.direct_attack.many",
                    joinNames(engaged), victim.getName()), true);
        }
    }
 
    public static void cancelDirectAttack(ServerPlayer player) {
        List<Mob> released = new ArrayList<>();
        String victimName = "";
        for (ServerLevel level : player.server.getAllLevels()) {
            for (Mob mob : findAllies(level, player.getUUID())) {
                if (MobOrderManager.getDirectTargetId(mob) == null) continue;
                if (victimName.isEmpty()) victimName = MobOrderManager.getDirectTargetName(mob);
                MobOrderManager.clearDirectTarget(mob);
                // Reinstala el modo guardado (el ataque directo nunca lo modificó).
                MobOrderManager.apply(mob, MobOrderManager.getOrder(mob));
                released.add(mob);
            }
        }
 
        if (!released.isEmpty()) {
            player.displayClientMessage(Component.translatable(
                    released.size() == 1 ? "message.pokecraft.direct_cancel.one" : "message.pokecraft.direct_cancel.many",
                    joinNames(released), Component.literal(victimName)), true);
        }
    }
 
    // ---------------------------------------------------------------------------------------------
    // Todos quietos
    // ---------------------------------------------------------------------------------------------
 
    public static void stayAll(ServerPlayer player) {
        List<Mob> changed = new ArrayList<>();
        for (ServerLevel level : player.server.getAllLevels()) {
            for (Mob mob : findAllies(level, player.getUUID())) {
                if (MobOrderManager.getOrder(mob) == MobOrder.STAY) continue;
                MobOrderManager.setOrder(mob, MobOrder.STAY);
                changed.add(mob);
            }
        }
 
        if (changed.isEmpty()) {
            player.displayClientMessage(Component.translatable("message.pokecraft.stay_none"), true);
        } else {
            player.displayClientMessage(Component.translatable(
                    changed.size() == 1 ? "message.pokecraft.stay_all.one" : "message.pokecraft.stay_all.many",
                    joinNames(changed)), true);
        }
    }
 
    // ---------------------------------------------------------------------------------------------
    // Utilidades
    // ---------------------------------------------------------------------------------------------
 
    /** Aliados capturados del jugador que están cargados en ese nivel. */
    private static List<Mob> findAllies(ServerLevel level, UUID ownerId) {
        return new ArrayList<>(level.getEntities(EntityTypeTest.forClass(Mob.class),
                mob -> mob.isAlive() && MobOrderManager.isFriendly(mob) && ownerId.equals(MobOrderManager.getOwnerId(mob))));
    }
 
    /** "A", "A y B", "A, B y C" (el "y" sale del idioma del jugador). */
    private static Component joinNames(List<Mob> mobs) {
        MutableComponent result = Component.empty();
        for (int i = 0; i < mobs.size(); i++) {
            if (i > 0) {
                result.append(i == mobs.size() - 1 ? Component.translatable("message.pokecraft.list_and") : Component.literal(", "));
            }
            result.append(mobs.get(i).getName());
        }
        return result;
    }
}