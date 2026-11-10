package com.jose.pokecraft.ai.goal;

import com.jose.pokecraft.ai.MobOrderManager;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.target.TargetGoal;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Ataque directo, parte de OBJETIVO. Es la única meta del targetSelector mientras dura el ataque directo, así que
 * cualquier objetivo previo desaparece y ninguna meta nativa puede cambiarlo. Fija el objetivo y lo mantiene sin
 * límite de distancia ni de visión (a diferencia del TargetGoal vanilla). Cuando la víctima muere o deja de estar
 * disponible, termina el ataque directo y el mob vuelve a su modo previo.
 *
 * Es parametrizable: el Modo Venganza la reutiliza con un resolver que devuelve al dueño y sin callback de fin
 * (la venganza no termina cuando el dueño no está disponible; simplemente espera a que vuelva).
 */
public class DirectTargetGoal extends TargetGoal implements ManagedGoal {
    private final Function<Mob, LivingEntity> resolver;
    @Nullable
    private final Consumer<Mob> onLost;

    public DirectTargetGoal(Mob mob) {
        this(mob, MobOrderManager::resolveDirectTarget, MobOrderManager::finishDirectAttack);
    }

    /**
     * @param resolver devuelve la víctima actual o null si no está disponible
     * @param onLost   se invoca cuando la víctima deja de estar disponible (puede ser null)
     */
    public DirectTargetGoal(Mob mob, Function<Mob, LivingEntity> resolver, @Nullable Consumer<Mob> onLost) {
        super(mob, false);
        this.resolver = resolver;
        this.onLost = onLost;
        this.setFlags(EnumSet.of(Goal.Flag.TARGET));
    }

    private void lost() {
        if (onLost != null) onLost.accept(mob);
    }

    @Override
    public boolean canUse() {
        LivingEntity victim = resolver.apply(mob);
        if (victim == null) {
            lost();
            return false;
        }
        this.targetMob = victim;
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        LivingEntity victim = resolver.apply(mob);
        if (victim == null) {
            lost();
            return false;
        }
        this.targetMob = victim;
        mob.setTarget(victim);
        return true;
    }

    @Override
    public void start() {
        // TargetGoal.start() no asigna el objetivo: hay que hacerlo explícitamente.
        mob.setTarget(targetMob);
        super.start();
    }
}
