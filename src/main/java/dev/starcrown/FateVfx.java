package dev.starcrown;

import java.io.*;
import com.google.gson.*;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.server.packs.resources.ResourceProvider;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.*;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** Cached authored sheets, procedural wind, golden aperture and a genuinely breaking black mask. */
@Mod.EventBusSubscriber(modid=StarcrownDoll.ID,value=Dist.CLIENT)
public final class FateVfx {
    private static ShaderInstance shader;
    private static VertexBuffer analysis,cube,gate,shards;
    private static final VertexFormat FORMAT=DefaultVertexFormat.POSITION_TEX_COLOR;
    /** Resource apply runs on the render executor; this listener owns and closes its program. */
    public static void reload(net.minecraft.server.packs.resources.ResourceManager resources){
        if(shader!=null)shader.close();shader=null;release();
        try(VfxRenderState ignored=new VfxRenderState()){
            load(resources);shader=new ShaderInstance(resources,FateItemRenderer.id("fate_retrieval"),FORMAT);
            com.mojang.logging.LogUtils.getLogger().info("Fate retrieval shader and geometry loaded");
        }catch(IOException|RuntimeException error){release();com.mojang.logging.LogUtils.getLogger().error("Fate shader unavailable; item use remains functional",error);}
    }
    private static void release(){for(var b:new VertexBuffer[]{analysis,cube,gate,shards})if(b!=null)b.close();analysis=cube=gate=shards=null;}
    private static VertexBuffer upload(BufferBuilder b){var v=new VertexBuffer(VertexBuffer.Usage.STATIC);v.bind();try{v.upload(b.end());}finally{VertexBuffer.unbind();}return v;}
    private static BufferBuilder builder(){var b=new BufferBuilder(2*1024*1024);b.begin(VertexFormat.Mode.TRIANGLES,FORMAT);return b;}
    private static void vertex(BufferBuilder b,Vector3f p,float u,float v,float r,float g,float blue){b.vertex(p.x,p.y,p.z).uv(u,v).color(r,g,blue,1).endVertex();}
    private static void load(ResourceProvider resources)throws IOException{
        var b=builder();JsonObject root;
        try(var in=resources.openAsReader(FateItemRenderer.id("models/fate/analysis.json"))){root=JsonParser.parseReader(in).getAsJsonObject();}
        for(var node:root.getAsJsonArray("paths")){
            var path=node.getAsJsonObject();float radius=path.get("radius").getAsFloat();var color=path.getAsJsonArray("color");var pts=path.getAsJsonArray("points");
            for(int i=0;i<pts.size()-1;i++){
                Vector3f a=point(pts.get(i)),z=point(pts.get(i+1)),dir=new Vector3f(z).sub(a);if(dir.lengthSquared()<1e-10)continue;dir.normalize();
                Vector3f n=dir.cross(Math.abs(dir.y)<.9?new Vector3f(0,1,0):new Vector3f(1,0,0),new Vector3f()).normalize().mul(radius);
                Vector3f m=dir.cross(n,new Vector3f()).normalize().mul(radius);
                Vector3f[] corners={n,m,new Vector3f(n).negate(),new Vector3f(m).negate()};
                for(int j=0;j<4;j++){
                    Vector3f p0=new Vector3f(a).add(corners[j]),p1=new Vector3f(a).add(corners[(j+1)%4]);
                    Vector3f p2=new Vector3f(z).add(corners[(j+1)%4]),p3=new Vector3f(z).add(corners[j]);
                    for(var p:new Vector3f[]{p0,p1,p2,p0,p2,p3})vertex(b,p,0,0,color.get(0).getAsFloat(),color.get(1).getAsFloat(),color.get(2).getAsFloat());
                }
            }
        }
        analysis=upload(b);b=builder();
        float lo=-10.2f,hi=10.2f,y0=3.5f,y1=35;
        Vector3f[] c={new Vector3f(lo,y0,lo),new Vector3f(hi,y0,lo),new Vector3f(hi,y1,lo),new Vector3f(lo,y1,lo),new Vector3f(lo,y0,hi),new Vector3f(hi,y0,hi),new Vector3f(hi,y1,hi),new Vector3f(lo,y1,hi)};
        int[][] faces={{0,3,2,1},{4,5,6,7},{0,4,7,3},{1,2,6,5},{0,1,5,4},{3,7,6,2}};
        for(var f:faces)for(int j:new int[]{0,1,2,0,2,3})vertex(b,c[f[j]],0,0,1,1,1);
        cube=upload(b);b=builder();
        Vector3f[] quad={new Vector3f(-1,-1,0),new Vector3f(1,-1,0),new Vector3f(1,1,0),new Vector3f(-1,1,0)};
        for(int i:new int[]{0,1,2,0,2,3})vertex(b,quad[i],(quad[i].x+1)/2,(quad[i].y+1)/2,1,1,1);
        gate=upload(b);b=builder();
        // Shared ring vertices completely tile the black surface before separation.
        java.util.Random random=new java.util.Random(81742);int sectors=20;
        Vector3f[][] rings=new Vector3f[5][sectors];
        for(int r=0;r<5;r++)for(int i=0;i<sectors;i++){
            double angle=i*Math.PI*2/sectors;float radius=r==0?0:r==4?5:(float)(r*.45*(.8+random.nextDouble()*.4));
            rings[r][i]=new Vector3f((float)Math.cos(angle)*radius,(float)Math.sin(angle)*radius,0);
        }
        for(int r=0;r<4;r++)for(int i=0;i<sectors;i++){
            int j=(i+1)%sectors;
            shard(b,rings[r][i],rings[r+1][i],rings[r+1][j]);
            if(r>0)shard(b,rings[r][i],rings[r+1][j],rings[r][j]);
        }
        shards=upload(b);
    }
    private static Vector3f point(JsonElement v){var a=v.getAsJsonArray();return new Vector3f(a.get(0).getAsFloat(),a.get(1).getAsFloat(),a.get(2).getAsFloat());}
    private static void shard(BufferBuilder b,Vector3f a,Vector3f c,Vector3f d){Vector3f center=new Vector3f(a).add(c).add(d).div(3);for(var p:new Vector3f[]{a,c,d})vertex(b,p,center.x,center.y,1,1,1);}
    private static void uniform(String name,float value){var u=shader.getUniform(name);if(u!=null)u.set(value);}
    private static void draw(VertexBuffer buffer,Matrix4f model,Matrix4f projection,int mode,float age){
        uniform("Mode",mode);uniform("Age",age);
        Vector3f camera=new Matrix4f(model).invert().transformPosition(new Vector3f());var u=shader.getUniform("CameraLocal");if(u!=null)u.set(camera.x,camera.y,camera.z);
        buffer.bind();try{buffer.drawWithShader(model,projection,shader);}finally{VertexBuffer.unbind();}
    }
    /** Composite the ignition over the lit skin, rather than hiding its bright center behind the fist. */
    static void handLight(float age,float side){
        if(shader==null||age<134||age>=143)return;
        var mc=Minecraft.getInstance();
        try(VfxRenderState ignored=new VfxRenderState()){
            RenderSystem.disableDepthTest();RenderSystem.depthMask(false);RenderSystem.disableCull();RenderSystem.enableBlend();RenderSystem.defaultBlendFunc();
            uniform("Aspect",(float)mc.getWindow().getWidth()/mc.getWindow().getHeight());
            var center=shader.getUniform("MaskCenter");if(center!=null)center.set(side*.35f,-.16f);
            draw(gate,new Matrix4f(),new Matrix4f(),5,age);
        }
    }
    @SubscribeEvent public static void render(RenderLevelStageEvent event){
        boolean worldPass=event.getStage()==RenderLevelStageEvent.Stage.AFTER_PARTICLES;
        boolean maskPass=event.getStage()==RenderLevelStageEvent.Stage.AFTER_LEVEL;
        if((!worldPass&&!maskPass)||shader==null||FateClient.CASTS.isEmpty())return;
        var mc=Minecraft.getInstance();if(mc.level==null)return;
        Vec3 camera=event.getCamera().getPosition();float partial=event.getPartialTick();
        try(VfxRenderState ignored=new VfxRenderState()){
            RenderSystem.disableCull();RenderSystem.enableDepthTest();RenderSystem.depthMask(false);RenderSystem.enableBlend();RenderSystem.defaultBlendFunc();RenderSystem.setShaderColor(1,1,1,1);
            uniform("Aspect",(float)mc.getWindow().getWidth()/mc.getWindow().getHeight());
            for(var cast:FateClient.CASTS.values()){
                float age=FateClient.age(cast,partial);if(age<0||age>=160)continue;
                if(cast.origin().distanceToSqr(camera)>128*128)continue;
                var entity=mc.level.getEntity(cast.entity());if(!(entity instanceof Player player))continue;
                Vec3 anchor=cast.origin().add(0,.6,0);
                Matrix4f base=new Matrix4f(event.getPoseStack().last().pose()).translate((float)(anchor.x-camera.x),(float)(anchor.y-camera.y),(float)(anchor.z-camera.z)).rotateY((180-cast.yaw())*Mth.DEG_TO_RAD);
                if(worldPass&&age<114){
                    RenderSystem.disableBlend();RenderSystem.depthMask(true);draw(analysis,base,event.getProjectionMatrix(),0,age);
                    RenderSystem.enableBlend();RenderSystem.depthMask(false);draw(cube,base,event.getProjectionMatrix(),1,age);
                }
                Vec3 forward=player.getLookAngle();Vec3 right=new Vec3(-forward.z,0,forward.x).normalize();
                boolean left=(player.getMainArm()==net.minecraft.world.entity.HumanoidArm.LEFT)^cast.offhand();
                Vec3 hand=player.getEyePosition(partial).add(forward.scale(.85)).add(right.scale(left?-.28:.28)).add(0,-.25,0);
                boolean local=player==mc.player&&mc.options.getCameraType().isFirstPerson();
                if(maskPass&&local&&age>=80&&age<114){
                    RenderSystem.disableDepthTest();float side=left?-1:1;
                    draw(gate,new Matrix4f().translate(side*.35f,-.16f,0).scale(.83f*mc.getWindow().getHeight()/mc.getWindow().getWidth(),.83f,1),new Matrix4f(),2,age);
                    RenderSystem.enableDepthTest();
                }
                if(worldPass&&!local&&age>=80&&age<114){
                    Matrix4f billboard=new Matrix4f(event.getPoseStack().last().pose()).translate((float)(hand.x-camera.x),(float)(hand.y-camera.y),(float)(hand.z-camera.z))
                            .rotate(event.getCamera().rotation()).scale(.48f);
                    draw(gate,billboard,event.getProjectionMatrix(),2,age);
                }
                if(maskPass&&age>=114&&local){
                    RenderSystem.disableDepthTest();
                    float side=left?-1:1;var center=shader.getUniform("MaskCenter");if(center!=null)center.set(side*.35f,-.16f);
                    draw(shards,new Matrix4f(),new Matrix4f(),3,age);RenderSystem.enableDepthTest();
                }
                // Other players see the same black surface break locally around the active hand.
                if(worldPass&&age>=114&&!local){
                    Vec3 surface=hand.subtract(forward.scale(.6));
                    Matrix4f billboard=new Matrix4f(event.getPoseStack().last().pose()).translate((float)(surface.x-camera.x),(float)(surface.y-camera.y),(float)(surface.z-camera.z))
                            .rotate(event.getCamera().rotation()).scale(.65f);
                    var center=shader.getUniform("MaskCenter");if(center!=null)center.set(0f,0f);
                    draw(shards,billboard,event.getProjectionMatrix(),4,age);
                }
            }
        }
    }
}
