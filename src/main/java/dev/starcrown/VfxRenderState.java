package dev.starcrown;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferUploader;
import net.minecraft.client.renderer.ShaderInstance;
import org.lwjgl.opengl.*;

/** Exact state touched by the Huangwu offscreen integration and reconstruction passes. */
final class VfxRenderState implements AutoCloseable {
    final int draw=GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING),read=GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
    final int active=GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE),program=GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
    final int vao=GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING),array=GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING);
    final int equationRGB=GL11.glGetInteger(GL20.GL_BLEND_EQUATION_RGB),equationAlpha=GL11.glGetInteger(GL20.GL_BLEND_EQUATION_ALPHA);
    final int front=GL11.glGetInteger(GL11.GL_FRONT_FACE),depthFunc=GL11.glGetInteger(GL11.GL_DEPTH_FUNC);
    final int src=GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB),dst=GL11.glGetInteger(GL14.GL_BLEND_DST_RGB),srcA=GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA),dstA=GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA);
    final int[] viewport=new int[4],box=new int[4],textures=new int[2],shaderTextures=new int[2];
    final float[] mask=new float[4],clear=new float[4],color=RenderSystem.getShaderColor().clone();
    final boolean depth=GL11.glIsEnabled(GL11.GL_DEPTH_TEST),write=GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK),blend=GL11.glIsEnabled(GL11.GL_BLEND),cull=GL11.glIsEnabled(GL11.GL_CULL_FACE),scissor=GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
    final ShaderInstance shader=RenderSystem.getShader();
    VfxRenderState(){
        GL11.glGetIntegerv(GL11.GL_VIEWPORT,viewport);GL11.glGetIntegerv(GL11.GL_SCISSOR_BOX,box);
        GL11.glGetFloatv(GL11.GL_COLOR_WRITEMASK,mask);GL11.glGetFloatv(GL11.GL_COLOR_CLEAR_VALUE,clear);
        for(int i=0;i<2;i++){shaderTextures[i]=RenderSystem.getShaderTexture(i);GlStateManager._activeTexture(GL13.GL_TEXTURE0+i);textures[i]=GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);}
        GlStateManager._activeTexture(active);
    }
    public void close(){
        GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,draw);GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,read);
        RenderSystem.viewport(viewport[0],viewport[1],viewport[2],viewport[3]);
        for(int i=0;i<2;i++){RenderSystem.setShaderTexture(i,shaderTextures[i]);GlStateManager._activeTexture(GL13.GL_TEXTURE0+i);GlStateManager._bindTexture(textures[i]);}
        GlStateManager._activeTexture(active);BufferUploader.invalidate();GlStateManager._glBindVertexArray(vao);GlStateManager._glBindBuffer(GL15.GL_ARRAY_BUFFER,array);
        RenderSystem.setShader(()->shader);GlStateManager._glUseProgram(program);RenderSystem.setShaderColor(color[0],color[1],color[2],color[3]);
        GL11.glFrontFace(front);RenderSystem.depthFunc(depthFunc);RenderSystem.depthMask(write);
        if(depth)RenderSystem.enableDepthTest();else RenderSystem.disableDepthTest();
        RenderSystem.blendEquation(equationRGB);GL20.glBlendEquationSeparate(equationRGB,equationAlpha);
        RenderSystem.blendFuncSeparate(src,dst,srcA,dstA);if(blend)RenderSystem.enableBlend();else RenderSystem.disableBlend();
        if(cull)RenderSystem.enableCull();else RenderSystem.disableCull();
        RenderSystem.enableScissor(box[0],box[1],box[2],box[3]);if(!scissor)RenderSystem.disableScissor();
        RenderSystem.colorMask(mask[0]>.5,mask[1]>.5,mask[2]>.5,mask[3]>.5);RenderSystem.clearColor(clear[0],clear[1],clear[2],clear[3]);
    }
}
