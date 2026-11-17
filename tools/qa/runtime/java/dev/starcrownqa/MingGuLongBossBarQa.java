package dev.starcrownqa;

import dev.starcrown.*;
import java.nio.file.*;
import java.util.ArrayList;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.opengl.GL11;

/** Opt-in hidden GPU client: exercises production networking and the real HUD. */
@Mod.EventBusSubscriber(modid="starcrownqa",value=Dist.CLIENT)
public final class MingGuLongBossBarQa {
    private static boolean started,failed,finished;
    private static volatile int ticks,captures;
    private static volatile boolean ready;
    private static MingGuLongEntity dragon,second;
    private static Path root(){return Minecraft.getInstance().gameDirectory.toPath();}
    private static boolean enabled(){return Files.exists(root().resolve("enable-dragon-bossbar-qa"));}
    private static synchronized void log(String text){try{Files.writeString(root().resolve("bossbar-results.txt"),text+"\n",StandardOpenOption.CREATE,StandardOpenOption.APPEND);}catch(Exception e){throw new RuntimeException(e);}}
    private static void check(boolean ok,String text){if(!ok)throw new AssertionError(text);log("PASS "+text);}
    private static void fail(Throwable error){failed=true;error.printStackTrace();log("FAIL "+error);Minecraft.getInstance().stop();}
    @SuppressWarnings("unchecked")
    private static java.util.Map<java.util.UUID,net.minecraft.client.gui.components.LerpingBossEvent> bars() throws Exception {
        var overlay=Minecraft.getInstance().gui.getBossOverlay();
        for(var field:overlay.getClass().getDeclaredFields())if(java.util.Map.class.isAssignableFrom(field.getType())){
            field.setAccessible(true);return (java.util.Map<java.util.UUID,net.minecraft.client.gui.components.LerpingBossEvent>)field.get(overlay);
        }
        throw new AssertionError("Boss overlay map missing");
    }
    private static MingGuLongEntity spawn(ServerLevel level,double x){
        var d=StarcrownDoll.MINGGULONG.get().create(level);
        ((Mob)d).setNoAi(true);((Entity)d).setNoGravity(true);((Entity)d).moveTo(x,101,0,180,0);level.addFreshEntity(d);return d;
    }
    @SubscribeEvent public static void client(TickEvent.ClientTickEvent event){
        if(event.phase!=TickEvent.Phase.END||!enabled())return;
        var mc=Minecraft.getInstance();
        // Only the opt-in acceptance client hides its own GLFW window; framebuffer rendering stays active.
        if(Files.exists(root().resolve("hide-qa-window")))org.lwjgl.glfw.GLFW.glfwHideWindow(mc.getWindow().getWindow());
        if(started||failed||mc.level==null||mc.player==null||mc.getSingleplayerServer()==null||mc.screen!=null)return;
        started=true;log("GPU "+GL11.glGetString(GL11.GL_RENDERER));
        mc.getSingleplayerServer().execute(()->{try{
            var server=mc.getSingleplayerServer();var l=server.overworld();var p=server.getPlayerList().getPlayers().get(0);
            DivineAnchor.unlock(p);DivinityInventory.force(p,net.minecraft.world.item.ItemStack.EMPTY);p.getInventory().armor.set(3,net.minecraft.world.item.ItemStack.EMPTY);DivineWill.update(p,false);DivineSeven.release(p);p.removeAllEffects();p.setGameMode(GameType.CREATIVE);
            l.setDayTime(6000);l.setWeatherParameters(6000,0,false,false);l.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false,server);
            var old=new ArrayList<Entity>();l.getAllEntities().forEach(old::add);for(var e:old)if(e instanceof Mob||e instanceof MingGuLongVortex||e instanceof MingGuLongProjectile)e.discard();
            for(int x=-9;x<=9;x++)for(int z=-5;z<=15;z++){l.setBlockAndUpdate(new BlockPos(x,100,z),Blocks.SMOOTH_STONE.defaultBlockState());for(int y=101;y<=110;y++)l.setBlockAndUpdate(new BlockPos(x,y,z),Blocks.AIR.defaultBlockState());}
            p.teleportTo(l,0,103.5,12,180,12);dragon=spawn(l,0);ticks=0;ready=true;
        }catch(Throwable e){fail(e);}});
    }
    @SubscribeEvent public static void server(TickEvent.ServerTickEvent event){
        if(event.phase!=TickEvent.Phase.END||!enabled()||!ready||failed||finished)return;
        try{
            ticks++;
            if(ticks==59&&(captures&1)==0){ticks--;return;}
            if(ticks==60)((LivingEntity)dragon).setHealth(333);
            if(ticks==119&&(captures&2)==0){ticks--;return;}
            if(ticks==120)((LivingEntity)dragon).setHealth(166.5F);
            if(ticks==179&&(captures&4)==0){ticks--;return;}
            if(ticks==180)((LivingEntity)dragon).heal(333);
            if(ticks==239&&(captures&8)==0){ticks--;return;}
            if(ticks==240)((LivingEntity)dragon).setHealth(0);
            if(ticks==299&&(captures&16)==0){ticks--;return;}
            if(ticks==300){var l=Minecraft.getInstance().getSingleplayerServer().overworld();dragon=spawn(l,-3);second=spawn(l,3);((LivingEntity)second).setHealth(333);((Entity)second).setCustomName(Component.literal("Second Dragon"));}
            if(ticks==359&&(captures&32)==0){ticks--;return;}
            if(ticks==360){((Entity)dragon).discard();((Entity)second).discard();}
            if(ticks==419&&(captures&64)==0){ticks--;return;}
            if(ticks>=440){finished=true;log("COMPLETE live full/half/quarter/heal/death/two dragons/removal");Minecraft.getInstance().execute(()->Minecraft.getInstance().stop());}
        }catch(Throwable e){fail(e);}
    }
    @SubscribeEvent public static void render(TickEvent.RenderTickEvent event){
        if(event.phase!=TickEvent.Phase.END||!enabled()||!ready||failed||finished)return;
        int stage=ticks/60,bit=1<<stage;if(ticks%60<35||stage>6||(captures&bit)!=0)return;
        try{
            var mc=Minecraft.getInstance();var bars=bars();
            String[] shots={"full","half","quarter","healed","dead","two-dragons","removed"};
            if(stage==4||stage==6)check(bars.isEmpty(),shots[stage]+" clears client bars");
            else {
                check(bars.size()==(stage==5?2:1),shots[stage]+" correct number of networked boss bars");
                if(stage<4){float expected=new float[]{1,.5F,.25F,.75F}[stage];check(Math.abs(bars.values().iterator().next().getProgress()-expected)<.002F,shots[stage]+" client progress="+expected);}
            }
            captures|=bit;
            Screenshot.grab(mc.gameDirectory,"bossbar-"+shots[stage]+".png",mc.getMainRenderTarget(),m->{});
            log("GPU_CAPTURE "+shots[stage]);
        }catch(Throwable e){fail(e);}
    }
}
