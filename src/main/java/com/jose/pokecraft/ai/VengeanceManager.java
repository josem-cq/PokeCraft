package com.jose.pokecraft.ai;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.stream.LongStream;

/**
 * Modo Venganza.
 *
 * Disparador: cuando el dueño golpea a su aliado estando en modo "quieto", cada golpe se registra en una ventana
 * deslizante de 1 minuto. Al juntar {@value #HITS_REQUIRED} golpes dentro de esa ventana se hace UNA tirada (y la
 * ventana se vacía, así que la siguiente tirada exige otros {@value #HITS_REQUIRED} golpes seguidos) con
 * probabilidad base {@value #BASE_CHANCE}, que se duplica por cada {@value #HITS_PER_DOUBLING} golpes acumulados.
 *
 * El contador acumulado se reinicia al cambiar el día del mundo (dayTime / 24000), no cada 24000 ticks desde el
 * primer golpe: así, dormir o avanzar el reloj con /time acorta el ciclo, como pide el diseño.
 *
 * Todo el estado (contador, día, ventana, bandera) vive en el NBT persistente del mob: sobrevive a recargas del
 * mundo. Una vez activada, la venganza dura hasta que el mob muere (no tiene condición de fin).
 */
public final class VengeanceManager {

    public static final int HITS_REQUIRED = 5;                  // golpes por ráfaga
    public static final long HIT_WINDOW_TICKS = 20L * 60L;       // ventana: 1 minuto
    public static final double BASE_CHANCE = 0.006D;             // probabilidad base (0,6 %)
    public static final int HITS_PER_DOUBLING = 10;              // golpes acumulados para duplicar
    public static final long TICKS_PER_DAY = 24000L;             // duración del día para el reinicio

    private static final String KEY_HITS_TOTAL = "PokeCraftOwnerHits";
    private static final String KEY_HITS_DAY = "PokeCraftOwnerHitsDay";
    private static final String KEY_HIT_WINDOW = "PokeCraftOwnerHitWindow";

    private VengeanceManager() {}

    /** Probabilidad de entrar en Venganza para un total acumulado de golpes (tope 100 %). */
    public static double chanceFor(int totalHits) {
        int doublings = Math.min(totalHits / HITS_PER_DOUBLING, 30);
        return Math.min(1.0D, BASE_CHANCE * Math.pow(2.0D, doublings));
    }

    /** Registra un golpe real del dueño sobre su aliado. Solo cuenta si el mob está en modo "quieto". */
    public static void recordOwnerHit(Mob mob, ServerPlayer owner) {
        if (!mob.isAlive() || MobOrderManager.isVengeance(mob)) return;
        if (MobOrderManager.getOrder(mob) != MobOrder.STAY) return;
        if (!(mob.level() instanceof ServerLevel level)) return;

        CompoundTag data = mob.getPersistentData();
        long now = level.getGameTime();
        long day = Math.floorDiv(level.getDayTime(), TICKS_PER_DAY);

        // Reinicio del contador acumulado cada día in-game (se comprueba de forma perezosa al recibir el golpe).
        if (!data.contains(KEY_HITS_DAY) || data.getLong(KEY_HITS_DAY) != day) {
            data.putInt(KEY_HITS_TOTAL, 0);
            data.putLong(KEY_HITS_DAY, day);
        }
        int total = data.getInt(KEY_HITS_TOTAL) + 1;
        data.putInt(KEY_HITS_TOTAL, total);

        // Ventana deslizante: se descartan golpes de hace 1 minuto o más (y marcas de tiempo futuras obsoletas).
        long[] window = LongStream.concat(
                Arrays.stream(data.getLongArray(KEY_HIT_WINDOW)).filter(t -> t <= now && now - t < HIT_WINDOW_TICKS),
                LongStream.of(now)).toArray();
        if (window.length < HITS_REQUIRED) {
            data.putLongArray(KEY_HIT_WINDOW, window);
            return;
        }

        data.remove(KEY_HIT_WINDOW); // ráfaga consumida: la siguiente tirada exige otra ráfaga completa
        if (mob.getRandom().nextDouble() < chanceFor(total)) {
            activate(mob, owner);
        }
    }

    /** Activa el Modo Venganza: cura al máximo y hace del dueño su único objetivo. */
    public static void activate(Mob mob, ServerPlayer owner) {
        CompoundTag data = mob.getPersistentData();
        data.putBoolean(MobOrderManager.KEY_VENGEANCE, true);
        data.remove(KEY_HIT_WINDOW);

        MobOrderManager.clearDirectTarget(mob);
        if (mob.isPassenger()) mob.stopRiding();
        if (mob.isLeashed()) mob.dropLeash(true, true);

        mob.setHealth(mob.getMaxHealth());
        // Instala las metas de venganza (sustituyen a las del modo guardado, que no se modifica).
        MobOrderManager.apply(mob, MobOrderManager.getOrder(mob));
        mob.setTarget(owner);

        if (mob.level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.ANGRY_VILLAGER, mob.getX(), mob.getY(0.8D), mob.getZ(), 12, 0.4D, 0.4D, 0.4D, 0.0D);
            level.playSound(null, mob.blockPosition(), SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 1.0F, 1.0F);
        }
        owner.displayClientMessage(Component.translatable("message.pokecraft.vengeance.activated", mob.getDisplayName()), true);
    }

    /**
     * Víctima del mob en venganza: su dueño, si está conectado, vivo y en la misma dimensión. Si no lo está, el mob
     * espera sin objetivo (no hay teletransporte ni persecución entre dimensiones) y retoma al volver el dueño.
     */
    @Nullable
    public static LivingEntity resolveTarget(Mob mob) {
        Player owner = MobOrderManager.getOwner(mob);
        return owner != null && owner.isAlive() && !owner.isSpectator() ? owner : null;
    }
}