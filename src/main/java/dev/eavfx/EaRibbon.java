package dev.eavfx;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RegisterShadersEvent;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.io.IOException;

/** Soft wind strips, with separate absorption and additive batches. World positions are camera-relative. */
public final class EaRibbon extends RenderType {
    private static ShaderInstance shader, darkShader;
    private static final MultiBufferSource.BufferSource BUFFERS =
            MultiBufferSource.immediate(new BufferBuilder(32_768));
    private static final MultiBufferSource.BufferSource DARK_BUFFERS =
            MultiBufferSource.immediate(new BufferBuilder(16_384));
    private static final RenderType TYPE = create("ea_item_ribbon", DefaultVertexFormat.POSITION_COLOR_TEX,
            VertexFormat.Mode.QUADS, 32_768, false, false, CompositeState.builder()
                    .setShaderState(new ShaderStateShard(() -> shader))
                    .setTransparencyState(ADDITIVE_TRANSPARENCY)
                    .setDepthTestState(LEQUAL_DEPTH_TEST)
                    .setCullState(NO_CULL)
                    .setWriteMaskState(COLOR_WRITE)
                    .createCompositeState(false));
    private static final RenderType DARK_TYPE = create("ea_item_wind_shadow", DefaultVertexFormat.POSITION_COLOR_TEX,
            VertexFormat.Mode.QUADS, 16_384, false, true, CompositeState.builder()
                    .setShaderState(new ShaderStateShard(() -> darkShader))
                    .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                    .setDepthTestState(LEQUAL_DEPTH_TEST)
                    .setCullState(NO_CULL)
                    .setWriteMaskState(COLOR_WRITE)
                    .createCompositeState(false));
    private static final Matrix4f LAST_POSE = new Matrix4f(), LAST_MODEL_VIEW = new Matrix4f();
    private static Vec3 localEye = Vec3.ZERO;
    private static boolean eyeCached;

    private EaRibbon() {
        super("ea_ribbon_unused", DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS,
                256, false, false, () -> {}, () -> {});
    }

    public static void registerShader(RegisterShadersEvent event) throws IOException {
        event.registerShader(new ShaderInstance(event.getResourceProvider(),
                new ResourceLocation("starcrown_ea", "item_ribbon"), DefaultVertexFormat.POSITION_COLOR_TEX),
                loaded -> shader = loaded);
        event.registerShader(new ShaderInstance(event.getResourceProvider(),
                new ResourceLocation("starcrown_ea", "item_ribbon_dark"), DefaultVertexFormat.POSITION_COLOR_TEX),
                loaded -> darkShader = loaded);
    }

    /** Adds two crossing strips; flush once after the complete effect, not after every segment. */
    public static void draw(PoseStack pose, Vec3 a, Vec3 b, float width,
                            float red, float green, float blue, float alpha) {
        if (shader == null) return;
        drawInto(BUFFERS, TYPE, pose, a, b, width, red, green, blue, alpha);
    }

    public static void drawDark(PoseStack pose, Vec3 a, Vec3 b, float width,
                                float red, float green, float blue, float alpha) {
        if (darkShader == null) return;
        drawInto(DARK_BUFFERS, DARK_TYPE, pose, a, b, width, red, green, blue, alpha);
    }

    private static void drawInto(MultiBufferSource.BufferSource buffers, RenderType type, PoseStack pose,
                                 Vec3 a, Vec3 b, float width, float red, float green, float blue, float alpha) {
        Vec3 segment = b.subtract(a);
        if (segment.lengthSqr() < 1.0e-10 || !Float.isFinite(width) || !Float.isFinite(alpha)
                || width <= 0 || alpha <= 0) return;
        Vec3 axis = segment.normalize();
        // An item pose is not camera-relative world space. Recover its actual local eye first;
        // otherwise the billboard turns toward the model origin and flattens at some hand poses.
        Matrix4f matrix = pose.last().pose();
        Matrix4f modelView = RenderSystem.getModelViewMatrix();
        if (!eyeCached || !LAST_POSE.equals(matrix) || !LAST_MODEL_VIEW.equals(modelView)) {
            LAST_POSE.set(matrix); LAST_MODEL_VIEW.set(modelView); eyeCached = true;
            Matrix4f combined = new Matrix4f(modelView).mul(matrix);
            Vector3f eye = Math.abs(combined.determinant()) > 1.0e-12f
                    ? combined.invert().transformPosition(new Vector3f()) : new Vector3f();
            localEye = new Vec3(eye.x, eye.y, eye.z);
        }
        Vec3 view = a.add(b).scale(0.5).subtract(localEye);
        Vec3 side = axis.cross(view);
        if (side.lengthSqr() < 1.0e-8) {
            side = axis.cross(Math.abs(axis.y) < 0.9 ? new Vec3(0, 1, 0) : new Vec3(1, 0, 0));
        }
        side = side.normalize().scale(width * 0.5);
        Vec3 other = axis.cross(side).normalize().scale(width * 0.5);
        VertexConsumer vertices = buffers.getBuffer(type);
        strip(vertices, matrix, a, b, side, red, green, blue, alpha);
        strip(vertices, matrix, a, b, other, red, green, blue, alpha * 0.6f);
    }

    private static void strip(VertexConsumer out, Matrix4f matrix, Vec3 a, Vec3 b, Vec3 offset,
                              float red, float green, float blue, float alpha) {
        vertex(out, matrix, a.add(offset), red, green, blue, alpha, 0, 0);
        vertex(out, matrix, b.add(offset), red, green, blue, alpha, 1, 0);
        vertex(out, matrix, b.subtract(offset), red, green, blue, alpha, 1, 1);
        vertex(out, matrix, a.subtract(offset), red, green, blue, alpha, 0, 1);
    }

    private static void vertex(VertexConsumer out, Matrix4f matrix, Vec3 p,
                               float red, float green, float blue, float alpha, float u, float v) {
        out.vertex(matrix, (float) p.x, (float) p.y, (float) p.z)
                .color(red, green, blue, Math.min(1, alpha)).uv(u, v).endVertex();
    }

    public static void flush() {
        DARK_BUFFERS.endBatch(DARK_TYPE);
        BUFFERS.endBatch(TYPE);
    }
}
