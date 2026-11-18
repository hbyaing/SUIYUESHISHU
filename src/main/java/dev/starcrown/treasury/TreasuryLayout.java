package dev.starcrown.treasury;

import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;

/** Gate positions are relative to the caster's feet: right, up and horizontal forward. */
public final class TreasuryLayout {
    public record Gate(float x, float y, float z, float radius) {}

    private static final float RIM_GAP = 0.30f;

    private TreasuryLayout() {}

    public static List<Gate> gates(int count, long seed) {
        if (count != 20 && count != 100) {
            throw new IllegalArgumentException("Gate count must be 20 or 100: " + count);
        }
        float width = count == 20 ? 16.0f : 34.5f;
        float height = count == 20 ? 7.6f : 16.5f;
        float baseY = 2.35f;
        double eyeY = baseY + height * .40;
        double eyeZ = count == 20 ? 12 : 21;
        var gates = new ArrayList<Gate>(count);
        var random = new SplittableRandom(seed);
        int[] layers = new int[count];
        for (int i = 0; i < count; i++) layers[i] = i % 3;
        // The first gate leads the cast from the front. All remaining depths are shuffled.
        for (int i = count - 1; i > 1; i--) {
            int j = random.nextInt(1, i + 1);
            int swap = layers[i];
            layers[i] = layers[j];
            layers[j] = swap;
        }
        double[] viewScale = new double[count];
        double tightest = 1;
        for (int i = 0; i < count; i++) {
            int layer = layers[i];
            float z = -2.8f - 2.55f * layer + (float)random.nextDouble(-.52, .52);
            float radius = (float)random.nextDouble(.66, 1.22) + .05f * (1 - layer);
            float floor = baseY + .24f * layer;
            double scale = viewScale[i] = (eyeZ + 5.35) / (eyeZ - z);
            if (i < 4) {
                // Loose corner landmarks keep the outer silhouette wide even with only twenty gates.
                float x = (float)(width * random.nextDouble(0.36, 0.50)) * ((i & 1) == 0 ? -1 : 1);
                float y = (float)(height * random.nextDouble(0.015, 0.15));
                gates.add(new Gate(x, floor + (i < 2 ? y : height - y), z, radius));
                continue;
            }
            Gate best = null;
            double bestClearance = -1;
            Gate neighbor = random.nextDouble() < .55 ? gates.get(random.nextInt(i)) : null;
            double preferred = random.nextDouble(1.0, 1.55);
            for (int trial = 0; trial < 96; trial++) {
                float x, y;
                if (neighbor != null && trial < 16) {
                    // Put loose pairs and trios across different depths, with open spaces between them.
                    double angle = random.nextDouble(2 * Math.PI);
                    double gap = (radius + neighbor.radius() + RIM_GAP) * random.nextDouble(1.0, 1.55);
                    x = (float)(neighbor.x() + gap * Math.cos(angle));
                    y = (float)(neighbor.y() + gap * Math.sin(angle));
                    if (Math.abs(x) > width / 2 || y < floor || y > floor + height) continue;
                } else {
                    x = (float)random.nextDouble(-width / 2, width / 2);
                    y = floor + (float)random.nextDouble(height);
                }
                if (Math.abs(x) < radius + .55f) y = Math.max(y, 2.3f + radius);
                double clearance = Double.POSITIVE_INFINITY;
                for (int j = 0; j < gates.size(); j++) {
                    Gate other = gates.get(j);
                    double dx = x - other.x(), dy = y - other.y();
                    double spacing = radius + other.radius() + RIM_GAP;
                    clearance = Math.min(clearance, (dx * dx + dy * dy) / (spacing * spacing));
                    // A physically separated rear gate should also remain visible from in front of the caster.
                    dx = x * scale - other.x() * viewScale[j];
                    dy = (y - eyeY) * scale - (other.y() - eyeY) * viewScale[j];
                    spacing = radius * scale + other.radius() * viewScale[j] + RIM_GAP * .6;
                    clearance = Math.min(clearance, (dx * dx + dy * dy) / (spacing * spacing));
                }
                if (clearance > bestClearance) {
                    best = new Gate(x, y, z, radius);
                    bestClearance = clearance;
                }
                if (clearance >= preferred * preferred) break;
            }
            gates.add(best);
            tightest = Math.min(tightest, bestClearance);
        }
        if (tightest < 1) separateNeighbors(gates, baseY, eyeY, viewScale);
        return List.copyOf(gates);
    }

    private static void separateNeighbors(ArrayList<Gate> gates, float baseY, double eyeY, double[] viewScale) {
        int count = gates.size();
        double[] x = new double[count], y = new double[count];
        for (int i = 0; i < count; i++) {
            x[i] = gates.get(i).x();
            y[i] = gates.get(i).y();
        }
        double stretch = 1;
        for (int pass = 0; pass <= 20; pass++) {
            boolean moved = false;
            for (int i = 1; i < count; i++) {
                for (int j = 0; j < i; j++) {
                    double dx = x[i] - x[j], dy = y[i] - y[j];
                    double gap = gates.get(i).radius() + gates.get(j).radius() + RIM_GAP;
                    double squared = dx * dx + dy * dy;
                    double si = viewScale[i], sj = viewScale[j];
                    double vx = x[i] * si - x[j] * sj, vy = (y[i] - eyeY) * si - (y[j] - eyeY) * sj;
                    double apparentGap = gates.get(i).radius() * si + gates.get(j).radius() * sj + RIM_GAP * .6;
                    double viewSquared = vx * vx + vy * vy;
                    if (viewSquared * gap * gap < squared * apparentGap * apparentGap) {
                        dx = vx;
                        dy = vy;
                        squared = viewSquared;
                        gap = apparentGap;
                    } else {
                        si = sj = 1;
                    }
                    if (squared >= gap * gap) continue;
                    if (squared == 0) {
                        dx = 0.000001;
                        squared = dx * dx;
                    }
                    double ratio = gap / Math.sqrt(squared);
                    // Keep the denser clusters intact; only compensate globally for any residual overlap.
                    if (pass == 20) {
                        stretch = Math.max(stretch, ratio);
                        continue;
                    }
                    double shift = 0.5 * (ratio - 1);
                    x[i] += dx * shift / si;
                    y[i] += dy * shift / si;
                    x[j] -= dx * shift / sj;
                    y[j] -= dy * shift / sj;
                    moved = true;
                }
            }
            for (int i = 0; i < count; i++) {
                float radius = gates.get(i).radius();
                y[i] = Math.max(y[i], Math.abs(x[i]) < radius + .55 ? 2.3 + radius : baseY);
            }
            if (!moved) break;
        }
        // A common lift in projection preserves the clear gaps between front and rear gates.
        double lift = 0;
        for (int i = 0; i < count; i++) {
            Gate g = gates.get(i);
            float finalX = (float)(x[i] * stretch);
            double floor = Math.abs(finalX) < g.radius() + .55 ? 2.3 + g.radius() : baseY;
            x[i] = finalX;
            y[i] = eyeY + (y[i] - eyeY) * stretch;
            lift = Math.max(lift, (floor - y[i]) * viewScale[i]);
        }
        for (int i = 0; i < count; i++) {
            Gate g = gates.get(i);
            gates.set(i, new Gate((float)x[i], (float)(y[i] + lift / viewScale[i]), g.z(), g.radius()));
        }
    }
}
