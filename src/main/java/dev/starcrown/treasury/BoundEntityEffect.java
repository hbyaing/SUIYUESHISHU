package dev.starcrown.treasury;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.starcrown.StarcrownDoll;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterShadersEvent;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.core.animatable.GeoAnimatable;
import software.bernie.geckolib.event.GeoRenderEvent;
import software.bernie.geckolib.renderer.GeoRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;

/** Reuses the posed body's vertices, normals and cutout texture for the binding material. */
@Mod.EventBusSubscriber(modid=StarcrownDoll.ID,value=Dist.CLIENT)
public final class BoundEntityEffect extends RenderType {
    private static final Map<ResourceLocation,RenderType> MATERIALS = new HashMap<>();
    private static final Set<LivingEntityRenderer<?,?>> ATTACHED = Collections.newSetFromMap(new WeakHashMap<>());
    private record Frame(Matrix4f toEntity, TreasuryClient.BindingSkin skin) {}
    private static final Map<LivingEntity,Frame> FRAMES = new WeakHashMap<>();
    private static ShaderInstance shader;

    private BoundEntityEffect() {
        super("unused",DefaultVertexFormat.NEW_ENTITY,VertexFormat.Mode.QUADS,256,false,false,()->{},()->{});
    }

    private static RenderType material(ResourceLocation texture) {
        return MATERIALS.computeIfAbsent(texture,key -> create("starcrown_heaven_binding",DefaultVertexFormat.NEW_ENTITY,
                VertexFormat.Mode.QUADS,1536,false,false,CompositeState.builder()
                        .setShaderState(new ShaderStateShard(()->shader))
                        .setTextureState(new TextureStateShard(key,false,false))
                        .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                        .setDepthTestState(LEQUAL_DEPTH_TEST)
                        .setLayeringState(POLYGON_OFFSET_LAYERING)
                        .setWriteMaskState(COLOR_WRITE).setCullState(NO_CULL)
                        .createCompositeState(false)));
    }

    @Mod.EventBusSubscriber(modid=StarcrownDoll.ID,value=Dist.CLIENT,bus=Mod.EventBusSubscriber.Bus.MOD)
    public static final class Resources {
        @SubscribeEvent public static void shaders(RegisterShadersEvent event) throws IOException {
            MATERIALS.clear();
            event.registerShader(new ShaderInstance(event.getResourceProvider(),
                    new ResourceLocation(StarcrownDoll.ID,"heaven_binding"),DefaultVertexFormat.NEW_ENTITY),s->shader=s);
        }
    }

    @SubscribeEvent public static <T extends LivingEntity,M extends EntityModel<T>> void before(RenderLivingEvent.Pre<T,M> event) {
        var renderer=event.getRenderer();
        if (capture(event.getEntity(),event.getPoseStack(),event.getPartialTick()) && !ATTACHED.contains(renderer)) {
            renderer.addLayer(new ModelBinding<>(renderer));
            ATTACHED.add(renderer);
        }
    }

    @SubscribeEvent public static void geoBefore(GeoRenderEvent.Entity.Pre event) {
        if (event.getEntity() instanceof LivingEntity living) capture(living, event.getPoseStack(), event.getPartialTick());
    }

    private static boolean capture(LivingEntity entity, PoseStack pose, float partial) {
        var skin = TreasuryClient.bindingSkin(entity, partial);
        if (skin.strength() <= 0 || shader == null || entity.isInvisible()) {
            FRAMES.remove(entity);
            return false;
        }
        // The entity origin is still at its feet, before body yaw, limb animation and model-specific pivots.
        // Gecko's pre-render callback can already contain the model scale. Keep the coordinates in world units.
        Matrix4f toEntity = new Matrix4f(pose.last().pose()).normalize3x3().invert();
        FRAMES.put(entity, new Frame(toEntity, skin));
        return true;
    }

    private static final class ModelBinding<T extends LivingEntity,M extends EntityModel<T>> extends RenderLayer<T,M> {
        ModelBinding(LivingEntityRenderer<T,M> parent) { super(parent); }

        @Override public void render(PoseStack pose,MultiBufferSource buffers,int light,T entity,float walk,float stride,
                                     float partial,float age,float yaw,float pitch) {
            Frame frame=FRAMES.remove(entity);
            if (frame==null) return;
            var out=buffers.getBuffer(material(getTextureLocation(entity)));
            getParentModel().renderToBuffer(pose,new BodySpace(out,frame,entity),light,OverlayTexture.NO_OVERLAY,1,1,1,1);
        }
    }

    @SubscribeEvent public static void geoLayers(GeoRenderEvent.Entity.CompileRenderLayers event) {
        event.addLayer(new GeoBinding<>(event.getRenderer()));
    }

    private static final class GeoBinding<T extends GeoAnimatable> extends GeoRenderLayer<T> {
        GeoBinding(GeoRenderer<T> parent) { super(parent); }

        @Override public void render(PoseStack pose,T animatable,BakedGeoModel model,RenderType base,
                                     MultiBufferSource buffers,VertexConsumer original,float partial,int light,int overlay) {
            if (!(animatable instanceof LivingEntity entity)) return;
            Frame frame=FRAMES.remove(entity);
            if (frame==null) return;
            RenderType type=material(getTextureResource(animatable));
            VertexConsumer out=new BodySpace(buffers.getBuffer(type),frame,entity);
            getRenderer().reRender(model,pose,buffers,animatable,type,out,partial,light,overlay,1,1,1,1);
        }
    }

    // Store posed body-space coordinates in the two unused short UV channels. Every vertex carries
    // its own parameters, so two bound entities can safely share the same deferred render batch.
    private static final class BodySpace implements VertexConsumer {
        private final VertexConsumer out;
        private final Matrix4f toBody;
        private final float width,height;
        private final float opacity;
        private final int spread,pulse,contactHeight,contactTurn;
        private final Vector3f local=new Vector3f();

        BodySpace(VertexConsumer out,Frame frame,LivingEntity entity) {
            this.out=out;
            toBody=frame.toEntity;
            var skin=frame.skin;
            opacity=skin.opacity();
            spread=Math.round(255*skin.spread());
            pulse=Math.round(255*skin.pulse());
            contactHeight=Math.round(255*skin.contactHeight());
            contactTurn=pack(skin.contactTurn());
            width=Math.max(.25f,entity.getBbWidth());
            height=Math.max(.35f,entity.getBbHeight());
        }

        private static int pack(float value) {
            return Math.round(Mth.clamp(value,-7.99f,7.99f)*2048)+16384;
        }

        @Override public VertexConsumer vertex(double x,double y,double z) {
            local.set((float)x,(float)y,(float)z).mulPosition(toBody);
            out.vertex(x,y,z);
            return this;
        }

        @Override public VertexConsumer color(int r,int g,int b,int a) { out.color(spread,pulse,contactHeight,Math.round(a*opacity));return this; }
        @Override public VertexConsumer uv(float u,float v) { out.uv(u,v);return this; }
        @Override public VertexConsumer overlayCoords(int u,int v) {
            out.overlayCoords(pack(local.x/width),pack(local.y/height));return this;
        }
        @Override public VertexConsumer uv2(int u,int v) { out.uv2(pack(local.z/width),contactTurn);return this; }
        @Override public VertexConsumer normal(float x,float y,float z) { out.normal(x,y,z);return this; }
        @Override public void endVertex() { out.endVertex(); }
        @Override public void defaultColor(int r,int g,int b,int a) { out.defaultColor(spread,pulse,contactHeight,Math.round(a*opacity)); }
        @Override public void unsetDefaultColor() { out.unsetDefaultColor(); }
    }
}
