package dev.starcrownqa;

import dev.eavfx.EaClient;
import dev.eavfx.EaNetwork;
import dev.starcrown.StarcrownDoll;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Isolated acceptance: actual held mesh, both hands, moving chant/blast and cancellation. */
@Mod.EventBusSubscriber(modid="starcrownqa",value=Dist.CLIENT)
public final class EaChargeQa {
    private static boolean setup,finished;
    private static volatile boolean ready;
    private static int tick = -60; // Let the held-item equip animation settle before capturing idle.
    private static int failures;
    private static boolean recordSpin;
    private static int lastSpinFrame = -1;
    private static long releaseStart = Long.MIN_VALUE;
    private static int releaseStep;
    private static String capture;
    private static List<SoundInstance> canceled=List.of();
    private static boolean enabled(Minecraft mc){return Files.exists(mc.gameDirectory.toPath().resolve("enable-ea-charge-qa"));}
    private static void check(String label,boolean pass){
        if(!pass)failures++;
        System.out.println("EA CHARGE QA "+label+"="+pass);
    }
    private static void server(java.util.function.Consumer<net.minecraft.server.level.ServerPlayer> work){
        var mc=Minecraft.getInstance();var id=mc.player.getUUID();
        mc.getSingleplayerServer().execute(()->work.accept(mc.getSingleplayerServer().getPlayerList().getPlayer(id)));
    }
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event){
        var mc=Minecraft.getInstance();
        if(event.phase!=TickEvent.Phase.END||finished||!enabled(mc))return;
        org.lwjgl.glfw.GLFW.glfwHideWindow(mc.getWindow().getWindow());
        if(mc.getOverlay()!=null)return;
        if(mc.screen instanceof net.minecraft.client.gui.screens.ConfirmScreen){
            mc.screen.children().stream().filter(w->w instanceof net.minecraft.client.gui.components.Button)
                    .map(w->(net.minecraft.client.gui.components.Button)w)
                    .filter(w->w.getMessage().getString().equals("Proceed")).findFirst().ifPresent(w->w.onPress());
        }
        if(mc.player==null||mc.getSingleplayerServer()==null)return;
        if(!setup){
            setup=true;mc.setScreen(null);mc.options.hideGui=false;
            recordSpin=Files.exists(mc.gameDirectory.toPath().resolve("record-ea-spin"));
            mc.options.setCameraType(CameraType.FIRST_PERSON);
            server(player->{
                var world=player.serverLevel();
                for(int x=-9;x<=9;x++)for(int z=-9;z<=9;z++)
                    world.setBlockAndUpdate(new BlockPos(x,100,z),Blocks.SMOOTH_STONE.defaultBlockState());
                world.setDayTime(6000);world.setWeatherParameters(12000,0,false,false);
                player.setGameMode(GameType.CREATIVE);
                player.teleportTo(world,.5,101,.5,0,0);
                player.getInventory().clearContent();
                player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(StarcrownDoll.EA.get()));
                ready=true;
            });return;
        }
        if(!ready)return;
        tick++;
        switch(tick){
            case -40 -> EaMusicQa.start(mc);
            case -8 -> check("vanilla_music_started",EaMusicQa.matches(mc,false));
            case 20 -> capture="ea-idle-first.png";
            case 30 -> {mc.options.keyUse.setDown(true);mc.gameMode.useItem(mc.player,InteractionHand.MAIN_HAND);}
            case 95 -> capture="ea-charge-first.png";
            case 105 -> {mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);mc.options.hideGui=true;}
            case 120 -> capture="ea-overhead-main.png";
            case 130 -> server(p->p.teleportTo(p.serverLevel(),3.5,101,.5,0,0));
            case 145 -> {
                checkFollowing("moving_chant_main",List.of("spin","chant"));
                check("music_paused_main_chant",EaMusicQa.matches(mc,true));
                canceled=sounds(List.of("spin","chant"));
                capture="ea-overhead-moved.png";
            }
            case 160 -> {mc.options.keyUse.setDown(false);mc.gameMode.releaseUsingItem(mc.player);}
            case 172 -> {
                check("cancel_stops_audio",canceled.size()==2&&canceled.stream().noneMatch(mc.getSoundManager()::isActive));
                check("music_resumed_after_cancel",EaMusicQa.matches(mc,false));
            }
            case 180 -> server(p->{
                p.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);
                p.setItemInHand(InteractionHand.OFF_HAND,new ItemStack(StarcrownDoll.EA.get()));
            });
            case 200 -> {mc.options.keyUse.setDown(true);mc.gameMode.useItem(mc.player,InteractionHand.OFF_HAND);}
            case 265 -> capture="ea-overhead-offhand.png";
            case 280 -> {mc.options.setCameraType(CameraType.FIRST_PERSON);mc.options.hideGui=false;}
            case 295 -> capture="ea-charge-first-offhand.png";
            case 310 -> {mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);mc.options.hideGui=true;}
            case 330 -> server(p->p.teleportTo(p.serverLevel(),.5,101,.5,0,0));
            case 345 -> {
                checkFollowing("moving_chant_offhand",List.of("spin","chant"));
                check("music_paused_offhand_chant",EaMusicQa.matches(mc,true));
            }
            // Aim into the sky for the release, keeping terrain work out of this test.
            case 720 -> {
                mc.player.setXRot(-80);
                server(p->{
                    p.teleportTo(p.serverLevel(),.5,101,.5,0,-80);
                    EaBeamDamageQa.setup(p);
                });
            }
        }
        if(tick>=720) checkRelease(mc);
    }

    private static void checkRelease(Minecraft mc){
        if(releaseStart==Long.MIN_VALUE){
            try{
                var casts=EaClient.class.getDeclaredField("CASTS");casts.setAccessible(true);
                for(Object visual:((Map<?,?>)casts.get(null)).values()){
                    var field=visual.getClass().getDeclaredField("packet");field.setAccessible(true);
                    var packet=(EaNetwork.CastPacket)field.get(visual);
                    if(packet.phase()==1&&packet.entityId()==mc.player.getId()){
                        releaseStart=packet.startTick();
                        break;
                    }
                }
            }catch(ReflectiveOperationException error){throw new IllegalStateException(error);}
            if(releaseStart==Long.MIN_VALUE){
                if(tick>940){check("release_packet",false);finished=true;mc.stop();}
                return;
            }
        }
        long age=mc.level.getGameTime()-releaseStart;
        switch(releaseStep){
            case 0 -> {
                if(age<15)return;
                mc.options.keyUse.setDown(false);
                server(p->p.teleportTo(p.serverLevel(),3.5,101,.5,0,-80));
            }
            case 1 -> {
                if(age<35)return;
                checkFollowing("moving_blast",List.of("blast"));
                check("music_resumed_after_fire",EaMusicQa.matches(mc,false));
            }
            case 2 -> {
                if(age<172)return;
                capture="ea-blast-sustain.png";
                checkFollowing("sound_during_sustained_beam",List.of("blast"));
                System.out.println("EA RELEASE QA tail sample age="+age);
                server(p->EaBeamDamageQa.spawnLate(p));
            }
            case 3 -> {
                if(age<198)return;
                server(p->EaBeamDamageQa.spawnAfter(p));
            }
            case 4 -> {
                if(age<214)return;
                check("cast_audio_finished",sounds(List.of("spin","chant","blast")).isEmpty());
                finished=true;
                server(p->{
                    boolean damagePassed=EaBeamDamageQa.passed();
                    mc.execute(()->{
                        check("server_beam_damage",damagePassed);
                        System.out.println("EA CHARGE QA COMPLETE failures="+failures);
                        mc.stop();
                    });
                });
            }
        }
        releaseStep++;
    }
    private static List<SoundInstance> sounds(List<String> fields){
        var result=new ArrayList<SoundInstance>();
        try{
            var casts=EaClient.class.getDeclaredField("CASTS");casts.setAccessible(true);
            for(Object visual:((Map<?,?>)casts.get(null)).values())for(String name:fields){
                var field=visual.getClass().getDeclaredField(name);field.setAccessible(true);
                Object sound=field.get(visual);if(sound instanceof SoundInstance instance)result.add(instance);
            }
        }catch(ReflectiveOperationException error){throw new IllegalStateException(error);}
        return result;
    }
    private static void checkFollowing(String label,List<String> names){
        var mc=Minecraft.getInstance();var sounds=sounds(names);
        boolean follows=sounds.size()==names.size();
        for(var sound:sounds){
            double error=new Vec3(sound.getX(),sound.getY(),sound.getZ()).distanceTo(mc.player.getEyePosition());
            boolean active=mc.getSoundManager().isActive(sound);
            System.out.println("EA AUDIO "+sound.getLocation()+" error="+error+" active="+active);
            follows&=error<.02&&active;
        }
        check(label,follows);
    }
    @SubscribeEvent public static void camera(ViewportEvent.ComputeCameraAngles event){
        var mc=Minecraft.getInstance();
        if(!ready||finished||!enabled(mc)||mc.options.getCameraType()==CameraType.FIRST_PERSON)return;
        try{
            Vec3 base=mc.player.position();
            Vec3 eye=base.add(-3,2.8,4.2),aim=base.add(0,2.1,0);
            if(releaseStart!=Long.MIN_VALUE){
                eye=new Vec3(-12,110,17);
                aim=new Vec3(.5,111,2);
            }
            Vec3 delta=aim.subtract(eye);
            var setter=java.util.Arrays.stream(net.minecraft.client.Camera.class.getDeclaredMethods())
                    .filter(method->method.getReturnType()==void.class)
                    .filter(method->java.util.Arrays.equals(method.getParameterTypes(),new Class<?>[]{Vec3.class}))
                    .findFirst().orElseThrow();
            setter.setAccessible(true);setter.invoke(event.getCamera(),eye);
            event.setYaw((float)Math.toDegrees(Math.atan2(-delta.x,delta.z)));
            event.setPitch((float)Math.toDegrees(Math.atan2(-delta.y,delta.horizontalDistance())));
        }catch(ReflectiveOperationException error){throw new IllegalStateException(error);}
    }
    @SubscribeEvent public static void render(TickEvent.RenderTickEvent event){
        if(event.phase!=TickEvent.Phase.END||finished)return;
        var mc=Minecraft.getInstance();
        if(recordSpin && tick>=64 && tick<92){
            int ms=Math.round((tick-64+mc.getFrameTime())*50f);
            if(ms/20!=lastSpinFrame){
                lastSpinFrame=ms/20;
                Screenshot.grab(mc.gameDirectory,String.format(java.util.Locale.ROOT,"ea-spin-%04d.png",ms),
                        mc.getMainRenderTarget(),message->{});
            }
        }
        if(capture==null)return;
        int gl=org.lwjgl.opengl.GL11.glGetError();check("GL_"+capture,gl==0);
        Screenshot.grab(mc.gameDirectory,capture,mc.getMainRenderTarget(),message->{});
        if(capture.startsWith("ea-overhead-")){
            var renderer=(net.minecraft.client.renderer.entity.player.PlayerRenderer)mc.getEntityRenderDispatcher().getRenderer(mc.player);
            var model=renderer.getModel();
            boolean off=mc.player.getUsedItemHand()==InteractionHand.OFF_HAND;
            check("raised_arm_"+capture,(off?model.leftArm:model.rightArm).xRot<-2.8f);
        }
        capture=null;
    }
}
