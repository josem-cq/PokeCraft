package com.jose.pokecraft.event;

import com.jose.pokecraft.PokeCraftMod;
import com.jose.pokecraft.ai.MobOrderManager;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.event.entity.living.LivingHealEvent;

/**
 * Impide que jefes ({@link Tags.EntityTypes#BOSSES}) y aliados capturados de PokeCraft se curen pasivamente
 * mientras están en combate (regeneración natural, efectos de regeneración, mecánicas propias de jefes que usan
 * {@code LivingEntity#heal}...).
 *
 * <p>Se registra solo en el bus de juego ({@code NeoForge.EVENT_BUS}), igual que {@link ModEventHandlers}:
 * {@code @EventBusSubscriber} sin {@code bus} usa ese bus en NeoForge 1.21.1.
 *
 * <p>Una entidad cuenta como "en combate" si se cumple CUALQUIERA de estas condiciones:
 * <ul>
 *   <li>Es un {@link Mob} con objetivo vivo y no eliminado.</li>
 *   <li>Recibió daño de otra criatura hace menos de {@link #COMBAT_WINDOW_TICKS} ticks. Esta ventana cubre los
 *       huecos entre cambios de objetivo o pérdidas momentáneas de visión.</li>
 * </ul>
 * Fuera de combate la curación no se toca.
 *
 * <p>Limitación conocida: {@code LivingHealEvent} no informa del origen de la curación, así que mientras dura el
 * combate también se bloquean pociones o comida que curen al aliado. Lo que no llama a {@code heal} sino a
 * {@code setHealth} directamente (los cristales del Ender Dragon, por ejemplo) no pasa por este evento.
 */
@EventBusSubscriber(modid = PokeCraftMod.MOD_ID)
public final class CombatHealEvents {

    /** Ventana (ticks) tras el último daño recibido de otra criatura en la que se sigue considerando combate. */
    public static final int COMBAT_WINDOW_TICKS = 100;

    private CombatHealEvents() {}

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onLivingHeal(LivingHealEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide() || event.getAmount() <= 0.0F) return;
        if (!isGuardedEntity(entity) || !isInCombat(entity)) return;

        event.setCanceled(true);
    }

    /** Jefe (etiqueta común {@code #c:bosses}) o criatura capturada/aliada de PokeCraft. */
    private static boolean isGuardedEntity(LivingEntity entity) {
        return entity.getType().is(Tags.EntityTypes.BOSSES) || MobOrderManager.isFriendly(entity);
    }

    /** Objetivo vivo, o daño reciente de otra criatura dentro de {@link #COMBAT_WINDOW_TICKS}. */
    private static boolean isInCombat(LivingEntity entity) {
        if (entity instanceof Mob mob) {
            LivingEntity target = mob.getTarget();
            if (target != null && target.isAlive() && !target.isRemoved()) {
                return true;
            }
        }
        // El timestamp vale 0 si nunca recibió daño de una criatura; sin esa comprobación, una entidad recién
        // aparecida (tickCount pequeño) contaría como "dañada hace poco".
        int hurtTimestamp = entity.getLastHurtByMobTimestamp();
        return hurtTimestamp > 0 && entity.tickCount - hurtTimestamp < COMBAT_WINDOW_TICKS;
    }
}