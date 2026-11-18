package dev.starcrown;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

@Mod.EventBusSubscriber(modid=StarcrownDoll.ID,bus=Mod.EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
public final class EnkiduClient {
    @SubscribeEvent public static void renderers(EntityRenderersEvent.RegisterRenderers event){
        event.registerEntityRenderer(StarcrownDoll.ENKIDU.get(),Renderer::new);
    }
    private static final class Renderer extends GeoEntityRenderer<EnkiduCompanion> {
        Renderer(EntityRendererProvider.Context context){
            super(context,new Model());
            shadowRadius=.28F;
            scaleWidth=.40F;
            scaleHeight=.40F;
        }
        @Override public boolean shouldShowName(EnkiduCompanion entity){return false;}
    }
    private static final class Model extends GeoModel<EnkiduCompanion> {
        private static final ResourceLocation GEO=new ResourceLocation(StarcrownDoll.ID,"geo/enkidu.geo.json");
        private static final ResourceLocation ANIMATION=new ResourceLocation(StarcrownDoll.ID,"animations/enkidu.animation.json");
        private static final ResourceLocation TEXTURE=new ResourceLocation(StarcrownDoll.ID,"textures/entity/enkidu.png");
        @Override public ResourceLocation getModelResource(EnkiduCompanion pet){return GEO;}
        @Override public ResourceLocation getAnimationResource(EnkiduCompanion pet){return ANIMATION;}
        @Override public ResourceLocation getTextureResource(EnkiduCompanion pet){return TEXTURE;}
    }
}
