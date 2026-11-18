package dev.starcrown;

import java.io.IOException;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterNamedRenderTypesEvent;
import net.minecraftforge.client.event.RegisterShadersEvent;
import net.minecraftforge.client.event.TextureStitchEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** The two gold wings use the existing item draw, atlas and world lightmap. */
@Mod.EventBusSubscriber(modid = StarcrownDoll.ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public abstract class ExcaliburGold extends RenderType {
    private static final ResourceLocation FINISH = new ResourceLocation(StarcrownDoll.ID, "excalibur_gold");
    private static final ResourceLocation SPRITE = new ResourceLocation(StarcrownDoll.ID, "item/excalibur_sword_atlas");
    private static ShaderInstance shader;
    private static float left, top, right, bottom;

    private static final RenderType ITEM = create("excalibur_gold", DefaultVertexFormat.NEW_ENTITY,
            VertexFormat.Mode.QUADS, 4096, true, false, CompositeState.builder()
                    .setShaderState(new ShaderStateShard(ExcaliburGold::finish))
                    .setTextureState(BLOCK_SHEET_MIPPED).setLightmapState(LIGHTMAP).setOverlayState(OVERLAY)
                    .createCompositeState(true));

    private ExcaliburGold(String name, VertexFormat format, VertexFormat.Mode mode, int size,
                          boolean crumbling, boolean sort, Runnable begin, Runnable end) {
        super(name, format, mode, size, crumbling, sort, begin, end);
    }

    @SubscribeEvent
    public static void types(RegisterNamedRenderTypesEvent event) {
        event.register("excalibur_gold", RenderType.solid(), ITEM);
    }

    @SubscribeEvent
    public static void programs(RegisterShadersEvent event) throws IOException {
        event.registerShader(new ShaderInstance(event.getResourceProvider(), FINISH, DefaultVertexFormat.NEW_ENTITY),
                loaded -> shader = loaded);
    }

    @SubscribeEvent
    public static void atlas(TextureStitchEvent.Post event) {
        var atlas = event.getAtlas();
        if (!TextureAtlas.LOCATION_BLOCKS.equals(atlas.location())) return;
        var sprite = atlas.getSprite(SPRITE);
        left = sprite.getU(225.0 * 16 / 512);
        right = sprite.getU(508.0 * 16 / 512);
        top = sprite.getV(8.0 * 16 / 1024);
        bottom = sprite.getV(167.0 * 16 / 1024);
    }

    private static ShaderInstance finish() {
        if (shader == null) return GameRenderer.getRendertypeEntityCutoutShader();
        shader.safeGetUniform("GuardUV").set(left, top, right, bottom);
        return shader;
    }
}
