package dev.starcrown;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;
import java.util.ArrayList;
import java.util.List;

/** Basic geometry using Minecraft's own shader, only when the optional volume shader cannot load. */
final class MingGuLongHuangwuFallback {
    private record Effect(Vec3 position,float radius,float time,boolean dark) {}
    static void render(RenderLevelStageEvent event) {
        var mc=Minecraft.getInstance();if(mc.level==null)return;
        Vec3 camera=event.getCamera().getPosition();float partial=event.getPartialTick();
        List<Effect> effects=new ArrayList<>();
        for(Entity e:mc.level.entitiesForRendering()) {
            if(e.distanceToSqr(camera)>96*96)continue;
            float age,radius;boolean dark=false;
            Vec3 pos=e.getPosition(partial);
            if(e instanceof MingGuLongEntity dragon&&dragon.getAnimationKey()==13) {
                age=dragon.getHuangwuAge(partial)/20f;if(age>=1.8f)continue;
                radius=.13f+.40f*Math.min(1,age/1.4f);
                pos=dragon.getEyePosition(partial).add(dragon.getLookAngle().scale(1.35));
            }else if(e instanceof MingGuLongProjectile p){age=(p.getVisualAge()+partial)/20f;radius=.36f;}
            else if(e instanceof MingGuLongVortex v){
                age=(v.getVisualAge()+partial)/20f;dark=true;
                float fade=Math.min(1,Math.min(age/.35f,Math.max(0,11.2f-age)/1.2f));
                radius=(1.02f+.045f*(float)Math.sin(age*3))*fade;pos=pos.add(0,1.05,0);
            }else continue;
            if(radius>.01f)effects.add(new Effect(pos,radius,age,dark));
            if(effects.size()>=8)break;
        }
        if(effects.isEmpty())return;
        boolean depth=GL11.glIsEnabled(GL11.GL_DEPTH_TEST),blend=GL11.glIsEnabled(GL11.GL_BLEND),cull=GL11.glIsEnabled(GL11.GL_CULL_FACE),mask=GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        int src=GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB),dst=GL11.glGetInteger(GL14.GL_BLEND_DST_RGB),srcA=GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA),dstA=GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA);
        var shader=RenderSystem.getShader();float[] color=RenderSystem.getShaderColor().clone();
        var pose=event.getPoseStack();
        try {
            RenderSystem.setShader(GameRenderer::getPositionColorShader);RenderSystem.setShaderColor(1,1,1,1);
            RenderSystem.enableDepthTest();RenderSystem.disableCull();
            for(var effect:effects) {
                pose.pushPose();
                try {
                    Vec3 p=effect.position.subtract(camera);pose.translate(p.x,p.y,p.z);
                    pose.mulPose(event.getCamera().rotation());Matrix4f matrix=pose.last().pose();
                    BufferBuilder b=Tesselator.getInstance().getBuilder();
                    RenderSystem.disableBlend();RenderSystem.depthMask(true);
                    b.begin(VertexFormat.Mode.TRIANGLES,DefaultVertexFormat.POSITION_COLOR);
                    float r=effect.radius*.70f,red=effect.dark?.003f:.65f,green=effect.dark?.002f:.93f,blue=effect.dark?.008f:1;
                    for(int j=0;j<10;j++)for(int i=0;i<24;i++) {
                        double a=-Math.PI/2+j*Math.PI/10,c=a+Math.PI/10,u=i*Math.PI/12,v=u+Math.PI/12;
                        float[] p0=sphere(a,u,r),p1=sphere(c,u,r),p2=sphere(c,v,r),p3=sphere(a,v,r);
                        tri(b,matrix,p0,p1,p2,red,green,blue,1);tri(b,matrix,p0,p2,p3,red,green,blue,1);
                    }
                    BufferUploader.drawWithShader(b.end());
                    RenderSystem.enableBlend();RenderSystem.blendFunc(com.mojang.blaze3d.platform.GlStateManager.SourceFactor.SRC_ALPHA,com.mojang.blaze3d.platform.GlStateManager.DestFactor.ONE);RenderSystem.depthMask(false);
                    b.begin(VertexFormat.Mode.TRIANGLES,DefaultVertexFormat.POSITION_COLOR);
                    ring(b,matrix,effect.radius,.09f*effect.radius,.32f,.85f,1,.9f,effect.time);
                    ring(b,matrix,effect.radius*1.13f,.28f*effect.radius,1,.025f,.24f,.38f,effect.time);
                    ring(b,matrix,effect.radius*1.34f,.52f*effect.radius,.7f,.005f,.1f,.13f,effect.time);
                    BufferUploader.drawWithShader(b.end());
                }finally {pose.popPose();}
            }
        }finally {
            RenderSystem.depthMask(mask);if(depth)RenderSystem.enableDepthTest();else RenderSystem.disableDepthTest();
            if(cull)RenderSystem.enableCull();else RenderSystem.disableCull();
            RenderSystem.blendFuncSeparate(src,dst,srcA,dstA);if(blend)RenderSystem.enableBlend();else RenderSystem.disableBlend();
            RenderSystem.setShader(()->shader);RenderSystem.setShaderColor(color[0],color[1],color[2],color[3]);
        }
    }
    private static float[] sphere(double a,double u,float r){return new float[]{(float)(Math.cos(a)*Math.cos(u)*r),(float)(Math.sin(a)*r),(float)(Math.cos(a)*Math.sin(u)*r)};}
    private static float[] circle(double a,float r){return new float[]{(float)Math.cos(a)*r,(float)Math.sin(a)*r,0};}
    private static void ring(BufferBuilder b,Matrix4f m,float r,float width,float red,float green,float blue,float alpha,float time){
        for(int lane=0;lane<4;lane++)for(int i=0;i<72;i++){
            double a=i*Math.PI/36,c=(i+1)*Math.PI/36;
            float lo=r+width*(lane/4f-.5f),hi=lo+width/4;
            float flicker=.8f+.2f*(float)Math.sin(a*5-time*4);
            float opacity=alpha*(float)Math.sin((lane+.5)*Math.PI/4)*flicker;
            var p0=circle(a,lo);var p1=circle(c,lo);var p2=circle(c,hi);var p3=circle(a,hi);
            tri(b,m,p0,p1,p2,red,green,blue,opacity);tri(b,m,p0,p2,p3,red,green,blue,opacity);
        }
    }
    private static void tri(BufferBuilder b,Matrix4f m,float[] a,float[] c,float[] d,float red,float green,float blue,float alpha){
        for(var p:new float[][]{a,c,d})b.vertex(m,p[0],p[1],p[2]).color(red,green,blue,alpha).endVertex();
    }
}
