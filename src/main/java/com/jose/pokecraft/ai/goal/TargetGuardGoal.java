package com.jose.pokecraft.ai.goal;
 
import com.jose.pokecraft.ai.MobOrder;
import com.jose.pokecraft.ai.MobOrderManager;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
 
/**
 * Red de seguridad sin banderas: se ejecuta cada tick junto a la IA nativa y elimina cualquier objetivo que
 * el modo actual no permita (cualquiera en FOLLOW/STAY; el dueño y sus aliados en PROTECT/ATTACK).
 * Cubre casos que escapan al targetSelector: ira persistente de NeutralMob, mobs con Brain, mods que llaman a setTarget...
 */
public class TargetGuardGoal extends Goal implements ManagedGoal {
    private final Mob mob;
    private final MobOrder order;
 
    public TargetGuardGoal(Mob mob, MobOrder order) {
        this.mob = mob;
        this.order = order;
    }
 
    @Override
    public boolean canUse() {
        return true;
    }
 
    @Override
    public boolean canContinueToUse() {
        return true;
    }
 
    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }
 
    @Override
    public void tick() {
        LivingEntity target = mob.getTarget();
        if (target != null && !MobOrderManager.isTargetAllowed(mob, target, order)) {
            MobOrderManager.dropTarget(mob);
        }
    }
}