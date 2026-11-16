package dev.starcrownqa;

import dev.starcrown.*;
import java.nio.file.*;
import java.util.ArrayList;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.opengl.GL11;

@Mod.EventBusSubscriber(modid="starcrownqa",value=Dist.CLIENT)
public final class MingGuLongDeathQa {
    private static boolean started,failed,finished;
    private static volatile boolean ready;
    private static volatile int ticks,captures;
    private static MingGuLongEntity dragon;
    private static Path root(){return Minecraft.getInstance().gameDirectory.toPath();}
    private static boolean enabled(){return Files.exists(root().resolve("enable-dragon-death-qa"));}
    private static synchronized void log(String s){try{Files.writeString(root().resolve("death-results.txt"),s+"\n",StandardOpenOption.CREATE,StandardOpenOption.APPEND);}catch(Exception e){throw new RuntimeException(e);}}
    private static void check(boolean ok,String s){if(!ok)throw new AssertionError(s);log("PASS "+s);}
    private static void fail(Throwable e){failed=true;e.printStackTrace();log("FAIL "+e);Minecraft.getInstance().stop();}
    @SubscribeEvent public static void client(TickEvent.ClientTickEvent e){
        if(e.phase!=TickEvent.Phase.END||!enabled()||started||failed)return;var mc=Minecraft.getInstance();
        if(mc.level==null||mc.player==null||mc.screen!=null||mc.getSingleplayerServer()==null)return;
        started=true;log("GPU "+GL11.glGetString(GL11.GL_RENDERER));
        mc.getSingleplayerServer().execute(()->{try{
            var s=mc.getSingleplayerServer();var l=s.overworld();var p=s.getPlayerList().getPlayers().get(0);
            p.setGameMode(GameType.CREATIVE);l.setDayTime(6000);l.setWeatherParameters(6000,0,false,false);l.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false,s);
            var old=new ArrayList<Entity>();l.getAllEntities().forEach(old::add);for(var x:old)if(x instanceof Mob)x.discard();
            for(int x=-12;x<=12;x++)for(int z=-12;z<=12;z++){l.setBlockAndUpdate(new BlockPos(x,100,z),Blocks.SMOOTH_STONE.defaultBlockState());for(int y=101;y<=111;y++)l.setBlockAndUpdate(new BlockPos(x,y,z),Blocks.AIR.defaultBlockState());}
            dragon=StarcrownDoll.MINGGULONG.get().create(l);((Mob)dragon).setNoAi(true);((Entity)dragon).moveTo(0,101,0,90,0);l.addFreshEntity(dragon);
            p.teleportTo(l,9,104,10,138,14);ticks=0;ready=true;
        }catch(Throwable x){fail(x);}});
    }
    @SubscribeEvent public static void server(TickEvent.ServerTickEvent e){
        if(e.phase!=TickEvent.Phase.END||!enabled()||!ready||failed||finished)return;
        try{
            ticks++;
            if(ticks==79&&(captures&1)==0){ticks--;return;}
            if(ticks==80){var l=Minecraft.getInstance().getSingleplayerServer().overworld();((LivingEntity)dragon).hurt(l.damageSources().genericKill(),10000);check("Fight_Die".equals(dragon.getAnimationName()),"fatal hit starts Fight_Die");}
            if(ticks==101)check(!((Entity)dragon).isRemoved(),"server retains corpse beyond vanilla 20 ticks");
            if(ticks==140)check(!((Entity)dragon).isRemoved(),"server retains body through 60-tick clip");
            if(ticks==145)check(((Entity)dragon).isRemoved(),"server removes corpse after clip");
            if(ticks>=175){check(captures==31,"all five GPU states captured");finished=true;log("COMPLETE GPU death clip and delayed removal");Minecraft.getInstance().execute(()->Minecraft.getInstance().stop());}
        }catch(Throwable x){fail(x);}
    }
    @SubscribeEvent public static void render(TickEvent.RenderTickEvent e){
        if(e.phase!=TickEvent.Phase.END||!enabled()||!ready||failed||finished)return;
        int slot=ticks>=150?4:ticks>=135?3:ticks>=110?2:ticks>=87?1:ticks>=50&&ticks<80?0:-1;
        if(slot<0||(captures&(1<<slot))!=0)return;
        try{
            var mc=Minecraft.getInstance();var local=mc.level.getEntity(((Entity)dragon).getId());
            if(slot<4){check(local instanceof MingGuLongEntity,"client entity present at stage "+slot);if(slot>0)check("Fight_Die".equals(((MingGuLongEntity)local).getAnimationName()),"client plays death stage "+slot);}
            else check(local==null,"client removes corpse after completed clip");
            String[] names={"alive","death-start","death-middle","death-end","removed"};
            captures|=1<<slot;Screenshot.grab(mc.gameDirectory,"dragon-"+names[slot]+".png",mc.getMainRenderTarget(),m->{});
            log("GPU_CAPTURE "+names[slot]+" serverDeathTime="+((LivingEntity)dragon).deathTime);
        }catch(Throwable x){fail(x);}
    }
}
