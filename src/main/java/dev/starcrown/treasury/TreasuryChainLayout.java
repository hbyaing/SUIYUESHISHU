package dev.starcrown.treasury;

import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;
import net.minecraft.world.phys.Vec3;

public final class TreasuryChainLayout {
    private static final double[] HEIGHT_FRACTIONS = {0.06, 0.76, 0.35, 1.0, 0.18, 0.59};

    private TreasuryChainLayout() {}

    /** World-space offsets from the target's bounding-box center to the chain gates. */
    public static List<Vec3> anchors(long seed, float width, float height) {
        double bodyWidth = Float.isFinite(width) && width > 0 ? width : 0.6;
        double bodyHeight = Float.isFinite(height) && height > 0 ? height : 1.8;
        var random = new SplittableRandom(seed);
        int count = 5 + random.nextInt(2);
        double heading = random.nextDouble(2 * Math.PI);
        double step = 2 * Math.PI / count;
        // Start outside even the diagonal of a square hitbox and leave a visible span of chain.
        double radius = bodyWidth * Math.sqrt(0.5) + Math.max(2.9, 1.8 + bodyHeight * 0.35);
        double low = Math.max(0.95, bodyHeight * 0.20);
        double high = Math.max(low + 1.6, bodyHeight * 1.18 + 0.25);
        var anchors = new ArrayList<Vec3>(count);
        for (int i = 0; i < count; i++) {
            double angle = heading + step * (i + random.nextDouble(-0.06, 0.06));
            double reach = radius + random.nextDouble(0, 0.45);
            double y = low + (high - low) * HEIGHT_FRACTIONS[i] - bodyHeight * 0.5;
            anchors.add(new Vec3(Math.cos(angle) * reach, y, Math.sin(angle) * reach));
        }
        return List.copyOf(anchors);
    }
}
