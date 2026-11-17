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

/** Actual server-spawned effect, moving camera, odd-sized target, reload and disconnect acceptance. */
@Mod.EventBusSubscriber(modid="starcrownqa",value=Dist.CLIENT)
public final class HuangwuLifecycleQa {
    static boolean active,finished,reloadDone;static Throwable reloadError;static int frames,phase,wait;
    static List<VertexBuffer> oldBuffers;
    static Object get(String name)throws Exception{var f=MingGuLongHuangwuVfx.class.getDeclaredField(name);f.setAccessible(true);return f.get(null);}
    static void set(String name,Object value)throws Exception{var f=MingGuLongHuangwuVfx.class.getDeclaredField(name);f.setAccessible(true);f.set(null,value);}
    static void log(String s){try{Files.writeString(Minecraft.getInstance().gameDirectory.toPath().resolve("lifecycle-results.txt"),s+"\n",StandardOpenOption.CREATE,StandardOpenOption.APPEND);}catch(Exception e){throw new RuntimeException(e);}}
    static void check(boolean b,String s){if(!b)throw new AssertionError(s);log("PASS "+s);}
    static void fail(Throwable t){log("FAIL "+t);t.printStackTrace();finished=true;Minecraft.getInstance().stop();}
    static void begin(){active=true;HuangwuShaderQa.field();log("BEGIN actual server effect after shader-fault/recovery test");}
    static void camera(int frame){
        var mc=Minecraft.getInstance();double x=1+Math.sin(frame*.13)*2,z=frame<20?9:frame<40?2.2:6;
        double y=frame<40?103.3:101.2;
        float yaw=180+(float)Math.sin(frame*.13)*12;
        mc.getSingleplayerServer().execute(()->{var server=mc.getSingleplayerServer();var p=server.getPlayerList().getPlayers().get(0);p.teleportTo(server.overworld(),x,y,z,yaw,frame<40?11:3);});
    }
    static void blit(RenderTarget a,RenderTarget b){
        GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,a.frameBufferId);GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,b.frameBufferId);
        GL30.glBlitFramebuffer(0,0,a.width,a.height,0,0,b.width,b.height,GL11.GL_COLOR_BUFFER_BIT|GL11.GL_DEPTH_BUFFER_BIT,GL11.GL_NEAREST);b.bindWrite(true);
    }
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event){
        if(!active||finished||event.phase!=TickEvent.Phase.END)return;
        if(phase==0&&++wait%2==0)camera(Math.min(59,frames));
        if(phase==3){
            // Follow the menu's normal disconnect sequence, outside a world-render callback.
            // clearLevel may pump nested frames while waiting for the integrated server.
            phase=4;var mc=Minecraft.getInstance();mc.level.disconnect();mc.clearLevel();phase=5;
        }
    }
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void render(RenderLevelStageEvent event){
        if(!active||finished||event.getStage()!=RenderLevelStageEvent.Stage.AFTER_PARTICLES)return;
        var mc=Minecraft.getInstance();
        try{
            if(phase==0){
                if(!HuangwuShaderQa.ready)return;
                if(frames==0)check(get("volumeTarget")!=null,"actual server vortex uses reduced integration target");
                if(frames==10||frames==30||frames==55)Screenshot.grab(mc.gameDirectory,"movement-"+frames+".png",mc.getMainRenderTarget(),m->{});
                if(++frames<65){error();return;}
                log("PASS 65 frames with lateral movement, turning, low camera and near-plane crossing");
                var main=mc.getMainRenderTarget();int w=main.width,h=main.height;
                var copy=new TextureTarget(w,h,true,Minecraft.ON_OSX);blit(main,copy);
                main.resize(961,601,Minecraft.ON_OSX);blit(copy,main);MingGuLongHuangwuVfx.render(event);
                var small=(RenderTarget)get("volumeTarget");var depth=(RenderTarget)get("depthCopy");
                check(small.width==481&&small.height==301&&depth.width==961&&depth.height==601,"odd-sized resize rebuilds both integration/depth targets");
                Screenshot.grab(mc.gameDirectory,"resize-961x601.png",main,m->{});
                main.resize(w,h,Minecraft.ON_OSX);blit(copy,main);MingGuLongHuangwuVfx.render(event);copy.destroyBuffers();main.bindWrite(true);
                check(((RenderTarget)get("volumeTarget")).width==(int)Math.ceil(w*.5),"original dimensions restored");
                VfxClientConfig.HUANGWU_RESOLUTION.set(.33);MingGuLongHuangwuVfx.render(event);
                check(((RenderTarget)get("volumeTarget")).width==(int)Math.ceil(w*.33),"low-GPU 0.33 option changes actual attachment width");
                VfxClientConfig.HUANGWU_RESOLUTION.set(.5);Object program=get("composite");set("composite",null);MingGuLongHuangwuVfx.render(event);set("composite",program);
                check(get("shader")!=null,"missing reconstruction program retains native advanced volume");error();
                oldBuffers=new ArrayList<>();for(Object part:(List<?>)get("parts")){var method=part.getClass().getDeclaredMethod("buffer");method.setAccessible(true);oldBuffers.add((VertexBuffer)method.invoke(part));}
                phase=1;mc.reloadResourcePacks().whenComplete((v,t)->{reloadError=t;reloadDone=true;});
                log("REQUESTED actual Minecraft resource-pack reload");
            }else if(phase==1&&reloadDone&&mc.getOverlay()==null){
                if(reloadError!=null)throw new RuntimeException(reloadError);
                check(oldBuffers.stream().allMatch(VertexBuffer::isInvalid),"resource reload closes every old immutable mesh buffer");
                check((boolean)get("loaded")&&((List<?>)get("parts")).size()==72&&get("shader")!=null&&get("composite")!=null,"resource reload preloads fresh meshes and both shaders");
                phase=2;wait=0;HuangwuShaderQa.field();
            }else if(phase==2&&HuangwuShaderQa.ready&&++wait>20){
                check(get("volumeTarget")!=null,"server-spawned effect resumes after resource reload");error();
                Screenshot.grab(mc.gameDirectory,"resource-reloaded.png",mc.getMainRenderTarget(),m->{});
                phase=3;wait=0;
            }
        }catch(Throwable t){fail(t);}
    }
    static void error(){int e=GL11.glGetError();if(e!=GL11.GL_NO_ERROR)throw new AssertionError("GL error "+e);}
    @SubscribeEvent public static void end(TickEvent.RenderTickEvent event){
        if(!active||finished||phase!=5||event.phase!=TickEvent.Phase.END)return;
        try{check(get("depthCopy")==null&&get("volumeTarget")==null,"actual world disconnect releases both effect targets");log("COMPLETE movement, resize, low-GPU config, reconstruction fallback, resource reload and disconnect");finished=true;Minecraft.getInstance().stop();}
        catch(Throwable t){fail(t);}
    }
}
