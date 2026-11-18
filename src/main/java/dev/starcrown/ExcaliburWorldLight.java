package dev.starcrown;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.io.IOException;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterShadersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = StarcrownDoll.ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public abstract class ExcaliburWorldLight extends RenderType {
    private static final ResourceLocation PROGRAM = new ResourceLocation(StarcrownDoll.ID, "excalibur_world_light");
    private static final TransparencyStateShard WORLD_LIGHT_BLEND = new TransparencyStateShard("excalibur_world_light",
            () -> {
                RenderSystem.enableBlend();
                RenderSystem.blendFunc(GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
            },
            () -> {
                RenderSystem.disableBlend();
                RenderSystem.defaultBlendFunc();
            });
    private static ShaderInstance shader;

    private ExcaliburWorldLight(String name, VertexFormat format, VertexFormat.Mode mode, int size,
                                boolean crumbling, boolean sort, Runnable begin, Runnable end) {
        super(name, format, mode, size, crumbling, sort, begin, end);
    }

    static RenderType material(ResourceLocation atlas) {
        // AFTER_PARTICLES runs inside the Fabulous particles target. Add directly to the lit scene,
        // then restore the enclosing target before Forge resumes the particles pass.
        var scene = new OutputStateShard("excalibur_scene", PARTICLES_TARGET::clearRenderState,
                PARTICLES_TARGET::setupRenderState);
        return create("excalibur_world_light", DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS,
                32768, false, false, CompositeState.builder()
                        .setShaderState(new ShaderStateShard(() -> shader))
                        .setTextureState(new TextureStateShard(atlas, true, false))
                        .setTransparencyState(WORLD_LIGHT_BLEND)
                        .setDepthTestState(LEQUAL_DEPTH_TEST).setWriteMaskState(COLOR_WRITE)
                        .setCullState(NO_CULL).setOutputState(scene).createCompositeState(false));
    }

    @SubscribeEvent
    public static void registerShader(RegisterShadersEvent event) throws IOException {
        event.registerShader(new ShaderInstance(event.getResourceProvider(), PROGRAM, DefaultVertexFormat.NEW_ENTITY),
                loaded -> shader = loaded);
    }
}
