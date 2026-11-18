package dev.starcrown;

import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.Map;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;

/** Each invocation raises the sword's own glints from the terrain around its bearer. */
final class ExcaliburGroundSparks {
    private static final int COUNT = 48;
    private static final int LIFE = 38;
    private static final double RADIUS = 7;
    private static final double GOLDEN_ANGLE = Math.PI * (3 - Math.sqrt(5));
    private static final Map<Player, ExcaliburGroundSparks> ACTIVE = new IdentityHashMap<>();

    private final int[] cycles = new int[COUNT];
    private final Vec3[] feet = new Vec3[COUNT];
    private Vec3 lastCenter;
    private float lastAge;

    private ExcaliburGroundSparks() {
        Arrays.fill(cycles, -1);
    }

    static void clear() {
        ACTIVE.clear();
    }

    static ExcaliburVisuals.Frame draw(RenderLevelStageEvent event, ClientLevel level, float partial) {
        Vec3 eye = event.getCamera().getPosition();
        ACTIVE.keySet().removeIf(player -> player.isRemoved() || ExcaliburChargePose.chantHand(player) == null);
        ExcaliburVisuals.Frame frame = null;
        for (Player player : level.players()) {
            if (player.distanceToSqr(eye) > 80 * 80 || ExcaliburChargePose.chantHand(player) == null) continue;
            float age = ExcaliburChargePose.chargeAge(player, partial);
            if (age >= ExcaliburChant.PREPARE_TICKS + ExcaliburChant.DURATION) continue;
            if (frame == null) frame = new ExcaliburVisuals.Frame(event, false);
            ACTIVE.computeIfAbsent(player, unused -> new ExcaliburGroundSparks())
                    .drawField(frame, player, player.getPosition(partial), age);
        }
        return frame;
    }

    private void drawField(ExcaliburVisuals.Frame frame, Player player, Vec3 center, float age) {
        if (lastCenter != null && (age + 2 < lastAge || lastCenter.distanceToSqr(center) > RADIUS * RADIUS)) {
            Arrays.fill(cycles, -1);
        }
        lastCenter = center;
        lastAge = age;
        double rotation = (player.getUUID().getLeastSignificantBits() & 0xffff) * (Math.PI * 2 / 65536);
        double strength = Math.min(1, age / 4) * (.78 + .22 * Math.min(1, age / 90));
        for (int i = 0; i < COUNT; i++) {
            double time = age / LIFE + (i * 17 % COUNT) / (double) COUNT;
            int cycle = (int) time;
            double flight = time - cycle;
            double angle = rotation + i * GOLDEN_ANGLE + cycle * 1.71;
            if (cycles[i] != cycle) {
                // Equal-area annuli give the rim and the center the same number of glints per block.
                double radius = RADIUS * Math.sqrt((i + .5) / COUNT);
                feet[i] = ground(player, center.add(radius * Math.cos(angle), 0, radius * Math.sin(angle)));
                cycles[i] = cycle;
            }
            Vec3 foot = feet[i];
            if (foot == null) continue;
            double dx = foot.x - center.x, dz = foot.z - center.z;
            if (dx * dx + dz * dz > RADIUS * RADIUS) continue;
            double height = .06 + flight * (.64 + .25 * ((i + cycle) % 5));
            double size = .22 + .045 * ((i + cycle) % 4) + .10 * flight;
            frame.chargeSpark(foot.add(0, height, 0), size, angle + .6 * flight,
                    i % 3 == 0, Math.sin(Math.PI * flight) * strength);
        }
    }

    private static Vec3 ground(Player player, Vec3 position) {
        var hit = player.level().clip(new ClipContext(position.add(0, 1.05, 0), position.add(0, -12, 0),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        return hit.getType() == HitResult.Type.BLOCK && !hit.isInside() ? hit.getLocation() : null;
    }
}
