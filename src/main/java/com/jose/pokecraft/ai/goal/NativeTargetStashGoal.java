package com.jose.pokecraft.ai.goal;
 
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
 
import java.util.List;
 
/**
 * Guarda las metas de objetivo NATIVAS del mob (antes de que otros modos vacíen el targetSelector) para poder
 * restaurarlas en el modo Libre. Nunca se ejecuta (canUse = false) y NO es {@link ManagedGoal}, así que
 * sobrevive a los cambios de modo. Vive dentro del propio mob, por lo que no hay referencias estáticas que filtren memoria.
 */
public class NativeTargetStashGoal extends Goal {
    private final List<WrappedGoal> stashed;
 
    public NativeTargetStashGoal(List<WrappedGoal> stashed) {
        this.stashed = stashed;
    }
 
    public List<WrappedGoal> getStashed() {
        return stashed;
    }
 
    @Override
    public boolean canUse() {
        return false;
    }
}
 