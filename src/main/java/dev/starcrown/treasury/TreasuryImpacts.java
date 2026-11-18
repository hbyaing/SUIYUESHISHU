package dev.starcrown.treasury;

import dev.starcrown.StarcrownDoll;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import javax.annotation.Nullable;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class TreasuryImpacts {
    private static final ResourceKey<DamageType> TYPE = ResourceKey.create(Registries.DAMAGE_TYPE,
            new ResourceLocation(StarcrownDoll.ID, "treasury_impact"));
    private static final double RADIUS = 2.75;
    private static final float DAMAGE = 20.0F;
    private static final Map<ServerLevel, List<RecentSound>> RECENT_SOUNDS = new WeakHashMap<>();

    private record RecentSound(long tick, Vec3 point) {}

    private TreasuryImpacts() {}

    public static void hit(ServerPlayer owner, Vec3 point, @Nullable LivingEntity direct, long seed) {
        hit(owner, point, direct, seed, point.subtract(owner.getEyePosition()));
    }

    public static void hit(ServerPlayer owner, Vec3 point, @Nullable LivingEntity direct, long seed, Vec3 incoming) {
        ServerLevel level = owner.serverLevel();
        var type = level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(TYPE);
        DamageSource source = new DamageSource(type, null, owner, point);
        var nearby = level.getEntitiesOfClass(LivingEntity.class, new AABB(point, point).inflate(RADIUS),
                target -> target != direct && canHit(owner, target)
                        && target.getBoundingBox().distanceToSqr(point) <= RADIUS * RADIUS);
        if (direct != null && canHit(owner, direct)) direct.hurt(source, DAMAGE);
        for (var target : nearby) target.hurt(source, DAMAGE);

        Vec3 normal = incoming.lengthSqr() < 1.0e-8 ? new Vec3(0, 1, 0) : incoming.normalize().scale(-1);
        Vec3 visiblePoint = point.add(normal.scale(0.42));
        TreasuryVolleyNetwork.impact(level, visiblePoint, normal, seed);
        playImpactSound(level, point, seed);
    }

    private static boolean canHit(ServerPlayer owner, LivingEntity target) {
        return target.level() == owner.level() && target != owner && target.isAlive() && !target.isSpectator()
                && (!(target instanceof Player player) || !player.isCreative() && owner.canHarmPlayer(player));
    }

    private static void playImpactSound(ServerLevel level, Vec3 point, long seed) {
        long now = level.getGameTime();
        var sounds = RECENT_SOUNDS.computeIfAbsent(level, key -> new ArrayList<>());
        sounds.removeIf(sound -> now - sound.tick >= 5);
        for (var sound : sounds) {
            if (sound.point.distanceToSqr(point) < 20.0 * 20.0) return;
        }
        sounds.add(new RecentSound(now, point));
        float pitch = 1.1F + ((seed ^ (seed >>> 32)) & 255L) / 1024.0F;
        level.playSound(null, point.x, point.y, point.z, SoundEvents.GENERIC_EXPLODE,
                SoundSource.PLAYERS, 1.85F, pitch);
    }
}
