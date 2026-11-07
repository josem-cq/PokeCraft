package com.jose.pokecraft.ai.goal;
 
import com.jose.pokecraft.ai.MobOrderManager;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.target.TargetGoal;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;
 
import java.util.EnumSet;
 
/**
 * Selección de objetivo del modo "Protegiendo" (genérica para cualquier {@link Mob}; los HurtByTargetGoal /
 * OwnerHurt*TargetGoal vanilla exigen PathfinderMob o TamableAnimal). Orden de prioridad:
 *  1. Quien haya herido al propio mob (venganza).
 *  2. Quien haya herido al dueño.
 *  3. A quien el dueño haya golpeado por última vez.
 * Nunca elige al dueño ni a sus aliados. Solo elige el objetivo; el ataque lo ejecuta la IA nativa.
 */
public class ProtectTargetGoal extends TargetGoal implements ManagedGoal {
    private LivingEntity candidate;
    private int lastSelfHurtTs;
    private int lastOwnerHurtByTs;
    private int lastOwnerHurtTs;
 
    public ProtectTargetGoal(Mob mob) {
        super(mob, false);
        this.setFlags(EnumSet.of(Goal.Flag.TARGET));
 
        // Se ignora el combate anterior al cambio de modo.
        this.lastSelfHurtTs = mob.getLastHurtByMobTimestamp();
        Player owner = MobOrderManager.getOwner(mob);
        if (owner != null) {
            this.lastOwnerHurtByTs = owner.getLastHurtByMobTimestamp();
            this.lastOwnerHurtTs = owner.getLastHurtMobTimestamp();
        }
    }
 
    @Override
    public boolean canUse() {
        Player owner = MobOrderManager.getOwner(mob);
        LivingEntity picked = pick(owner);
        if (picked == null) return false;
        this.candidate = picked;
        return true;
    }
 
    @Nullable
    private LivingEntity pick(@Nullable Player owner) {
        int ts = mob.getLastHurtByMobTimestamp();
        if (ts != lastSelfHurtTs) {
            lastSelfHurtTs = ts;
            LivingEntity attacker = mob.getLastHurtByMob();
            if (isValid(attacker)) return attacker;
        }
        if (owner == null) return null;
 
        ts = owner.getLastHurtByMobTimestamp();
        if (ts != lastOwnerHurtByTs) {
            lastOwnerHurtByTs = ts;
            LivingEntity attacker = owner.getLastHurtByMob();
            if (isValid(attacker)) return attacker;
        }
 
        ts = owner.getLastHurtMobTimestamp();
        if (ts != lastOwnerHurtTs) {
            lastOwnerHurtTs = ts;
            LivingEntity victim = owner.getLastHurtMob();
            if (isValid(victim)) return victim;
        }
        return null;
    }
 
    private boolean isValid(@Nullable LivingEntity entity) {
        return entity != null
                && entity.isAlive()
                && MobOrderManager.isAllowedTarget(mob, entity)
                && canAttack(entity, TargetingConditions.DEFAULT);
    }
 
    @Override
    public void start() {
        this.targetMob = candidate;
        super.start();
    }
}
 