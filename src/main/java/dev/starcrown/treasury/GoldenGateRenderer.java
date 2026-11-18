package dev.starcrown.treasury;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.starcrown.StarcrownDoll;
import it.unimi.dsi.fastutil.HashCommon;
import java.io.IOException;
import java.util.List;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterShadersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

/** Batches weapon gate surfaces from every active cast into one draw per render pass. */
@Mod.EventBusSubscriber(modid=StarcrownDoll.ID, value=Dist.CLIENT, bus=Mod.EventBusSubscriber.Bus.MOD)
public final class GoldenGateRenderer extends RenderType {
    private static final Vec3 UP = new Vec3(0, 1, 0);
    private static final Vec3 SOUTH = new Vec3(0, 0, 1);
    private static final float EXTENT = 1.32f;
    // The shader starts a newborn refill with a small, already visible liquid ridge.
    private static final int REFILL_OPENING = 255;
    private static final ResourceLocation NOISE = new ResourceLocation(StarcrownDoll.ID, "textures/treasury/liquid_noise.png");
    private static final MultiBufferSource.BufferSource BUFFER = MultiBufferSource.immediate(new BufferBuilder(16384));
    private static ShaderInstance shader;
    enum Pass { BODY, HALO, SORTED_HALO }
    private static final RenderType BODY = layer(Pass.BODY);
    private static final RenderType HALO = layer(Pass.HALO);
    private static final RenderType SORTED_HALO = layer(Pass.SORTED_HALO);

    private static RenderType layer(Pass pass) {
        boolean opaque = pass == Pass.BODY;
        boolean sortedTarget = pass == Pass.SORTED_HALO;
        return create("starcrown_treasury_gate_" + pass.name().toLowerCase(java.util.Locale.ROOT),
                DefaultVertexFormat.POSITION_COLOR_TEX, VertexFormat.Mode.QUADS, 16384, false, !opaque,
                CompositeState.builder().setShaderState(new ShaderStateShard(() -> shader))
                        .setTextureState(new TextureStateShard(NOISE, true, false))
                        .setTransparencyState(opaque ? NO_TRANSPARENCY : TRANSLUCENT_TRANSPARENCY)
                        .setCullState(NO_CULL).setDepthTestState(LEQUAL_DEPTH_TEST)
                        // Fabulous sorts its separate cloud/glass/particle targets using their depth.
                        .setWriteMaskState(opaque || sortedTarget ? COLOR_DEPTH_WRITE : COLOR_WRITE)
                        .setOutputState(sortedTarget ? PARTICLES_TARGET : MAIN_TARGET).createCompositeState(false));
    }

    private static RenderType type(Pass pass) {
        return switch (pass) { case BODY -> BODY; case HALO -> HALO; case SORTED_HALO -> SORTED_HALO; };
    }

    private GoldenGateRenderer() {
        super("unused", DefaultVertexFormat.POSITION_COLOR_TEX, VertexFormat.Mode.QUADS, 256, false, false, ()->{}, ()->{});
    }

    @SubscribeEvent
    public static void register(RegisterShadersEvent event) throws IOException {
        event.registerShader(new ShaderInstance(event.getResourceProvider(),
                new ResourceLocation(StarcrownDoll.ID, "treasury_gate"), DefaultVertexFormat.POSITION_COLOR_TEX),
                loaded -> shader = loaded);
    }

    static void drawWeapons(PoseStack pose, Vec3 eye, Iterable<TreasuryClient.Visual> visuals, long now,
                            float partial, Pass pass) {
        if (shader == null) return;
        RenderType type = type(pass);
        VertexConsumer out = null;
        for (var visual : visuals) {
            var cast = visual.cast();
            long ticks = now - cast.start();
            if (cast.chains() || ticks < 0 || visual.expired(now)) continue;
            Vec3 heading = new Vec3(cast.forward().x, 0, cast.forward().z).normalize();
            if (heading.lengthSqr() < .5) heading = SOUTH;
            Vec3 right = heading.cross(UP);
            Vec3 origin = cast.origin().subtract(eye);
            var gates = visual.gates();
            for (int i = 0; i < gates.size(); i++) {
                int launchTick = cast.launchAge(i);
                if (ticks >= launchTick + TreasuryVolley.CLOSE_END_AFTER_LAUNCH) continue;
                if (out == null) out = begin(partial, 1f, pass, type, false);
                TreasuryLayout.Gate gate = gates.get(i);
                Vec3 center = origin.add(right.scale(gate.x())).add(0, gate.y(), 0).add(heading.scale(gate.z()));
                long variation = gateVariation(cast.seed(), i);
                int opening = cast.refill() ? REFILL_OPENING : delayTicks(i, variation);
                quad(out, pose.last().pose(), center, right, UP, gate.radius(), (int)ticks, launchTick,
                        opening, (int)(variation >>> 56));
            }
        }
        if (out != null) BUFFER.endBatch(type);
    }

    static int openingDelay(TreasuryNetwork.Cast cast, int index) {
        return cast.refill() ? 0 : delayTicks(index, gateVariation(cast.seed(), index));
    }

    private static long gateVariation(long seed, int index) {
        return HashCommon.murmurHash3(seed + 0x9e3779b97f4a7c15L * (index + 1L));
    }

    private static int delayTicks(int index, long variation) {
        return index == 0 ? 0 : Math.floorMod((int)variation, 5);
    }

    /** Offsets, including the target center, are relative to the pose origin. */
    public static void drawChainGates(PoseStack pose, Vec3 targetOffset, float width, float height,
                                      long seed, float stageAge, float motionAge, Pass pass) {
        float openingAge = stageAge < 65 ? stageAge : motionAge - (stageAge - 65);
        // Keep the mouth open while the links are held, then contract with their 15-tick retraction.
        float opening = smooth(openingAge / 12f) * (1f - smooth((stageAge - 65f) / 15f));
        if (shader == null || opening < .001f) return;
        List<Vec3> anchors = TreasuryChainLayout.anchors(seed, width, height);
        if (anchors.isEmpty()) return;
        float radius = Mth.clamp(.45f + .12f * width + .04f * height, .50f, 1.40f);
        RenderType type = type(pass);
        VertexConsumer out = begin(motionAge, opening, pass, type, true);
        for (int i = 0; i < anchors.size(); i++) {
            Vec3 anchor = anchors.get(i);
            Vec3 normal = anchor.scale(-1).normalize();
            Vec3 right = normal.cross(Math.abs(normal.y) > .98 ? SOUTH : UP).normalize();
            Vec3 up = right.cross(normal).normalize();
            int texturePhase = (int)(gateVariation(seed, i) >>> 56);
            quad(out, pose.last().pose(), targetOffset.add(anchor), right, up, radius, 0, 255, 0, texturePhase);
        }
        BUFFER.endBatch(type);
    }

    private static float smooth(float progress) {
        float t = Mth.clamp(progress, 0f, 1f);
        return t * t * (3f - 2f * t);
    }

    private static VertexConsumer begin(float age, float opening, Pass pass, RenderType type, boolean chainGate) {
        shader.safeGetUniform("ChainGate").set(chainGate ? 1 : 0);
        shader.safeGetUniform("CloseWindow").set((float)TreasuryVolley.CLOSE_START_AFTER_LAUNCH,
                (float)TreasuryVolley.CLOSE_END_AFTER_LAUNCH);
        shader.safeGetUniform("SolidPass").set(pass == Pass.BODY ? 1 : 0);
        shader.safeGetUniform("Time").set(age * .05f);
        shader.safeGetUniform("Opening").set(opening);
        return BUFFER.getBuffer(type);
    }

    private static void quad(VertexConsumer out, Matrix4f pose, Vec3 center, Vec3 right, Vec3 up,
                             float radius, int age, int launchTick, int opening, int texturePhase) {
        Vec3 horizontal = right.scale(radius * EXTENT);
        Vec3 vertical = up.scale(radius * EXTENT);
        vertex(out, pose, center.subtract(horizontal).subtract(vertical), -EXTENT, -EXTENT, age, launchTick, opening, texturePhase);
        vertex(out, pose, center.add(horizontal).subtract(vertical), EXTENT, -EXTENT, age, launchTick, opening, texturePhase);
        vertex(out, pose, center.add(horizontal).add(vertical), EXTENT, EXTENT, age, launchTick, opening, texturePhase);
        vertex(out, pose, center.subtract(horizontal).add(vertical), -EXTENT, EXTENT, age, launchTick, opening, texturePhase);
    }

    private static void vertex(VertexConsumer out, Matrix4f pose, Vec3 p, float u, float v,
                               int age, int launchTick, int opening, int texturePhase) {
        out.vertex(pose, (float)p.x, (float)p.y, (float)p.z)
                .color(age, launchTick, opening, texturePhase).uv(u, v).endVertex();
    }
}
