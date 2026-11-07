package com.jose.pokecraft.ai.goal;
 
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
 
import java.util.function.Predicate;
 
/** Selección de objetivo del modo Ataque: el vanilla NearestAttackableTargetGoal, marcado como gestionado. */
public class AttackTargetGoal extends NearestAttackableTargetGoal<LivingEntity> implements ManagedGoal {
    public AttackTargetGoal(Mob mob, Predicate<LivingEntity> predicate) {
        super(mob, LivingEntity.class, 10, true, false, predicate);
    }
}
 