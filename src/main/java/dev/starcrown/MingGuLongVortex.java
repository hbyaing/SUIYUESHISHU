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
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.util.Mth;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Server-side staged vortex for the frozen impact point. */
public final class MingGuLongVortex extends Entity {
    private static final EntityDataAccessor<Integer> AGE = SynchedEntityData.defineId(MingGuLongVortex.class, EntityDataSerializers.INT);
    private final Vec3 impact;
    private final int ownerId;
    private final int sequenceId;
    private final Set<UUID> secondaryHit = new HashSet<>();
    private static final int DURATION_TICKS = 200;
    private static final double PULL_HALF_EXTENT = 4.5D;
    private static final double CONTACT_DAMAGE_RADIUS = 1.5D;
    private static final float CONTACT_DAMAGE_PER_TICK = 20.0F;

    public MingGuLongVortex(EntityType<? extends MingGuLongVortex> type, Level level) {
        super(type, level);
        this.impact = Vec3.ZERO;
        this.ownerId = -1;
        this.sequenceId = 0;
        noPhysics = true;
    }

    public MingGuLongVortex(EntityType<? extends MingGuLongVortex> type, ServerLevel level, MingGuLongEntity owner, Vec3 impact, int sequenceId) {
        super(type, level);
        this.impact = impact;
        this.ownerId = owner.getId();
        this.sequenceId = sequenceId;
        setPos(impact.x, impact.y, impact.z);
        noPhysics = true;
    }

    @Override protected void defineSynchedData() { entityData.define(AGE, 0); }

    @Override public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel server)) return;
        int age = entityData.get(AGE) + 1;
        entityData.set(AGE, age);
        Entity owner = server.getEntity(ownerId);
        if (!(owner instanceof MingGuLongEntity dragon)) { discard(); return; }
        // The black hole is a continuous 10-second field, with a 9x9x9 pull box.
        if (age <= DURATION_TICKS) applyVortexTick(server, dragon);
        // Preserve ten seconds of attraction, then play the authored collapse,
        // secondary flash and fragments. GPU visuals replace placeholder smoke.
        if (age == DURATION_TICKS + 13) applySecondaryBurst(server, dragon);
        if (age >= DURATION_TICKS + 39) discard();
    }

    private void applyVortexTick(ServerLevel server, MingGuLongEntity dragon) {
        AABB field = new AABB(
            impact.x - PULL_HALF_EXTENT, impact.y - PULL_HALF_EXTENT, impact.z - PULL_HALF_EXTENT,
            impact.x + PULL_HALF_EXTENT, impact.y + PULL_HALF_EXTENT, impact.z + PULL_HALF_EXTENT);
        for (LivingEntity target : server.getEntitiesOfClass(LivingEntity.class, field,
            entity -> !(entity instanceof MingGuLongEntity) && entity.isAlive())) {
            Vec3 pull = impact.subtract(target.position());
            Vec3 horizontal = new Vec3(pull.x, 0, pull.z);
            if (horizontal.lengthSqr() > 0.001D) {
                Vec3 motion = horizontal.normalize().scale(Math.min(1.5D / 20.0D, horizontal.length() * 0.08D));
                target.push(motion.x, Mth.clamp(pull.y * 0.05D, -0.6D / 20.0D, 0.6D / 20.0D), motion.z);
            }
            // Contact with the generation point is lethal pressure: 20 HP every server tick.
            if (target.position().distanceToSqr(impact) <= CONTACT_DAMAGE_RADIUS * CONTACT_DAMAGE_RADIUS) {
                MingGuLongDamage.hurt(dragon, target, CONTACT_DAMAGE_PER_TICK);
            }
        }
    }

    private void applySecondaryBurst(ServerLevel server, MingGuLongEntity dragon) {
        for (LivingEntity target : server.getEntitiesOfClass(LivingEntity.class, new AABB(impact, impact).inflate(5.5D), entity -> !(entity instanceof MingGuLongEntity) && entity.isAlive())) {
            if (secondaryHit.add(target.getUUID())) MingGuLongDamage.hurt(dragon, target, dragon.skillBaseDamage() * 0.85F);
        }
    }

    public int getVisualAge() { return entityData.get(AGE); }

    @Override protected void addAdditionalSaveData(CompoundTag tag) { tag.putInt("Age", entityData.get(AGE)); }
    @Override protected void readAdditionalSaveData(CompoundTag tag) { entityData.set(AGE, tag.getInt("Age")); }
}
