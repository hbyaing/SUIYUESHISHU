package dev.starcrown.treasury;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.util.Arrays;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** Golden chain links follow arc length, so the links feed through the gate as the spear advances. */
public final class HeavenChainRenderer extends RenderType {
    private static final int BUFFER_SIZE = 512 * 1024;
    private static final MultiBufferSource.BufferSource BUFFERS =
            MultiBufferSource.immediate(new BufferBuilder(BUFFER_SIZE));
    private static final RenderType GOLD = create("starcrowndoll_heaven_chain", DefaultVertexFormat.POSITION_COLOR,
            VertexFormat.Mode.QUADS, BUFFER_SIZE, false, false, CompositeState.builder()
                    .setShaderState(new ShaderStateShard(GameRenderer::getPositionColorShader))
                    .setTransparencyState(NO_TRANSPARENCY).setDepthTestState(LEQUAL_DEPTH_TEST)
                    .setWriteMaskState(COLOR_DEPTH_WRITE).setCullState(NO_CULL).createCompositeState(false));
    private static final Vec3 UP = new Vec3(0, 1, 0);
    private static final Vec3 X_AXIS = new Vec3(1, 0, 0);
    private static final Vec3 LIGHT = new Vec3(-.46, .81, -.36).normalize();
    private static final int END_STEPS = 5, TUBE_SIDES = 6;
    private static final int OUTLINE = 2 * (END_STEPS + 1);
    private static final int VERTICES = OUTLINE * TUBE_SIDES;
    private static final int MAX_LINKS = 48;
    private static final float[][] LINK = linkTemplate();
    private static final int[] QUADS = linkQuads();

    private HeavenChainRenderer() {
        super("unused", DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS,
                256, false, false, () -> {}, () -> {});
    }

    public static void draw(PoseStack pose, Vec3 targetOffset, float width, float height, long seed, float age) {
        draw(pose, targetOffset, width, height, seed, age, age);
    }

    /** Keep stageAge in [25, 65) for a sustained bind. motionAge counts ticks since the original cast. */
    public static void draw(PoseStack pose, Vec3 targetOffset, float width, float height, long seed,
                            float stageAge, float motionAge) {
        if (stageAge <= 12 || stageAge >= 80) return;
        float releaseAge = Math.max(0, stageAge - 65);
        // Preserve the exact amount paid out if the player cancels an opening gate.
        float openingAge = stageAge < 65 ? stageAge : Math.min(30, motionAge - releaseAge);
        if (openingAge <= 12) return;
        double remaining = 1 - ease(releaseAge / 15.0);
        var anchors = TreasuryChainLayout.anchors(seed, width, height);
        if (anchors.isEmpty()) return;
        pose.pushPose();
        try {
            pose.translate(targetOffset.x, targetOffset.y, targetOffset.z);
            Matrix4f matrix = pose.last().pose();
            Vector3f eye = new Matrix4f(RenderSystem.getModelViewMatrix()).mul(matrix).invert()
                    .transformPosition(new Vector3f());
            ChainBatch batch = new ChainBatch(BUFFERS.getBuffer(GOLD), matrix, new Vec3(eye.x, eye.y, eye.z));
            for (int i = 0; i < anchors.size(); i++) {
                double order = (double)i / (anchors.size() - 1);
                double lead = 12 + 4.5 * order;
                double contact = 23 + 2 * order;
                double extension = ease((openingAge - lead) / (contact - lead)) * remaining;
                if (extension <= 0) continue;
                double pull = lead + 4;
                double tension = ease((openingAge - pull) / (contact + 1 - pull)) * (1 - ease(releaseAge / 10));
                double sinceContact = motionAge - contact;
                double recoil = sinceContact > 0 && sinceContact < 6 && releaseAge == 0
                        ? Math.sin(sinceContact * 1.55) * Math.exp(-sinceContact * .67) : 0;
                Path path = path(anchors.get(i), width, height, i, anchors.size(), tension, recoil, motionAge);
                double pitch = Math.max(.33 * Mth.clamp(Math.sqrt(Math.max(.1, width) / .7), .83, 1.85),
                        path.length / MAX_LINKS);
                double reach = extension * path.length;
                double arrival = contact + 2;
                double pulseStart = lead + 5;
                double pulseHead = (motionAge - pulseStart) / (arrival - pulseStart) * path.length;
                double pulseWidth = Math.min(1.05 * pitch, path.length * .09);
                boolean pulseActive = releaseAge == 0 && motionAge >= pulseStart && pulseHead < path.length + pulseWidth;
                double twist = .48 + .19 * i + (.045 + .075 * (1 - extension)) * Math.sin(motionAge * .13 + i);
                Vec3 tip = path.at(reach), axis = path.tangent(reach, pitch);
                Vec3 base = perpendicular(axis, Math.abs(axis.y) > .92 ? X_AXIS : UP);
                double tipLight = pulseActive ? 1 - ease(Math.abs(reach - pulseHead) / pulseWidth) : 0;
                batch.spear(tip, axis, base, pitch * ease(reach / (.7 * pitch)), tipLight);
                for (int link = 0; link < MAX_LINKS; link++) {
                    double distance = reach - (.40 + link) * pitch;
                    if (distance < 0) break;
                    axis = path.tangent(distance, pitch);
                    base = perpendicular(axis, base);
                    Vec3 cross = axis.cross(base);
                    double angle = twist + (link & 1) * Math.PI * .5;
                    Vec3 across = base.scale(Math.cos(angle)).add(cross.scale(Math.sin(angle)));
                    double scale = Math.min(pitch, distance / .72);
                    if (scale < .01) break;
                    double pulse = pulseActive ? 1 - ease(Math.abs(distance - pulseHead) / pulseWidth) : 0;
                    batch.link(path.at(distance), axis, across, scale, pulse);
                }
            }
            BUFFERS.endBatch(GOLD);
        } finally {
            pose.popPose();
        }
    }

    private static double ease(double t) {
        t = Mth.clamp(t, 0, 1);
        return t * t * (3 - 2 * t);
    }

    private static Vec3 perpendicular(Vec3 axis, Vec3 reference) {
        Vec3 projected = reference.subtract(axis.scale(reference.dot(axis)));
        if (projected.lengthSqr() < 1e-8) return axis.cross(Math.abs(axis.y) < .9 ? UP : X_AXIS).normalize();
        return projected.normalize();
    }

    /** The free span meets a helix tangentially. Each gate grips a different side and height. */
    private static Path path(Vec3 gate, float width, float height, int index, int count,
                             double tighten, double recoil, float motionAge) {
        double side = (index & 1) == 0 ? 1 : -1;
        double radius = Math.max(.19, width * .54 + .035) * (1 + .36 * (1 - tighten));
        double theta = Math.atan2(gate.z, gate.x) + side * .18;
        double sweep = side * (2.05 + (index % 3) * .25);
        double level = ((index + .5) / count - .5) * .53 * height;
        double rise = -side * .22 * height;
        Vec3 entry = new Vec3(radius * Math.cos(theta), level, radius * Math.sin(theta));
        Vec3 entryTangent = new Vec3(-radius * Math.sin(theta) * sweep, rise,
                radius * Math.cos(theta) * sweep).normalize();
        Vec3 delta = entry.subtract(gate);
        double span = delta.length();
        double loose = 1 - tighten;
        double sag = Math.min(1.20, span * .24) * loose;
        double sway = Math.min(.24, span * .055) * loose * Math.sin(motionAge * .32 + index * 1.73)
                + Math.min(.16, span * .040) * recoil;
        // The first tangent exits through the actual gate normal. Slack affects only the middle of the span.
        Vec3 c1 = gate.subtract(gate.normalize().scale(span * .32));
        Vec3 c2 = entry.subtract(entryTangent.scale(Math.min(radius * (.48 + .52 * loose), span * .26)));
        final int freeSteps = 14, wrapSteps = 15;
        Vec3[] points = new Vec3[freeSteps + wrapSteps + 1];
        for (int i = 0; i <= freeSteps; i++) {
            double t = (double)i / freeSteps, u = 1 - t;
            double belly = 16 * t*t * u*u;
            Vec3 spanPoint = gate.scale(u*u*u).add(c1.scale(3*u*u*t)).add(c2.scale(3*u*t*t)).add(entry.scale(t*t*t));
            double roomBelow = Math.max(0, spanPoint.y + height*.5 - Math.min(.13, height*.18));
            double drop = Math.min(sag*belly, roomBelow*.84);
            double lateral = sway*belly + side*(sag*belly-drop)*.70;
            points[i] = spanPoint.add(-Math.sin(theta)*lateral, -drop, Math.cos(theta)*lateral);
        }
        for (int i = 1; i <= wrapSteps; i++) {
            double t = (double)i / wrapSteps, angle = theta + sweep * t;
            points[freeSteps+i] = new Vec3(radius * Math.cos(angle), level + rise * t, radius * Math.sin(angle));
        }
        return new Path(points);
    }

    private static final class Path {
        final Vec3[] points;
        final double[] distances;
        final double length;

        Path(Vec3[] points) {
            this.points = points;
            distances = new double[points.length];
            for (int i=1; i<points.length; i++) distances[i] = distances[i-1] + points[i].distanceTo(points[i-1]);
            length = distances[distances.length-1];
        }

        Vec3 at(double s) {
            if (s <= 0) return points[0];
            if (s >= length) return points[points.length-1];
            int i = Arrays.binarySearch(distances, s);
            if (i >= 0) return points[i];
            int next = -i-1, previous = next-1;
            return points[previous].lerp(points[next], (s-distances[previous]) / (distances[next]-distances[previous]));
        }

        Vec3 tangent(double s, double pitch) {
            return at(s+.20*pitch).subtract(at(s-.20*pitch)).normalize();
        }
    }

    /** Two semicircles and straight sides, with a hexagonal tube around the open center. */
    private static float[][] linkTemplate() {
        float[][] result = new float[VERTICES][];
        for (int j = 0; j < OUTLINE; j++) {
            int end = j / (END_STEPS+1), step = j % (END_STEPS+1);
            double theta = Math.PI * (end + (double)step / END_STEPS);
            double x = Math.cos(theta), y = Math.sin(theta), offset = end == 0 ? .29 : -.29;
            for (int k = 0; k < TUBE_SIDES; k++) {
                double phi = (k + .5) * 2 * Math.PI / TUBE_SIDES;
                float nx = (float)(x * Math.cos(phi)), ny = (float)(y * Math.cos(phi)), nz = (float)Math.sin(phi);
                result[j*TUBE_SIDES+k] = new float[] {(float)(.315*x + .095*nx),
                        (float)(offset + .315*y + .095*ny), .095f*nz, nx, ny, nz, (float)Math.max(0,-Math.cos(phi))};
            }
        }
        return result;
    }

    private static int[] linkQuads() {
        int[] result = new int[VERTICES*4];
        for (int j=0; j<OUTLINE; j++) for (int k=0; k<TUBE_SIDES; k++) {
            int next = (j+1)%OUTLINE, around = (k+1)%TUBE_SIDES, base = (j*TUBE_SIDES+k)*4;
            result[base]=j*TUBE_SIDES+k; result[base+1]=next*TUBE_SIDES+k;
            result[base+2]=next*TUBE_SIDES+around; result[base+3]=j*TUBE_SIDES+around;
        }
        return result;
    }

    /** Scratch arrays are reused for every link in this draw. Shading and transforms run once per unique vertex. */
    private static final class ChainBatch {
        final VertexConsumer out;
        final Matrix4f matrix;
        final Vec3 eye;
        final float[] xs = new float[VERTICES], ys = new float[VERTICES], zs = new float[VERTICES];
        final int[] colors = new int[VERTICES];

        ChainBatch(VertexConsumer out, Matrix4f matrix, Vec3 eye) {
            this.out = out;
            this.matrix = matrix;
            this.eye = eye;
        }

        void link(Vec3 origin, Vec3 tangent, Vec3 across, double scale, double pulse) {
            Vec3 binormal = across.cross(tangent);
            Vec3 half = eye.subtract(origin).normalize().add(LIGHT).normalize();
            for (int i=0; i<VERTICES; i++) {
                float[] v = LINK[i];
                xs[i]=(float)(origin.x + scale*(v[0]*across.x + v[1]*tangent.x + v[2]*binormal.x));
                ys[i]=(float)(origin.y + scale*(v[0]*across.y + v[1]*tangent.y + v[2]*binormal.y));
                zs[i]=(float)(origin.z + scale*(v[0]*across.z + v[1]*tangent.z + v[2]*binormal.z));
                double nx=v[3]*across.x + v[4]*tangent.x + v[5]*binormal.x;
                double ny=v[3]*across.y + v[4]*tangent.y + v[5]*binormal.y;
                double nz=v[3]*across.z + v[4]*tangent.z + v[5]*binormal.z;
                colors[i]=gold(nx,ny,nz,half,v[6],pulse);
            }
            for (int i : QUADS) vertex(xs[i],ys[i],zs[i],colors[i]);
        }

        void spear(Vec3 origin, Vec3 tangent, Vec3 across, double scale, double pulse) {
            Vec3 binormal = across.cross(tangent);
            Vec3 tip = origin.add(tangent.scale(.86*scale)), heel = origin.subtract(tangent.scale(.35*scale));
            Vec3[] edge = {origin.add(across.scale(.25*scale)), origin.add(binormal.scale(.13*scale)),
                    origin.subtract(across.scale(.25*scale)), origin.subtract(binormal.scale(.13*scale))};
            Vec3 half = eye.subtract(origin).normalize().add(LIGHT).normalize();
            for (int i=0; i<4; i++) {
                Vec3 a=edge[i], b=edge[(i+1)%4];
                facet(tip,a,b,half,pulse);
                facet(heel,b,a,half,pulse);
            }
        }

        void facet(Vec3 a, Vec3 b, Vec3 c, Vec3 half, double pulse) {
            Vec3 n = c.subtract(a).cross(b.subtract(a)).normalize();
            int color = gold(n.x,n.y,n.z,half,0,pulse);
            vertex((float)a.x,(float)a.y,(float)a.z,color); vertex((float)b.x,(float)b.y,(float)b.z,color);
            vertex((float)c.x,(float)c.y,(float)c.z,color); vertex((float)c.x,(float)c.y,(float)c.z,color);
        }

        static int gold(double nx, double ny, double nz, Vec3 half, double inner, double pulse) {
            double lambert = nx*LIGHT.x + ny*LIGHT.y + nz*LIGHT.z;
            double shade = Mth.clamp(.46 + .41*lambert + .11*ny, 0, 1);
            double glint = Math.max(0, nx*half.x + ny*half.y + nz*half.z);
            glint *= glint; glint *= glint; glint *= glint; glint *= glint;
            // Keep the inward-facing bevel bronze even as a bright reflection crosses the outer bevel.
            double occlusion = 1 - .42*inner;
            glint *= 1 - .75*inner;
            double streak = pulse*(1-inner)*(.22+.78*shade);
            int r = (int)Math.min(255, (87 + 151*shade)*occlusion + 78*glint + 120*streak);
            int g = (int)Math.min(255, (43 + 134*shade)*occlusion + 106*glint + 150*streak);
            int b = (int)Math.min(255, (11 + 55*shade)*occlusion + 153*glint + 154*streak);
            return r<<16 | g<<8 | b;
        }

        void vertex(float x, float y, float z, int color) {
            out.vertex(matrix,x,y,z).color(color>>16,(color>>8)&255,color&255,255).endVertex();
        }
    }
}
