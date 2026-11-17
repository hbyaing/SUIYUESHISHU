package dev.starcrown;
import com.mojang.blaze3d.pipeline.*;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import java.nio.ByteBuffer;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import org.joml.Matrix4f;
import org.lwjgl.opengl.*;

/** Only key-tip orbs contribute emission; never threshold the world color. */
public final class FateOrbBloom {
    private record Draw(Matrix4f model,Matrix4f projection,float age){}
    private static final List<Draw> draws=new ArrayList<>();
    private static ShaderInstance source,visible,blur,composite;
    private static VertexBuffer quad;
    private static TextureTarget emission,horizontal,vertical,scene;
    public static int composites;
    public static boolean enabled=true;
    private static boolean failed;
    static void reload(net.minecraft.server.packs.resources.ResourceManager resources){
        release();failed=false;for(var s:new ShaderInstance[]{source,visible,blur,composite})if(s!=null)s.close();source=visible=blur=composite=null;
        try{source=new ShaderInstance(resources,FateItemRenderer.id("fate_orb"),DefaultVertexFormat.POSITION_TEX);
            visible=new ShaderInstance(resources,FateItemRenderer.id("fate_orb_visible"),DefaultVertexFormat.POSITION_TEX);
            blur=new ShaderInstance(resources,FateItemRenderer.id("halo_blur"),DefaultVertexFormat.POSITION_TEX);
            composite=new ShaderInstance(resources,FateItemRenderer.id("halo_composite"),DefaultVertexFormat.POSITION_TEX);
        }catch(Exception e){com.mojang.logging.LogUtils.getLogger().error("Fate orb shader unavailable",e);}
    }
    public static void release(){for(var t:new TextureTarget[]{emission,horizontal,vertical,scene})if(t!=null)t.destroyBuffers();emission=horizontal=vertical=scene=null;if(quad!=null){quad.close();quad=null;}draws.clear();}
    private static void orb(Draw draw,boolean metadata){
        ShaderInstance program=metadata?source:visible;
        program.safeGetUniform("ModelViewMat").set(draw.model());program.safeGetUniform("ProjMat").set(draw.projection());
        program.safeGetUniform("Age").set(draw.age());program.safeGetUniform("Emission").set(metadata?1f:0f);
        RenderSystem.setShader(()->program);
        if(quad==null){var b=new BufferBuilder(256);b.begin(VertexFormat.Mode.TRIANGLES,DefaultVertexFormat.POSITION_TEX);
        for(float[] p:new float[][]{{-1,-1},{1,-1},{1,1},{-1,-1},{1,1},{-1,1}})b.vertex(p[0],p[1],0).uv((p[0]+1)/2,(p[1]+1)/2).endVertex();
        quad=new VertexBuffer(VertexBuffer.Usage.STATIC);quad.bind();quad.upload(b.end());VertexBuffer.unbind();}
        quad.bind();try{quad.drawWithShader(draw.model(),draw.projection(),program);}finally{VertexBuffer.unbind();}
    }
    static void draw(Matrix4f model,Matrix4f projection,float age){
        if(source==null||visible==null)return;var d=new Draw(new Matrix4f(model),new Matrix4f(projection),age);
        RenderSystem.enableDepthTest();RenderSystem.depthMask(false);RenderSystem.enableBlend();RenderSystem.defaultBlendFunc();orb(d,false);draws.add(d);
    }
    static void finish(){
        var mc=Minecraft.getInstance();var main=mc.getMainRenderTarget();
        if(draws.isEmpty())return;
        if(failed||!enabled||source==null||blur==null||composite==null||Minecraft.useShaderTransparency()||main.getDepthTextureId()<0||GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING)!=main.frameBufferId){draws.clear();return;}
        try(VfxRenderState ignored=new VfxRenderState()){
            targets(main);blit(main,scene,GL11.GL_COLOR_BUFFER_BIT|GL11.GL_DEPTH_BUFFER_BIT);
            clear(emission);blit(scene,emission,GL11.GL_DEPTH_BUFFER_BIT);emission.bindWrite(true);
            RenderSystem.enableDepthTest();RenderSystem.depthFunc(GL11.GL_LEQUAL);RenderSystem.depthMask(true);RenderSystem.disableCull();RenderSystem.disableBlend();
            for(var d:draws)orb(d,true);
            int input=emission.getColorTextureId();
            for(int i=0;i<2;i++){
                clear(horizontal);blur.safeGetUniform("Direction").set(2f/(i==0?emission.width:vertical.width),0f);fullscreen(blur,input,scene.getDepthTextureId());
                clear(vertical);blur.safeGetUniform("Direction").set(0f,2f/horizontal.height);fullscreen(blur,horizontal.getColorTextureId(),scene.getDepthTextureId());input=vertical.getColorTextureId();
            }
            main.bindWrite(true);RenderSystem.colorMask(true,true,true,false);composite.safeGetUniform("Intensity").set(1.4f);fullscreen(composite,scene.getColorTextureId(),vertical.getColorTextureId());composites++;
        }catch(RuntimeException error){failed=true;release();com.mojang.logging.LogUtils.getLogger().warn("Fate bloom disabled after render failure; core orb remains visible",error);}finally{draws.clear();}
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
            throw new IllegalStateException("Incomplete Fate orb bloom framebuffer");
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
}
