package dev.starcrownqa;

import dev.eavfx.EaClient;
import dev.starcrown.FateItems;
import dev.starcrown.StarcrownDoll;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.Camera;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.client.event.sound.PlaySoundEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWNativeWin32;

/** A single, playable foreground demonstration, scoped to the isolated launcher. */
@Mod.EventBusSubscriber(modid="starcrownqa", value=Dist.CLIENT)
public final class EaDrawChargeDemo {
    private static final Set<String> EXPECTED = Set.of("ea_draw_open", "ea_draw_void", "ea_draw_flash",
            "ea_draw_break", "ea_spin", "ea_chant", "ea_blast");
    private static final int[] DRAW_STILLS = {5, 65, 83, 89, 95, 102, 109, 115, 124, 132, 134, 136, 137, 138, 139, 140, 141, 142, 144, 154, 158};
    private static final int[] SWORD_STILLS = {4, 18, 34};
    private static final int[] CHARGE_STILLS = {20, 54, 125, 300, 490};
    private static final Map<String, SoundInstance> sounds = new HashMap<>();
    private static final Map<String, Integer> plays = new HashMap<>();
    private static final Map<String, Long> soundTicks = new HashMap<>();
    private static final Set<String> sources=ConcurrentHashMap.newKeySet();
    private static final Method CAMERA = Arrays.stream(Camera.class.getDeclaredMethods())
            .filter(m -> m.getReturnType()==void.class && Arrays.equals(m.getParameterTypes(),new Class<?>[]{Vec3.class}))
            .findFirst().orElseThrow();
    private static final Method MOUSE = Arrays.stream(MouseHandler.class.getDeclaredMethods())
            .filter(m -> !m.isSynthetic() && m.getReturnType()==void.class && Arrays.equals(m.getParameterTypes(),
                    new Class<?>[]{long.class,int.class,int.class,int.class})).findFirst().orElseThrow();
    static { CAMERA.setAccessible(true); MOUSE.setAccessible(true); }

    private enum Stage { IDLE, SETUP, READY, DRAW, SWORD, CHARGE, TAIL, DONE }
    private static Stage stage=Stage.IDLE;
    private static Path root;
    private static boolean enabled, checked, held, aimed;
    private static volatile boolean recording;
    private static volatile boolean equipped;
    private static int ticks, drawStill, swordStill, chargeStill, frameInterval=187;
    private static long zero, lastFrame=-1, drawStart=-1;
    private static volatile long serverDrawStart=-1;

    private static void mark(String file, String message) {
        try { Files.writeString(root.resolve(file), message); }
        catch(IOException ex) {throw new IllegalStateException(ex);}
    }
    private static synchronized void note(String event) {
        String line=(zero==0?0:System.currentTimeMillis()-zero)+"\t"+event;
        System.out.println("EA DEMO "+line);
        try {Files.writeString(root.resolve("ea-demo.events"),line+"\n",StandardOpenOption.CREATE,StandardOpenOption.APPEND);}
        catch(IOException ex) {throw new IllegalStateException(ex);}
    }
    private static void input(Minecraft mc, boolean down) {
        try {MOUSE.invoke(mc.mouseHandler,mc.getWindow().getWindow(),mc.options.keyUse.getKey().getValue(),
                down?GLFW.GLFW_PRESS:GLFW.GLFW_RELEASE,0);}
        catch(ReflectiveOperationException ex){throw new IllegalStateException(ex);}
        held=down;
    }
    private static void enter(Stage next) {stage=next; ticks=0; note("STAGE\t"+next);}
    @SubscribeEvent public static void keyStarted(net.minecraftforge.event.entity.living.LivingEntityUseItemEvent.Start e){
        if(enabled && e.getEntity() instanceof net.minecraft.server.level.ServerPlayer p && e.getItem().is(StarcrownDoll.BAB_ILU.get()))
            serverDrawStart=p.serverLevel().getGameTime();
    }
    @SubscribeEvent public static void retrieved(net.minecraftforge.event.entity.living.LivingEntityUseItemEvent.Finish e) {
        if(!enabled || !(e.getEntity() instanceof net.minecraft.server.level.ServerPlayer)
                || !e.getItem().is(StarcrownDoll.BAB_ILU.get()) || !e.getResultStack().is(StarcrownDoll.EA.get()))return;
        var mc=Minecraft.getInstance();
        mc.execute(()->{
            // Release this press after authoritative completion, before vanilla can repeat it on the replacement item.
            if(stage==Stage.DRAW && held){input(mc,false);note("KEY_FINISHED_ON_SERVER");}
        });
    }
    private static void finish(Minecraft mc,String reason) {
        if(held)input(mc,false);
        recording=false;
        mc.options.setCameraType(CameraType.FIRST_PERSON);
        mc.options.hideGui=false;
        enter(Stage.DONE);
        mark("ea-demo.complete",reason);
    }
    private static void prepare(Minecraft mc) {
        enter(Stage.SETUP);
        mc.setScreen(null);
        mc.options.hideGui=false;
        mc.options.setCameraType(CameraType.FIRST_PERSON);
        mc.options.bobView().set(false);
        mc.options.fov().set(70);
        GLFW.glfwSetWindowTitle(mc.getWindow().getWindow(),"Minecraft · 王律拔剑与 Ea 蓄力");
        var server=mc.getSingleplayerServer();
        var id=mc.player.getUUID();
        server.execute(()->{
            var p=server.getPlayerList().getPlayer(id);
            var world=p.serverLevel();
            world.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false,server);
            world.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false,server);
            world.setDayTime(12700);
            world.setWeatherParameters(60000,0,false,false);
            for(int x=-11;x<=11;x++)for(int z=-11;z<=11;z++){
                var block= Math.abs(x)==11 || Math.abs(z)==11 ? Blocks.CUT_SANDSTONE : Blocks.SMOOTH_SANDSTONE;
                world.setBlockAndUpdate(new BlockPos(x,100,z),block.defaultBlockState());
            }
            p.setGameMode(GameType.CREATIVE);
            p.teleportTo(world,.5,101,.5,0,-17);
            p.getInventory().clearContent();
            p.getInventory().selected=0;
            p.getCooldowns().removeCooldown(StarcrownDoll.BAB_ILU.get());
            p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(StarcrownDoll.BAB_ILU.get()));
            p.inventoryMenu.broadcastChanges();
            equipped=true;
        });
    }
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent e) {
        if(e.phase!=TickEvent.Phase.END || stage==Stage.DONE)return;
        var mc=Minecraft.getInstance();
        if(!checked){
            root=mc.gameDirectory.toPath();
            enabled=Files.isRegularFile(root.resolve("enable-ea-demo"));
            checked=true;
        }
        if(!enabled || mc.getOverlay()!=null)return;
        if(mc.screen instanceof ConfirmScreen && stage==Stage.IDLE){
            mc.screen.children().stream().filter(Button.class::isInstance).map(Button.class::cast)
                    .filter(b->b.getMessage().getContents() instanceof TranslatableContents t && t.getKey().equals("gui.proceed"))
                    .findFirst().ifPresent(Button::onPress);
            return;
        }
        if(mc.level==null || mc.player==null || mc.gameMode==null || mc.getSingleplayerServer()==null){
            if(stage!=Stage.IDLE)finish(mc,"FAIL left demonstration world");
            return;
        }
        try{
            if(stage==Stage.IDLE){prepare(mc);return;}
            if(mc.screen!=null)return;
            ticks++;
            switch(stage){
                case SETUP -> {
                    if(!equipped || ticks<45 || !mc.player.getMainHandItem().is(StarcrownDoll.BAB_ILU.get()))return;
                    mark("ea-demo.window",Long.toString(GLFWNativeWin32.glfwGetWin32Window(mc.getWindow().getWindow())));
                    enter(Stage.READY);
                }
                case READY -> {
                    if(!recording && Files.exists(root.resolve("ea-demo.record"))){
                        if(Files.exists(root.resolve("ea-demo.frame-sequence")))frameInterval=70;
                        zero=System.currentTimeMillis(); recording=true; ticks=0;
                        note("ORIGIN\t"+zero);
                    }
                    if(recording && ticks>=14){enter(Stage.DRAW);input(mc,true);}
                }
                case DRAW -> {
                    if(drawStart<0 && mc.player.isUsingItem())drawStart=mc.level.getGameTime()-mc.player.getTicksUsingItem();
                    if(mc.player.getMainHandItem().is(StarcrownDoll.EA.get())){
                        if(held)input(mc,false);
                        enter(Stage.SWORD);
                    }else if(ticks>FateItems.DURATION+60)finish(mc,"FAIL retrieval did not grant Ea");
                }
                case SWORD -> {
                    if(ticks==44){enter(Stage.CHARGE);input(mc,true);}
                }
                case CHARGE -> {
                    float age=EaClient.ageFor(mc.player);
                    if(age>=32 && mc.options.getCameraType().isFirstPerson())mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
                    if(age>=526 && !aimed){
                        aimed=true;
                        mc.player.setXRot(-84);
                        note("AIM_AT_SKY\t"+age);
                    }
                    if(plays.containsKey("ea_blast")){
                        if(held)input(mc,false);
                        enter(Stage.TAIL);
                    }else if(ticks>620)finish(mc,"FAIL full chant did not release");
                }
                case TAIL -> {
                    if(ticks>=200){
                        mc.player.setXRot(-12);
                        boolean stopped=sounds.values().stream().noneMatch(mc.getSoundManager()::isActive);
                        boolean once=EXPECTED.equals(plays.keySet()) && EXPECTED.equals(sources)
                                && plays.values().stream().allMatch(count->count==1);
                        long prelude=soundTicks.getOrDefault("ea_chant",-9999L)-soundTicks.getOrDefault("ea_spin",9999L);
                        long verse=soundTicks.getOrDefault("ea_blast",-9999L)-soundTicks.getOrDefault("ea_chant",9999L);
                        boolean timed=Math.abs(prelude-40)<=4 && Math.abs(verse-521)<=4;
                        boolean sword=mc.player.getMainHandItem().is(StarcrownDoll.EA.get()) && !mc.player.isUsingItem();
                        note("CHANT_TIMING\tpreludeTicks="+prelude+"\tverseTicks="+verse);
                        note("HANDOFF\tEa="+sword+"\tsoundsStopped="+stopped+"\tevents="+plays+"\tchannels="+sources);
                        finish(mc,once && stopped && sword && timed ? "PASS" : "FAIL handoff or sound timeline");
                    }
                }
                default -> {}
            }
        }catch(RuntimeException ex){ex.printStackTrace();finish(mc,"FAIL "+ex);}
    }
    @SubscribeEvent public static void sound(PlaySoundEvent e){
        if(!recording || e.getSound()==null)return;
        var sound=e.getSound(); var id=sound.getLocation();
        if(!id.getNamespace().equals("starcrowndoll") || !id.getPath().startsWith("ea_"))return;
        sounds.put(id.getPath(),sound);plays.merge(id.getPath(),1,Integer::sum);
        var mc=Minecraft.getInstance();
        soundTicks.put(id.getPath(),mc.level.getGameTime());
        if(id.getPath().equals("ea_blast") && held)input(mc,false);
        note("SOUND\t"+id.getPath()+"\tgameTime="+mc.level.getGameTime());
    }
    @SubscribeEvent public static void source(net.minecraftforge.client.event.sound.PlaySoundSourceEvent e){source(e.getSound());}
    @SubscribeEvent public static void stream(net.minecraftforge.client.event.sound.PlayStreamingSourceEvent e){source(e.getSound());}
    private static void source(SoundInstance sound){
        if(!recording || !sound.getLocation().getNamespace().equals("starcrowndoll"))return;
        String id=sound.getLocation().getPath();
        if(!id.startsWith("ea_"))return;
        sources.add(id);
        note("CHANNEL\t"+id+"\t"+sound.getVolume()+"\t"+sound.getPitch());
    }
    @SubscribeEvent public static void gui(RenderGuiEvent.Pre e) {
        if(enabled && stage!=Stage.IDLE && stage!=Stage.DONE)e.setCanceled(true);
    }
    private static double smooth(double t){t=Math.max(0,Math.min(1,t));return t*t*(3-2*t);}
    @SubscribeEvent public static void camera(ViewportEvent.ComputeCameraAngles e) {
        var mc=Minecraft.getInstance();
        if(!recording || mc.options.getCameraType().isFirstPerson() || mc.player==null)return;
        double age=stage==Stage.TAIL?560:EaClient.ageFor(mc.player);
        double wide=smooth((age-70)/60);
        double close=smooth((age-350)/85);
        double distance=6.1+9.0*wide-4.6*close;
        double azimuth=.42+.42*smooth((age-90)/310);
        var base=mc.player.position();
        var eye=base.add(-Math.sin(azimuth)*distance,2.6+3.1*wide-1.9*close,Math.cos(azimuth)*distance);
        var aim=base.add(0,1.8+2.0*wide-1.2*close,0);
        if(stage==Stage.TAIL){
            double fly=smooth(ticks/20.);
            eye=eye.lerp(base.add(-12,5.6,16),fly);
            aim=aim.lerp(base.add(0,7,.6),fly);
        }
        var d=aim.subtract(eye);
        try{CAMERA.invoke(e.getCamera(),eye);}catch(ReflectiveOperationException ex){throw new IllegalStateException(ex);}
        e.setYaw((float)Math.toDegrees(Math.atan2(-d.x,d.z)));
        e.setPitch((float)Math.toDegrees(Math.atan2(-d.y,d.horizontalDistance())));
    }
    private static void shot(Minecraft mc,String name){
        Screenshot.grab(mc.gameDirectory,name+".png",mc.getMainRenderTarget(),message->{});
    }
    @SubscribeEvent public static void render(TickEvent.RenderTickEvent e){
        if(e.phase!=TickEvent.Phase.END || !recording)return;
        var mc=Minecraft.getInstance();
        if(mc.player==null || mc.level==null || mc.screen!=null)return;
        long elapsed=System.currentTimeMillis()-zero;
        if(lastFrame<0 || elapsed-lastFrame>=frameInterval){
            shot(mc,String.format(Locale.ROOT,"ea-frame-%07d",elapsed));
            lastFrame=elapsed;
        }
        long start=serverDrawStart>=0?serverDrawStart:drawStart;
        float age=start>=0 ? mc.level.getGameTime()-start+mc.getFrameTime() : -1;
        if(stage==Stage.DRAW && drawStill<DRAW_STILLS.length && age>=DRAW_STILLS[drawStill]){
            int target=DRAW_STILLS[drawStill++];shot(mc,"ea-draw-"+target);note("DRAW_STILL\t"+target+"\t"+age);
        }
        if(stage==Stage.SWORD && swordStill<SWORD_STILLS.length && age>=FateItems.DURATION+SWORD_STILLS[swordStill]){
            int target=FateItems.DURATION+SWORD_STILLS[swordStill++];shot(mc,"ea-draw-"+target);note("SWORD_STILL\t"+target+"\t"+age);
        }
        if(stage!=Stage.CHARGE)return;
        age=EaClient.ageFor(mc.player);
        if(chargeStill<CHARGE_STILLS.length && age>=CHARGE_STILLS[chargeStill]){
            int target=CHARGE_STILLS[chargeStill++];shot(mc,"ea-charge-"+target);note("CHARGE_STILL\t"+target+"\t"+age);
        }
    }
}
