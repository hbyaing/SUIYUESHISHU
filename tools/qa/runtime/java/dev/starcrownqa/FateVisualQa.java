package dev.starcrownqa;

import dev.starcrown.*;
import java.nio.file.*;
import java.util.*;
import net.minecraft.client.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.core.BlockPos;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.opengl.GL11;

@Mod.EventBusSubscriber(modid="starcrownqa",value=Dist.CLIENT)
public final class FateVisualQa {
    static int tick,lastShot=-1,phase;static boolean setup,started,finished;static volatile boolean ready;
    static Path root(){return Minecraft.getInstance().gameDirectory.toPath();}
    static void log(String s){try{Files.writeString(root().resolve("fate-visual-results.txt"),s+"\n",StandardOpenOption.CREATE,StandardOpenOption.APPEND);}catch(Exception e){throw new RuntimeException(e);}}
    @SubscribeEvent public static void client(TickEvent.ClientTickEvent event){
        if(event.phase!=TickEvent.Phase.END||finished||!Files.exists(root().resolve("enable-fate-qa")))return;
        var mc=Minecraft.getInstance();mc.options.pauseOnLostFocus=false;org.lwjgl.glfw.GLFW.glfwHideWindow(mc.getWindow().getWindow());
        if(mc.getOverlay()==null&&mc.screen instanceof net.minecraft.client.gui.screens.ConfirmScreen){
            mc.screen.children().stream().filter(w->w instanceof net.minecraft.client.gui.components.Button).map(w->(net.minecraft.client.gui.components.Button)w).filter(b->b.getMessage().getString().equals("Proceed")).findFirst().ifPresent(b->b.onPress());return;
        }
        if(mc.level==null||mc.player==null||mc.getSingleplayerServer()==null||mc.getOverlay()!=null)return;
        try{
            if(!setup){setup=true;mc.setScreen(null);mc.mouseHandler.releaseMouse();mc.options.hideGui=false;mc.options.setCameraType(CameraType.FIRST_PERSON);log("GPU "+GL11.glGetString(GL11.GL_RENDERER));
                var f=FateVfx.class.getDeclaredField("shader");f.setAccessible(true);if(f.get(null)==null)throw new AssertionError("production shader not loaded");log("PASS production shader registered");
                for(String mesh:List.of("key","ea")){var m=FateItemRenderer.class.getDeclaredField(mesh);m.setAccessible(true);if(((List<?>)m.get(null)).isEmpty())throw new AssertionError(mesh+" mesh not loaded");}log("PASS both production meshes loaded");
                var id=mc.player.getUUID();mc.getSingleplayerServer().execute(()->{
                    var p=mc.getSingleplayerServer().getPlayerList().getPlayer(id);var l=p.serverLevel();
                    l.setDayTime(18000);l.setWeatherParameters(6000,0,false,false);
                    l.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_DOMOBSPAWNING).set(false,l.getServer());
                    for(int x=-14;x<=14;x++)for(int z=-14;z<=14;z++)l.setBlockAndUpdate(new BlockPos(x,99,z),net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
                    p.setGameMode(GameType.CREATIVE);p.teleportTo(l,.5,100,7,180,-20);p.getInventory().clearContent();p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(StarcrownDoll.BAB_ILU.get()));ready=true;
                });return;
            }
            if(!ready)return;tick++;
            var usedHand=phase==0?InteractionHand.MAIN_HAND:InteractionHand.OFF_HAND;
            if(!started&&tick>=25&&mc.player.getItemInHand(usedHand).is(StarcrownDoll.BAB_ILU.get())){
                mc.options.keyUse.setDown(true);mc.gameMode.useItem(mc.player,usedHand);started=true;tick=0;log("START actual client item use phase="+phase);
            }
            if(started)mc.options.keyUse.setDown(tick<165);
            if(started&&phase==1&&tick<80){
                if(tick==20||tick==40||tick==60){mc.player.setYRot((tick/20-1)*90);mc.player.setYHeadRot(mc.player.getYRot());}
            }
            if(started&&tick==53)FateOrbBloom.enabled=false;
            if(started&&tick==55)FateOrbBloom.enabled=true;
            if(started&&tick==180){
                if(!mc.player.getItemInHand(usedHand).is(StarcrownDoll.EA.get()))throw new AssertionError("actual server completion not synchronized to held sword");
                for(int i=0;i<mc.player.getInventory().getContainerSize();i++)if(mc.player.getInventory().getItem(i).is(StarcrownDoll.BAB_ILU.get()))throw new AssertionError("summoned sword retained a key");
                if(FateOrbBloom.composites==0)throw new AssertionError("orb bloom never composited");
                net.minecraft.world.item.CreativeModeTabs.tryRebuildTabContents(mc.level.enabledFeatures(),true,mc.level.registryAccess());
                for(var tab:net.minecraft.world.item.CreativeModeTabs.allTabs()){
                    if(tab.getDisplayItems().stream().anyMatch(i->i.is(StarcrownDoll.EA.get()))||tab.getSearchTabDisplayItems().stream().anyMatch(i->i.is(StarcrownDoll.EA.get())))throw new AssertionError("Ea visible in creative tab/search");
                }
                log("PASS key consumed, Ea absent from all creative tabs/search, orb bloom ran phase="+phase);
                log("PASS actual eight-second use grants held sword phase="+phase);
                if(phase==0)mc.setScreen(new net.minecraft.client.gui.screens.inventory.InventoryScreen(mc.player));
            }
            if(started&&tick>=200){
                if(phase==1){log("COMPLETE actual renderer, reload, main-hand and offhand network use");finished=true;mc.stop();return;}
                phase=1;tick=0;lastShot=-1;started=false;ready=false;mc.setScreen(null);mc.options.keyUse.setDown(false);
                mc.reloadResourcePacks().whenComplete((ignored,error)->mc.execute(()->{try{
                    if(error!=null)throw new RuntimeException(error);
                    var f=FateVfx.class.getDeclaredField("shader");f.setAccessible(true);if(f.get(null)==null)throw new AssertionError("shader missing after real resource reload");log("PASS actual resource reload retains production shader");
                    mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);var id=mc.player.getUUID();
                    mc.getSingleplayerServer().execute(()->{var p=mc.getSingleplayerServer().getPlayerList().getPlayer(id);p.getInventory().clearContent();p.getCooldowns().removeCooldown(StarcrownDoll.BAB_ILU.get());p.teleportTo(p.serverLevel(),.5,100,7,180,0);p.serverLevel().setDayTime(6000);p.setItemInHand(InteractionHand.OFF_HAND,new ItemStack(StarcrownDoll.BAB_ILU.get()));ready=true;});
                }catch(Throwable e){log("FAIL "+e);finished=true;mc.stop();}}));
            }
        }catch(Throwable e){log("FAIL "+e);e.printStackTrace();finished=true;mc.stop();}
    }
    @SubscribeEvent public static void camera(net.minecraftforge.client.event.ViewportEvent.ComputeCameraAngles event){
        if(!started||phase!=1||tick<65||tick>=80)return;
        try{var method=net.minecraft.client.Camera.class.getDeclaredMethod("move",double.class,double.class,double.class);method.setAccessible(true);method.invoke(event.getCamera(),-24d,9d,0d);}
        catch(Exception error){throw new RuntimeException(error);}
    }
    @SubscribeEvent public static void gui(net.minecraftforge.client.event.RenderGuiEvent.Pre event){if(setup&&!finished)event.setCanceled(true);}
    @SubscribeEvent public static void render(TickEvent.RenderTickEvent event){
        if(event.phase!=TickEvent.Phase.END||!started||finished||tick==lastShot)return;
        if(!Set.of(5,35,54,55,70,95,120,136,144,149,158,175,190).contains(tick))return;
        lastShot=tick;var mc=Minecraft.getInstance();int error=GL11.glGetError();log("CAPTURE "+tick+" GL="+error);
        Screenshot.grab(mc.gameDirectory,(phase==0?"fate-":"fate-third-")+tick+".png",mc.getMainRenderTarget(),m->{});
    }
}
