package com.jose.pokecraft.ai;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Indicador de modo al apuntar con el palo (mano principal) a un aliado propio.
 *
 * Se resuelve en el SERVIDOR porque el modo vive en el NBT persistente del mob, que no se sincroniza al cliente.
 * Muestra el modo en la misma zona que los mensajes de cambio de modo (barra de acción) como un PULSO único:
 * solo cuando la mirada pasa a posarse sobre una entidad distinta de la anterior. Mantener la mirada fija no lo
 * repite; apartarla (a otra entidad, al vacío, o guardar el palo) reinicia el estado, así que volver a mirar al
 * mismo mob lo muestra otra vez.
 */
public final class LookIndicatorManager {
    /** Alcance de la mirada, en bloques. */
    public static final double LOOK_RANGE = 16.0D;
    private static final int CHECK_INTERVAL_TICKS = 2;

    /** Jugador -> última entidad aliada sobre la que se mostró el indicador (solo hilo del servidor). */
    private static final Map<UUID, UUID> LAST_LOOKED = new HashMap<>();

    private LookIndicatorManager() {}

    public static void tick(ServerPlayer player) {
        if (player.tickCount % CHECK_INTERVAL_TICKS != 0) return;

        Mob looked = player.getMainHandItem().is(Items.STICK) ? findLookedAlly(player) : null;
        UUID lookedId = looked == null ? null : looked.getUUID();

        // put() devuelve la entidad anterior; remove() también. Si no hay aliado enfocado se olvida el estado.
        UUID previous = lookedId == null
                ? LAST_LOOKED.remove(player.getUUID())
                : LAST_LOOKED.put(player.getUUID(), lookedId);

        if (looked != null && !lookedId.equals(previous)) {
            show(player, looked);
        }
    }

    public static void forget(UUID playerId) {
        LAST_LOOKED.remove(playerId);
    }

    private static void show(ServerPlayer player, Mob mob) {
        Component mode = MobOrderManager.isVengeance(mob)
                ? Component.translatable("pokecraft.order.vengeance")
                : MobOrderManager.getOrder(mob).displayName();
        player.displayClientMessage(Component.translatable("message.pokecraft.order_look", mob.getDisplayName(), mode), true);
    }

    /**
     * Entidad a la que apunta el jugador (la más cercana en su línea de mirada, sin atravesar bloques); solo se
     * devuelve si es un aliado capturado de este jugador. Que otra criatura se interponga tapa al aliado.
     */
    private static Mob findLookedAlly(ServerPlayer player) {
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getViewVector(1.0F);
        Vec3 end = eye.add(look.scale(LOOK_RANGE));

        double limitSqr = LOOK_RANGE * LOOK_RANGE;
        HitResult blockHit = player.level().clip(
                new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        if (blockHit.getType() != HitResult.Type.MISS) {
            limitSqr = blockHit.getLocation().distanceToSqr(eye);
        }

        AABB search = player.getBoundingBox().expandTowards(look.scale(LOOK_RANGE)).inflate(1.0D);
        LivingEntity nearest = null;
        double nearestSqr = limitSqr;
        for (LivingEntity candidate : player.level().getEntitiesOfClass(LivingEntity.class, search,
                e -> e != player && e.isAlive() && !e.isSpectator() && e.isPickable())) {
            AABB box = candidate.getBoundingBox().inflate(0.1D);
            Optional<Vec3> hit = box.clip(eye, end);
            double distSqr;
            if (box.contains(eye)) {
                distSqr = 0.0D;
            } else if (hit.isPresent()) {
                distSqr = eye.distanceToSqr(hit.get());
            } else {
                continue;
            }
            if (distSqr <= nearestSqr) {
                nearest = candidate;
                nearestSqr = distSqr;
            }
        }

        if (nearest instanceof Mob mob
                && MobOrderManager.isFriendly(mob)
                && player.getUUID().equals(MobOrderManager.getOwnerId(mob))) {
            return mob;
        }
        return null;
    }
}
