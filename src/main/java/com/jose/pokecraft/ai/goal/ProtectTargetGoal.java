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
 * OwnerHurt*TargetGoal vanilla exigen PathfinderMob o TamableAnimal). Son DOS instancias con prioridades distintas:
 *  - SELF  (prioridad 1): quien haya herido al propio mob, sea otro mob o un jugador distinto del dueño.
 *  - OWNER (prioridad 2): quien haya herido al dueño y, si no, lo último que golpeó el dueño.
 * SELF tiene mejor prioridad, así que puede interrumpir a OWNER si el mob es atacado mientras ayudaba al dueño.
 * Nunca elige al dueño ni a sus aliados. Solo elige el objetivo; el ataque lo ejecuta la IA nativa.
 */
public class ProtectTargetGoal extends TargetGoal implements ManagedGoal {
    public enum Role { SELF, OWNER }
 
    private final Role role;
    private LivingEntity candidate;
    private int lastSelfHurtTs;
    private int lastOwnerHurtByTs;
    private int lastOwnerHurtTs;
 
    public ProtectTargetGoal(Mob mob, Role role) {
        super(mob, false);
        this.role = role;
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
        LivingEntity picked = role == Role.SELF ? pickSelf() : pickOwner(MobOrderManager.getOwner(mob));
        if (picked == null) return false;
        this.candidate = picked;
        return true;
    }
 
    @Nullable
    private LivingEntity pickSelf() {
        int ts = mob.getLastHurtByMobTimestamp();
        if (ts == lastSelfHurtTs) return null;
        lastSelfHurtTs = ts;
        LivingEntity attacker = mob.getLastHurtByMob();
        return isValid(attacker) ? attacker : null;
    }
 
    @Nullable
    private LivingEntity pickOwner(@Nullable Player owner) {
        if (owner == null) return null;
 
        int ts = owner.getLastHurtByMobTimestamp();
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