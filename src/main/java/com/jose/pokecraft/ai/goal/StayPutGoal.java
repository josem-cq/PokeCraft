package com.jose.pokecraft.ai.goal;
 
import com.jose.pokecraft.ai.MobOrderManager;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
 
import java.util.EnumSet;
 
/**
 * Modo "Quieto": ocupa la bandera MOVE con la prioridad más alta (0), de modo que ninguna meta nativa de
 * desplazamiento (paseo, huida, persecución...) puede arrancar, y mantiene parada la navegación.
 * No usa JUMP, así que el mob puede seguir flotando en el agua sin ahogarse.
 */
public class StayPutGoal extends Goal implements ManagedGoal {
    private final Mob mob;
 
    public StayPutGoal(Mob mob) {
        this.mob = mob;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE));
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
    public boolean isInterruptable() {
        return false;
    }
 
    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }
 
    @Override
    public void start() {
        MobOrderManager.haltMovement(mob);
    }
 
    @Override
    public void tick() {
        MobOrderManager.haltMovement(mob);
    }
}