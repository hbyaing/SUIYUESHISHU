package dev.starcrownqa;

import dev.starcrown.treasury.TreasuryClient;
import dev.starcrown.treasury.TreasuryItems;
import dev.starcrown.treasury.TreasuryLayout;
import dev.starcrown.treasury.TreasurySavedData;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.client.Camera;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Ravager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

@Mod.EventBusSubscriber(modid="starcrownqa",value=Dist.CLIENT)
public final class TreasuryQa {
    private static final Vec3 BASE=new Vec3(.5,101,.5);
    private static final Method CAMERA_POSITION=Arrays.stream(Camera.class.getDeclaredMethods())
            .filter(method->method.getReturnType()==void.class && Arrays.equals(method.getParameterTypes(),new Class<?>[]{Vec3.class}))
            .findFirst().orElseThrow();
    static { CAMERA_POSITION.setAccessible(true); }
    private static boolean setup,finished,started;
    private static volatile boolean ready;
    private static int tick;
    private static volatile int failures;
    private static String capture;
    private static Ravager target;
    private static volatile int targetId=-1;
    private static int lastMotionBucket=-1;
    private static Vec3 boundPosition;
    private static UUID firstProbeOwner;
    private static long pushedAt=-1;
    private static boolean pushChecked;
    private static volatile boolean persistenceChecked;
    private static volatile long observedServerAge=-1;
    private static int cancelWait;
    private static Vec3[] normalView,fullView;

    private static Item item(String path){
        return ForgeRegistries.ITEMS.getValue(new ResourceLocation("starcrowndoll",path));
    }
    private static void onServer(Consumer<ServerPlayer> work){
        var mc=Minecraft.getInstance();
        var server=mc.getSingleplayerServer();
        var id=mc.player.getUUID();
        server.execute(()->work.accept(server.getPlayerList().getPlayer(id)));
    }
    private static synchronized void check(String name,boolean ok){
        if(!ok)failures++;
        System.out.println("TREASURY QA "+name+"="+ok);
    }
    private static void use(Minecraft mc){
        mc.gameMode.useItem(mc.player,InteractionHand.MAIN_HAND);
    }
    private static void gateShot(int expected,String name){
        int count=TreasuryClient.activeGateCount();
        System.out.println("TREASURY QA "+name+" actual gate count="+count);
        check(name+"_count_"+expected,count==expected);
        checkGateSpread(name,expected);
        capture=name+".png";
    }
    private static void checkGateSpread(String name,int expected){
        var gates=new ArrayList<TreasuryLayout.Gate>();
        try{
            var field=TreasuryClient.class.getDeclaredField("ACTIVE");
            field.setAccessible(true);
            for(var visual:((Map<?,?>)field.get(null)).values()){
                var accessor=visual.getClass().getDeclaredMethod("gates");
                accessor.setAccessible(true);
                for(Object raw:(List<?>)accessor.invoke(visual))gates.add((TreasuryLayout.Gate)raw);
            }
        }catch(ReflectiveOperationException ex){throw new IllegalStateException(ex);}
        if(gates.size()!=expected)return; // gateShot already reports a count mismatch.
        var xs=gates.stream().mapToDouble(TreasuryLayout.Gate::x).summaryStatistics();
        var ys=gates.stream().mapToDouble(TreasuryLayout.Gate::y).summaryStatistics();
        double midX=(xs.getMin()+xs.getMax())*.5,midY=(ys.getMin()+ys.getMax())*.5;
        double width=xs.getMax()-xs.getMin(),height=ys.getMax()-ys.getMin();
        double closest=Double.POSITIVE_INFINITY;
        int quadrants=0;
        for(int i=0;i<gates.size();i++){
            var a=gates.get(i);
            int quadrant=(a.x()>midX?1:0)+(a.y()>midY?2:0);
            quadrants|=1<<quadrant;
            for(int j=0;j<i;j++){
                var b=gates.get(j);
                double spacing=Math.hypot(a.x()-b.x(),a.y()-b.y())/(a.radius()+b.radius());
                closest=Math.min(closest,spacing);
            }
        }
        long xBands=gates.stream().mapToInt(gate->Math.round(gate.x()*4)).distinct().count();
        long yBands=gates.stream().mapToInt(gate->Math.round(gate.y()*4)).distinct().count();
        int gridColumns=expected==20?5:10;
        check(name+"_broad_distribution",width>height && quadrants==15 && closest>.66);
        check(name+"_loose_centers",xBands>gridColumns && yBands>expected/gridColumns);
        var seedA=TreasuryLayout.gates(expected,7);
        var seedB=TreasuryLayout.gates(expected,29);
        boolean changesCenters=false;
        for(int i=0;i<expected;i++)
            if(seedA.get(i).x()!=seedB.get(i).x() || seedA.get(i).y()!=seedB.get(i).y())changesCenters=true;
        check(name+"_seeded_positions",changesCenters && seedA.equals(TreasuryLayout.gates(expected,7)));
        System.out.printf(Locale.ROOT,"TREASURY QA %s spread %.2fx%.2f, nearest/radii=%.2f%n",name,width,height,closest);
    }
    private static void chainShot(String name){
        int gates=TreasuryClient.activeChainGateCount();
        System.out.println("TREASURY QA "+name+" actual chain gates="+gates);
        check(name+"_active",TreasuryClient.activeChainCount()==1 && (gates==5 || gates==6));
        capture=name+".png";
    }
    private static void spawnTarget(ServerPlayer player){
        var level=player.serverLevel();
        target=EntityType.RAVAGER.create(level);
        target.setNoAi(true);
        target.setSilent(true);
        target.setPersistenceRequired();
        target.moveTo(BASE.x,BASE.y,BASE.z+8,180,0);
        level.addFreshEntity(target);
        targetId=target.getId();
    }
    private static void checkOverlay(Minecraft mc,boolean active){
        var entity=mc.level.getEntity(targetId);
        float strength=entity instanceof Ravager mob ? TreasuryClient.bindingStrength(mob,mc.getFrameTime()) : -1;
        System.out.println("TREASURY QA target shader strength="+strength);
        check(active?"bound_shader_on":"bound_shader_off",active?strength>0:strength==0);
    }
    private static void initialize(Minecraft mc){
        setup=true;
        mc.setScreen(null);
        mc.options.hideGui=true;
        mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
        mc.options.fov().set(65);
        normalView=gateView(mc,20);
        fullView=gateView(mc,100);
        onServer(player->{
            var level=player.serverLevel();
            level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false,level.getServer());
            level.setDayTime(13500);
            level.setWeatherParameters(10000,0,false,false);
            // The narrow sandstone terrace leaves free sky behind the portal array.
            for(int x=-3;x<=3;x++)for(int z=-3;z<=12;z++){
                boolean border=x==-3 || x==3 || z==-3 || z==12;
                var block=border?Blocks.CUT_SANDSTONE:Blocks.SMOOTH_SANDSTONE;
                if((x==-2 || x==2) && (z==0 || z==8))block=Blocks.SEA_LANTERN;
                level.setBlockAndUpdate(new BlockPos(x,100,z),block.defaultBlockState());
            }
            player.setGameMode(GameType.CREATIVE);
            player.teleportTo(level,BASE.x,BASE.y,BASE.z,0,0);
            player.getInventory().clearContent();
            player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(item("gate_of_babylon")));
            ready=true;
        });
    }

    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event){
        if(event.phase!=TickEvent.Phase.END || finished)return;
        var mc=Minecraft.getInstance();
        if(!setup && !Files.exists(mc.gameDirectory.toPath().resolve("enable-treasury-qa")))return;
        if(mc.getOverlay()!=null)return;
        if(mc.screen instanceof ConfirmScreen){
            mc.screen.children().stream().filter(Button.class::isInstance).map(Button.class::cast)
                    .filter(button->button.getMessage().getString().equals("Proceed")).findFirst().ifPresent(Button::onPress);
        }
        if(mc.player==null || mc.getSingleplayerServer()==null)return;
        if(!setup){initialize(mc);return;}
        if(!ready)return;
        if(!started){
            if(!mc.player.getMainHandItem().is(item("gate_of_babylon")))return;
            started=true;
        }
        if(tick==432 && !persistenceChecked && ++cancelWait<=180)return;
        tick++;
        switch(tick){
            case 25 -> use(mc);
            case 72 -> gateShot(20,"treasury-normal");
            case 139 -> check("normal_cleaned_up",TreasuryClient.activeGateCount()==0);
            case 145 -> mc.options.keyShift.setDown(true);
            case 155 -> {check("sneaking_for_mode_switch",mc.player.isShiftKeyDown());use(mc);}
            case 164 -> mc.options.keyShift.setDown(false);
            case 179 -> use(mc);
            case 228 -> gateShot(100,"treasury-full");
            case 296 -> {
                check("full_cleaned_up",TreasuryClient.activeGateCount()==0);
                onServer(player->{
                    player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(item("chains_of_heaven")));
                    spawnTarget(player);
                    firstProbeOwner=player.getUUID();
                });
            }
            case 320 -> {check("chain_item_in_hand",mc.player.getMainHandItem().is(item("chains_of_heaven")));use(mc);}
            case 338 -> chainShot("chains-launch");
            case 365 -> {
                chainShot("chains-bound");
                checkOverlay(mc,true);
            }
            case 418 -> {
                check("held_beyond_80_ticks",TreasuryClient.activeChainCount()==1 && TreasuryClient.activeChainGateCount()>=5);
                checkOverlay(mc,true);
            }
            case 433 -> {
                if(!persistenceChecked)System.out.println("TREASURY QA server probe timeout: last age="+observedServerAge);
                check("server_persistence_probe_finished",persistenceChecked);
                use(mc);
            }
            case 456 -> {
                check("manual_cancel_retracted",TreasuryClient.activeChainCount()==0 && TreasuryClient.activeChainGateCount()==0);
                checkOverlay(mc,false);
                onServer(player->{
                    check("manual_cancel_unbound",!TreasuryItems.isBound(target));
                    target.setPos(target.getX()+.4,target.getY()+.25,target.getZ());
                    target.setDeltaMovement(.6,.45,0);
                    target.hurtMarked=true;
                });
            }
            case 466 -> onServer(player->{
                check("target_moves_after_cancel",boundPosition!=null && target.position().distanceTo(boundPosition)>.2);
                target.discard();
            });
            case 521 -> onServer(TreasuryQa::spawnTarget);
            case 545 -> use(mc);
            case 588 -> {
                checkOverlay(mc,true);
                onServer(player->{
                    check("second_target_bound",TreasuryItems.isBound(target));
                    target.kill();
                });
            }
            case 618 -> {
                check("death_auto_clear",TreasuryClient.activeChainCount()==0 && TreasuryClient.activeChainGateCount()==0);
                onServer(player->check("dead_target_unbound",!target.isAlive() && !TreasuryItems.isBound(target)));
            }
            case 650 -> use(mc);
            case 692 -> chainShot("chains-empty");
            case 745 -> {
                check("empty_chain_cleaned_up",TreasuryClient.activeChainCount()==0);
                finished=true;
                System.out.println("TREASURY QA COMPLETE failures="+failures);
                mc.stop();
            }
        }
    }

    @SubscribeEvent(priority=EventPriority.LOWEST)
    public static void probeBinding(TickEvent.ServerTickEvent event){
        if(event.phase!=TickEvent.Phase.END || firstProbeOwner==null || persistenceChecked)return;
        var data=TreasurySavedData.get(event.getServer());
        var binding=data.byOwner(firstProbeOwner);
        if(binding==null || binding.cast().target().filter(target.getUUID()::equals).isEmpty())return;
        long age=target.level().getGameTime()-binding.cast().start();
        observedServerAge=age;
        if(pushedAt<0){
            if(binding.anchor()==null || !TreasuryItems.isBound(target))return;
            boundPosition=binding.anchor();
            double error=target.position().distanceTo(boundPosition);
            System.out.printf(Locale.ROOT,"TREASURY QA push age=%d anchorDistance=%.5f%n",age,error);
            check("target_bound",age>=25 && error<.06);
            target.setPos(target.getX()+.4,target.getY()+.25,target.getZ());
            target.setDeltaMovement(.7,.55,0);
            target.hurtMarked=true;
            pushedAt=age;
            return;
        }
        if(!pushChecked && age-pushedAt>=3){
            double error=target.position().distanceTo(boundPosition);
            System.out.printf(Locale.ROOT,"TREASURY QA after push age=%d elapsed=%d anchorDistance=%.5f%n",age,age-pushedAt,error);
            check("restraint_stops_push",TreasuryItems.isBound(target) && error<.06);
            pushChecked=true;
        }
        if(!pushChecked || age<=80)return;
        double error=target.position().distanceTo(boundPosition);
        System.out.printf(Locale.ROOT,"TREASURY QA persistent age=%d anchorDistance=%.5f%n",age,error);
        check("still_restrained_after_80_ticks",TreasuryItems.isBound(target) && error<.06);
        net.minecraft.world.level.saveddata.SavedData saved=data;
        var restored=TreasurySavedData.load(saved.save(new CompoundTag())).byOwner(firstProbeOwner);
        check("binding_nbt_round_trip",restored!=null && restored.cast().id().equals(binding.cast().id())
                && restored.cast().target().filter(target.getUUID()::equals).isPresent()
                && boundPosition.equals(restored.anchor()));
        persistenceChecked=true;
        firstProbeOwner=null;
    }
    private static Vec3[] gateView(Minecraft mc,int count){
        var gates=TreasuryLayout.gates(count,0L);
        double minY=-.7,maxY=2,halfWidth=2;
        for(var gate:gates){
            maxY=Math.max(maxY,gate.y()+gate.radius());
            halfWidth=Math.max(halfWidth,Math.abs(gate.x())+gate.radius());
        }
        double centerY=(minY+maxY)*.5;
        double tanY=Math.tan(Math.toRadians(mc.options.fov().get()*.5));
        double tanX=tanY*mc.getWindow().getWidth()/mc.getWindow().getHeight();
        double distance=Math.max(5.5,centerY/(tanY*.89));
        double padding=Math.max(.65,halfWidth*.035);
        for(var gate:gates){
            double xRoom=(Math.abs(gate.x())+gate.radius()+padding)/(tanX*.90);
            double yRoom=(Math.abs(gate.y()-centerY)+gate.radius()+padding)/(tanY*.89);
            distance=Math.max(distance,gate.z()+Math.max(xRoom,yRoom));
        }
        return new Vec3[]{BASE.add(0,centerY,distance),BASE.add(0,centerY,0)};
    }
    @SubscribeEvent public static void camera(ViewportEvent.ComputeCameraAngles event){
        if(!started || finished)return;
        Vec3 eye,aim;
        if(tick<296){
            var view=tick<167?normalView:fullView;
            eye=view[0];aim=view[1];
        }else if(tick>=650){
            eye=BASE.add(10,3.6,7.7);aim=BASE.add(0,1.5,6.4);
        }else{
            // The ravager faces north. Its near flank and the far gate apertures share a single view.
            eye=BASE.add(6.6,4.55,1.6);aim=BASE.add(0,1.7,8);
        }
        var look=aim.subtract(eye);
        try{CAMERA_POSITION.invoke(event.getCamera(),eye);}
        catch(ReflectiveOperationException error){throw new IllegalStateException(error);}
        event.setYaw((float)Math.toDegrees(Math.atan2(-look.x,look.z)));
        event.setPitch((float)Math.toDegrees(Math.atan2(-look.y,look.horizontalDistance())));
    }
    @SubscribeEvent public static void render(TickEvent.RenderTickEvent event){
        if(event.phase!=TickEvent.Phase.END || !started || finished)return;
        var mc=Minecraft.getInstance();
        if(tick>=317 && tick<455){
            int ms=Math.round((tick-317+mc.getFrameTime())*50f);
            if(ms/80>lastMotionBucket){
                lastMotionBucket=ms/80;
                String name=String.format(Locale.ROOT,"treasury-motion-%04d.png",ms);
                Screenshot.grab(mc.gameDirectory,name,mc.getMainRenderTarget(),message->{});
            }
        }
        if(capture==null)return;
        check("gl_"+capture,org.lwjgl.opengl.GL11.glGetError()==0);
        Screenshot.grab(mc.gameDirectory,capture,mc.getMainRenderTarget(),message->{});
        capture=null;
    }
    private TreasuryQa(){}
}
