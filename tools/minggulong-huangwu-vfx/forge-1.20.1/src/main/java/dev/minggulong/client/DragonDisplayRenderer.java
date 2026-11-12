package dev.minggulong.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.minggulong.DragonDisplay;
import dev.minggulong.MingGuLongMod;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = MingGuLongMod.ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class DragonDisplayRenderer extends EntityRenderer<DragonDisplay> {
    private final MingGuLongModel<DragonDisplay> model;
    public DragonDisplayRenderer(EntityRendererProvider.Context context) {
        super(context);
        model = new MingGuLongModel<>(context.bakeLayer(MingGuLongModel.LAYER));
        shadowRadius = .8F;
    }
    @SubscribeEvent public static void layers(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(MingGuLongModel.LAYER, MingGuLongModel::createBodyLayer);
    }
    @SubscribeEvent public static void renderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(MingGuLongMod.DRAGON.get(), DragonDisplayRenderer::new);
    }
    @Override public ResourceLocation getTextureLocation(DragonDisplay entity) { return MingGuLongModel.TEXTURE; }
    @Override public void render(DragonDisplay entity, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(180 - yaw));
        pose.translate(0, 1.5, 0);
        pose.scale(1, -1, 1);
        model.renderToBuffer(pose, buffers.getBuffer(model.renderType(getTextureLocation(entity))), light, OverlayTexture.NO_OVERLAY, 1, 1, 1, 1);
        pose.popPose();
        super.render(entity, yaw, partialTick, pose, buffers, light);
    }
}
