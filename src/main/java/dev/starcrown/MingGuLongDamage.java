package dev.starcrown;

import net.minecraft.core.registries.Registries;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Tagged damage keeps combat attribution and Forge damage events intact. */
public final class MingGuLongDamage {
    public static final ResourceKey<DamageType> TRUE_DAMAGE = key("eternal_dragon_true");
    public static final ResourceKey<DamageType> VOID_DAMAGE = key("eternal_dragon_void");
    public static final float IMPACT_DAMAGE = 200.0F;
    public static final double IMPACT_HALF_SIZE = 1.5D;
    public static final float LIFE_STEAL_RATIO = 0.40F;

    private static ResourceKey<DamageType> key(String name) {
        return ResourceKey.create(Registries.DAMAGE_TYPE, new ResourceLocation(StarcrownDoll.ID, name));
    }

    public static DamageSource source(MingGuLongEntity owner, boolean voidImpact, Vec3 position) {
        var holder = owner.level().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE)
            .getHolderOrThrow(voidImpact ? VOID_DAMAGE : TRUE_DAMAGE);
        return new DamageSource(holder, owner, owner, position);
    }

    public static boolean hurt(MingGuLongEntity owner, LivingEntity target, float amount) {
        return hurt(owner, target, source(owner, false, owner.position()), amount);
    }

    private static boolean hurt(MingGuLongEntity owner, LivingEntity target, DamageSource source, float amount) {
        if (owner.level().isClientSide || target instanceof MingGuLongEntity || !target.isAlive()) return false;
        float healthBefore = target.getHealth();
        boolean applied = target.hurt(source, amount);
        // Heal from health actually removed, after events, absorption and lethal
        // overkill. Canceled/immune hits and absorption-only hits grant nothing.
        float healthLost = Math.max(0.0F, healthBefore - Math.max(0.0F, target.getHealth()));
        if (applied && healthLost > 0.0F && owner.isAlive()) owner.heal(healthLost * LIFE_STEAL_RATIO);
        return applied;
    }

    public static void impact(ServerLevel level, MingGuLongEntity owner, Vec3 position) {
        // Fixed damage inside the 3x3x3 impact volume: no distance falloff and
        // no vanilla Explosion damage pass, block destruction or fire.
        var source = source(owner, true, position);
        for (var target : level.getEntitiesOfClass(LivingEntity.class,
                new AABB(position, position).inflate(IMPACT_HALF_SIZE),
                entity -> entity.isAlive() && !(entity instanceof MingGuLongEntity))) {
            hurt(owner, target, source, IMPACT_DAMAGE);
        }
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, position.x, position.y, position.z, 1, 0, 0, 0, 0);
        level.playSound(null, position.x, position.y, position.z, SoundEvents.GENERIC_EXPLODE,
            SoundSource.HOSTILE, 2.0F, 0.75F);
    }

    private MingGuLongDamage() {}
}
