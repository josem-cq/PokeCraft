package com.jose.pokecraft.ai.goal;
 
import com.jose.pokecraft.ai.MobOrderManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.animal.WaterAnimal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
 
import java.util.EnumSet;
 
/**
 * Equivalente al FollowOwnerGoal vanilla, pero para CUALQUIER {@link Mob} (el vanilla exige TamableAnimal).
 * Solo se ocupa del desplazamiento; no toca el objetivo de ataque.
 * Si el mob tiene un objetivo vivo, cede el control para que las metas nativas (melee, arco...) actúen.
 */
public class FollowOwnerMobGoal extends Goal implements ManagedGoal {
    private final Mob mob;
    private final double speed;
    private final double startDistSqr;
    private final double stopDistSqr;
    private final double teleportDistSqr;
 
    private Player owner;
    private int timeToRecalcPath;
 
    public FollowOwnerMobGoal(Mob mob, double speed, float startDistance, float stopDistance, float teleportDistance) {
        this.mob = mob;
        this.speed = speed;
        this.startDistSqr = startDistance * startDistance;
        this.stopDistSqr = stopDistance * stopDistance;
        this.teleportDistSqr = teleportDistance * teleportDistance;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }
    private boolean hasLiveTarget() {
        LivingEntity target = mob.getTarget();
        return target != null && target.isAlive();
    }
 
    @Override
    public boolean canUse() {
        Player candidate = MobOrderManager.getOwner(mob);
        if (candidate == null || candidate.isSpectator() || !candidate.isAlive()) return false;
        if (hasLiveTarget() || mob.isLeashed() || mob.isPassenger()) return false;
        if (mob.distanceToSqr(candidate) < startDistSqr) return false;
        this.owner = candidate;
        return true;
    }
 
    @Override
    public boolean canContinueToUse() {
        if (owner == null || !owner.isAlive() || hasLiveTarget() || mob.isLeashed() || mob.isPassenger()) return false;
        return mob.distanceToSqr(owner) > stopDistSqr;
    }
 
    @Override
    public void start() {
        timeToRecalcPath = 0;
    }
 
    @Override
    public void stop() {
        owner = null;
        mob.getNavigation().stop();
    }
 
    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }
 
    @Override
    public void tick() {
        if (owner == null) return;
        mob.getLookControl().setLookAt(owner, 10.0F, mob.getMaxHeadXRot());
        if (--timeToRecalcPath > 0) return;
        timeToRecalcPath = adjustedTickDelay(10);
 
        if (mob.distanceToSqr(owner) >= teleportDistSqr) {
            teleportToOwner();
        } else {
            mob.getNavigation().moveTo(owner, speed);
        }
    }
 
    private void teleportToOwner() {
        BlockPos base = owner.blockPosition();
        for (int i = 0; i < 10; i++) {
            int dx = mob.getRandom().nextIntBetweenInclusive(-3, 3);
            int dy = mob.getRandom().nextIntBetweenInclusive(-1, 1);
            int dz = mob.getRandom().nextIntBetweenInclusive(-3, 3);
            if (Math.abs(dx) < 2 && Math.abs(dz) < 2) continue;
            if (tryTeleportTo(base.offset(dx, dy, dz))) return;
        }
    }
 
    private boolean tryTeleportTo(BlockPos pos) {
        var level = mob.level();
        boolean aquatic = mob instanceof WaterAnimal;
        boolean flying = mob.isNoGravity() || mob.getType().is(net.minecraft.tags.EntityTypeTags.FALL_DAMAGE_IMMUNE);
 
        if (aquatic) {
            if (!level.getFluidState(pos).is(FluidTags.WATER)) return false;
        } else if (!flying) {
            BlockPos below = pos.below();
            if (!level.getBlockState(below).isFaceSturdy(level, below, Direction.UP)) return false;
        }
 
        AABB moved = mob.getBoundingBox().move(
                pos.getX() + 0.5D - mob.getX(),
                pos.getY() - mob.getY(),
                pos.getZ() + 0.5D - mob.getZ());
        if (!level.noCollision(mob, moved)) return false;
 
        mob.moveTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, mob.getYRot(), mob.getXRot());
        mob.getNavigation().stop();
        return true;
    }
}