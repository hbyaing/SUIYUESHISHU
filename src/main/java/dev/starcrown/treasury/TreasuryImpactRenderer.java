package dev.starcrown.treasury;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.starcrown.StarcrownDoll;
import java.util.ArrayDeque;
import javax.annotation.Nullable;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

@Mod.EventBusSubscriber(modid = StarcrownDoll.ID, value = Dist.CLIENT)
public final class TreasuryImpactRenderer extends RenderType {
    private static final int LIFETIME = 16;
    private static final int MAX_BURSTS = 40;
    private static final int SEGMENTS = 32;
    private static final float[] COS = new float[SEGMENTS + 1];
    private static final float[] SIN = new float[SEGMENTS + 1];
    private static final RenderType MAIN = layer(false);
    private static final RenderType SORTED = layer(true);
    private static final ResourceLocation STAR = new ResourceLocation(StarcrownDoll.ID, "textures/treasury/impact_star.png");
    private static final RenderType STARS = starLayer(false);
    private static final RenderType SORTED_STARS = starLayer(true);
    private static final MultiBufferSource.BufferSource BUFFER = MultiBufferSource.immediate(new BufferBuilder(65536));
    private static final MultiBufferSource.BufferSource STAR_BUFFER = MultiBufferSource.immediate(new BufferBuilder(16384));
    private static final ArrayDeque<Burst> ACTIVE = new ArrayDeque<>();
    private static ClientLevel world;

    private record Spark(Vec3 velocity, float lifetime) {}
    private record Burst(Vec3 point, Vec3 normal, Quaternionf plane, float phase, long start,
                         float flashStrength, Spark[] sparks) {}

    static {
        for (int i = 0; i <= SEGMENTS; i++) {
            COS[i] = (float) Math.cos(i * Math.PI * 2 / SEGMENTS);
            SIN[i] = (float) Math.sin(i * Math.PI * 2 / SEGMENTS);
        }
    }

    private TreasuryImpactRenderer() {
        super("unused", DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS, 256,
                false, false, () -> {}, () -> {});
    }

    private static RenderType layer(boolean sorted) {
        return create("treasury_impact_" + (sorted ? "particles" : "main"), DefaultVertexFormat.POSITION_COLOR,
                VertexFormat.Mode.QUADS, 65536, false, true, CompositeState.builder()
                        .setShaderState(POSITION_COLOR_SHADER).setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                        .setDepthTestState(LEQUAL_DEPTH_TEST).setCullState(NO_CULL)
                        .setWriteMaskState(sorted ? COLOR_DEPTH_WRITE : COLOR_WRITE)
                        .setOutputState(sorted ? PARTICLES_TARGET : MAIN_TARGET).createCompositeState(false));
    }

    private static RenderType starLayer(boolean sorted) {
        return create("treasury_impact_stars_" + (sorted ? "particles" : "main"), DefaultVertexFormat.POSITION_COLOR_TEX,
                VertexFormat.Mode.QUADS, 16384, false, true, CompositeState.builder()
                        .setShaderState(POSITION_COLOR_TEX_SHADER).setTextureState(new TextureStateShard(STAR, true, false))
                        .setTransparencyState(TRANSLUCENT_TRANSPARENCY).setDepthTestState(LEQUAL_DEPTH_TEST)
                        .setCullState(NO_CULL).setWriteMaskState(sorted ? COLOR_DEPTH_WRITE : COLOR_WRITE)
                        .setOutputState(sorted ? PARTICLES_TARGET : MAIN_TARGET).createCompositeState(false));
    }

    private static void useLevel(ClientLevel level) {
        if (world != level) {
            ACTIVE.clear();
            world = level;
        }
    }

    static void add(Vec3 point, Vec3 normal, long seed) {
        useLevel(Minecraft.getInstance().level);
        if (world == null) return;
        long now = world.getGameTime();
        int nearby = 0;
        for (var burst : ACTIVE) {
            long age = now - burst.start;
            double distance = burst.point.distanceToSqr(point);
            if (age < 2 && distance < 0.55 * 0.55 && burst.normal.dot(normal) > 0.85) return;
            if (age < 7 && distance < 2.25) nearby++;
        }
        if (ACTIVE.size() >= MAX_BURSTS) ACTIVE.removeFirst();
        var plane = new Quaternionf().rotationTo(0, 0, 1, (float) normal.x, (float) normal.y, (float) normal.z);
        var random = RandomSource.create(seed);
        float phase = random.nextFloat() * Mth.TWO_PI;
        Vec3 tangent = (Math.abs(normal.y) < .9 ? new Vec3(0, 1, 0) : new Vec3(1, 0, 0)).cross(normal).normalize();
        Vec3 bitangent = normal.cross(tangent);
        Spark[] sparks = new Spark[nearby == 0 ? 4 : nearby < 3 ? 3 : 2];
        for (int i = 0; i < sparks.length; i++) {
            float angle = phase + (i + .34F * random.nextFloat()) * Mth.TWO_PI / sparks.length;
            float speed = .14F + .14F * random.nextFloat();
            Vec3 velocity = tangent.scale(Mth.cos(angle) * speed).add(bitangent.scale(Mth.sin(angle) * speed))
                    .add(normal.scale(.07 + .10 * random.nextFloat()));
            sparks[i] = new Spark(velocity, 6.5F + 3.5F * random.nextFloat());
        }
        ACTIVE.addLast(new Burst(point, normal, plane, phase, now, 1F / (1F + .36F * nearby), sparks));
    }

    public static int activeCount() { return ACTIVE.size(); }

    @Nullable public static Vec3 latestPoint() { return ACTIVE.isEmpty() ? null : ACTIVE.getLast().point; }

    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        useLevel(Minecraft.getInstance().level);
        if (world != null) ACTIVE.removeIf(burst -> world.getGameTime() - burst.start >= LIFETIME);
    }

    @SubscribeEvent public static void render(RenderLevelStageEvent event) {
        if (ACTIVE.isEmpty() || world != Minecraft.getInstance().level) return;
        boolean sorted = Minecraft.useShaderTransparency();
        if (event.getStage() != (sorted ? RenderLevelStageEvent.Stage.AFTER_PARTICLES :
                RenderLevelStageEvent.Stage.AFTER_WEATHER)) return;
        // At AFTER_WEATHER the camera rotation is already in RenderSystem's model-view.
        PoseStack pose = sorted ? event.getPoseStack() : new PoseStack();
        Vec3 eye = event.getCamera().getPosition();
        RenderType type = sorted ? SORTED : MAIN;
        RenderType starType = sorted ? SORTED_STARS : STARS;
        VertexConsumer out = BUFFER.getBuffer(type);
        VertexConsumer stars = STAR_BUFFER.getBuffer(starType);
        for (var burst : ACTIVE) {
            float age = world.getGameTime() - burst.start + event.getPartialTick();
            if (age < 0 || age >= LIFETIME || !event.getFrustum().isVisible(new AABB(burst.point, burst.point).inflate(3.0))) continue;
            float t = age / LIFETIME;
            float radius = .30F + 1.93F * (1 - (1 - t) * (1 - t) * (1 - t));
            float fade = Mth.clamp((1 - t) / .58F, 0, 1);
            Vec3 relative = burst.point.subtract(eye);
            pose.pushPose();
            pose.translate(relative.x, relative.y, relative.z);
            pose.pushPose();
            pose.mulPose(burst.plane);
            Matrix4f m = pose.last().pose();
            // Three adjoining bands keep a hot gold edge legible against both pale walls and a dark sky.
            float w = .15F * (1 - .5F * t);
            band(out, m, radius - 1.45F*w, radius - .45F*w, 0xFF820E, .22F*fade, 0xFFAB27, .85F*fade);
            band(out, m, radius - .45F*w, radius + .38F*w, 0xFFF2AA, fade, 0xFFD055, fade);
            band(out, m, radius + .38F*w, radius + 1.20F*w, 0xFF830D, .94F*fade, 0xDB4D08, .25F*fade);
            for (int i = 0; i < 9; i++) {
                float angle = burst.phase + i * Mth.TWO_PI / 9F;
                float start = radius * (.62F + .15F * SIN[(i*5) % SEGMENTS]);
                float length = (.49F + .22F * COS[(i*3) % SEGMENTS]) * (1 - t);
                ray(out, m, angle, start, length, .075F * (1 - .6F*t), fade);
            }
            pose.popPose();
            drawSparks(stars, pose, event.getCamera(), burst, age, relative.length());
            pose.popPose();
        }
        BUFFER.endBatch(type);
        STAR_BUFFER.endBatch(starType);
    }

    private static void drawSparks(VertexConsumer out, PoseStack pose, Camera camera, Burst burst, float age, double distance) {
        if (age >= 10) return;
        float close = Mth.clamp((float) distance / 2.5F, .25F, 1F);
        float strength = burst.flashStrength * close;
        if (age < 5) {
            float hot = 1F - age / 5F;
            float size = close * (.45F + .87F * hot);
            pose.pushPose();
            pose.mulPose(camera.rotation());
            Matrix4f m = pose.last().pose();
            star(out, m, burst.phase + .2F, size * 1.3F, size * .74F, 0xFF9022, .60F * strength * hot);
            star(out, m, burst.phase, size, size * .85F, 0xFFDE91, .95F * strength * hot);
            pose.popPose();
        }
        if (age < .4F) return;
        float emerge = Mth.clamp((age - .4F) * 1.65F, 0, 1);
        for (Spark spark : burst.sparks) {
            float t = age / spark.lifetime;
            if (t >= 1) continue;
            float fade = emerge * Mth.clamp((1F - t) * 2.4F, 0, 1);
            float travel = age * (1F - .32F * t);
            Vec3 v = spark.velocity;
            pose.pushPose();
            pose.translate(v.x * travel, v.y * travel - .0018 * age * age, v.z * travel);
            pose.mulPose(camera.rotation());
            var left = camera.getLeftVector();
            var up = camera.getUpVector();
            double dx = v.x * left.x + v.y * left.y + v.z * left.z;
            double dy = v.x * up.x + v.y * up.y + v.z * up.z;
            float angle = (float) Mth.atan2(dy, dx);
            float size = close * (.18F + .27F * (1F - t));
            star(out, pose.last().pose(), angle, size, size * .31F, 0xFFC45D, .87F * strength * fade);
            pose.popPose();
        }
    }

    private static void star(VertexConsumer out, Matrix4f m, float angle, float width, float height, int color, float alpha) {
        float c = Mth.cos(angle), s = Mth.sin(angle);
        float x = c * width, y = s * width, u = -s * height, v = c * height;
        starVertex(out, m, -x-u, -y-v, 0, 1, color, alpha);
        starVertex(out, m,  x-u,  y-v, 1, 1, color, alpha);
        starVertex(out, m,  x+u,  y+v, 1, 0, color, alpha);
        starVertex(out, m, -x+u, -y+v, 0, 0, color, alpha);
    }

    private static void starVertex(VertexConsumer out, Matrix4f m, float x, float y, float u, float v, int color, float alpha) {
        out.vertex(m, x, y, 0).color(color >>> 16, (color >>> 8) & 255, color & 255, (int) (255 * alpha))
                .uv(u, v).endVertex();
    }

    private static void band(VertexConsumer out, Matrix4f m, float r0, float r1, int c0, float a0, int c1, float a1) {
        for (int i = 0; i < SEGMENTS; i++) {
            vertex(out, m, COS[i]*r0, SIN[i]*r0, c0, a0);
            vertex(out, m, COS[i]*r1, SIN[i]*r1, c1, a1);
            vertex(out, m, COS[i+1]*r1, SIN[i+1]*r1, c1, a1);
            vertex(out, m, COS[i+1]*r0, SIN[i+1]*r0, c0, a0);
        }
    }

    private static void ray(VertexConsumer out, Matrix4f m, float angle, float start, float length, float width, float alpha) {
        float x = Mth.cos(angle), y = Mth.sin(angle);
        float middle = start + length * .26F;
        vertex(out, m, x*start, y*start, 0xFFF4BC, alpha);
        vertex(out, m, x*middle-y*width, y*middle+x*width, 0xFFAA28, alpha);
        vertex(out, m, x*(start+length), y*(start+length), 0xFF750C, .5F*alpha);
        vertex(out, m, x*middle+y*width, y*middle-x*width, 0xFFAA28, alpha);
    }

    private static void vertex(VertexConsumer out, Matrix4f m, float x, float y, int rgb, float alpha) {
        out.vertex(m, x, y, 0).color(rgb >>> 16, (rgb >>> 8) & 255, rgb & 255, (int)(255 * alpha)).endVertex();
    }
}
