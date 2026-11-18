package dev.eavfx;

import net.minecraft.world.phys.Vec3;

/** A world-space cast with a unit direction and an interpolated age in game ticks. */
public record EaFrame(long id, int ownerId, Vec3 origin, Vec3 direction,
                      float charge, float releaseAge, float strength,
                      float maxLength, long seed) {
    public boolean released() { return releaseAge >= 0; }
    public float blastAge() { return Math.max(0, releaseAge); }

    /** Both the damage query and the rendered cylinder grow from the first release tick. */
    public static float lengthAt(float releaseAge, float maxLength) {
        if (releaseAge < 0 || releaseAge >= EaAudioConfig.RELEASE_SOUND_TICKS) return 0;
        return maxLength * Math.min(1, (releaseAge + 1) / 24f);
    }

    public float length() { return lengthAt(releaseAge, maxLength); }

    public float fade() {
        if (!released()) return strength;
        float remaining = EaAudioConfig.RELEASE_SOUND_TICKS - releaseAge;
        return strength * Math.max(0, Math.min(1, remaining / 12f));
    }
}
