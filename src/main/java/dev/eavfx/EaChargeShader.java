package dev.eavfx;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.util.Random;
import java.util.function.Function;
import net.minecraft.Util;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraftforge.client.event.RegisterShadersEvent;
import org.joml.Vector3f;

public final class EaChargeShader extends RenderType {
    private static ShaderInstance runeShader, arcShader;
    private static final Function<ResourceLocation, RenderType> RUNES = Util.memoize(texture -> create(
            "ea_charged_runes", DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS, 65536, false, false,
            CompositeState.builder().setShaderState(new ShaderStateShard(() -> runeShader))
                    .setTextureState(new TextureStateShard(texture, true, false))
                    .setTransparencyState(TRANSLUCENT_TRANSPARENCY).setDepthTestState(LEQUAL_DEPTH_TEST)
                    .setCullState(NO_CULL).setWriteMaskState(COLOR_WRITE).createCompositeState(false)));
    private static final RenderType ARCS = create("ea_rotor_arcs", DefaultVertexFormat.NEW_ENTITY,
            VertexFormat.Mode.QUADS, 16384, false, false, CompositeState.builder()
                    .setShaderState(new ShaderStateShard(() -> arcShader)).setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                    .setDepthTestState(LEQUAL_DEPTH_TEST).setCullState(NO_CULL)
                    .setWriteMaskState(COLOR_WRITE).createCompositeState(false));

    private EaChargeShader() {
        super("unused", DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS, 256, false, false, () -> {}, () -> {});
    }

    static void register(RegisterShadersEvent event) {
        runeShader = arcShader = null;
        try {
            event.registerShader(new ShaderInstance(event.getResourceProvider(),
                    new ResourceLocation("starcrown_ea", "ea_blade_runes"), DefaultVertexFormat.NEW_ENTITY),
                    loaded -> runeShader = loaded);
            event.registerShader(new ShaderInstance(event.getResourceProvider(),
                    new ResourceLocation("starcrown_ea", "ea_blade_arc"), DefaultVertexFormat.NEW_ENTITY),
                    loaded -> arcShader = loaded);
        } catch (IOException | RuntimeException error) {
            LogUtils.getLogger().warn("Ea charging blade shaders unavailable", error);
        }
    }

    public static boolean ready() {
        return runeShader != null && arcShader != null;
    }

    public static VertexConsumer runes(MultiBufferSource buffers, ResourceLocation originalGlow) {
        return buffers.getBuffer(RUNES.apply(originalGlow));
    }

    /** The rune layer uses the otherwise unused lightmap U coordinate for the widening of the original glyphs. */
    public static int runeCharge(float age) {
        return Math.round(240 * Mth.clamp(age / 10f, 0, 1));
    }

    private record Polar(float angle, float height, float radius) {}
    private record Node(Vector3f position, Vector3f tangent, float taper) {}
    private record Discharge(Node[] trunk, Node[] fork) {}

    /** Built on mesh reload. Each section has its own measured taper and alternating discharge paths. */
    public static final class Rotor {
        private final int section;
        private final float halfWidth;
        private final Discharge[][] paths = new Discharge[2][8];

        public Rotor(int section, Vector3f pivot, Vector3f axis, float bottom, float top, float[] vertices) {
            this.section = section;
            float rootRadius = 0, tipRadius = 0;
            for (int i = 0; i < vertices.length; i += 8) {
                float y = vertices[i + 1], along = (y - pivot.y) / axis.y;
                float radius = (float)Math.hypot(vertices[i] - pivot.x - axis.x * along,
                        vertices[i + 2] - pivot.z - axis.z * along);
                if (y <= Mth.lerp(.12f, bottom, top)) rootRadius = Math.max(rootRadius, radius);
                if (y >= Mth.lerp(.88f, bottom, top)) tipRadius = Math.max(tipRadius, radius);
            }
            halfWidth = .13f * (rootRadius + tipRadius);
            for (int variant = 0; variant < paths[0].length; variant++) {
                float angle = variant * 2.3999632f + section * 1.71f;
                for (int lane = 0; lane < paths.length; lane++) {
                    Random random = new Random(7307L * variant + 937L * section + 59L * lane);
                    float start = angle + lane * Mth.PI;
                    float sign = ((variant + lane + section) & 1) == 0 ? 1 : -1;
                    boolean crossing = ((variant + lane) & 1) == 0;
                    float sweep = sign * (crossing ? 3.5f : 1.15f);
                    float y0 = .05f + .20f * random.nextFloat();
                    float height = (crossing ? .43f : .65f) + .09f * random.nextFloat();
                    Polar[] trunk = new Polar[15];
                    for (int i = 0; i < trunk.length; i++) {
                        float t = i / (trunk.length - 1f);
                        float arch = Mth.sin(t * Mth.PI);
                        float bend = arch * (.34f * Mth.sin(t * 10 + section) + .44f * (random.nextFloat() - .5f));
                        float y = y0 + height * t + arch * (.075f * Mth.sin(t * 12 + lane)
                                + .064f * (random.nextFloat() - .5f));
                        float radius = 1.015f + arch * (.27f + .43f * random.nextFloat());
                        trunk[i] = new Polar(start + sweep * t + bend, y, radius);
                    }
                    Polar knot = trunk[6 + random.nextInt(4)];
                    Polar[] fork = new Polar[6];
                    fork[0] = knot;
                    for (int i = 1; i < fork.length; i++) {
                        float t = i / (fork.length - 1f);
                        fork[i] = new Polar(knot.angle() - sign * (.96f * t + .23f * (random.nextFloat() - .5f)),
                                knot.height() + t * .19f + .037f * (random.nextFloat() - .5f),
                                knot.radius() + t * .31f + .15f * (random.nextFloat() - .5f));
                    }
                    paths[lane][variant] = new Discharge(
                            fit(trunk, pivot, axis, bottom, top, rootRadius, tipRadius),
                            fit(fork, pivot, axis, bottom, top, rootRadius, tipRadius));
                }
            }
        }

        private static Node[] fit(Polar[] path, Vector3f pivot, Vector3f axis, float bottom, float top,
                                  float rootRadius, float tipRadius) {
            Vector3f[] points = new Vector3f[path.length];
            for (int i = 0; i < path.length; i++) {
                Polar p = path[i];
                float y = Mth.lerp(p.height(), bottom, top);
                float radius = p.radius() * Mth.lerp(p.height(), rootRadius, tipRadius);
                points[i] = new Vector3f(pivot).fma((y - pivot.y) / axis.y, axis)
                        .add(Mth.cos(p.angle()) * radius, 0, Mth.sin(p.angle()) * radius);
            }
            Node[] nodes = new Node[path.length];
            for (int i = 0; i < path.length; i++) {
                Vector3f tangent = new Vector3f(points[Math.min(i + 1, points.length - 1)])
                        .sub(points[Math.max(0, i - 1)]).normalize();
                float t = i / (path.length - 1f);
                nodes[i] = new Node(points[i], tangent, .12f + .88f * Mth.clamp(6 * Math.min(t, 1 - t), 0, 1));
            }
            return nodes;
        }

        public void draw(PoseStack pose, MultiBufferSource buffers, float age) {
            float strength = Mth.clamp((age - 10f) / 14f, 0, 1);
            for (int lane = 0; lane < paths.length; lane++) {
                float time = age - 10 - section * 2.25f - lane * 3.1f;
                if (time < 0) continue;
                float interval = 9.7f + section * .71f;
                int pulse = Mth.floor(time / interval);
                float phase = time - pulse * interval;
                float life = lane == 0 ? 6.75f : 4.8f;
                if (phase >= life || (lane == 1 && (pulse + section) % 4 == 2)) continue;
                float envelope = Mth.clamp(phase / .9f, 0, 1) * Mth.clamp((life - phase) / 1.5f, 0, 1);
                float restrike = 1 - .46f * (1 - Mth.clamp(Math.abs(phase - 3.1f) / .85f, 0, 1));
                Discharge flash = paths[lane][pulse % paths[lane].length];
                VertexConsumer out = buffers.getBuffer(ARCS);
                float scale = pose.last().pose().getColumn(0, new org.joml.Vector4f()).length();
                float width = halfWidth * scale * (lane == 0 ? 1 : .80f);
                stroke(out, pose.last(), flash.trunk(), width, strength * envelope * restrike);
                if (phase > 1.1f && phase < life - 1.1f && (pulse + lane + section) % 3 != 1) {
                    float flick = Mth.clamp(Math.min(phase - 1.1f, life - 1.1f - phase), 0, 1);
                    stroke(out, pose.last(), flash.fork(), width * .50f, strength * envelope * flick * .75f);
                }
            }
        }

        private static void stroke(VertexConsumer out, PoseStack.Pose pose, Node[] nodes, float width, float alpha) {
            for (int i = 0; i + 1 < nodes.length; i++) {
                edge(out, pose, nodes[i], -width, alpha);
                edge(out, pose, nodes[i + 1], -width, alpha);
                edge(out, pose, nodes[i + 1], width, alpha);
                edge(out, pose, nodes[i], width, alpha);
            }
        }

        private static void edge(VertexConsumer out, PoseStack.Pose pose, Node node, float width, float alpha) {
            Vector3f p = node.position(), t = node.tangent();
            out.vertex(pose.pose(), p.x, p.y, p.z).color(1f, 1f, 1f, alpha)
                    .uv(width * node.taper(), 0).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(0)
                    .normal(pose.normal(), t.x, t.y, t.z).endVertex();
        }
    }
}
