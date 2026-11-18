package dev.starcrown;

import org.joml.Matrix3f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Model-space choreography. Distances are vanilla skin pixels, with negative Y up and negative Z forward. */
final class ExcaliburSwordStroke {
    private static final float VOICE_END = 115.4F, EX_ONSET = 155.4F, CALIBUR_ONSET = 198F;
    private static final float CUT_START = 218F, HIT = ExcaliburChant.DURATION, FOLLOW = HIT + 4F;

    record Pose(Vector3f shoulders, Vector3f upper, Quaternionf leadArm, Quaternionf supportArm, Quaternionf blade) {}

    static float weight(float tick) {
        return ease(tick, ExcaliburChant.LAST_LINE_TICK, VOICE_END)
                * (1F - ease(tick, FOLLOW, HIT + ExcaliburChant.Client.RECOVERY_TICKS));
    }

    static Pose solve(float tick, float weight, float headPitch, float headYaw, Vector3f lead, Vector3f support,
                      Vector3f upper, Vector3f lower) {
        float wind = ease(tick, EX_ONSET, CALIBUR_ONSET);
        float coil = ease(tick, CALIBUR_ONSET, CUT_START);
        float t = clamp((tick - CUT_START) / (HIT - CUT_START));
        float slash = t * t * (2F - t);
        float tailRemaining = clamp((FOLLOW - tick) / (FOLLOW - HIT));
        float tail = 1F - tailRemaining * tailRemaining;
        float shotPitch = Math.max(-1.30F, Math.min(1.30F, headPitch));
        float shotYaw = headYaw * slash;
        float bladeAngle = -.035F - .46F * wind - .60F * coil + (2.666F + shotPitch) * slash + .28F * tail;
        float reachAngle = .88F + .08F * wind + .08F * coil - (1.02F + .53F * shotPitch) * slash - .17F * tail;
        var shoulders = new Vector3f(0F, -3.9F - .50F * coil + 4.2F * slash,
                -1.8F - .60F * coil + 1.65F * slash).mul(weight);

        var axis = upper.sub(lower, new Vector3f());
        float spacing = axis.length();
        axis.div(spacing);
        var toVertical = new Quaternionf().rotationTo(axis, new Vector3f(0, -1, 0));
        // Ease the authored cut angles separately so that a steep downward swing keeps its direction past 180°.
        var blade = new Quaternionf().rotationY(shotYaw * weight).rotateX(bladeAngle * weight)
                .mul(new Quaternionf().slerp(toVertical, weight));
        blade.transform(axis);

        // With the upper grip at P, the lower grip is P - axis * spacing. Their distance from each
        // shoulder stays fixed, so P lies on the circle shared by these two reach spheres.
        var upperReach = upper.sub(lead, new Vector3f());
        var lowerReach = lower.sub(support, new Vector3f());
        var span = support.sub(lead, new Vector3f()).fma(spacing, axis);
        float distance = span.length();
        span.div(distance);
        float along = (upperReach.lengthSquared() - lowerReach.lengthSquared() + distance * distance) / (2F * distance);
        float radius = (float)Math.sqrt(Math.max(0F, upperReach.lengthSquared() - along * along));
        // The wrists can point across the body further than the shoulders can comfortably reach.
        var aim = new Vector3f(0F, -(float)Math.sin(reachAngle), -(float)Math.cos(reachAngle))
                .rotateY(Math.max(-.75F, Math.min(.75F, shotYaw)));
        var outward = new Vector3f(upperReach).normalize().lerp(aim, weight);
        outward.fma(-outward.dot(span), span).normalize();
        var newUpper = new Vector3f(lead).fma(along, span).fma(radius, outward);
        var newLower = new Vector3f(newUpper).fma(-spacing, axis);
        var leadArm = new Quaternionf().rotationTo(upperReach, newUpper.sub(lead, new Vector3f()));
        var supportArm = new Quaternionf().rotationTo(lowerReach, newLower.sub(support));
        return new Pose(shoulders, newUpper.add(shoulders), leadArm, supportArm, blade);
    }

    static float ease(float tick, float start, float end) {
        float t = clamp((tick - start) / (end - start));
        return t * t * (3F - 2F * t);
    }

    static Vector3f modelAngles(Quaternionf rotation) {
        var m = new Matrix3f().rotation(rotation);
        double horizontal = Math.hypot(m.m00, m.m01);
        float y = (float)Math.atan2(-m.m02, horizontal);
        if (horizontal < .00001) return new Vector3f((float)Math.atan2(-m.m21, m.m11), y, 0F);
        return new Vector3f((float)Math.atan2(m.m12, m.m22), y, (float)Math.atan2(m.m01, m.m00));
    }

    private static float clamp(float t) { return Math.max(0F, Math.min(1F, t)); }
}
