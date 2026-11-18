package dev.starcrown;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.nio.ByteBuffer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RegisterShadersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import org.joml.Vector2f;
import org.joml.Vector4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL30;

/** Refract a small patch of the finished world behind the first-person Invisible Air model. */
@Mod.EventBusSubscriber(modid = StarcrownDoll.ID, value = Dist.CLIENT)
public final class ExcaliburAirRefraction {
    private static ShaderInstance shader;
    private static VertexBuffer quad;
    private static int patch, patchWidth, patchHeight;
    private static boolean unavailable;
    private static final Matrix4f IDENTITY = new Matrix4f();
    private static final boolean EXTERNAL_PIPELINE = ModList.get().isLoaded("oculus") || ModList.get().isLoaded("iris")
            || ExcaliburAirRefraction.class.getResource("/net/optifine/shaders/Shaders.class") != null;

    @Mod.EventBusSubscriber(modid = StarcrownDoll.ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class Shaders {
        @SubscribeEvent
        public static void register(RegisterShadersEvent event) {
            release();
            shader = null;
            unavailable = false;
            if (EXTERNAL_PIPELINE) return;
            try {
                var id = new ResourceLocation(StarcrownDoll.ID, "invisible_air_refraction");
                event.registerShader(new ShaderInstance(event.getResourceProvider(), id, DefaultVertexFormat.POSITION),
                        loaded -> shader = loaded);
            } catch (IOException | RuntimeException error) {
                LogUtils.getLogger().warn("Invisible Air scene refraction unavailable", error);
            }
        }
    }

    @SubscribeEvent
    public static void logout(ClientPlayerNetworkEvent.LoggingOut event) { release(); }

    private static void release() {
        if (patch != 0) GlStateManager._deleteTexture(patch);
        if (quad != null) quad.close();
        patch = patchWidth = patchHeight = 0;
        quad = null;
    }

    public static void draw(LivingEntity bearer, ItemStack stack, ItemDisplayContext context, boolean left, PoseStack pose) {
        var mc = Minecraft.getInstance();
        if (shader == null || unavailable || bearer != mc.player || mc.getCameraEntity() != bearer
                || !mc.options.getCameraType().isFirstPerson() || Boolean.getBoolean("starcrown.disableAirRefraction")) return;
        // Only one local hand gets a copy. Third-person players never enter this pass.
        var primary = bearer.getMainHandItem();
        if (left != (bearer.getMainArm() == HumanoidArm.LEFT) && primary.getItem() instanceof ExcaliburItem
                && ExcaliburItem.isConcealed(primary)) return;
        var main = mc.getMainRenderTarget();
        int width = main.viewWidth, height = main.viewHeight;
        if (width <= 0 || height <= 0 || main.frameBufferId <= 0
                || GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING) != main.frameBufferId
                || GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING) != main.frameBufferId
                || GL11.glGetInteger(GL11.GL_READ_BUFFER) != GL30.GL_COLOR_ATTACHMENT0
                || GL30.glGetFramebufferAttachmentParameteri(GL30.GL_READ_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0,
                        GL30.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_NAME) != main.getColorTextureId()) return;
        var model = mc.getItemRenderer().getModel(stack, bearer.level(), bearer, bearer.getId() + context.ordinal());
        Matrix4f projection = new Matrix4f(RenderSystem.getProjectionMatrix()).mul(RenderSystem.getModelViewMatrix());
        pose.pushPose();
        try {
            model.applyTransform(context, pose, left);
            projection.mul(pose.last().pose());
        } finally {
            pose.popPose();
        }
        // Positions below are the pressure mesh coordinates minus the baked-item centre (.5,.5,.5).
        Vector2f base = project(projection, 0, .15F, 0, width, height);
        Vector2f tip = project(projection, 0, 1.23F, 0, width, height);
        Vector2f middle = project(projection, 0, .59F, 0, width, height);
        Vector2f axisX = project(projection, .082F, .59F, 0, width, height);
        Vector2f axisZ = project(projection, 0, .59F, .074F, width, height);
        if (base == null || tip == null || middle == null || axisX == null || axisZ == null) return;
        Vector2f line = new Vector2f(tip).sub(base);
        if (line.lengthSquared() < 20) return;
        Vector2f across = new Vector2f(-line.y, line.x).normalize();
        float radius = (float)Math.hypot(axisX.sub(middle).dot(across), axisZ.sub(middle).dot(across));
        float pixelScale = Mth.clamp(height / 650F, .65F, 4F);
        float pad = radius + 4 * pixelScale + 1;
        int x = Math.max(0, Mth.floor(Math.min(base.x, tip.x) - pad));
        int y = Math.max(0, Mth.floor(Math.min(base.y, tip.y) - pad));
        int w = Math.min(width, Mth.ceil(Math.max(base.x, tip.x) + pad)) - x;
        int h = Math.min(height, Mth.ceil(Math.max(base.y, tip.y) + pad)) - y;
        // Skip the occasional extreme camera/animation pose rather than copying a broad region.
        if (w < 2 || h < 2 || (long)w * h > (long)width * height / 5 || w > 4096 || h > 4096) return;

        try (VfxRenderState state = new VfxRenderState()) {
            if (state.viewport[0] != 0 || state.viewport[1] != 0
                    || state.viewport[2] != width || state.viewport[3] != height) return;
            GlStateManager._activeTexture(GL13.GL_TEXTURE0);
            ensurePatch(w, h);
            GL11.glCopyTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, x, y, w, h);
            shader.setSampler("ScenePatch", patch);
            shader.safeGetUniform("BladeBase").set(base.x, base.y);
            shader.safeGetUniform("BladeTip").set(tip.x, tip.y);
            shader.safeGetUniform("BladeRadius").set(radius);
            shader.safeGetUniform("CropOrigin").set((float)x, (float)y);
            shader.safeGetUniform("PatchSize").set((float)patchWidth, (float)patchHeight);
            shader.safeGetUniform("CopiedSize").set((float)w, (float)h);
            shader.safeGetUniform("Time").set((bearer.tickCount % 32768) + mc.getFrameTime());
            shader.safeGetUniform("PixelScale").set(pixelScale);
            if (quad == null) buildQuad();
            // Map the quad to the copied rectangle. Later vanilla hand drawing retains its own depth buffer.
            RenderSystem.viewport(x, y, w, h);
            RenderSystem.disableCull();
            RenderSystem.disableBlend();
            RenderSystem.disableDepthTest();
            RenderSystem.depthMask(false);
            RenderSystem.colorMask(true, true, true, false);
            quad.bind();
            quad.drawWithShader(IDENTITY, IDENTITY, shader);
            VertexBuffer.unbind();
        } catch (RuntimeException error) {
            unavailable = true;
            LogUtils.getLogger().warn("Invisible Air scene copy disabled for this resource session", error);
        }
    }

    private static Vector2f project(Matrix4f clip, float x, float y, float z, int w, int h) {
        var point = clip.transform(new Vector4f(x, y, z, 1));
        if (point.w < .03F || !point.isFinite()) return null;
        return new Vector2f((point.x / point.w + 1) * w / 2, (point.y / point.w + 1) * h / 2);
    }

    private static void ensurePatch(int width, int height) {
        if (patch == 0) patch = GlStateManager._genTexture();
        GlStateManager._bindTexture(patch);
        if (width <= patchWidth && height <= patchHeight) return;
        patchWidth = Math.max(patchWidth, Mth.smallestEncompassingPowerOfTwo(width));
        patchHeight = Math.max(patchHeight, Mth.smallestEncompassingPowerOfTwo(height));
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, patchWidth, patchHeight, 0,
                GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (ByteBuffer)null);
        GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
    }

    private static void buildQuad() {
        var builder = new BufferBuilder(128);
        builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION);
        builder.vertex(-1, -1, 0).endVertex();
        builder.vertex(1, -1, 0).endVertex();
        builder.vertex(1, 1, 0).endVertex();
        builder.vertex(-1, 1, 0).endVertex();
        quad = new VertexBuffer(VertexBuffer.Usage.STATIC);
        quad.bind();
        quad.upload(builder.end());
        VertexBuffer.unbind();
    }
}
