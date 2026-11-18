package dev.eavfx;

import dev.starcrown.StarcrownDoll;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

final class EaBeamDamage {
    private static final ResourceKey<DamageType> VOID = ResourceKey.create(Registries.DAMAGE_TYPE,
            new ResourceLocation(StarcrownDoll.ID, "ea_void"));
    private static final float DAMAGE = 300f;
    private static final double QUERY_LENGTH = 32.0;

    private EaBeamDamage() {}

    static void apply(ServerPlayer owner, Vec3 origin, Vec3 axis, float length) {
        if (length <= 0) return;
        var level = owner.serverLevel();
        var type = level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(VOID);
        var source = new DamageSource(type, owner, owner, origin);
        double radius = EaServer.BEAM_RADIUS * Math.min(1.0, length / 8.0);
        // The centers of the rounded end caps stay one radius inside the visible beam.
        Vec3 start = origin.add(axis.scale(radius));
        double axisLength = Math.max(0, length - 2 * radius);
        Set<LivingEntity> checked = new HashSet<>();
        Vec3 from = origin;
        for (double distance = 0; distance < length; distance += QUERY_LENGTH) {
            Vec3 to = origin.add(axis.scale(Math.min(length, distance + QUERY_LENGTH)));
            AABB query = new AABB(from, to).inflate(radius);
            // The entity index only visits loaded sections. Narrow queries also bound diagonal casts.
            var candidates = level.getEntitiesOfClass(LivingEntity.class, query,
                    entity -> entity != owner && entity.isAlive() && !entity.isSpectator());
            for (LivingEntity target : candidates) {
                if (!owner.isAlive() || owner.level() != level) return;
                if (checked.add(target) && intersects(start, axis, axisLength, radius, target.getBoundingBox())) {
                    target.hurt(source, DAMAGE);
                }
            }
            from = to;
        }
    }

    /** The squared distance from a point on a line to an AABB is convex along the line. */
    private static boolean intersects(Vec3 start, Vec3 axis, double length, double radius, AABB box) {
        double low = 0;
        double high = length;
        double radiusSquared = radius * radius;
        for (int iteration = 0; iteration < 36; iteration++) {
            double t = (low + high) * .5;
            double x = start.x + t * axis.x;
            double y = start.y + t * axis.y;
            double z = start.z + t * axis.z;
            double dx = x - Mth.clamp(x, box.minX, box.maxX);
            double dy = y - Mth.clamp(y, box.minY, box.maxY);
            double dz = z - Mth.clamp(z, box.minZ, box.maxZ);
            if (dx * dx + dy * dy + dz * dz <= radiusSquared + 1e-7) return true;
            double slope = axis.x * dx + axis.y * dy + axis.z * dz;
            if (slope == 0) return false;
            if (slope > 0) high = t;
            else low = t;
        }
        return false;
    }
}
