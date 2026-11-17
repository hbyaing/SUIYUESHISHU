package dev.starcrown;

import com.google.gson.*;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import java.io.*;
import java.util.*;
import java.util.zip.GZIPInputStream;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
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
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;

/** User-approved GamePrep meshes, textures and GPU density field, driven by server skill age. */
@Mod.EventBusSubscriber(modid=StarcrownDoll.ID,value=Dist.CLIENT)
public final class MingGuLongHuangwuVfx {
    private static ShaderInstance shader;
    private static final List<Part> parts=new ArrayList<>();
    private static RenderTarget depthCopy;
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
        shader=null;for(var part:parts)part.buffer.close();parts.clear();loaded=false;
        if(depthCopy!=null){depthCopy.destroyBuffers();depthCopy=null;}
        try {
            event.registerShader(new ShaderInstance(event.getResourceProvider(),id("huangwu"),DefaultVertexFormat.POSITION_TEX_COLOR_NORMAL),value->shader=value);
        } catch(IOException | RuntimeException error) {
            com.mojang.logging.LogUtils.getLogger().warn("Huangwu shader unavailable; using vanilla geometry VFX. Gameplay remains enabled.",error);
        }
    }

    private record Part(int stage,int kind,boolean volume,float bound,ResourceLocation texture,
                        Vector3f tint,float[][] position,float[][] rotation,float[][] scale,VertexBuffer buffer) {
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
    private static void load() throws IOException {
        if(loaded)return;var resources=Minecraft.getInstance().getResourceManager();
        JsonObject spec;try(var reader=resources.openAsReader(SPEC)){spec=JsonParser.parseReader(reader).getAsJsonObject();}
        try(var stream=new DataInputStream(new GZIPInputStream(resources.open(MESH)))) {
            for(var node:spec.getAsJsonArray("objects")) {
                JsonObject entry=node.getAsJsonObject();int count=entry.get("count").getAsInt();
                var builder=new BufferBuilder(count*32);builder.begin(VertexFormat.Mode.TRIANGLES,DefaultVertexFormat.POSITION_TEX_COLOR_NORMAL);
                for(int i=0;i<count;i++) {
                    float x=stream.readFloat(),y=stream.readFloat(),z=stream.readFloat(),u=stream.readFloat(),v=stream.readFloat();
                    float nx=stream.readFloat(),ny=stream.readFloat(),nz=stream.readFloat();
                    builder.vertex(x,y,z).uv(u,v).color(255,255,255,255).normal(nx,ny,nz).endVertex();
                }
                var buffer=new VertexBuffer(VertexBuffer.Usage.STATIC);buffer.bind();buffer.upload(builder.end());VertexBuffer.unbind();
                JsonArray tint=entry.getAsJsonArray("tint");
                parts.add(new Part(entry.get("stage").getAsInt(),entry.get("kind").getAsInt(),entry.get("volume").getAsBoolean(),entry.get("bound").getAsFloat(),id("textures/vfx/huangwu/"+entry.get("texture").getAsString()),new Vector3f(tint.get(0).getAsFloat(),tint.get(1).getAsFloat(),tint.get(2).getAsFloat()),channel(entry,"position"),channel(entry,"rotation"),channel(entry,"scale"),buffer));
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
        try {
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
                load();
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
        } catch(IOException error){throw new IllegalStateException("Unable to load Huangwu assets",error);}
    }

    private static void drawAll(List<Draw> draws,Matrix4f projection) {
        var mc=Minecraft.getInstance();var target=mc.getMainRenderTarget();
        boolean depth=GL11.glIsEnabled(GL11.GL_DEPTH_TEST),blend=GL11.glIsEnabled(GL11.GL_BLEND),cull=GL11.glIsEnabled(GL11.GL_CULL_FACE),mask=GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        int src=GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB),dst=GL11.glGetInteger(GL14.GL_BLEND_DST_RGB),srcA=GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA),dstA=GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA);
        var oldShader=RenderSystem.getShader();float[] color=RenderSystem.getShaderColor().clone();
        int oldTexture=RenderSystem.getShaderTexture(0);
        int oldFrontFace=GL11.glGetInteger(GL11.GL_FRONT_FACE);
        try {
            GL11.glFrontFace(GL11.GL_CW);
            RenderSystem.setShaderColor(1,1,1,1);RenderSystem.disableCull();RenderSystem.enableDepthTest();RenderSystem.depthMask(true);
            // Solid dark core first. Volume integration reads an independent copy
            // of actual scene/core depth; it never samples its own render target.
            RenderSystem.disableBlend();
            for(Draw draw:draws)if(draw.part.kind==7)draw(draw,projection,false);
            if(depthCopy==null)depthCopy=new TextureTarget(target.width,target.height,true,Minecraft.ON_OSX);
            if(depthCopy.width!=target.width||depthCopy.height!=target.height)depthCopy.resize(target.width,target.height,Minecraft.ON_OSX);
            depthCopy.copyDepthFrom(target);target.bindWrite(false);
            RenderSystem.depthMask(false);
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            draws.sort(Comparator.comparingDouble((Draw d)->d.distance).reversed());
            for(Draw draw:draws)if(draw.part.kind!=7) {
                // Bounds are only a volume proxy. The fragment shader terminates
                // the ray at the copied terrain/entity/core depth.
                if(draw.part.volume)RenderSystem.disableDepthTest();else RenderSystem.enableDepthTest();
                draw(draw,projection,true);
            }
        } finally {
            VertexBuffer.unbind();target.bindWrite(false);
            RenderSystem.depthMask(mask);if(depth)RenderSystem.enableDepthTest();else RenderSystem.disableDepthTest();
            if(cull)RenderSystem.enableCull();else RenderSystem.disableCull();
            RenderSystem.blendFuncSeparate(src,dst,srcA,dstA);if(blend)RenderSystem.enableBlend();else RenderSystem.disableBlend();
            RenderSystem.setShader(()->oldShader);RenderSystem.setShaderColor(color[0],color[1],color[2],color[3]);
            RenderSystem.setShaderTexture(0,oldTexture);
            GL11.glFrontFace(oldFrontFace);
        }
    }
    private static void draw(Draw draw,Matrix4f projection,boolean sceneDepth) {
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
        shader.safeGetUniform("InverseProjectionMat").set(new Matrix4f(projection).invert());
        shader.safeGetUniform("ScreenSize").set((float)Minecraft.getInstance().getMainRenderTarget().width,(float)Minecraft.getInstance().getMainRenderTarget().height);
        // VertexBuffer.drawWithShader populates Sampler0 from RenderSystem's
        // texture slot; setting ShaderInstance's sampler alone is overwritten.
        RenderSystem.setShaderTexture(0,part.texture);
        if(sceneDepth)shader.setSampler("SceneDepth",depthCopy.getDepthTextureId());
        part.buffer.bind();part.buffer.drawWithShader(draw.matrix,projection,shader);
    }
}
