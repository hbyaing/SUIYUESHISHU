package dev.starcrownqa;

import dev.starcrown.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.io.ByteArrayInputStream;
import java.util.*;
import java.util.function.Consumer;
import com.mojang.datafixers.util.Pair;
import com.google.gson.JsonParser;
import net.minecraft.client.*;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RegisterShadersEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.api.distmarker.Dist;
import org.lwjgl.opengl.GL11;

/** Exercises the production constructor, failure handling, fallback draw and recovery. QA-only. */
@Mod.EventBusSubscriber(modid="starcrownqa",value=Dist.CLIENT)
public final class HuangwuShaderQa {
    static boolean started,finished,shot;static volatile boolean ready;static int phase,ticks;
    static MingGuLongEntity dragon;static MingGuLongVortex vortex;
    static Path root(){return Minecraft.getInstance().gameDirectory.toPath();}
    static void log(String s){try{Files.writeString(root().resolve("shader-results.txt"),s+"\n",StandardOpenOption.CREATE,StandardOpenOption.APPEND);}catch(Exception e){throw new RuntimeException(e);}}
    static void check(boolean b,String s){if(!b)throw new AssertionError(s);log("PASS "+s);}
    static void fail(Throwable t){t.printStackTrace();log("FAIL "+t);finished=true;Minecraft.getInstance().stop();}
    static ShaderInstance shader()throws Exception {var f=MingGuLongHuangwuVfx.class.getDeclaredField("shader");f.setAccessible(true);return (ShaderInstance)f.get(null);}
    static void field(){
        ready=false;Minecraft.getInstance().getSingleplayerServer().execute(()->{try {
            var mc=Minecraft.getInstance();var l=mc.getSingleplayerServer().overworld();
            if(vortex!=null)((Entity)vortex).discard();
            vortex=new MingGuLongVortex(StarcrownDoll.MINGGULONG_VORTEX.get(),l,dragon,new Vec3(1,101,0),777+phase);
            l.addFreshEntity(vortex);ticks=0;ready=true;
        }catch(Throwable e){fail(e);}});
    }
    static void register(boolean broken)throws Exception {
        var resources=Minecraft.getInstance().getResourceManager();ResourceProvider provider=resources;
        if(broken){
            var base=resources.getResourceOrThrow(new ResourceLocation(StarcrownDoll.ID,"shaders/core/huangwu.json"));
            String json;try(var reader=base.openAsReader()){var object=JsonParser.parseReader(reader).getAsJsonObject();object.addProperty("fragment","starcrowndoll:huangwu_qa_broken");json=object.toString();}
            String changed=json;
            provider=id->{
                String value=id.getPath().equals("shaders/core/huangwu.json")?changed:
                    id.getPath().equals("shaders/core/huangwu_qa_broken.fsh")?"#version 150\nout vec4 fragColor;\nvoid main(){ this_is_a_deliberate_shader_fault; }\n":null;
                if(value!=null&&id.getNamespace().equals(StarcrownDoll.ID))return Optional.of(new Resource(base.source(),()->new ByteArrayInputStream(value.getBytes(StandardCharsets.UTF_8))));
                return resources.getResource(id);
            };
        }
        var programs=new ArrayList<Pair<ShaderInstance,Consumer<ShaderInstance>>>();
        MingGuLongHuangwuVfx.registerShaders(new RegisterShadersEvent(provider,programs));
        for(var pair:programs)pair.getSecond().accept(pair.getFirst());
        check(broken?shader()==null:shader()!=null,broken?"compiler failure is caught; basic geometry mode selected":"shader registration recovers after a previous failure");
    }
    @SubscribeEvent public static void client(TickEvent.ClientTickEvent event){
        if(event.phase!=TickEvent.Phase.END||!Files.exists(root().resolve("enable-huangwu-shader-qa"))||finished)return;
        var mc=Minecraft.getInstance();org.lwjgl.glfw.GLFW.glfwHideWindow(mc.getWindow().getWindow());mc.options.pauseOnLostFocus=false;
        try {
            if(mc.level==null||mc.player==null||mc.getSingleplayerServer()==null||mc.getOverlay()!=null||mc.screen!=null)return;
            if(!started){started=true;log("GPU "+GL11.glGetString(GL11.GL_RENDERER));check(shader()!=null,"fixed GLSL compiles during real production-JAR startup");
                mc.getSingleplayerServer().execute(()->{try {
                    var l=mc.getSingleplayerServer().overworld();var p=mc.getSingleplayerServer().getPlayerList().getPlayers().get(0);
                    var old=new ArrayList<Entity>();l.getAllEntities().forEach(old::add);for(var e:old)if(e instanceof Mob||e instanceof MingGuLongVortex||e instanceof MingGuLongProjectile)e.discard();
                    l.setDayTime(6000);l.setWeatherParameters(6000,0,false,false);l.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false,l.getServer());
                    for(int x=-9;x<=9;x++)for(int z=-6;z<=12;z++){l.setBlockAndUpdate(new BlockPos(x,100,z),Blocks.SMOOTH_STONE.defaultBlockState());for(int y=101;y<110;y++)l.setBlockAndUpdate(new BlockPos(x,y,z),Blocks.AIR.defaultBlockState());}
                    p.setGameMode(GameType.CREATIVE);p.teleportTo(l,1,103.3,9,180,11);
                    dragon=StarcrownDoll.MINGGULONG.get().create(l);((Entity)dragon).moveTo(-3,101,0,0,0);((Mob)dragon).setNoAi(true);((Entity)dragon).setNoGravity(true);l.addFreshEntity(dragon);field();
                }catch(Throwable e){fail(e);}});return;
            }
            if(ready&&++ticks>=50)shot=true;
        }catch(Throwable e){fail(e);}
    }
    @SubscribeEvent public static void render(TickEvent.RenderTickEvent event){
        if(event.phase!=TickEvent.Phase.END||!shot||finished)return;shot=false;
        try {
            var mc=Minecraft.getInstance();check(GL11.glGetError()==GL11.GL_NO_ERROR,"phase "+phase+" renders without GL errors");
            Screenshot.grab(mc.gameDirectory,"shader-"+new String[]{"advanced","fallback","restored"}[phase]+".png",mc.getMainRenderTarget(),m->{});
            log("GPU_CAPTURE phase "+phase);
            if(phase==0){phase=1;register(true);field();}
            else if(phase==1){check(mc.player.isAlive()&&mc.level!=null,"world and player continue running with compiler failure");phase=2;register(false);field();}
            else {finished=true;log("COMPLETE advanced shader, injected compile failure, vanilla fallback and recovery");
                if(Files.exists(root().resolve("enable-huangwu-lifecycle-qa")))HuangwuLifecycleQa.begin();else mc.stop();}
        }catch(Throwable e){fail(e);}
    }
}
