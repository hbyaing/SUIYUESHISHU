package dev.starcrownqa;

import dev.starcrown.*;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.function.Supplier;
import net.minecraft.client.*;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.opengl.GL11;

/** Opt-in real Iron's casting, isolated from the user's client and save. */
@Mod.EventBusSubscriber(modid="starcrownqa",value=Dist.CLIENT)
public final class DollCastingCameraQa {
    private static final String[] NAMES={"no_doll","chengran","twilight","yule","calm","self","curios_chengran"};
    private static int stage,ticks,passes,blockedHeads,armFrames,bootTicks,prePlayers,preLiving,canceledLiving;
    private static boolean started,failed,complete,shot,sceneBuilt;
    private static volatile boolean ready;
    private static Path root(){return Minecraft.getInstance().gameDirectory.toPath();}
    private static boolean enabled(){return Files.exists(root().resolve("enable-doll-camera-qa"));}
    private static boolean baseline(){return Files.exists(root().resolve("camera-qa-baseline"));}
    private static boolean noIron(){return Files.exists(root().resolve("camera-qa-no-iron"));}
    private static synchronized void log(String s){try{Files.writeString(root().resolve("camera-results.txt"),s+"\n",StandardOpenOption.CREATE,StandardOpenOption.APPEND);}catch(Exception e){throw new RuntimeException(e);}}
    private static void fail(Throwable e){failed=true;e.printStackTrace();log("FAIL "+e);Minecraft.getInstance().stop();}
    private static Object call(Object target,String name,Object... args)throws Exception{
        var type=target instanceof Class<?> c?c:target.getClass();
        for(var m:type.getMethods()){
            if(!m.getName().equals(name)||m.getParameterCount()!=args.length)continue;
            try{return m.invoke(target instanceof Class<?>?null:target,args);}catch(IllegalArgumentException mismatch){}
        }
        throw new NoSuchMethodException(type.getName()+"."+name);
    }
    private static Class<?> iron(String name)throws Exception{return Class.forName("io.redspace.ironsspellbooks."+name);}
    private static boolean pass()throws Exception{return (boolean)call(Class.forName("dev.kosmx.playerAnim.api.firstPerson.FirstPersonMode"),"isFirstPersonPass");}
    private static Item item(int index){return switch(index){case 0->Items.AIR;case 2->StarcrownDoll.TWILIGHT_ITEM.get();case 3->StarcrownDoll.YULE_ITEM.get();case 4->StarcrownDoll.CALM_ITEM.get();case 5->StarcrownDoll.SELF_ITEM.get();default->StarcrownDoll.ITEM.get();};}
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event){
        if(event.phase!=TickEvent.Phase.END||!enabled()||failed||complete)return;
        var mc=Minecraft.getInstance();
        if(mc.player==null||mc.level==null||mc.getSingleplayerServer()==null||mc.getOverlay()!=null){
            if(mc.getOverlay()==null&&++bootTicks%40==0&&mc.screen!=null){
                log("BOOT_SCREEN "+mc.screen.getClass().getName()+" title="+mc.screen.getTitle().getString());
                Screenshot.grab(mc.gameDirectory,"boot-screen.png",mc.getMainRenderTarget(),m->{});
                var buttons=mc.screen.children().stream().filter(w->w instanceof net.minecraft.client.gui.components.Button).map(w->(net.minecraft.client.gui.components.Button)w).toList();
                for(int i=0;i<buttons.size();i++)log("BOOT_BUTTON "+i+" "+buttons.get(i).getMessage().getString());
                // Only a one-shot command supplied for this isolated QA game directory.
                var command=root().resolve("camera-boot-button.txt");
                if(Files.exists(command))try{int index=Integer.parseInt(Files.readString(command).trim());Files.delete(command);buttons.get(index).onPress();}catch(Exception e){fail(e);}
            }
            return;
        }
        try{
            if(!started){started=true;stage=1;mc.options.pauseOnLostFocus=false;mc.setScreen(null);log("GPU="+GL11.glGetString(GL11.GL_RENDERER));}
            if(ticks==0){setup();ticks++;return;}
            if(!ready)return;ticks++;
            if(ticks==25){
                if(!DivinityInventory.equipped(mc.player).is(item(stage)))throw new AssertionError("QA equipment did not sync: "+NAMES[stage]+" actual="+DivinityInventory.equipped(mc.player));
                if(!noIron())cast();
            }
            if(ticks==45)capture("first_person");
            if(ticks==55)mc.player.setXRot(-35);
            if(ticks==65)capture("look_up");
            if(ticks==75)mc.player.setXRot(40);
            if(ticks==85)capture("look_down");
            if(ticks==95){mc.player.setXRot(0);mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);}
            if(ticks==110)capture("third_person_cast");
            if(ticks==120){cancel();mc.options.setCameraType(CameraType.FIRST_PERSON);mc.setScreen(new InventoryScreen(mc.player));}
            if(ticks==135)capture("inventory");
            if(ticks==145){
                log("RESULT "+NAMES[stage]+" passes="+passes+" visible_head_frames="+blockedHeads+" visible_arm_frames="+armFrames+" playerPre="+prePlayers+" livingPre="+preLiving+" livingCanceled="+canceledLiving);
                if(!baseline()&&!noIron()&&(passes==0||blockedHeads!=0||armFrames==0))throw new AssertionError("Casting body visibility "+NAMES[stage]);
                if(noIron()||++stage==(baseline()?2:NAMES.length)){complete=true;log("COMPLETE (screenshots require visual review)");mc.stop();return;}
                ticks=0;passes=blockedHeads=armFrames=prePlayers=preLiving=canceledLiving=0;ready=false;shot=false;mc.setScreen(null);
            }
        }catch(Throwable e){fail(e);}
    }
    private static void capture(String label){var mc=Minecraft.getInstance();Screenshot.grab(mc.gameDirectory,String.format("%02d_%s_%s.png",stage,NAMES[stage],label),mc.getMainRenderTarget(),m->{});}
    private static void setup(){
        var mc=Minecraft.getInstance();mc.options.setCameraType(CameraType.FIRST_PERSON);var id=mc.player.getUUID();int index=stage;
        mc.getSingleplayerServer().execute(()->{try{
            var p=mc.getSingleplayerServer().getPlayerList().getPlayer(id);var level=p.serverLevel();
            level.setDayTime(6000);level.setWeatherParameters(6000,0,false,false);
            level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false,level.getServer());
            if(!sceneBuilt){sceneBuilt=true;
                var old=new java.util.ArrayList<Entity>();level.getAllEntities().forEach(old::add);for(var e:old)if(e instanceof Mob)e.discard();
                for(int x=-8;x<=8;x++)for(int z=-8;z<=8;z++){
                    level.setBlockAndUpdate(new BlockPos(x,100,z),Blocks.SMOOTH_STONE.defaultBlockState());
                    for(int y=101;y<109;y++)level.setBlockAndUpdate(new BlockPos(x,y,z),Blocks.AIR.defaultBlockState());
                }
                level.setBlockAndUpdate(new BlockPos(1,101,0),Blocks.REDSTONE_BLOCK.defaultBlockState());
                level.setBlockAndUpdate(new BlockPos(-1,101,0),Blocks.EMERALD_BLOCK.defaultBlockState());
                level.setBlockAndUpdate(new BlockPos(0,102,0),Blocks.GOLD_BLOCK.defaultBlockState());
            }
            p.setGameMode(GameType.CREATIVE);p.teleportTo(level,0,101,5,180,0);
            DivineAnchor.unlock(p);DivinityInventory.force(p,ItemStack.EMPTY);p.getInventory().armor.set(3,ItemStack.EMPTY);
            if(index==6)DivinityInventory.force(p,new ItemStack(item(index)));else p.getInventory().armor.set(3,new ItemStack(item(index)));
            p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,new ItemStack(Items.STICK));
            p.setItemInHand(net.minecraft.world.InteractionHand.OFF_HAND,ItemStack.EMPTY);
            p.getInventory().setChanged();p.inventoryMenu.broadcastFullState();
            p.connection.send(new net.minecraft.network.protocol.game.ClientboundSetCarriedItemPacket(p.getInventory().selected));
            p.connection.send(new net.minecraft.network.protocol.game.ClientboundSetEquipmentPacket(p.getId(),java.util.List.of(
                com.mojang.datafixers.util.Pair.of(EquipmentSlot.HEAD,p.getItemBySlot(EquipmentSlot.HEAD).copy()),
                com.mojang.datafixers.util.Pair.of(EquipmentSlot.MAINHAND,p.getMainHandItem().copy()),
                com.mojang.datafixers.util.Pair.of(EquipmentSlot.OFFHAND,p.getOffhandItem().copy()))));
            DivineWill.update(p,index==1||index==6);log("SETUP "+NAMES[index]);ready=true;
        }catch(Throwable e){fail(e);}});
    }
    private static void cast(){
        var mc=Minecraft.getInstance();var id=mc.player.getUUID();mc.getSingleplayerServer().execute(()->{try{
            var p=mc.getSingleplayerServer().getPlayerList().getPlayer(id);
            var spell=((Supplier<?>)iron("api.registry.SpellRegistry").getField("BLACK_HOLE_SPELL").get(null)).get();
            var duration=iron("api.spells.AbstractSpell").getDeclaredField("castTime");duration.setAccessible(true);duration.setInt(spell,240);
            // The doll grants cast-speed buffs. Normalize only the QA spell duration so
            // each visual sample is actually captured during a server-authorized cast.
            int effective=(int)call(spell,"getEffectiveCastTime",1,p);
            if(effective<200)duration.setInt(spell,(int)Math.ceil(240.0*240/Math.max(1,effective)));
            var source=iron("api.spells.CastSource").getField("COMMAND").get(null);
            boolean result=(boolean)call(spell,"attemptInitiateCast",p.getMainHandItem(),1,p.level(),p,source,false,"mainhand");
            log("SERVER_CAST "+NAMES[stage]+" success="+result+" effectiveTicks="+call(spell,"getEffectiveCastTime",1,p));
            if(!result)throw new AssertionError("Iron's cast did not start");
        }catch(Throwable e){fail(e);}});
    }
    private static void cancel(){if(noIron())return;var mc=Minecraft.getInstance();var id=mc.player.getUUID();mc.getSingleplayerServer().execute(()->{try{call(iron("api.util.Utils"),"serverSideCancelCast",mc.getSingleplayerServer().getPlayerList().getPlayer(id));}catch(Throwable e){fail(e);}});}
    @SubscribeEvent(priority=EventPriority.LOWEST,receiveCanceled=true)
    public static void rendered(RenderLivingEvent.Post<?,?> event){
        if(!enabled()||failed||complete||noIron()||event.getEntity()!=Minecraft.getInstance().getCameraEntity())return;
        try{if(pass()&&event.getRenderer().getModel() instanceof PlayerModel<?> model){
            passes++;if(model.head.visible||model.hat.visible)blockedHeads++;
            if(model.leftArm.visible||model.rightArm.visible)armFrames++;
            if(!shot){shot=true;log("FIRST_PASS "+NAMES[stage]+" head="+model.head.visible+" body="+model.body.visible+" arms="+model.leftArm.visible+","+model.rightArm.visible);}
        }}catch(Throwable e){fail(e);}
    }
    @SubscribeEvent(priority=EventPriority.LOWEST,receiveCanceled=true)
    public static void playerPre(net.minecraftforge.client.event.RenderPlayerEvent.Pre event){
        if(enabled()&&!noIron()&&event.getEntity()==Minecraft.getInstance().player)try{if(pass()){
            prePlayers++;if(prePlayers==1)log("PLAYER_PRE "+NAMES[stage]+" canceled="+event.isCanceled()+" worn="+DivineWill.wearing(event.getEntity()));
        }}catch(Throwable e){fail(e);}
    }
    @SubscribeEvent(priority=EventPriority.LOWEST,receiveCanceled=true)
    public static void livingPre(RenderLivingEvent.Pre<?,?> event){
        if(enabled()&&!noIron()&&event.getEntity()==Minecraft.getInstance().player)try{if(pass()){
            preLiving++;if(event.isCanceled())canceledLiving++;
            if(preLiving==1)log("LIVING_PRE "+NAMES[stage]+" canceled="+event.isCanceled()+" head="+((PlayerModel<?>)event.getRenderer().getModel()).head.visible);
        }}catch(Throwable e){fail(e);}
    }
    @SubscribeEvent public static void frame(TickEvent.RenderTickEvent event){
        if(enabled()&&event.phase==TickEvent.Phase.START&&Minecraft.getInstance().level!=null)
            Minecraft.getInstance().particleEngine.setLevel(Minecraft.getInstance().level); // Clear unrelated automatic buff particles in QA only.
    }
}
