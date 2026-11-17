package dev.starcrownqa;

import com.mojang.blaze3d.pipeline.*;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.starcrown.*;
import java.nio.file.*;
import java.lang.reflect.*;
import java.util.*;
import net.minecraft.client.*;
import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.opengl.*;

/** QA only: alternate actual native and reduced production draws against the same scene/depth/time. */
@Mod.EventBusSubscriber(modid="starcrownqa",value=Dist.CLIENT)
public final class HuangwuLiteQa {
    private record Scene(String name,int stage,int age,double x,double y,double z,float yaw,float pitch){}
    private static final Scene[] SCENES={
        new Scene("landing-close",2,60,1,103.3,9,180,11),
        new Scene("landing-far",2,60,1,104.2,22,180,5),
        new Scene("charge",0,30,1,103.3,9,180,11),
        new Scene("released",1,8,1,103.3,9,180,11),
        new Scene("near-clip",2,60,1.8,102.2,2.0,180,3),
        new Scene("ground-silhouette",2,60,4,101.1,9,158,4),
        new Scene("side",2,60,9,103,0,90,12),
        new Scene("above",2,60,0,111,1,180,82),
        new Scene("behind",2,60,0,103,-9,0,12),
        new Scene("collapse",2,207,1,103.3,9,180,11),
        new Scene("burst",2,220,1,103.3,9,180,11)
    };
    private record Query(int id,int scene,int mode){}
    private static final List<Query> queries=new ArrayList<>();
    private static final List<Double>[][] samples=new List[SCENES.length][2];
    private static boolean started,finished;private static volatile boolean ready;
    private static int scene,ticks,frames,tail;
    private static Entity fixture;
    private static RenderTarget saved;
    private static RenderLevelStageEvent terrain;
    private static Path root(){return Minecraft.getInstance().gameDirectory.toPath();}
    private static boolean enabled(){return Files.exists(root().resolve("enable-huangwu-lite-qa"));}
    private static void log(String s){try{Files.writeString(root().resolve("lite-results.txt"),s+"\n",StandardOpenOption.CREATE,StandardOpenOption.APPEND);}catch(Exception e){throw new RuntimeException(e);}}
    private static void check(boolean b,String s){if(!b)throw new AssertionError(s);log("PASS "+s);}
    private static Object field(String name)throws Exception{var f=HuangwuLiteRenderer.class.getDeclaredField(name);f.setAccessible(true);return f.get(null);}
    private static void fail(Throwable t){t.printStackTrace();log("FAIL "+t);finished=true;Minecraft.getInstance().stop();}
    private static void data(Entity e,String key,int value)throws Exception{
        var f=e.getClass().getDeclaredField(key);f.setAccessible(true);e.getEntityData().set((EntityDataAccessor<Integer>)f.get(null),value);
    }
    private static void configure(){
        ready=false;ticks=frames=0;
        var mc=Minecraft.getInstance();var s=SCENES[scene];
        if(fixture!=null){fixture.discard();fixture=null;}
        mc.getSingleplayerServer().execute(()->{try{
            var server=mc.getSingleplayerServer();var p=server.getPlayerList().getPlayers().get(0);
            p.teleportTo(server.overworld(),s.x,s.y,s.z,s.yaw,s.pitch);ready=true;
        }catch(Throwable t){fail(t);}});
    }
    private static void fixture()throws Exception{
        if(fixture!=null&&!fixture.isRemoved())return;
        var mc=Minecraft.getInstance();var s=SCENES[scene];
        fixture=s.stage==0?StarcrownDoll.MINGGULONG.get().create(mc.level):s.stage==1?new MingGuLongProjectile(StarcrownDoll.MINGGULONG_PROJECTILE.get(),mc.level):new MingGuLongVortex(StarcrownDoll.MINGGULONG_VORTEX.get(),mc.level);
        fixture.setId(900001+scene);fixture.moveTo(0,101,0,0,0);fixture.setNoGravity(true);
        if(fixture instanceof Mob m)m.setNoAi(true);
        if(s.stage==0){data(fixture,"ANIMATION",13);data(fixture,"ANIMATION_AGE",s.age);}
        else data(fixture,"AGE",s.age);
        fixture.xo=fixture.getX();fixture.yo=fixture.getY();fixture.zo=fixture.getZ();
        mc.level.putNonPlayerEntity(fixture.getId(),fixture);
    }
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event){
        if(event.phase!=TickEvent.Phase.END||!enabled()||finished)return;
        var mc=Minecraft.getInstance();
        try{
            if(mc.level==null||mc.player==null||mc.getSingleplayerServer()==null||mc.getOverlay()!=null||mc.screen!=null)return;
            if(!started){
                started=true;MinecraftForge.EVENT_BUS.unregister(MingGuLongHuangwuVfx.class);
                mc.options.pauseOnLostFocus=false;mc.options.hideGui=true;mc.options.bobView().set(false);mc.options.fov().set(70);mc.options.entityShadows().set(false);
                log("GPU "+GL11.glGetString(GL11.GL_RENDERER)+"; GL "+GL11.glGetString(GL11.GL_VERSION));
                log("native window "+mc.getMainRenderTarget().width+"x"+mc.getMainRenderTarget().height+"; steps=12; pair modes=1.0,0.5; warmup=12; samples=24 each; timer includes core/depth copy/fog/reconstruction; fixture ages fixed");
                check(field("shader")!=null&&field("composite")!=null,"both production shaders compile on actual GPU");
                var legacy=MingGuLongHuangwuVfx.class.getDeclaredField("parts");legacy.setAccessible(true);check(((List<?>)legacy.get(null)).isEmpty(),"default LITE loads zero legacy meshes");
                for(int i=0;i<samples.length;i++)for(int j=0;j<2;j++)samples[i][j]=new ArrayList<>();
                bounds();
                mc.getSingleplayerServer().execute(()->{try{
                    var server=mc.getSingleplayerServer();var l=server.overworld();var p=server.getPlayerList().getPlayers().get(0);
                    var old=new ArrayList<Entity>();l.getAllEntities().forEach(old::add);for(var e:old)if(!(e instanceof net.minecraft.world.entity.player.Player))e.discard();
                    p.setGameMode(GameType.CREATIVE);l.setDayTime(6000);l.setWeatherParameters(6000,0,false,false);l.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false,server);
                    l.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false,server);
                    for(int x=-12;x<=12;x++)for(int z=-8;z<=25;z++){
                        l.setBlockAndUpdate(new BlockPos(x,100,z),Blocks.SMOOTH_STONE.defaultBlockState());
                        for(int y=101;y<111;y++)l.setBlockAndUpdate(new BlockPos(x,y,z),Blocks.AIR.defaultBlockState());
                    }
                    for(int y=101;y<=104;y++)l.setBlockAndUpdate(new BlockPos(-2,y,3),Blocks.STONE_BRICKS.defaultBlockState());
                    var pig=EntityType.PIG.create(l);pig.moveTo(1.5,101,4,0,0);pig.setNoAi(true);pig.setInvulnerable(true);l.addFreshEntity(pig);
                    configure();
                }catch(Throwable t){fail(t);}});return;
            }
            if(ready&&scene<SCENES.length&&++ticks>35)fixture();
        }catch(Throwable t){fail(t);}
    }
    private static void blit(RenderTarget a,RenderTarget b){
        GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,a.frameBufferId);GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,b.frameBufferId);
        GL30.glBlitFramebuffer(0,0,a.width,a.height,0,0,b.width,b.height,GL11.GL_COLOR_BUFFER_BIT|GL11.GL_DEPTH_BUFFER_BIT,GL11.GL_NEAREST);b.bindWrite(true);
    }
    private static void poll(){
        queries.removeIf(q->{if(GL15.glGetQueryObjecti(q.id,GL15.GL_QUERY_RESULT_AVAILABLE)==0)return false;
            samples[q.scene][q.mode].add(GL33.glGetQueryObjectui64(q.id,GL15.GL_QUERY_RESULT)/1_000_000.0);GL15.glDeleteQueries(q.id);return true;});
    }
    private static int[] state(){
        int[] s=new int[29];int i=0;
        for(int key:new int[]{GL30.GL_DRAW_FRAMEBUFFER_BINDING,GL30.GL_READ_FRAMEBUFFER_BINDING,GL20.GL_CURRENT_PROGRAM,GL13.GL_ACTIVE_TEXTURE,GL30.GL_VERTEX_ARRAY_BINDING,GL15.GL_ARRAY_BUFFER_BINDING,GL11.GL_FRONT_FACE,GL11.GL_DEPTH_FUNC,GL14.GL_BLEND_SRC_RGB,GL14.GL_BLEND_DST_RGB,GL14.GL_BLEND_SRC_ALPHA,GL14.GL_BLEND_DST_ALPHA,GL20.GL_BLEND_EQUATION_RGB,GL20.GL_BLEND_EQUATION_ALPHA})s[i++]=GL11.glGetInteger(key);
        for(int key:new int[]{GL11.GL_BLEND,GL11.GL_DEPTH_TEST,GL11.GL_CULL_FACE,GL11.GL_SCISSOR_TEST})s[i++]=GL11.glIsEnabled(key)?1:0;
        s[i++]=GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK)?1:0;s[i++]=RenderSystem.getShaderTexture(0);s[i++]=RenderSystem.getShaderTexture(1);
        int[] v=new int[4];GL11.glGetIntegerv(GL11.GL_VIEWPORT,v);for(int n:v)s[i++]=n;
        GL11.glGetIntegerv(GL11.GL_SCISSOR_BOX,v);for(int n:v)s[i++]=n;return s;
    }
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void render(RenderLevelStageEvent event){
        if(!started||finished||!enabled())return;
        if(event.getStage()==RenderLevelStageEvent.Stage.AFTER_PARTICLES){var pose=new com.mojang.blaze3d.vertex.PoseStack();pose.last().pose().set(event.getPoseStack().last().pose());terrain=new RenderLevelStageEvent(event.getStage(),event.getLevelRenderer(),pose,new org.joml.Matrix4f(event.getProjectionMatrix()),event.getRenderTick(),event.getPartialTick(),event.getCamera(),event.getFrustum());return;}
        if(event.getStage()!=RenderLevelStageEvent.Stage.AFTER_LEVEL||terrain==null)return;
        try{
            poll();
            if(scene>=SCENES.length){if(++tail>10&&queries.isEmpty())complete();return;}
            if(!ready||ticks<=45||fixture==null)return;
            var mc=Minecraft.getInstance();var main=mc.getMainRenderTarget();
            if(saved==null||saved.width!=main.width||saved.height!=main.height){if(saved!=null)saved.destroyBuffers();saved=new TextureTarget(main.width,main.height,true,Minecraft.ON_OSX);}
            RenderSystem.disableScissor();blit(main,saved);
            var fixed=new RenderLevelStageEvent(terrain.getStage(),terrain.getLevelRenderer(),terrain.getPoseStack(),terrain.getProjectionMatrix(),terrain.getRenderTick(),0,terrain.getCamera(),terrain.getFrustum());
            for(int j=0;j<2;j++){
                int mode=(frames%2==0)?j:1-j;blit(saved,main);
                VfxClientConfig.HUANGWU_RESOLUTION.set(mode==0?1.0:0.5);
                VfxClientConfig.HUANGWU_MAX_PIXELS.set(8294400);
                int[] before=state();int q=0;
                if(frames>=12){q=GL15.glGenQueries();GL15.glBeginQuery(GL33.GL_TIME_ELAPSED,q);}
                MingGuLongHuangwuVfx.render(fixed);
                MingGuLongHuangwuVfx.render(event);
                if(q!=0){GL15.glEndQuery(GL33.GL_TIME_ELAPSED);queries.add(new Query(q,scene,mode));}
                if(frames==13)check(Arrays.equals(before,state()),SCENES[scene].name+" mode "+mode+" restores all checked GL states");
                if(frames==14)Screenshot.grab(mc.gameDirectory,SCENES[scene].name+(mode==0?"-native.png":"-half.png"),main,m->{});
            }
            checkError();
            if(++frames>=36){log("CAPTURED "+SCENES[scene].name);fixture.discard();fixture=null;scene++;if(scene<SCENES.length)configure();}
        }catch(Throwable t){fail(t);}
    }
    private static void checkError(){int error=GL11.glGetError();if(error!=GL11.GL_NO_ERROR)throw new AssertionError("GL error "+error);}
    private static void bounds()throws Exception{
        Class<?> c=Class.forName("dev.starcrown.HuangwuScreenBounds");var project=c.getDeclaredMethod("project",Vector3f.class,Vector3f.class,Matrix4f.class,Matrix4f.class,int.class,int.class);project.setAccessible(true);
        var projection=new Matrix4f().perspective((float)Math.toRadians(70),1.6f,.05f,200f);
        Vector3f lo=new Vector3f(-1),hi=new Vector3f(1);
        check(project.invoke(null,lo,hi,new Matrix4f().translation(100,0,-5),projection,1280,800)==null,"offscreen box culled");
        check(project.invoke(null,lo,hi,new Matrix4f().translation(0,0,5),projection,1280,800)==null,"box entirely behind camera culled");
        check(project.invoke(null,lo,hi,new Matrix4f().translation(0,0,-.8f),projection,1280,800)!=null,"near-plane crossing box retained");
        check(project.invoke(null,lo,hi,new Matrix4f(),projection,1280,800)!=null,"camera inside proxy retained");
        Object b=project.invoke(null,lo,hi,new Matrix4f().translation(0,0,-5),projection,1280,800);
        var width=c.getDeclaredMethod("width");width.setAccessible(true);check(b!=null&&(int)width.invoke(b)<1280,"ordinary bound does not fall back to fullscreen");
    }
    private static void complete()throws Exception{
        for(int i=0;i<samples.length;i++){
            for(int j=0;j<2;j++){Collections.sort(samples[i][j]);check(samples[i][j].size()==24,SCENES[i].name+" mode "+j+" has 24 actual GPU queries");}
            double a=median(samples[i][0]),b=median(samples[i][1]);log(String.format(Locale.ROOT,"RESULT %s native=%.4f ms half=%.4f ms reduction=%.2f%% nativeSamples=%s halfSamples=%s",SCENES[i].name,a,b,(1-b/a)*100,samples[i][0],samples[i][1]));
        }
        VfxClientConfig.HUANGWU_RESOLUTION.set(.5);saved.destroyBuffers();saved=null;
        log("COMPLETE paired GPU timing; screenshot native vs half; no production timer hooks");finished=true;
        VfxClientConfig.HUANGWU_MAX_PIXELS.set(360000);
        MinecraftForge.EVENT_BUS.register(MingGuLongHuangwuVfx.class);
        HuangwuLiteLifecycleQa.begin();
    }
    private static double median(List<Double> x){return (x.get(11)+x.get(12))*.5;}
}
