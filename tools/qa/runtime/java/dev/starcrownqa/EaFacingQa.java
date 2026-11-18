package dev.starcrownqa;

import dev.starcrown.StarcrownDoll;
import java.nio.file.Files;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
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

@Mod.EventBusSubscriber(modid="starcrownqa",value=Dist.CLIENT)
public final class EaFacingQa {
    private static boolean setup,finished;
    private static volatile boolean ready;
    private static int tick,shot;
    private static boolean enabled(Minecraft mc){
        return enuma(mc)||Files.exists(mc.gameDirectory.toPath().resolve("enable-ea-facing-qa"));
    }
    private static boolean enuma(Minecraft mc){return cylinder(mc)||Files.exists(mc.gameDirectory.toPath().resolve("enable-enuma-qa"));}
    private static boolean cylinder(Minecraft mc){return Files.exists(mc.gameDirectory.toPath().resolve("enable-ea-cylinder-qa"));}
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
            setup=true;
            mc.setScreen(null);
            mc.options.hideGui=true;
            mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
            var id=mc.player.getUUID();
            mc.getSingleplayerServer().execute(()->{
                var player=mc.getSingleplayerServer().getPlayerList().getPlayer(id);
                var world=player.serverLevel();
                for(int x=-9;x<=9;x++)for(int z=-9;z<=9;z++)
                    world.setBlockAndUpdate(new BlockPos(x,100,z),Blocks.SMOOTH_STONE.defaultBlockState());
                world.setDayTime(6000);
                world.setWeatherParameters(12000,0,false,false);
                player.setGameMode(GameType.CREATIVE);
                player.teleportTo(world,.5,101,.5,0,cylinder(mc)?0:9);
                if(cylinder(mc)){
                    world.setBlockAndUpdate(new BlockPos(3,102,9),Blocks.STONE.defaultBlockState());
                    world.setBlockAndUpdate(new BlockPos(4,102,9),Blocks.STONE.defaultBlockState());
                    world.setBlockAndUpdate(new BlockPos(0,102,11),Blocks.BEDROCK.defaultBlockState());
                    world.setBlockAndUpdate(new BlockPos(0,102,15),Blocks.STONE.defaultBlockState());
                }
                player.getInventory().clearContent();
                player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(StarcrownDoll.EA.get()));
                ready=true;
            });
            return;
        }
        if(!ready||!mc.player.getMainHandItem().is(StarcrownDoll.EA.get()))return;
        tick++;
        if(enuma(mc)){
            int shift=cylinder(mc)?dev.eavfx.EaAudioConfig.DEFAULT_CHANT_TICKS-120:0;
            if(tick==12){
                mc.options.setCameraType(CameraType.FIRST_PERSON);
                mc.options.hideGui=false;
                mc.options.keyUse.setDown(true);
                mc.gameMode.useItem(mc.player,InteractionHand.MAIN_HAND);
            }
            if(tick==168+shift){
                mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
                mc.options.hideGui=true;
            }
            if(tick==195+shift)mc.options.keyUse.setDown(false);
            if(tick>=215+shift&&shot==3){
                finished=true;
                if(cylinder(mc))mc.getSingleplayerServer().execute(()->{
                    var world=mc.getSingleplayerServer().overworld();
                    boolean inside=world.getBlockState(new BlockPos(3,102,9)).isAir();
                    boolean outside=world.getBlockState(new BlockPos(4,102,9)).is(Blocks.STONE);
                    boolean bedrock=world.getBlockState(new BlockPos(0,102,11)).is(Blocks.BEDROCK);
                    boolean behind=world.getBlockState(new BlockPos(0,102,15)).isAir();
                    System.out.println("EA TERRAIN inside="+inside+" outside="+outside+" bedrock="+bedrock+" behind="+behind);
                    mc.execute(mc::stop);
                });
                else mc.stop();
            }
            return;
        }
        if(tick==84){
            mc.options.setCameraType(CameraType.FIRST_PERSON);
            mc.options.hideGui=false;
        }
        if(tick>=120&&shot==3){finished=true;mc.stop();}
    }
    @SubscribeEvent public static void camera(ViewportEvent.ComputeCameraAngles event){
        var mc=Minecraft.getInstance();
        boolean cast=enuma(mc);
        int shift=cylinder(mc)?dev.eavfx.EaAudioConfig.DEFAULT_CHANT_TICKS-120:0;
        if(!ready||finished||!enabled(mc)||(cast?tick<168+shift:tick>=84))return;
        try{
            Vec3 eye=cast?new Vec3(-6,103.8,5):tick<48?new Vec3(3.1,102.28,3.6):new Vec3(3.25,102.4,-1.45);
            Vec3 aim=cast?new Vec3(.5,102.1,4):new Vec3(.64,102.03,.85);
            Vec3 delta=aim.subtract(eye);
            var setter=java.util.Arrays.stream(net.minecraft.client.Camera.class.getDeclaredMethods())
                    .filter(method->method.getReturnType()==void.class)
                    .filter(method->java.util.Arrays.equals(method.getParameterTypes(),new Class<?>[]{Vec3.class}))
                    .findFirst().orElseThrow();
            setter.setAccessible(true);
            setter.invoke(event.getCamera(),eye);
            event.setYaw((float)Math.toDegrees(Math.atan2(-delta.x,delta.z)));
            event.setPitch((float)Math.toDegrees(Math.atan2(-delta.y,delta.horizontalDistance())));
        }catch(ReflectiveOperationException error){throw new IllegalStateException(error);}
    }
    @SubscribeEvent public static void render(TickEvent.RenderTickEvent event){
        if(event.phase!=TickEvent.Phase.END||finished)return;
        var mc=Minecraft.getInstance();
        boolean cast=enuma(mc);
        int frame=cast?(tick>=(cylinder(mc)?202+dev.eavfx.EaAudioConfig.DEFAULT_CHANT_TICKS-120:186)?3:tick>=72?2:tick>=39?1:0)
                :(tick>=106?3:tick>=70?2:tick>=34?1:0);
        if(frame<=shot)return;
        shot=frame;
        String name=cast?switch(frame){case 1->"ea-charge.png";case 2->"ea-chant.png";default->"ea-blast.png";}
                :switch(frame){case 1->"ea-front-oblique.png";case 2->"ea-holder-side.png";default->"ea-hand-view.png";};
        System.out.println("Ea capture "+name+" GL="+org.lwjgl.opengl.GL11.glGetError());
        Screenshot.grab(mc.gameDirectory,name,mc.getMainRenderTarget(),message->{});
    }
}
