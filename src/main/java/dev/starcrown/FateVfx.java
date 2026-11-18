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
        if(shader!=null)shader.close();shader=null;release();FateOrbBloom.reload(resources);
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
            double angle=(i+(r%2)*.38+(random.nextDouble()-.5)*.32)*Math.PI*2/sectors;
            float radius=r==0?0:r==4?5:(float)(r*.45*(.78+random.nextDouble()*.44));
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
    private static void shard(BufferBuilder b,Vector3f a,Vector3f c,Vector3f d){
        Vector3f center=new Vector3f(a).add(c).add(d).div(3);
        vertex(b,a,center.x,center.y,1,0,0);vertex(b,c,center.x,center.y,0,1,0);vertex(b,d,center.x,center.y,0,0,1);
    }
    private static void uniform(String name,float value){var u=shader.getUniform(name);if(u!=null)u.set(value);}
    private static void draw(VertexBuffer buffer,Matrix4f model,Matrix4f projection,int mode,float age){
        uniform("Mode",mode);uniform("Age",age);
        Vector3f camera=new Matrix4f(model).invert().transformPosition(new Vector3f());var u=shader.getUniform("CameraLocal");if(u!=null)u.set(camera.x,camera.y,camera.z);
        buffer.bind();try{buffer.drawWithShader(model,projection,shader);}finally{VertexBuffer.unbind();}
    }
    /** Use the projected fist from the actual hand camera, including bob, hand FOV and handedness. */
    static void handSurface(float age,float side,float targetX,float targetY,float fistX,float fistY,boolean foreground){
        if(shader==null||age<80||age>=158)return;
        if(foreground&&age<111)return;
        var mc=Minecraft.getInstance();
        try(VfxRenderState ignored=new VfxRenderState()){
            RenderSystem.disableDepthTest();RenderSystem.depthMask(false);RenderSystem.disableCull();RenderSystem.enableBlend();RenderSystem.defaultBlendFunc();
            float aspect=(float)mc.getWindow().getWidth()/mc.getWindow().getHeight();
            uniform("Aspect",aspect);uniform("HandSide",side);
            shader.safeGetUniform("MaskCenter").set(fistX,fistY);
            Matrix4f identity=new Matrix4f();
            if(!foreground&&age<114){
                draw(gate,new Matrix4f().translate(targetX,targetY,0).scale(.89f/aspect,.89f,1),identity,2,age);
            }else if((!foreground&&age>=114&&age<141)||(foreground&&age>=141)){
                draw(shards,identity,identity,3,age);
            }
            if(foreground&&age>=114&&age<144)draw(gate,identity,identity,5,age);
        }
    }
    @SubscribeEvent public static void render(RenderLevelStageEvent event){
        if(event.getStage()==RenderLevelStageEvent.Stage.AFTER_CUTOUT_BLOCKS){FateAnalysisPlacement.begin(event.getPoseStack().last().pose(),event.getCamera().getPosition());return;}
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
                var entity=mc.level.getEntity(cast.entity());if(!(entity instanceof Player player)||player.position().distanceToSqr(camera)>128*128)continue;
                boolean local=player==mc.player&&mc.options.getCameraType().isFirstPerson();
                if(maskPass&&local&&age<114){
                    FateAnalysisPlacement.local(player,partial,age);
                    if(age<80)drawLocalKey(player,partial,age);
                }
                Vec3 tip=FateAnalysisPlacement.tip(cast.owner());
                Vec3 anchor=tip==null?player.position():tip.add(0,-FateAnalysisPlacement.COLUMN_BOTTOM*FateAnalysisPlacement.SCALE,0);
                Matrix4f base=FateAnalysisPlacement.view().translate((float)(anchor.x-camera.x),(float)(anchor.y-camera.y),(float)(anchor.z-camera.z))
                        .rotateY(FateAnalysisPlacement.rotation(player.getViewYRot(partial))).scale(FateAnalysisPlacement.SCALE);
                if(maskPass&&age<114&&tip!=null){
                    RenderSystem.enableDepthTest();RenderSystem.disableCull();RenderSystem.disableBlend();RenderSystem.depthMask(true);draw(analysis,base,event.getProjectionMatrix(),0,age);
                    RenderSystem.enableBlend();RenderSystem.depthMask(false);draw(cube,base,event.getProjectionMatrix(),1,age);
                    Matrix4f orb=FateAnalysisPlacement.view().translate((float)(tip.x-camera.x),(float)(tip.y-camera.y),(float)(tip.z-camera.z)).rotate(event.getCamera().rotation()).scale(.19f);
                    FateOrbBloom.draw(orb,event.getProjectionMatrix(),age);

                }
                Vec3 forward=player.getLookAngle();Vec3 right=new Vec3(-forward.z,0,forward.x).normalize();
                boolean left=(player.getMainArm()==net.minecraft.world.entity.HumanoidArm.LEFT)^cast.offhand();
                Vec3 hand=FateAnalysisPlacement.grip(cast.owner());
                if(hand==null)hand=player.getEyePosition(partial).add(forward.scale(.85)).add(right.scale(left?-.28:.28)).add(0,-.25,0);
                uniform("HandSide",left?-1:1);
                if(worldPass&&!local&&age>=80&&age<114){
                    Vec3 target=hand.add(forward.scale(.16*(1-FateClient.step(age,80,100))));
                    Matrix4f billboard=new Matrix4f(event.getPoseStack().last().pose()).translate((float)(target.x-camera.x),(float)(target.y-camera.y),(float)(target.z-camera.z))
                            .rotate(event.getCamera().rotation()).scale(.48f);
                    draw(gate,billboard,event.getProjectionMatrix(),2,age);
                }
                // Other players see the same black surface break locally around the active hand.
                if(worldPass&&age>=114&&!local){
                    Vec3 surface=hand.add(hand.subtract(camera).normalize().scale(.105));
                    Matrix4f billboard=new Matrix4f(event.getPoseStack().last().pose()).translate((float)(surface.x-camera.x),(float)(surface.y-camera.y),(float)(surface.z-camera.z))
                            .rotate(event.getCamera().rotation()).scale(.65f);
                    var center=shader.getUniform("MaskCenter");if(center!=null)center.set(0f,0f);
                    draw(shards,billboard,event.getProjectionMatrix(),4,age);
                    if(age>=125&&age<144){
                        // The black plane sits behind the grip. Put the flash on its camera-facing skin surface.
                        Vec3 knuckles=hand.add(camera.subtract(hand).normalize().scale(.24));
                        Matrix4f flare=new Matrix4f(event.getPoseStack().last().pose())
                                .translate((float)(knuckles.x-camera.x),(float)(knuckles.y-camera.y),(float)(knuckles.z-camera.z))
                                .rotate(event.getCamera().rotation()).scale(.98f);
                        draw(gate,flare,event.getProjectionMatrix(),6,age);
                    }
                }
            }
            if(maskPass){FateOrbBloom.finish();FateAnalysisPlacement.end();}
        }
    }
    private static void drawLocalKey(Player player,float partial,float age){
        var mc=Minecraft.getInstance();var buffers=mc.renderBuffers().bufferSource();
        Vec3 grip=FateAnalysisPlacement.localGrip(player,partial,age);
        var pose=new PoseStack();pose.last().pose().set(FateAnalysisPlacement.model(grip,player.getViewYRot(partial)));
        pose.last().normal().set(new org.joml.Matrix3f(pose.last().pose()).invert().transpose());
        RenderSystem.depthMask(true);
        FateItemRenderer.draw(false,pose,buffers,net.minecraft.client.renderer.LightTexture.FULL_BRIGHT);
        boolean left=(player.getMainArm()==net.minecraft.world.entity.HumanoidArm.LEFT)^FateClient.CASTS.get(player.getUUID()).offhand();
        pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(-90));
        pose.translate((left?-1:1)*.3125,-.68,0);
        var renderer=(net.minecraft.client.renderer.entity.player.PlayerRenderer)mc.getEntityRenderDispatcher().getRenderer((net.minecraft.client.player.AbstractClientPlayer)player);
        if(left)renderer.renderLeftHand(pose,buffers,net.minecraft.client.renderer.LightTexture.FULL_BRIGHT,(net.minecraft.client.player.AbstractClientPlayer)player);
        else renderer.renderRightHand(pose,buffers,net.minecraft.client.renderer.LightTexture.FULL_BRIGHT,(net.minecraft.client.player.AbstractClientPlayer)player);
        buffers.endBatch();RenderSystem.depthMask(false);
    }
}
