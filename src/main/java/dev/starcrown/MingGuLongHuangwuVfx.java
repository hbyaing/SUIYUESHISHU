package dev.starcrown;

import com.google.gson.*;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import java.io.*;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.zip.GZIPInputStream;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceProvider;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterShadersEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.opengl.*;

/** User-approved GamePrep meshes, textures and GPU density field, driven by server skill age. */
@Mod.EventBusSubscriber(modid=StarcrownDoll.ID,value=Dist.CLIENT)
public final class MingGuLongHuangwuVfx {
    private static ShaderInstance shader,composite;
    private static final List<Part> parts=new ArrayList<>();
    private static RenderTarget depthCopy,volumeTarget;
    private static boolean offscreenFailed;
    private static final ResourceLocation SPEC=id("vfx/huangwu.json"),MESH=id("vfx/huangwu-mesh.bin.gz");
    private static boolean loaded;
    // Keep charge and projectile unchanged. The landed field is sized from
    // its visible density support (about 2.73 authored units), not the larger
    // transparent proxy bounds which made the previous black hole too small.
    private static final float LANDING_DIAMETER = 5.0F;
    private static final float LANDING_SCALE = LANDING_DIAMETER / 2.73F;
    private static final float[] STAGE_SCALE = {3.8567253F, 6.249984F, LANDING_SCALE};
    // Authored ground vortex is at y=.72/16, while the core anchor is .88.
    // Scale about the ground contact so enlargement cannot bury the vortex.
    private static float landingGroundLift(float scale){return (.88F-.72F/16F)*scale;}
    private static ResourceLocation id(String path){return new ResourceLocation(StarcrownDoll.ID,path);}

    public static void registerShaders(RegisterShadersEvent event) {
        // Reload must not keep a program or buffers belonging to the previous resource set.
        shader=composite=null;for(var part:parts)part.buffer.close();parts.clear();loaded=false;
        releaseTargets();offscreenFailed=false;
        try {
            event.registerShader(new ShaderInstance(event.getResourceProvider(),id("huangwu"),DefaultVertexFormat.POSITION_TEX_COLOR_NORMAL),value->{
                // Resource reload pays parsing, VBO upload and texture upload once, before a skill can appear.
                try(VfxRenderState ignored=new VfxRenderState()) {
                    load(event.getResourceProvider());
                    for(var part:parts)Minecraft.getInstance().getTextureManager().getTexture(part.texture);
                    shader=value;
                }catch(IOException|RuntimeException error){
                    for(var part:parts)part.buffer.close();parts.clear();loaded=false;shader=null;
                    com.mojang.logging.LogUtils.getLogger().warn("Huangwu preload failed; using geometry fallback until resource reload",error);
                }
            });
        } catch(IOException | RuntimeException error) {
            com.mojang.logging.LogUtils.getLogger().warn("Huangwu shader unavailable; using vanilla geometry VFX. Gameplay remains enabled.",error);
        }
        try {
            event.registerShader(new ShaderInstance(event.getResourceProvider(),id("huangwu_composite"),DefaultVertexFormat.POSITION_TEX),value->composite=value);
        }catch(IOException|RuntimeException error){
            offscreenFailed=true;
            com.mojang.logging.LogUtils.getLogger().warn("Huangwu reconstruction unavailable; retaining native volume rendering",error);
        }
    }

    private static void releaseTargets(){
        if(depthCopy!=null)depthCopy.destroyBuffers();if(volumeTarget!=null)volumeTarget.destroyBuffers();
        depthCopy=volumeTarget=null;
    }
    @SubscribeEvent public static void frame(net.minecraftforge.event.TickEvent.RenderTickEvent event){
        if(event.phase==net.minecraftforge.event.TickEvent.Phase.START&&Minecraft.getInstance().level==null&&depthCopy!=null)releaseTargets();
    }
    @SubscribeEvent public static void disconnect(net.minecraftforge.client.event.ClientPlayerNetworkEvent.LoggingOut event){
        // Menu frames need not dispatch RenderTickEvent; disconnect is the authoritative cleanup point.
        if(RenderSystem.isOnRenderThread())releaseTargets();else RenderSystem.recordRenderCall(MingGuLongHuangwuVfx::releaseTargets);
    }

    private record Part(int stage,int kind,boolean volume,float bound,ResourceLocation texture,
                        Vector3f tint,float[][] position,float[][] rotation,float[][] scale,VertexBuffer buffer,Vector3f lo,Vector3f hi) {
        Matrix4f matrix(float time){
            Vector3f p=sample(position,time,0),r=sample(rotation,time,0),s=sample(scale,time,1);
            return new Matrix4f().translation(p.div(16)).rotateZ(r.z*Mth.DEG_TO_RAD)
                .rotateY(r.y*Mth.DEG_TO_RAD).rotateX(r.x*Mth.DEG_TO_RAD).scale(s);
        }
        boolean visible(float time){Vector3f s=sample(scale,time,1);return Math.max(Math.abs(s.x),Math.max(Math.abs(s.y),Math.abs(s.z)))>1e-5;}
    }
    private static float[][] channel(JsonObject entry,String key){
        JsonArray a=entry.getAsJsonObject("channels").getAsJsonArray(key);float[][] result=new float[a.size()][4];
        for(int i=0;i<a.size();i++)for(int j=0;j<4;j++)result[i][j]=a.get(i).getAsJsonArray().get(j).getAsFloat();return result;
    }
    private static Vector3f sample(float[][] keys,float time,float fallback){
        if(keys.length==0)return new Vector3f(fallback);
        int lo=0,hi=keys.length;while(lo<hi){int mid=(lo+hi)>>>1;if(keys[mid][0]<=time)lo=mid+1;else hi=mid;}
        int a=Math.max(0,lo-1),b=Math.min(keys.length-1,lo);float[] x=keys[a],y=keys[b];
        float t=a==b?0:Mth.clamp((time-x[0])/(y[0]-x[0]),0,1);
        return new Vector3f(Mth.lerp(t,x[1],y[1]),Mth.lerp(t,x[2],y[2]),Mth.lerp(t,x[3],y[3]));
    }
    private static void load(ResourceProvider resources) throws IOException {
        if(loaded)return;
        JsonObject spec;try(var reader=resources.openAsReader(SPEC)){spec=JsonParser.parseReader(reader).getAsJsonObject();}
        try(var stream=new DataInputStream(new GZIPInputStream(resources.open(MESH)))) {
            for(var node:spec.getAsJsonArray("objects")) {
                JsonObject entry=node.getAsJsonObject();int count=entry.get("count").getAsInt();
                var builder=new BufferBuilder(count*32);builder.begin(VertexFormat.Mode.TRIANGLES,DefaultVertexFormat.POSITION_TEX_COLOR_NORMAL);
                Vector3f lo=new Vector3f(Float.POSITIVE_INFINITY),hi=new Vector3f(Float.NEGATIVE_INFINITY);
                for(int i=0;i<count;i++) {
                    float x=stream.readFloat(),y=stream.readFloat(),z=stream.readFloat(),u=stream.readFloat(),v=stream.readFloat();
                    float nx=stream.readFloat(),ny=stream.readFloat(),nz=stream.readFloat();
                    lo.min(new Vector3f(x,y,z));hi.max(new Vector3f(x,y,z));
                    builder.vertex(x,y,z).uv(u,v).color(255,255,255,255).normal(nx,ny,nz).endVertex();
                }
                var buffer=new VertexBuffer(VertexBuffer.Usage.STATIC);buffer.bind();buffer.upload(builder.end());VertexBuffer.unbind();
                JsonArray tint=entry.getAsJsonArray("tint");
                parts.add(new Part(entry.get("stage").getAsInt(),entry.get("kind").getAsInt(),entry.get("volume").getAsBoolean(),entry.get("bound").getAsFloat(),id("textures/vfx/huangwu/"+entry.get("texture").getAsString()),new Vector3f(tint.get(0).getAsFloat(),tint.get(1).getAsFloat(),tint.get(2).getAsFloat()),channel(entry,"position"),channel(entry,"rotation"),channel(entry,"scale"),buffer,lo,hi));
            }
        }
        loaded=true;
    }
    private record Draw(Part part,Matrix4f matrix,float time,float fieldTime,float distance) {}

    @SubscribeEvent public static void render(RenderLevelStageEvent event) {
        if(event.getStage()!=RenderLevelStageEvent.Stage.AFTER_PARTICLES)return;
        if(shader==null){MingGuLongHuangwuFallback.render(event);return;}
        var mc=Minecraft.getInstance();if(mc.level==null)return;
        Vec3 camera=event.getCamera().getPosition();float partial=event.getPartialTick();
        List<Draw> draws=new ArrayList<>();
            for(Entity entity:mc.level.entitiesForRendering()) {
                if(entity.distanceToSqr(camera)>128*128)continue;
                int stage;float time,fieldTime;Vector3f offset=new Vector3f();float yaw;
                if(entity instanceof MingGuLongEntity dragon&&dragon.getAnimationKey()==13) {
                    time=dragon.getHuangwuAge(partial)/20f;if(time>=1.8f)continue;
                    stage=0;fieldTime=time*(.84f/1.8f);yaw=Mth.rotLerp(partial,dragon.yBodyRotO,dragon.yBodyRot);
                } else if(entity instanceof MingGuLongProjectile projectile) {
                    stage=1;time=1.8f;fieldTime=(projectile.getVisualAge()+partial)/20f+.84f;yaw=entity.getYRot();
                    // The projectile meshes carry the source charge-anchor translation.
                    offset.set(-5.11132431f/16f,-6.826404572f/16f,19.981182098f/16f);
                } else if(entity instanceof MingGuLongVortex vortex) {
                    stage=2;float age=(vortex.getVisualAge()+partial)/20f;
                    time=age<.6f?2.1f+age:age<10?2.7f:3.33f+age-10;
                    fieldTime=age+1.14f;yaw=entity.getYRot();offset.set(0,-.88f,6.2f);
                } else continue;
                Vec3 pos=new Vec3(Mth.lerp(partial,entity.xo,entity.getX()),Mth.lerp(partial,entity.yo,entity.getY()),Mth.lerp(partial,entity.zo,entity.getZ()));
                // Gecko's native BB geometry reflects local X. Apply the same
                // transform to the original VFX geometry and its anchor curves.
                Matrix4f base=new Matrix4f(event.getPoseStack().last().pose()).translate((float)(pos.x-camera.x),(float)(pos.y-camera.y)+.01f,(float)(pos.z-camera.z)).rotateY((180-yaw)*Mth.DEG_TO_RAD).scale(-1,1,1);
                if(stage==0){
                    Vector3f anchor=new Vector3f(5.11132431f/16f,6.826404572f/16f,-19.981182098f/16f);
                    base.translate(anchor).scale(STAGE_SCALE[stage]).translate(new Vector3f(anchor).negate());
                }else {
                    if(stage==2)base.translate(0,landingGroundLift(STAGE_SCALE[stage]),0);
                    base.scale(STAGE_SCALE[stage]).translate(offset);
                }
                for(var part:parts)if(part.stage==stage&&part.visible(time)) {
                    Matrix4f matrix=new Matrix4f(base).mul(part.matrix(time));
                    Vector3f center=matrix.transformPosition(new Vector3f());
                    draws.add(new Draw(part,matrix,time,fieldTime,center.lengthSquared()));
                }
            }
            if(!draws.isEmpty())drawAll(draws,event.getProjectionMatrix());
    }

    private static void drawAll(List<Draw> draws,Matrix4f projection) {
        var target=Minecraft.getInstance().getMainRenderTarget();
        var visible=new ArrayList<Draw>();var coverage=new IdentityHashMap<Draw,HuangwuScreenBounds>();
        for(var draw:draws){
            var bounds=HuangwuScreenBounds.project(draw.part.lo,draw.part.hi,draw.matrix,projection,target.width,target.height);
            if(bounds!=null){visible.add(draw);coverage.put(draw,bounds);}
        }
        if(visible.isEmpty())return;
        draws=visible;
        Matrix4f inverseProjection=new Matrix4f(projection).invert();
        // Reuse the main target only. A foreign framebuffer must not be sampled or overwritten.
        if(GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING)!=target.frameBufferId)return;
        try(VfxRenderState ignored=new VfxRenderState()) {
            GL11.glFrontFace(GL11.GL_CW);RenderSystem.disableScissor();
            RenderSystem.colorMask(true,true,true,true);RenderSystem.setShaderColor(1,1,1,1);
            RenderSystem.disableCull();RenderSystem.enableDepthTest();RenderSystem.depthFunc(GL11.GL_LEQUAL);
            RenderSystem.depthMask(true);RenderSystem.disableBlend();
            for(Draw draw:draws)if(draw.part.kind==7)draw(draw,projection,inverseProjection,false,target.width,target.height);
            boolean volumes=draws.stream().anyMatch(d->d.part.volume);
            if(volumes) {
                if(depthCopy==null)depthCopy=new TextureTarget(target.width,target.height,true,Minecraft.ON_OSX);
                if(depthCopy.width!=target.width||depthCopy.height!=target.height)depthCopy.resize(target.width,target.height,Minecraft.ON_OSX);
                depthCopy.copyDepthFrom(target);target.bindWrite(true);
            }
            RenderSystem.depthMask(false);RenderSystem.enableBlend();RenderSystem.defaultBlendFunc();
            draws.sort(Comparator.comparingDouble((Draw d)->d.distance).reversed());
            double resolution=VfxClientConfig.HUANGWU_RESOLUTION.get();
            for(Draw draw:draws)if(draw.part.kind!=7) {
                var bounds=coverage.get(draw);
                if(draw.part.volume) {
                    RenderSystem.disableDepthTest();
                    if(resolution<.999&&composite!=null&&!offscreenFailed&&((long)bounds.width()*bounds.height())>=4096) {
                        boolean ready=true;
                        try{ensureVolumeTarget(target,resolution);}
                        catch(RuntimeException error){
                            offscreenFailed=true;ready=false;target.bindWrite(true);
                            com.mojang.logging.LogUtils.getLogger().warn("Huangwu reduced target unavailable; retaining native rendering until reload",error);
                        }
                        if(ready){volume(draw,projection,inverseProjection,target,bounds);continue;}
                    }
                }else RenderSystem.enableDepthTest();
                RenderSystem.enableScissor(bounds.x(),bounds.y(),bounds.width(),bounds.height());
                draw(draw,projection,inverseProjection,volumes,target.width,target.height);
            }
        }
    }
    private static void ensureVolumeTarget(RenderTarget main,double resolution){
        int width=Math.max(1,(int)Math.ceil(main.width*resolution)),height=Math.max(1,(int)Math.ceil(main.height*resolution));
        if(volumeTarget!=null&&volumeTarget.width==width&&volumeTarget.height==height)return;
        if(volumeTarget!=null){volumeTarget.destroyBuffers();volumeTarget=null;}
        volumeTarget=new TextureTarget(width,height,false,Minecraft.ON_OSX);
        GlStateManager._bindTexture(volumeTarget.getColorTextureId());
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D,0,GL30.GL_RGBA16F,width,height,0,GL11.GL_RGBA,GL11.GL_FLOAT,(ByteBuffer)null);
        volumeTarget.setFilterMode(GL11.GL_NEAREST);volumeTarget.bindWrite(true);
        if(GL30.glCheckFramebufferStatus(GL30.GL_DRAW_FRAMEBUFFER)!=GL30.GL_FRAMEBUFFER_COMPLETE)
            throw new IllegalStateException("Incomplete Huangwu volume framebuffer");
    }
    private static void volume(Draw draw,Matrix4f projection,Matrix4f inverseProjection,RenderTarget main,HuangwuScreenBounds bounds){
        // RenderTarget allocation/resize enables depth testing internally.
        // Both passes use the independent scene-depth texture, including the first frame.
        RenderSystem.disableDepthTest();RenderSystem.depthMask(false);
        // Clear the entire reusable target, including taps just outside the projected proxy.
        RenderSystem.disableScissor();volumeTarget.bindWrite(true);
        RenderSystem.clearColor(0,0,0,0);RenderSystem.clear(GL11.GL_COLOR_BUFFER_BIT,Minecraft.ON_OSX);
        var small=bounds.scaled(main.width,main.height,volumeTarget.width,volumeTarget.height);
        RenderSystem.enableScissor(small.x(),small.y(),small.width(),small.height());
        // Standard alpha blending over clear stores premultiplied RGB in the floating point target.
        RenderSystem.enableBlend();RenderSystem.defaultBlendFunc();
        draw(draw,projection,inverseProjection,true,volumeTarget.width,volumeTarget.height);
        main.bindWrite(true);RenderSystem.enableScissor(bounds.x(),bounds.y(),bounds.width(),bounds.height());
        composite.safeGetUniform("InverseProjectionMat").set(inverseProjection);
        composite.safeGetUniform("NativeSize").set((float)main.width,(float)main.height);
        composite.safeGetUniform("VolumeSize").set((float)volumeTarget.width,(float)volumeTarget.height);
        RenderSystem.setShader(()->composite);
        RenderSystem.setShaderTexture(0,volumeTarget.getColorTextureId());RenderSystem.setShaderTexture(1,depthCopy.getDepthTextureId());
        var builder=Tesselator.getInstance().getBuilder();builder.begin(VertexFormat.Mode.TRIANGLES,DefaultVertexFormat.POSITION_TEX);
        builder.vertex(-1,-1,0).uv(0,0).endVertex();builder.vertex(3,-1,0).uv(2,0).endVertex();builder.vertex(-1,3,0).uv(0,2).endVertex();
        BufferUploader.drawWithShader(builder.end());
    }
    private static void draw(Draw draw,Matrix4f projection,Matrix4f inverseProjection,boolean sceneDepth,int width,int height) {
        Part part=draw.part;Matrix4f inverse=new Matrix4f(draw.matrix).invert();
        Vector3f point=inverse.transformPosition(new Vector3f()),direction=inverse.transformDirection(new Vector3f(0,0,-1)).normalize();
        shader.safeGetUniform("uCamera").set(-point.x,point.z,point.y);
        shader.safeGetUniform("uRayDirection").set(-direction.x,direction.z,direction.y);
        shader.safeGetUniform("uOrthographic").set(Math.abs(projection.m33()-1)<1e-4f?1f:0f);
        shader.safeGetUniform("uTime").set(draw.fieldTime);shader.safeGetUniform("uKind").set((float)part.kind);
        shader.safeGetUniform("uBound").set(part.bound);shader.safeGetUniform("uHasMap").set(1f);
        shader.safeGetUniform("uTint").set(part.tint.x,part.tint.y,part.tint.z);
        shader.safeGetUniform("uUseDepth").set(sceneDepth?1f:0f);
        shader.safeGetUniform("InverseModelViewMat").set(inverse);
        shader.safeGetUniform("InverseProjectionMat").set(inverseProjection);
        // ScreenSize is reserved: VertexBuffer overwrites it with the WINDOW size.
        shader.safeGetUniform("IntegrationSize").set((float)width,(float)height);
        // VertexBuffer.drawWithShader populates Sampler0 from RenderSystem's
        // texture slot; setting ShaderInstance's sampler alone is overwritten.
        RenderSystem.setShaderTexture(0,part.texture);
        if(sceneDepth)shader.setSampler("SceneDepth",depthCopy.getDepthTextureId());
        part.buffer.bind();part.buffer.drawWithShader(draw.matrix,projection,shader);
    }
}
