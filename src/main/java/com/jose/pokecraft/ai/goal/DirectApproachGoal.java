package com.jose.pokecraft.ai.goal;

import com.jose.pokecraft.ai.MobOrderManager;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.function.Function;

/**
 * Ataque directo, parte de MOVIMIENTO. Acerca al mob a la víctima marcada con el palo usando solo su navegación
 * natural (nunca teletransporte), ignorando su rango de detección. Cuando la víctima está lo bastante cerca como
 * para que las metas nativas funcionen (melé / disparo), se desactiva y deja que la IA nativa persiga y ataque con
 * sus animaciones y sonidos de siempre. Si la víctima vuelve a alejarse, retoma el control (tiene mejor prioridad
 * que las metas de ataque nativas).
 *
 * La víctima se obtiene mediante un "resolver": por defecto la marcada con el palo; el Modo Venganza reutiliza esta
 * misma meta pasando un resolver que devuelve al dueño.
 *
 * Optimización: la ruta solo se recalcula cada 10 ticks y solo si la víctima se movió, la ruta terminó o pasaron
 * 40 ticks, en lugar de pedir un camino nuevo cada tick.
 */
public class DirectApproachGoal extends Goal implements ManagedGoal {
    private static final double SPEED = 1.2D;
    /** Distancia a partir de la cual toman el control las metas nativas. */
    private static final double MELEE_HANDOFF = 5.0D;
    private static final double RANGED_HANDOFF = 14.0D;
    private static final int RECALC_TICKS = 10;
    private static final int FORCE_RECALC_TICKS = 40;
    private static final double MOVED_SQR = 2.25D;

    private final Mob mob;
    private final Function<Mob, LivingEntity> resolver;
    private LivingEntity victim;
    private int recalcDelay;
    private int sinceLastPath;
    private Vec3 lastVictimPos = Vec3.ZERO;

    public DirectApproachGoal(Mob mob) {
        this(mob, MobOrderManager::resolveDirectTarget);
    }

    public DirectApproachGoal(Mob mob, Function<Mob, LivingEntity> resolver) {
        this.mob = mob;
        this.resolver = resolver;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    @Nullable
    private LivingEntity resolve() {
        return resolver.apply(mob);
    }

    /** ¿Está la víctima lo bastante cerca como para que actúe la IA nativa? */
    private boolean nativeAiCanTakeOver(LivingEntity target, double margin) {
        double distSqr = mob.distanceToSqr(target);
        if (mob instanceof RangedAttackMob) {
            double r = RANGED_HANDOFF - margin;
            return distSqr <= r * r && mob.getSensing().hasLineOfSight(target);
        }
        double r = MELEE_HANDOFF - margin;
        return distSqr <= r * r;
    }

    @Override
    public boolean canUse() {
        if (mob.isPassenger() || mob.isLeashed()) return false;
        LivingEntity target = resolve();
        if (target == null) return false;
        if (nativeAiCanTakeOver(target, 0.0D)) return false;
        this.victim = target;
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        LivingEntity target = resolve();
        if (target == null) return false;
        this.victim = target;
        // Histéresis de 1 bloque para no alternar de control en el borde.
        return !nativeAiCanTakeOver(target, 1.0D);
    }

    @Override
    public void start() {
        recalcDelay = 0;
        sinceLastPath = FORCE_RECALC_TICKS;
        lastVictimPos = Vec3.ZERO;
    }

    @Override
    public void stop() {
        victim = null;
        mob.getNavigation().stop();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        if (victim == null) return;
        mob.getLookControl().setLookAt(victim, 30.0F, 30.0F);

        sinceLastPath++;
        if (--recalcDelay > 0) return;
        recalcDelay = adjustedTickDelay(RECALC_TICKS);

        boolean moved = victim.position().distanceToSqr(lastVictimPos) > MOVED_SQR;
        if (!(moved || mob.getNavigation().isDone() || sinceLastPath >= FORCE_RECALC_TICKS)) return;

        sinceLastPath = 0;
        lastVictimPos = victim.position();
        // moveTo(Entity) traza una ruta parcial hacia el objetivo aunque esté fuera del radio de la navegación;
        // al recalcular periódicamente se va acercando. Si no hay ruta posible, se empuja en línea recta.
        if (!mob.getNavigation().moveTo(victim, SPEED)) {
            mob.getMoveControl().setWantedPosition(victim.getX(), victim.getY(), victim.getZ(), SPEED);
        }
    }
}