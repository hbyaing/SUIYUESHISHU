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
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.*;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import org.lwjgl.opengl.*;

/** One depth-aware bloom pass per world frame, sourced exclusively from worn halo triangles. */
@Mod.EventBusSubscriber(modid=StarcrownDoll.ID,value=Dist.CLIENT)
public final class EternalHaloBloom {
    private record Draw(Matrix4f modelView,Matrix4f projection) {}
    private static final List<Draw> DRAWS=new ArrayList<>();
    private static ShaderInstance source,blur,composite;
    private static TextureTarget emission,horizontal,vertical,scene;
    private static VertexBuffer mesh;
    private static boolean collecting,failed;
    private static int composites,lastDrawCount;
    private static int captureCalls,worldCaptures,mainCaptures;
    public static String diagnostics(){return "calls="+captureCalls+", collecting="+worldCaptures+", main="+mainCaptures+", queued="+lastDrawCount+", failed="+failed;}
    private static ResourceLocation id(String path){return new ResourceLocation(StarcrownDoll.ID,path);}

    public static void register(RegisterShadersEvent event) {
        release();source=blur=composite=null;failed=false;
        try {
            event.registerShader(new ShaderInstance(event.getResourceProvider(),id("halo_emission"),DefaultVertexFormat.POSITION_TEX),s->source=s);
            event.registerShader(new ShaderInstance(event.getResourceProvider(),id("halo_blur"),DefaultVertexFormat.POSITION_TEX),s->blur=s);
            event.registerShader(new ShaderInstance(event.getResourceProvider(),id("halo_composite"),DefaultVertexFormat.POSITION_TEX),s->composite=s);
        }catch(IOException|RuntimeException error) {
            failed=true;com.mojang.logging.LogUtils.getLogger().warn("Halo bloom unavailable; retaining luminous original halo model",error);
        }
    }
    public static void release() {
        for(var target:new TextureTarget[]{emission,horizontal,vertical,scene})if(target!=null)target.destroyBuffers();
        emission=horizontal=vertical=scene=null;
        if(mesh!=null){mesh.close();mesh=null;}DRAWS.clear();collecting=false;
    }
    public static void capture(PoseStack pose) {
        captureCalls++;if(collecting)worldCaptures++;
        if(!collecting||failed||source==null||DRAWS.size()>=128)return;
        var projection=RenderSystem.getProjectionMatrix();
        // Inventory portraits and item activation use orthographic projections and do not enter world bloom.
        if(Math.abs(projection.m33())>.001f)return;
        var main=Minecraft.getInstance().getMainRenderTarget();
        if(GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING)!=main.frameBufferId)return;
        mainCaptures++;
        DRAWS.add(new Draw(new Matrix4f(RenderSystem.getModelViewMatrix()).mul(pose.last().pose()),new Matrix4f(projection)));
    }
    public static int composites(){return composites;}
    public static int lastDrawCount(){return lastDrawCount;}
    public static boolean ready(){return !failed&&source!=null&&blur!=null&&composite!=null;}
    public static boolean allocated(){return emission!=null;}
    @SubscribeEvent public static void frame(TickEvent.RenderTickEvent event) {
        if(event.phase==TickEvent.Phase.START) {
            DRAWS.clear();collecting=false;
            if(Minecraft.getInstance().level==null&&emission!=null)release();
        }
    }
    @SubscribeEvent public static void render(RenderLevelStageEvent event) {
        // The sky stage is skipped at short view distances; cutout terrain always precedes entities.
        if(event.getStage()==RenderLevelStageEvent.Stage.AFTER_CUTOUT_BLOCKS) {DRAWS.clear();collecting=true;return;}
        if(event.getStage()!=RenderLevelStageEvent.Stage.AFTER_PARTICLES)return;
        collecting=false;lastDrawCount=DRAWS.size();
        if(DRAWS.isEmpty()||!ready())return;
        var mc=Minecraft.getInstance();var main=mc.getMainRenderTarget();
        if(Minecraft.useShaderTransparency()||main.getDepthTextureId()<0
                ||GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING)!=main.frameBufferId){DRAWS.clear();return;}
        try(State ignored=new State()) {
            targets(main);upload();
            blit(main,scene,GL11.GL_COLOR_BUFFER_BIT|GL11.GL_DEPTH_BUFFER_BIT);
            clear(emission);blit(scene,emission,GL11.GL_DEPTH_BUFFER_BIT);emission.bindWrite(true);
            RenderSystem.enableDepthTest();RenderSystem.depthFunc(GL11.GL_LEQUAL);RenderSystem.depthMask(true);
            RenderSystem.disableCull();RenderSystem.disableBlend();
            RenderSystem.setShaderColor(1,1,1,1);RenderSystem.setShaderTexture(0,EternalHaloRenderer.texture());
            for(var draw:DRAWS) {
                mesh.bind();mesh.drawWithShader(draw.modelView(),draw.projection(),source);
            }
            VertexBuffer.unbind();
            int input=emission.getColorTextureId();
            // Half resolution, two separable pairs. Alpha carries nearest source depth in 32-bit floats.
            for(int i=0;i<2;i++) {
                clear(horizontal);blur.safeGetUniform("Direction").set(2.0f/(i==0?emission.width:vertical.width),0f);
                fullscreen(blur,input,scene.getDepthTextureId());
                clear(vertical);blur.safeGetUniform("Direction").set(0f,2.0f/horizontal.height);
                fullscreen(blur,horizontal.getColorTextureId(),scene.getDepthTextureId());input=vertical.getColorTextureId();
            }
            main.bindWrite(true);RenderSystem.colorMask(true,true,true,false);
            composite.safeGetUniform("Intensity").set(.85f);
            fullscreen(composite,scene.getColorTextureId(),vertical.getColorTextureId());composites++;
        }catch(RuntimeException error) {
            failed=true;release();com.mojang.logging.LogUtils.getLogger().warn("Halo bloom disabled after render failure; original halo remains visible",error);
        }finally{DRAWS.clear();}
    }
    private static void upload() {
        if(mesh!=null)return;
        float[] data=EternalHaloRenderer.mesh();if(data.length==0)throw new IllegalStateException("Halo geometry not loaded");
        var builder=new BufferBuilder(data.length*4);builder.begin(VertexFormat.Mode.TRIANGLES,DefaultVertexFormat.POSITION_TEX);
        for(int i=0;i<data.length;i+=8)builder.vertex(data[i],data[i+1],data[i+2]).uv(data[i+6],data[i+7]).endVertex();
        mesh=new VertexBuffer(VertexBuffer.Usage.STATIC);mesh.bind();mesh.upload(builder.end());VertexBuffer.unbind();
    }
    private static void targets(RenderTarget main) {
        if(emission!=null&&emission.width==main.width&&emission.height==main.height)return;
        // Target resize does not invalidate the immutable geometry buffer.
        for(var target:new TextureTarget[]{emission,horizontal,vertical,scene})if(target!=null)target.destroyBuffers();
        emission=target(main.width,main.height,true,true);
        horizontal=target(Math.max(1,main.width/2),Math.max(1,main.height/2),false,true);
        vertical=target(Math.max(1,main.width/2),Math.max(1,main.height/2),false,true);
        scene=target(main.width,main.height,true,false);
    }
    private static TextureTarget target(int width,int height,boolean depth,boolean floating) {
        var target=new TextureTarget(width,height,depth,Minecraft.ON_OSX);
        if(floating) {
            GlStateManager._bindTexture(target.getColorTextureId());
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D,0,GL30.GL_RGBA32F,width,height,0,GL11.GL_RGBA,GL11.GL_FLOAT,(ByteBuffer)null);
        }
        // Nearest filtering preserves source-depth metadata at silhouettes; the Gaussian taps provide smoothing.
        target.setFilterMode(GL11.GL_NEAREST);target.bindWrite(true);
        if(GL30.glCheckFramebufferStatus(GL30.GL_DRAW_FRAMEBUFFER)!=GL30.GL_FRAMEBUFFER_COMPLETE)
            throw new IllegalStateException("Incomplete halo bloom framebuffer");
        return target;
    }
    private static void clear(TextureTarget target) {
        RenderSystem.disableScissor();RenderSystem.colorMask(true,true,true,true);RenderSystem.depthMask(true);
        target.setClearColor(0,0,0,1);target.clear(Minecraft.ON_OSX);target.bindWrite(true);
    }
    private static void blit(RenderTarget from,RenderTarget to,int bits) {
        GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,from.frameBufferId);
        GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,to.frameBufferId);
        GL30.glBlitFramebuffer(0,0,from.width,from.height,0,0,to.width,to.height,bits,GL11.GL_NEAREST);
    }
    private static void fullscreen(ShaderInstance shader,int texture,int auxiliary) {
        RenderSystem.disableDepthTest();RenderSystem.depthMask(false);RenderSystem.disableCull();RenderSystem.disableBlend();
        RenderSystem.setShader(()->shader);RenderSystem.setShaderTexture(0,texture);RenderSystem.setShaderTexture(1,auxiliary);
        var builder=Tesselator.getInstance().getBuilder();builder.begin(VertexFormat.Mode.TRIANGLES,DefaultVertexFormat.POSITION_TEX);
        builder.vertex(-1,-1,0).uv(0,0).endVertex();builder.vertex(3,-1,0).uv(2,0).endVertex();builder.vertex(-1,3,0).uv(0,2).endVertex();
        BufferUploader.drawWithShader(builder.end());
    }
    /** Restore every GL state touched by the effect, including framebuffer and viewport for nested passes. */
    private static final class State implements AutoCloseable {
        final int draw=GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING),read=GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING),active=GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        final int vao=GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING),array=GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING);
        final int[] viewport=new int[4],scissorBox=new int[4],textures=new int[2],shaderTextures=new int[2];
        final float[] mask=new float[4],color=RenderSystem.getShaderColor().clone();
        final int depthFunc=GL11.glGetInteger(GL11.GL_DEPTH_FUNC),src=GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB),dst=GL11.glGetInteger(GL14.GL_BLEND_DST_RGB),srcAlpha=GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA),dstAlpha=GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA);
        final boolean depth=GL11.glIsEnabled(GL11.GL_DEPTH_TEST),write=GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK),blend=GL11.glIsEnabled(GL11.GL_BLEND),cull=GL11.glIsEnabled(GL11.GL_CULL_FACE),scissor=GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
        final ShaderInstance shader=RenderSystem.getShader();
        State() {
            GL11.glGetIntegerv(GL11.GL_VIEWPORT,viewport);GL11.glGetIntegerv(GL11.GL_SCISSOR_BOX,scissorBox);GL11.glGetFloatv(GL11.GL_COLOR_WRITEMASK,mask);
            for(int i=0;i<2;i++){shaderTextures[i]=RenderSystem.getShaderTexture(i);GlStateManager._activeTexture(GL13.GL_TEXTURE0+i);textures[i]=GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);}
            GlStateManager._activeTexture(active);
        }
        @Override public void close() {
            GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,draw);GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,read);
            RenderSystem.viewport(viewport[0],viewport[1],viewport[2],viewport[3]);
            for(int i=0;i<2;i++){RenderSystem.setShaderTexture(i,shaderTextures[i]);GlStateManager._activeTexture(GL13.GL_TEXTURE0+i);GlStateManager._bindTexture(textures[i]);}
            // Fullscreen draws change BufferUploader's cached VAO; invalidate before restoring raw GL bindings.
            BufferUploader.invalidate();
            GlStateManager._activeTexture(active);GlStateManager._glBindVertexArray(vao);GlStateManager._glBindBuffer(GL15.GL_ARRAY_BUFFER,array);
            RenderSystem.setShader(()->shader);RenderSystem.setShaderColor(color[0],color[1],color[2],color[3]);
            if(depth)RenderSystem.enableDepthTest();else RenderSystem.disableDepthTest();RenderSystem.depthMask(write);RenderSystem.depthFunc(depthFunc);
            RenderSystem.blendFuncSeparate(src,dst,srcAlpha,dstAlpha);if(blend)RenderSystem.enableBlend();else RenderSystem.disableBlend();
            if(cull)RenderSystem.enableCull();else RenderSystem.disableCull();
            if(scissor)RenderSystem.enableScissor(scissorBox[0],scissorBox[1],scissorBox[2],scissorBox[3]);else RenderSystem.disableScissor();
            RenderSystem.colorMask(mask[0]>.5,mask[1]>.5,mask[2]>.5,mask[3]>.5);
        }
    }
}
