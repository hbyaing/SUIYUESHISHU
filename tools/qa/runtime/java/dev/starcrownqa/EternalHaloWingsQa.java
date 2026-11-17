package dev.starcrownqa;

import dev.starcrown.*;
import com.mojang.blaze3d.vertex.PoseStack;
import java.nio.file.*;
import java.util.ArrayList;
import net.minecraft.client.*;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import top.theillusivec4.curios.api.CuriosApi;

/** Production JAR + real world rendering; no GUI portrait is used to certify screen bloom. */
@Mod.EventBusSubscriber(modid="starcrownqa",value=Dist.CLIENT)
public final class EternalHaloWingsQa {
    private static boolean started,finished,shot,reloading;
    private static volatile boolean ready;
    private static int stage,ticks,previousComposites;
    private static ArmorStand camera;
    private static ItemStack halo;
    private static Path root(){return Minecraft.getInstance().gameDirectory.toPath();}
    private static boolean gapOnly(){return Files.exists(root().resolve("enable-halo-gap-qa"));}
    private static boolean enabled(){return Files.exists(root().resolve("enable-halo-wings-qa"))||gapOnly();}
    private static void log(String s){try{Files.writeString(root().resolve(gapOnly()?"halo-gap-results.txt":"halo-wings-results.txt"),s+"\n",StandardOpenOption.CREATE,StandardOpenOption.APPEND);}catch(Exception e){throw new RuntimeException(e);}}
    private static void check(boolean ok,String s){if(!ok)throw new AssertionError(s);log("PASS "+s);}
    private static void fail(Throwable error){error.printStackTrace();log("FAIL "+error);finished=true;Minecraft.getInstance().execute(()->Minecraft.getInstance().stop());}
    private static void bloom(boolean enabled)throws Exception {
        var field=EternalHaloBloom.class.getDeclaredField("failed");field.setAccessible(true);field.setBoolean(null,!enabled);
    }
    private static void setupPoseAudit() {
        var mc=Minecraft.getInstance();var model=new PlayerModel<AbstractClientPlayer>(mc.getEntityModels().bakeLayer(ModelLayers.PLAYER),false);
        var a=new PoseStack();EternalHaloRenderer.wornPose(model,a,100);
        model.head.xRot=.9f;model.head.yRot=1.2f;model.head.zRot=.4f;
        var b=new PoseStack();EternalHaloRenderer.wornPose(model,b,100);
        check(a.last().pose().equals(b.last().pose(),1e-7f),"actual worn transform ignores all three head rotations");
        check(Math.abs(EternalHaloRenderer.WORN_SCALE-1.083f)<.001,"original assembly uniformly scaled to match source body and player height");
        check(EternalHaloRenderer.BACK_OFFSET>.30f,"source halo position remains behind the head outer layer");
        model.body.yRot=.3f;var c=new PoseStack();EternalHaloRenderer.wornPose(model,c,100);
        check(!a.last().pose().equals(c.last().pose(),1e-4f),"torso motion still carries the floating halo");
        check(EternalHaloRenderer.HEIGHT_OFFSET<-.46f,"source-aligned halo anchor raised over 0.14 block from 1.20.48");
        check(EternalHaloRenderer.WORN_SCALE==EternalHaloRenderer.WINGS_SCALE,"halo and wings preserve the source relative scale");
        check(EternalHaloRenderer.wingTriangles()==6121,"both original source wings loaded: 6121 mesh triangles");
        var wa=new PoseStack();EternalHaloRenderer.wingsPose(model,wa,100);
        model.head.xRot=-1.1f;model.head.yRot=-1.4f;
        var wb=new PoseStack();EternalHaloRenderer.wingsPose(model,wb,100);
        check(wa.last().pose().equals(wb.last().pose(),1e-7f),"wings ignore independent head rotation");
        check(wa.last().pose().getTranslation(new org.joml.Vector3f()).equals(c.last().pose().getTranslation(new org.joml.Vector3f()),1e-7f),"halo and original wings share the same body anchor");
    }
    private static void camera(double x,double z,float yaw) {
        var mc=Minecraft.getInstance();if(camera==null)camera=new ArmorStand(EntityType.ARMOR_STAND,mc.level);
        camera.moveTo(x,101.12,z,yaw,0);camera.xo=x;camera.yo=101.12;camera.zo=z;
        camera.yRotO=yaw;camera.yHeadRot=yaw;camera.yHeadRotO=yaw;camera.yBodyRot=yaw;camera.yBodyRotO=yaw;
        camera.xRotO=0;mc.setCameraEntity(camera);
    }
    private static void serverStage(Runnable operation) {
        ready=false;Minecraft.getInstance().getSingleplayerServer().execute(()->{try{operation.run();ready=true;}catch(Throwable error){fail(error);}});
    }
    private static void wall(boolean on) {
        var level=Minecraft.getInstance().getSingleplayerServer().overworld();
        for(int x=-3;x<=3;x++)for(int y=101;y<=106;y++)level.setBlockAndUpdate(new BlockPos(x,y,2),on?Blocks.DEEPSLATE_TILES.defaultBlockState():Blocks.AIR.defaultBlockState());
    }
    private static void visibility(boolean value) {
        var p=Minecraft.getInstance().getSingleplayerServer().getPlayerList().getPlayers().get(0);
        CuriosApi.getCuriosInventory(p).resolve().orElseThrow().getStacksHandler("head").orElseThrow().getRenders().set(0,value);
        // Mutating the server's render list alone does not send Curios' visibility update.
        top.theillusivec4.curios.common.network.NetworkHandler.INSTANCE.send(
            net.minecraftforge.network.PacketDistributor.TRACKING_ENTITY_AND_SELF.with(()->p),
            new top.theillusivec4.curios.common.network.server.sync.SPacketSyncRender(p.getId(),"head",0,value));
    }
    @SubscribeEvent public static void client(TickEvent.ClientTickEvent event) {
        if(event.phase!=TickEvent.Phase.END||!enabled()||finished)return;var mc=Minecraft.getInstance();
        try {
            mc.options.pauseOnLostFocus=false;
            if(mc.level==null||mc.player==null||mc.getSingleplayerServer()==null||mc.getOverlay()!=null)return;
            if(!started) {
                if(mc.screen!=null)return;
                started=true;mc.options.hideGui=true;log("GPU "+GL11.glGetString(GL11.GL_RENDERER));
                check(EternalHaloBloom.ready(),"production bloom shaders compile");setupPoseAudit();
                serverStage(()->{
                    var server=mc.getSingleplayerServer();var level=server.overworld();var p=server.getPlayerList().getPlayers().get(0);
                    var entities=new ArrayList<Entity>();level.getAllEntities().forEach(entities::add);
                    for(var entity:entities)if(entity instanceof Mob||entity instanceof MingGuLongVortex||entity instanceof MingGuLongProjectile)entity.discard();
                    DivineAnchor.unlock(p);DivinityInventory.force(p,ItemStack.EMPTY);p.getInventory().clearContent();p.setItemSlot(EquipmentSlot.HEAD,ItemStack.EMPTY);
                    p.removeAllEffects();p.setInvisible(false);p.setGameMode(GameType.CREATIVE);p.teleportTo(level,0,101,0,0,0);
                    level.setDayTime(18000);level.setWeatherParameters(6000,0,false,false);
                    level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false,server);
                    level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false,server);
                    for(int x=-8;x<=8;x++)for(int z=-5;z<=8;z++) {
                        level.setBlockAndUpdate(new BlockPos(x,100,z),Blocks.SMOOTH_STONE.defaultBlockState());
                        for(int y=101;y<=109;y++)level.setBlockAndUpdate(new BlockPos(x,y,z),Blocks.AIR.defaultBlockState());
                    }
                    for(int x=-8;x<=8;x++)for(int y=101;y<110;y++)level.setBlockAndUpdate(new BlockPos(x,y,-4),Blocks.GRAY_CONCRETE.defaultBlockState());
                    halo=new ItemStack(StarcrownDoll.ETERNAL_RETURN.get());
                    CuriosApi.getCuriosInventory(p).resolve().orElseThrow().getStacksHandler("head").orElseThrow().getStacks().setStackInSlot(0,halo);
                    visibility(true);
                });return;
            }
            if(!ready||reloading)return;
            if(camera==null){camera(0,5,180);if(gapOnly()){stage=15;mc.options.hideGui=false;mc.gameRenderer.displayItemActivation(EternalReturnClient.rewindVisual());}}
            var player=mc.player;
            player.setPos(0,101,0);player.xo=0;player.yo=101;player.zo=0;
            player.setYRot(0);player.yRotO=0;player.yBodyRot=0;player.yBodyRotO=0;
            player.yHeadRot=stage==1?65:0;player.yHeadRotO=player.yHeadRot;
            player.setXRot(stage==1?-45:0);player.xRotO=player.getXRot();player.tickCount=200;
            player.setShiftKeyDown(stage==14);player.setPose(stage==14?Pose.CROUCHING:Pose.STANDING);
            if(++ticks==20)previousComposites=EternalHaloBloom.composites();
            if(ticks>=(stage==15?10:45))shot=true;
        }catch(Throwable error){fail(error);}
    }
    @SubscribeEvent public static void render(TickEvent.RenderTickEvent event) {
        if(event.phase!=TickEvent.Phase.END||!enabled()||!shot||finished)return;shot=false;
        try {
            var mc=Minecraft.getInstance();
            Screenshot.grab(mc.gameDirectory,"halo49-debug-"+stage+".png",mc.getMainRenderTarget(),m->{});
            log("DIAGNOSTIC stage "+stage+" "+EternalHaloBloom.diagnostics()+" equipped="+CuriosApi.getCuriosInventory(mc.player).resolve().orElseThrow().getStacksHandler("head").orElseThrow().getStacks().getStackInSlot(0));
            check(GL11.glGetError()==GL11.GL_NO_ERROR,"stage "+stage+" without OpenGL errors");
            if(stage<=2||stage==4||stage==7||stage==8)check(EternalHaloBloom.composites()>previousComposites&&EternalHaloBloom.lastDrawCount()>0,"stage "+stage+" actual equipped halo emits screen bloom");
            if(stage==3||stage==6||stage==9||stage==10||stage==11)check(EternalHaloBloom.composites()==previousComposites,"stage "+stage+" suppressed/unequipped/hidden halo has no bloom composites");
            String[] names={"front","head-turn","side","bloom-off","bloom-on","wall-on","wall-off","resized","reloaded","unequipped","hidden","invisible","restored","back","crouch","rewind-inner-only","reference-view"};
            Screenshot.grab(mc.gameDirectory,"halo49-"+names[stage]+".png",mc.getMainRenderTarget(),m->{});log("GPU_CAPTURE "+names[stage]);
            stage++;ticks=0;
            switch(stage) {
                case 1 -> {}
                case 2 -> camera(4,3,126.87f);
                case 3 -> {camera(0,5,180);bloom(false);}
                case 4 -> bloom(true);
                case 5 -> serverStage(()->wall(true));
                case 6 -> bloom(false);
                case 7 -> {bloom(true);serverStage(()->wall(false));mc.getWindow().setWindowed(960,600);}
                case 8 -> {
                    reloading=true;mc.reloadResourcePacks().whenComplete((value,error)->mc.execute(()->{
                        if(error!=null){fail(error);return;}
                        try{check(EternalHaloBloom.ready(),"resource reload restores shaders");reloading=false;}catch(Throwable e){fail(e);}
                    }));
                }
                case 9 -> serverStage(()->CuriosApi.getCuriosInventory(mc.getSingleplayerServer().getPlayerList().getPlayers().get(0)).resolve().orElseThrow().getStacksHandler("head").orElseThrow().getStacks().setStackInSlot(0,ItemStack.EMPTY));
                case 10 -> serverStage(()->{
                    var p=mc.getSingleplayerServer().getPlayerList().getPlayers().get(0);
                    var h=CuriosApi.getCuriosInventory(p).resolve().orElseThrow().getStacksHandler("head").orElseThrow();h.getStacks().setStackInSlot(0,halo);visibility(false);
                });
                case 11 -> serverStage(()->{
                    var p=mc.getSingleplayerServer().getPlayerList().getPlayers().get(0);
                    visibility(true);p.setInvisible(true);
                });
                case 12 -> {mc.options.hideGui=false;EternalHaloBloom.release();check(!EternalHaloBloom.allocated(),"bloom targets released");serverStage(()->mc.getSingleplayerServer().getPlayerList().getPlayers().get(0).setInvisible(false));}
                case 13 -> {check(EternalHaloBloom.composites()>previousComposites,"bloom recreated after target release");mc.options.hideGui=true;camera(0,-5,0);serverStage(()->{var level=mc.getSingleplayerServer().overworld();for(int x=-8;x<=8;x++)for(int y=101;y<110;y++)level.setBlockAndUpdate(new BlockPos(x,y,-4),Blocks.AIR.defaultBlockState());});}
                case 14 -> camera(4,3,126.87f);
                case 15 -> {camera(0,5,180);mc.options.hideGui=false;mc.gameRenderer.displayItemActivation(EternalReturnClient.rewindVisual());}
                case 16 -> {
                    mc.options.hideGui=true;mc.options.fov().set(35);camera(4,8,153.435f);
                    camera.setPos(4,103.12,8);camera.yo=103.12;camera.setXRot(15.5f);camera.xRotO=15.5f;
                }
                default -> {
                    finished=true;log(gapOnly()?"COMPLETE inner-only rewind overlay and source-reference camera comparison":"COMPLETE raised halo and original 6121-triangle double wings, head independence, front/side/back/crouch, bloom, occlusion, reload, resize, visibility and inner-only rewind");mc.stop();
                }
            }
            previousComposites=EternalHaloBloom.composites();
        }catch(Throwable error){fail(error);}
    }
}
