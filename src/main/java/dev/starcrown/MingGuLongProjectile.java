package dev.starcrown;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.entity.projectile.ProjectileUtil;

import java.util.List;

/** Frozen-target projectile for 荒芜之噬. It never retargets after release. */
public final class MingGuLongProjectile extends Entity {
    private static final EntityDataAccessor<Integer> AGE = SynchedEntityData.defineId(MingGuLongProjectile.class, EntityDataSerializers.INT);
    private Vec3 impactPoint;
    private final int sequenceId;
    private final int ownerId;
    private boolean impacted;

    public MingGuLongProjectile(EntityType<? extends MingGuLongProjectile> type, Level level) {
        super(type, level);
        this.impactPoint = Vec3.ZERO;
        this.sequenceId = 0;
        this.ownerId = -1;
        noPhysics = true;
    }

    public MingGuLongProjectile(EntityType<? extends MingGuLongProjectile> type, Level level, MingGuLongEntity owner, Vec3 impactPoint) {
        super(type, level);
        this.impactPoint = impactPoint;
        this.sequenceId = owner.getId() ^ (int) level.getGameTime();
        this.ownerId = owner.getId();
        Vec3 origin = owner.getHuangwuChargePosition();
        setPos(origin.x, origin.y, origin.z);
        setYRot(owner.yBodyRot);
        Vec3 direction = impactPoint.subtract(position()).normalize();
        setDeltaMovement(direction.scale(1.15D));
        noPhysics = true;
    }

    @Override protected void defineSynchedData() { entityData.define(AGE, 0); }

    @Override public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel server) || impacted) return;
        int age = entityData.get(AGE) + 1;
        entityData.set(AGE, age);
        Vec3 start = position();
        Vec3 movement = getDeltaMovement();
        Vec3 next = start.add(movement);
        BlockHitResult blockHit = level().clip(new ClipContext(start, next, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        Vec3 collisionEnd = blockHit.getType() == HitResult.Type.MISS ? next : blockHit.getLocation();
        EntityHitResult entityHit = ProjectileUtil.getEntityHitResult(this, start, collisionEnd,
            getBoundingBox().expandTowards(movement).inflate(0.3D),
            target -> target instanceof LivingEntity && !(target instanceof MingGuLongEntity) && target.isAlive(), 0.0F);
        if (entityHit != null) {
            impact(server, entityHit.getLocation());
            return;
        }
        if (blockHit.getType() != HitResult.Type.MISS) {
            impact(server, blockHit.getLocation());
            return;
        }

        double segmentLength = movement.length();
        Vec3 toAimPoint = impactPoint.subtract(start);
        double along = toAimPoint.dot(movement.normalize());
        if (along >= 0.0D && along <= segmentLength) {
            Vec3 closest = start.add(movement.normalize().scale(along));
            if (closest.distanceToSqr(impactPoint) <= 0.3D * 0.3D) {
                impact(server, impactPoint);
                return;
            }
        }

        setPos(next.x, next.y, next.z);
        if (age >= 40) impact(server, next);
    }

    private void impact(ServerLevel server, Vec3 actualImpactPoint) {
        impacted = true;
        impactPoint = actualImpactPoint;
        Entity owner = server.getEntity(ownerId);
        if (owner instanceof MingGuLongEntity dragon) {
            dragon.onHuangwuImpact(impactPoint, sequenceId, getYRot());
        }
        discard();
    }

    @Override protected void addAdditionalSaveData(CompoundTag tag) { tag.putInt("Age", entityData.get(AGE)); }
    public int getVisualAge() { return entityData.get(AGE); }
    @Override protected void readAdditionalSaveData(CompoundTag tag) { entityData.set(AGE, tag.getInt("Age")); }
}
