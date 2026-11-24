package com.jose.pokecraft.ai;

import com.jose.pokecraft.pokedex.MobStrengthManager;
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
 * Órdenes masivas del palo sobre los aliados capturados del jugador:
 *  - {@link #startDirectAttack}: clic izquierdo con el palo sobre una entidad.
 *  - {@link #cancelDirectAttack}: Shift + clic izquierdo con el palo.
 *  - {@link #stayAll}: Shift + clic derecho con el palo.
 * Solo afectan a los aliados que estén en la MISMA dimensión que el jugador y a un máximo de
 * {@link #ORDER_RADIUS} bloques de él; los demás no reciben la orden y conservan su modo y su objetivo.
 * Solo se llama desde el servidor.
 */
public final class DirectAttackManager {
    /** Alcance máximo del ataque directo hacia donde mira el jugador (bloques). */
    public static final double LOOK_RANGE = 256.0D;
    /** Radio máximo (bloques) alrededor del jugador dentro del cual las órdenes globales alcanzan a sus aliados. */
    public static final double ORDER_RADIUS = 256.0D;
    private static final double ORDER_RADIUS_SQR = ORDER_RADIUS * ORDER_RADIUS;
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
     * de renderizado/simulación del servidor. Después, la orden la reciben solo los aliados dentro del radio
     * (ver {@link #startDirectAttack}).
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

    /**
     * Ordena atacar a {@code clicked} a los aliados del jugador que estén en su dimensión y a un máximo de
     * {@link #ORDER_RADIUS} bloques. Los que estén en Quieto, no tengan ataque, o estén en PVE y la víctima sea un
     * jugador o un aliado de otro jugador, no participan.
     */
    public static void startDirectAttack(ServerPlayer player, Entity clicked) {
        if (!(clicked instanceof LivingEntity victim) || victim instanceof ArmorStand || !victim.isAlive()) return;

        if (MobOrderManager.isAlly(player.getUUID(), victim)) {
            // Se apuntó a un mob aliado capturado: no es un objetivo válido y se ignora.
            // (Depuración, descomentar para comprobarlo en el juego:)
            // player.displayClientMessage(Component.literal("mob aliado"), true);
            return;
        }

        List<Mob> inRange = findAlliesInRange(player);
        List<Mob> engaged = new ArrayList<>();
        int blockedByPve = 0;
        int alreadyOnIt = 0;
        for (Mob mob : inRange) {
            // El modo Quieto ignora la orden; los mobs sin ataque (vacas, ovejas...) tampoco participan.
            if (MobOrderManager.getOrder(mob) == MobOrder.STAY) continue;
            if (!mob.getAttributes().hasAttribute(Attributes.ATTACK_DAMAGE)) continue;
            // Si ya iban a por esta víctima (golpes repetidos con el palo) no se reinicia nada.
            if (victim.getUUID().equals(MobOrderManager.getDirectTargetId(mob))) {
                alreadyOnIt++;
                continue;
            }
            // PVE: no atacan a jugadores ni a aliados de otros jugadores.
            if (!MobOrderManager.pvpRuleAllows(mob, victim)) {
                blockedByPve++;
                continue;
            }

            MobOrderManager.setDirectTarget(mob, victim);
            engaged.add(mob);
        }

        if (!engaged.isEmpty()) {
            player.displayClientMessage(Component.translatable(
                    engaged.size() == 1 ? "message.pokecraft.direct_attack.one" : "message.pokecraft.direct_attack.many",
                    joinNames(engaged), victim.getName()), true);
        } else if (blockedByPve > 0) {
            player.displayClientMessage(Component.translatable(
                    "message.pokecraft.direct_attack.pve_blocked", victim.getName()), true);
        } else if (alreadyOnIt == 0) {
            // Ningún aliado recibió la orden y tampoco era un golpe repetido: se explica el motivo en lugar de
            // fallar en silencio (antes era imposible distinguir "fuera de radio" de "todos quietos").
            if (inRange.isEmpty()) {
                if (hasOwnedAlliesBeyondRange(player)) {
                    player.displayClientMessage(Component.translatable(
                            "message.pokecraft.direct_attack.out_of_range", (int) ORDER_RADIUS), true);
                }
            } else {
                player.displayClientMessage(Component.translatable(
                        "message.pokecraft.direct_attack.none_available", victim.getName()), true);
            }
        }
    }

    /** Cancela el ataque directo de los aliados del jugador que estén en su dimensión y dentro del radio. */
    public static void cancelDirectAttack(ServerPlayer player) {
        List<Mob> released = new ArrayList<>();
        String victimName = "";
        for (Mob mob : findAlliesInRange(player)) {
            if (MobOrderManager.getDirectTargetId(mob) == null) continue;
            if (victimName.isEmpty()) victimName = MobOrderManager.getDirectTargetName(mob);
            MobOrderManager.clearDirectTarget(mob);
            // Reinstala el modo guardado (el ataque directo nunca lo modificó).
            MobOrderManager.apply(mob, MobOrderManager.getOrder(mob));
            released.add(mob);
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

    /** Pone en Quieto a los aliados del jugador que estén en su dimensión y dentro del radio. */
    public static void stayAll(ServerPlayer player) {
        List<Mob> changed = new ArrayList<>();
        for (Mob mob : findAlliesInRange(player)) {
            if (MobOrderManager.getOrder(mob) == MobOrder.STAY) continue;
            MobOrderManager.setOrder(mob, MobOrder.STAY);
            changed.add(mob);
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

    /**
     * Aliados capturados del jugador que reciben una orden global: están cargados en el MISMO nivel que él y a no
     * más de {@link #ORDER_RADIUS} bloques (comparación con distanceToSqr). Los de otras dimensiones o más lejanos
     * quedan fuera y no cambian de modo ni de objetivo.
     */
    private static List<Mob> findAlliesInRange(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        UUID ownerId = player.getUUID();
        return new ArrayList<>(level.getEntities(EntityTypeTest.forClass(Mob.class),
                // Un mob en Modo Venganza ignora las órdenes globales (todos quietos, ataque directo y su cancelación).
                mob -> isOwnedAlly(mob, ownerId) && mob.level() == level
                        && mob.distanceToSqr(player) <= ORDER_RADIUS_SQR));
    }

    /** ¿Tiene el jugador aliados cargados en su dimensión pero fuera del radio de las órdenes? (solo para avisar). */
    private static boolean hasOwnedAlliesBeyondRange(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        UUID ownerId = player.getUUID();
        return !level.getEntities(EntityTypeTest.forClass(Mob.class), mob -> isOwnedAlly(mob, ownerId)).isEmpty();
    }

    private static boolean isOwnedAlly(Mob mob, UUID ownerId) {
        return mob.isAlive() && MobOrderManager.isFriendly(mob) && !MobOrderManager.isVengeance(mob)
                && ownerId.equals(MobOrderManager.getOwnerId(mob));
    }

    /**
     * "A", "A y B", "A, B y C" (el "y" sale del idioma del jugador). Cada nombre sale con el color de la categoría
     * de fortaleza del mob aliado y respeta su nombre personalizado si fue renombrado.
     */
    private static Component joinNames(List<Mob> mobs) {
        MutableComponent result = Component.empty();
        for (int i = 0; i < mobs.size(); i++) {
            if (i > 0) {
                result.append(i == mobs.size() - 1 ? Component.translatable("message.pokecraft.list_and") : Component.literal(", "));
            }
            result.append(MobStrengthManager.coloredName(mobs.get(i)));
        }
        return result;
    }
}