package dev.starcrown;

import com.mojang.blaze3d.pipeline.*;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.*;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.opengl.*;

/** Approved v4 procedural field. One bounded fog integration per effect, no legacy assets. */
@Mod.EventBusSubscriber(modid=StarcrownDoll.ID,value=Dist.CLIENT)
public final class HuangwuLiteRenderer {
    private static ShaderInstance shader,composite;
    private static RenderTarget depthCopy,volumeTarget;
    private static boolean failed,offscreenFailed;
    private static final List<Effect> pending=new ArrayList<>();
    private static Matrix4f projection;
    private static final Vector3f LO=new Vector3f(-3.4f),HI=new Vector3f(3.4f);
    private record Effect(Matrix4f modelView,float time,int stage,float fade,float distance) {}
    private static ResourceLocation id(String s){return new ResourceLocation(StarcrownDoll.ID,s);}

    public static void registerShaders(RegisterShadersEvent event){
        release();shader=composite=null;failed=offscreenFailed=false;
        if(VfxClientConfig.HUANGWU_MODE.get()!=VfxClientConfig.HuangwuMode.LITE)return;
        try{event.registerShader(new ShaderInstance(event.getResourceProvider(),id("huangwu_lite"),DefaultVertexFormat.POSITION_TEX),s->shader=s);}
        catch(IOException|RuntimeException e){failed=true;com.mojang.logging.LogUtils.getLogger().warn("Huangwu lite shader unavailable; using basic geometry until reload",e);}
        try{event.registerShader(new ShaderInstance(event.getResourceProvider(),id("huangwu_composite"),DefaultVertexFormat.POSITION_TEX),s->composite=s);}
        catch(IOException|RuntimeException e){offscreenFailed=true;com.mojang.logging.LogUtils.getLogger().warn("Huangwu lite reconstruction unavailable; using native integration until reload",e);}
    }
    private static void release(){
        pending.clear();projection=null;
        if(depthCopy!=null)depthCopy.destroyBuffers();if(volumeTarget!=null)volumeTarget.destroyBuffers();
        depthCopy=volumeTarget=null;
    }
    @SubscribeEvent public static void disconnect(ClientPlayerNetworkEvent.LoggingOut e){
        if(RenderSystem.isOnRenderThread())release();else RenderSystem.recordRenderCall(HuangwuLiteRenderer::release);
    }
    @SubscribeEvent public static void frame(net.minecraftforge.event.TickEvent.RenderTickEvent e){
        if(e.phase==net.minecraftforge.event.TickEvent.Phase.START&&Minecraft.getInstance().level==null&&depthCopy!=null)release();
    }

    public static void render(RenderLevelStageEvent event){
        if(event.getStage()==RenderLevelStageEvent.Stage.AFTER_PARTICLES){
            pending.clear();projection=null;
            if(shader==null||failed){MingGuLongHuangwuFallback.render(event);return;}
            var mc=Minecraft.getInstance();if(mc.level==null)return;
            projection=new Matrix4f(event.getProjectionMatrix());
            Vec3 camera=event.getCamera().getPosition();float partial=event.getPartialTick();
            for(Entity e:mc.level.entitiesForRendering()){
                if(e.distanceToSqr(camera)>128*128)continue;
                int stage;float time,scale,fade=1,yaw=e.getYRot();
                Vec3 pos=e.getPosition(partial);Vector3f anchor=new Vector3f();
                if(e instanceof MingGuLongEntity dragon&&dragon.getAnimationKey()==13){
                    time=dragon.getHuangwuAge(partial)/20f;if(time>=1.8f)continue;
                    stage=0;float growth=smooth(time/1.5f);scale=.20f+growth*.95f;fade=smooth(time/.15f);
                    yaw=Mth.rotLerp(partial,dragon.yBodyRotO,dragon.yBodyRot);
                    // Same authored charge anchor and Gecko axis convention as the original effect.
                    anchor.set(5.11132431f/16f,6.826404572f/16f,-19.981182098f/16f);
                }else if(e instanceof MingGuLongProjectile projectile){
                    stage=1;time=(projectile.getVisualAge()+partial)/20f+.84f;scale=.82f;
                }else if(e instanceof MingGuLongVortex vortex){
                    float age=(vortex.getVisualAge()+partial)/20f;time=age+1.14f;stage=2;
                    float growth=smooth(age/.6f),collapse=1-smooth((age-10f)/.65f);
                    scale=(5f/5.10f)*Math.max(.025f,growth*collapse);fade=smooth(age/.12f);
                    // The server burst is tick 213; collapse reaches its minimum on that tick.
                    if(age>=10.65f){stage=1;float tail=(age-10.65f)/1.30f;scale=.12f+1.5f*smooth(tail);fade=1-smooth(tail);}
                    pos=pos.add(0,1.53,0);
                }else continue;
                if(fade<=.001f)continue;
                Matrix4f mv=new Matrix4f(event.getPoseStack().last().pose())
                    .translate((float)(pos.x-camera.x),(float)(pos.y-camera.y)+.01f,(float)(pos.z-camera.z))
                    .rotateY((180-yaw)*Mth.DEG_TO_RAD).scale(-1,1,1).translate(anchor).scale(scale);
                pending.add(new Effect(mv,time,stage,fade,mv.transformPosition(new Vector3f()).lengthSquared()));
            }
        }else if(event.getStage()==RenderLevelStageEvent.Stage.AFTER_LEVEL&&projection!=null&&!pending.isEmpty()){
            // Use captured terrain matrices, but composite after clouds so they cannot cover the core.
            try{drawAll();}
            catch(RuntimeException e){failed=true;com.mojang.logging.LogUtils.getLogger().warn("Huangwu lite rendering failed; basic fallback selected until reload",e);}
            finally{pending.clear();}
        }
    }
    private static float smooth(float x){x=Mth.clamp(x,0,1);return x*x*(3-2*x);}
    private static void drawAll(){
        var main=Minecraft.getInstance().getMainRenderTarget();
        if(GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING)!=main.frameBufferId)return;
        var bounds=new IdentityHashMap<Effect,HuangwuScreenBounds>();
        pending.removeIf(e->{var b=HuangwuScreenBounds.project(LO,HI,e.modelView,projection,main.width,main.height);if(b==null)return true;bounds.put(e,b);return false;});
        if(pending.isEmpty())return;
        pending.sort(Comparator.comparingDouble((Effect e)->e.distance).reversed());
        Matrix4f inverseProjection=new Matrix4f(projection).invert();
        try(VfxRenderState ignored=new VfxRenderState()){
            RenderSystem.disableScissor();RenderSystem.colorMask(true,true,true,true);
            if(depthCopy==null)depthCopy=new TextureTarget(main.width,main.height,true,Minecraft.ON_OSX);
            if(depthCopy.width!=main.width||depthCopy.height!=main.height)depthCopy.resize(main.width,main.height,Minecraft.ON_OSX);
            depthCopy.copyDepthFrom(main);main.bindWrite(true);
            double factor=Math.min(VfxClientConfig.HUANGWU_RESOLUTION.get(),Math.sqrt(VfxClientConfig.HUANGWU_MAX_PIXELS.get()/((double)main.width*main.height*pending.size())));
            boolean reduced=factor<.999&&composite!=null&&!offscreenFailed;
            if(reduced)try{ensureTarget(main,factor);}catch(RuntimeException e){
                offscreenFailed=true;reduced=false;main.bindWrite(true);
                com.mojang.logging.LogUtils.getLogger().warn("Huangwu lite reduced target unavailable; native integration until reload",e);
            }
            RenderSystem.disableDepthTest();RenderSystem.depthMask(false);RenderSystem.disableCull();
            RenderSystem.setShaderColor(1,1,1,1);
            for(Effect effect:pending){
                var box=bounds.get(effect);
                if(reduced){
                    RenderSystem.disableScissor();volumeTarget.bindWrite(true);
                    RenderSystem.clearColor(0,0,0,0);RenderSystem.clear(GL11.GL_COLOR_BUFFER_BIT,Minecraft.ON_OSX);
                    var small=box.scaled(main.width,main.height,volumeTarget.width,volumeTarget.height);
                    RenderSystem.enableScissor(small.x(),small.y(),small.width(),small.height());
                    draw(effect,inverseProjection,volumeTarget.width,volumeTarget.height);
                    main.bindWrite(true);RenderSystem.enableScissor(box.x(),box.y(),box.width(),box.height());
                    composite.safeGetUniform("InverseProjectionMat").set(inverseProjection);
                    composite.safeGetUniform("NativeSize").set((float)main.width,(float)main.height);
                    composite.safeGetUniform("VolumeSize").set((float)volumeTarget.width,(float)volumeTarget.height);
                    RenderSystem.setShader(()->composite);RenderSystem.setShaderTexture(0,volumeTarget.getColorTextureId());
                    RenderSystem.setShaderTexture(1,depthCopy.getDepthTextureId());triangle();
                }else{
                    RenderSystem.enableScissor(box.x(),box.y(),box.width(),box.height());draw(effect,inverseProjection,main.width,main.height);
                }
            }
        }
    }
    private static void ensureTarget(RenderTarget main,double factor){
        int w=Math.max(1,(int)Math.floor(main.width*factor)),h=Math.max(1,(int)Math.floor(main.height*factor));
        if(volumeTarget!=null&&volumeTarget.width==w&&volumeTarget.height==h)return;
        if(volumeTarget!=null){volumeTarget.destroyBuffers();volumeTarget=null;}
        volumeTarget=new TextureTarget(w,h,false,Minecraft.ON_OSX);
        GlStateManager._bindTexture(volumeTarget.getColorTextureId());
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D,0,GL30.GL_RGBA16F,w,h,0,GL11.GL_RGBA,GL11.GL_FLOAT,(ByteBuffer)null);
        volumeTarget.setFilterMode(GL11.GL_NEAREST);volumeTarget.bindWrite(true);
        if(GL30.glCheckFramebufferStatus(GL30.GL_DRAW_FRAMEBUFFER)!=GL30.GL_FRAMEBUFFER_COMPLETE)throw new IllegalStateException("Incomplete Huangwu lite framebuffer");
    }
    private static void draw(Effect effect,Matrix4f inverseProjection,int w,int h){
        shader.safeGetUniform("InverseProjectionMat").set(inverseProjection);
        shader.safeGetUniform("InverseModelViewMat").set(new Matrix4f(effect.modelView).invert());
        shader.safeGetUniform("IntegrationSize").set((float)w,(float)h);
        shader.safeGetUniform("uTime").set(effect.time);shader.safeGetUniform("uStage").set((float)effect.stage);
        shader.safeGetUniform("uFade").set(effect.fade);
        RenderSystem.setShader(()->shader);RenderSystem.setShaderTexture(0,depthCopy.getDepthTextureId());triangle();
    }
    private static void triangle(){
        RenderSystem.enableBlend();RenderSystem.defaultBlendFunc();
        var b=Tesselator.getInstance().getBuilder();b.begin(VertexFormat.Mode.TRIANGLES,DefaultVertexFormat.POSITION_TEX);
        b.vertex(-1,-1,0).uv(0,0).endVertex();b.vertex(3,-1,0).uv(2,0).endVertex();b.vertex(-1,3,0).uv(0,2).endVertex();
        BufferUploader.drawWithShader(b.end());
    }
}
