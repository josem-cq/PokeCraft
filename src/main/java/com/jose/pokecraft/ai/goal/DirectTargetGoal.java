package com.jose.pokecraft.ai.goal;
 
import com.jose.pokecraft.ai.MobOrderManager;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.target.TargetGoal;
 
import java.util.EnumSet;
 
/**
 * Ataque directo, parte de OBJETIVO. Es la única meta del targetSelector mientras dura el ataque directo, así que
 * cualquier objetivo previo desaparece y ninguna meta nativa puede cambiarlo. Fija el objetivo y lo mantiene sin
 * límite de distancia ni de visión (a diferencia del TargetGoal vanilla). Cuando la víctima muere o deja de estar
 * disponible, termina el ataque directo y el mob vuelve a su modo previo.
 */
public class DirectTargetGoal extends TargetGoal implements ManagedGoal {
    public DirectTargetGoal(Mob mob) {
        super(mob, false);
        this.setFlags(EnumSet.of(Goal.Flag.TARGET));
    }
 
    @Override
    public boolean canUse() {
        LivingEntity victim = MobOrderManager.resolveDirectTarget(mob);
        if (victim == null) {
            MobOrderManager.finishDirectAttack(mob);
            return false;
        }
        this.targetMob = victim;
        return true;
    }
 
    @Override
    public boolean canContinueToUse() {
        LivingEntity victim = MobOrderManager.resolveDirectTarget(mob);
        if (victim == null) {
            MobOrderManager.finishDirectAttack(mob);
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