package dev.starcrownqa;

import com.mojang.blaze3d.pipeline.*;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.vertex.VertexBuffer;
import dev.starcrown.*;
import java.nio.file.*;
import java.util.*;
import net.minecraft.client.*;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.opengl.*;

/** QA-only lifecycle checks on the default lite renderer and real server-created effects. */
@Mod.EventBusSubscriber(modid="starcrownqa",value=Dist.CLIENT)
public final class HuangwuLiteLifecycleQa {
    static boolean active,finished,reloadDone;static Throwable reloadError;static int frames,phase,wait;
    static Object get(String name)throws Exception{var f=HuangwuLiteRenderer.class.getDeclaredField(name);f.setAccessible(true);return f.get(null);}
    static void set(String name,Object value)throws Exception{var f=HuangwuLiteRenderer.class.getDeclaredField(name);f.setAccessible(true);f.set(null,value);}
    static void log(String s){try{Files.writeString(Minecraft.getInstance().gameDirectory.toPath().resolve("lite-lifecycle.txt"),s+"\n",StandardOpenOption.CREATE,StandardOpenOption.APPEND);}catch(Exception e){throw new RuntimeException(e);}}
    static void check(boolean b,String s){if(!b)throw new AssertionError(s);log("PASS "+s);}
    static void fail(Throwable t){log("FAIL "+t);t.printStackTrace();finished=true;Minecraft.getInstance().stop();}
    static void begin(){
        active=true;log("BEGIN real server vortex lifecycle");
        var mc=Minecraft.getInstance();mc.getSingleplayerServer().execute(()->{
            var l=mc.getSingleplayerServer().overworld();
            var dragon=StarcrownDoll.MINGGULONG.get().create(l);((net.minecraft.world.entity.Mob)dragon).setNoAi(true);((net.minecraft.world.entity.Entity)dragon).setNoGravity(true);((net.minecraft.world.entity.Entity)dragon).moveTo(-5,101,-5,0,0);l.addFreshEntity(dragon);
            HuangwuShaderQa.dragon=dragon;HuangwuShaderQa.field();
        });
    }
    static void camera(int frame){
        var mc=Minecraft.getInstance();double x=1+Math.sin(frame*.13)*2,z=frame<20?9:frame<40?2.2:6;
        double y=frame<40?103.3:101.2;float yaw=180+(float)Math.sin(frame*.13)*12;
        mc.getSingleplayerServer().execute(()->{var server=mc.getSingleplayerServer();var p=server.getPlayerList().getPlayers().get(0);p.teleportTo(server.overworld(),x,y,z,yaw,frame<40?11:3);});
    }
    static void blit(RenderTarget a,RenderTarget b){
        GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,a.frameBufferId);GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,b.frameBufferId);
        GL30.glBlitFramebuffer(0,0,a.width,a.height,0,0,b.width,b.height,GL11.GL_COLOR_BUFFER_BIT|GL11.GL_DEPTH_BUFFER_BIT,GL11.GL_NEAREST);b.bindWrite(true);
    }
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event){
        if(!active||finished||event.phase!=TickEvent.Phase.END)return;
        if(phase==0&&++wait%2==0)camera(Math.min(59,frames));
        if(phase==6){phase=7;var mc=Minecraft.getInstance();mc.level.disconnect();mc.clearLevel();phase=8;}
    }
    static RenderLevelStageEvent terrain;
    static void draw(RenderLevelStageEvent event){MingGuLongHuangwuVfx.render(terrain);MingGuLongHuangwuVfx.render(event);}
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void render(RenderLevelStageEvent event){
        if(!active||finished)return;
        if(event.getStage()==RenderLevelStageEvent.Stage.AFTER_PARTICLES){var pose=new com.mojang.blaze3d.vertex.PoseStack();pose.last().pose().set(event.getPoseStack().last().pose());terrain=new RenderLevelStageEvent(event.getStage(),event.getLevelRenderer(),pose,new org.joml.Matrix4f(event.getProjectionMatrix()),event.getRenderTick(),event.getPartialTick(),event.getCamera(),event.getFrustum());return;}
        if(event.getStage()!=RenderLevelStageEvent.Stage.AFTER_LEVEL||terrain==null)return;
        var mc=Minecraft.getInstance();
        try{
            if(phase==0){
                if(!HuangwuShaderQa.ready)return;
                if(frames==10)check(get("volumeTarget")!=null,"actual server vortex uses reduced integration target");
                if(frames==10||frames==30||frames==55)Screenshot.grab(mc.gameDirectory,"lite-movement-"+frames+".png",mc.getMainRenderTarget(),m->{});
                if(++frames<65){error();return;}
                log("PASS 65 frames with lateral movement, turning, low camera and near-plane crossing");
                var main=mc.getMainRenderTarget();int w=main.width,h=main.height;
                var copy=new TextureTarget(w,h,true,Minecraft.ON_OSX);blit(main,copy);
                main.resize(961,601,Minecraft.ON_OSX);blit(copy,main);draw(event);
                var small=(RenderTarget)get("volumeTarget");var depth=(RenderTarget)get("depthCopy");
                check(small.width==480&&small.height==300&&depth.width==961&&depth.height==601,"odd-sized resize rebuilds both targets");
                main.resize(w,h,Minecraft.ON_OSX);blit(copy,main);draw(event);copy.destroyBuffers();main.bindWrite(true);
                VfxClientConfig.HUANGWU_RESOLUTION.set(.33);draw(event);
                check(((RenderTarget)get("volumeTarget")).width==(int)Math.floor(w*.33),"low-GPU 0.33 option changes actual target");
                VfxClientConfig.HUANGWU_RESOLUTION.set(1.0);VfxClientConfig.HUANGWU_MAX_PIXELS.set(57600);draw(event);
                small=(RenderTarget)get("volumeTarget");check((long)small.width*small.height<=57600,"integration respects configured pixel budget");
                VfxClientConfig.HUANGWU_RESOLUTION.set(.5);VfxClientConfig.HUANGWU_MAX_PIXELS.set(360000);
                Object program=get("composite");set("composite",null);draw(event);set("composite",program);
                check(get("shader")!=null,"missing compositor retains native lite rendering");error();
                phase=1;mc.reloadResourcePacks().whenComplete((v,t)->{reloadError=t;reloadDone=true;});
            }else if(phase==1&&reloadDone&&mc.getOverlay()==null){
                if(reloadError!=null)throw new RuntimeException(reloadError);
                check(get("shader")!=null&&get("composite")!=null,"real resource-pack reload compiles fresh lite programs");
                phase=2;wait=0;HuangwuShaderQa.field();
            }else if(phase==2&&HuangwuShaderQa.ready&&++wait>20){
                check(get("volumeTarget")!=null,"server effect resumes after reload");error();
                Screenshot.grab(mc.gameDirectory,"lite-reloaded.png",mc.getMainRenderTarget(),m->{});
                HuangwuShaderQa.register(true);check(get("shader")==null,"fault injection selects basic fallback");phase=3;wait=0;
            }else if(phase==3&&++wait>20){
                error();Screenshot.grab(mc.gameDirectory,"lite-fallback.png",mc.getMainRenderTarget(),m->{});
                HuangwuShaderQa.register(false);check(get("shader")!=null,"shader recovers after injected compiler fault");phase=4;wait=0;HuangwuShaderQa.field();
            }else if(phase==4&&++wait>20){
                error();Screenshot.grab(mc.gameDirectory,"lite-restored.png",mc.getMainRenderTarget(),m->{});
                VfxClientConfig.HUANGWU_MODE.set(VfxClientConfig.HuangwuMode.OFF);phase=5;wait=0;
            }else if(phase==5&&++wait>10){
                error();Screenshot.grab(mc.gameDirectory,"lite-off.png",mc.getMainRenderTarget(),m->{});
                check(mc.level!=null&&mc.player.isAlive(),"OFF mode keeps world and gameplay running");VfxClientConfig.HUANGWU_MODE.set(VfxClientConfig.HuangwuMode.LITE);phase=6;
            }
        }catch(Throwable t){fail(t);}
    }
    static void error(){int e=GL11.glGetError();if(e!=GL11.GL_NO_ERROR)throw new AssertionError("GL error "+e);}
    @SubscribeEvent public static void end(TickEvent.RenderTickEvent event){
        if(!active||finished||phase!=8||event.phase!=TickEvent.Phase.END)return;
        try{check(get("depthCopy")==null&&get("volumeTarget")==null,"actual disconnect releases both targets");log("COMPLETE lite movement resize budget reload fallback recovery off disconnect");finished=true;Minecraft.getInstance().stop();}
        catch(Throwable t){fail(t);}
    }
}
