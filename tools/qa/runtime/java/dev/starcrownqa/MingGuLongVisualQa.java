package dev.starcrownqa;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.math.Axis;
import dev.starcrown.MingGuLongEntity;
import dev.starcrown.MingGuLongRenderer;
import dev.starcrown.StarcrownDoll;
import java.nio.file.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.opengl.GL11;

/** Opt-in real production-renderer captures; this class is only in the QA JAR. */
@Mod.EventBusSubscriber(modid="starcrownqa",value=Dist.CLIENT)
public final class MingGuLongVisualQa {
    private static final int[] STATES={0,9,10,5,6,7,13};
    private static final int[] TIMES={30,15,14,25,20,20,60};
    private static final String[] LABELS={"Idle","Ground_attack_1","Ground_attack_2","Takeoff","Glide","Hover","Huangwu_charge"};
    private static Preview preview;
    private static int phase,ticks,finish;
    private static boolean shotDue;
    private static boolean failed;
    private static boolean worldScheduled;
    private static int worldTicks,worldPhase;
    private static Path root(){return Minecraft.getInstance().gameDirectory.toPath();}
    private static void log(String value) throws Exception {
        Files.writeString(root().resolve("native-render-results.txt"),value+"\n",StandardOpenOption.CREATE,StandardOpenOption.APPEND);
    }
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        var mc=Minecraft.getInstance();
        if(!failed&&event.phase==TickEvent.Phase.END&&Files.exists(root().resolve("enable-native-world-qa"))) {
            worldTick();return;
        }
        if(failed||event.phase!=TickEvent.Phase.END||!Files.exists(root().resolve("enable-native-json-qa")))return;
        try {
            if(mc.level==null||mc.player==null||mc.getOverlay()!=null)return;
            if(finish>0){if(++finish>10)mc.stop();return;}
            if(preview==null) {
                mc.options.guiScale().set(1);mc.resizeDisplay();
                mc.options.pauseOnLostFocus=false;
                log("GPU="+GL11.glGetString(GL11.GL_RENDERER)+"; vendor="+GL11.glGetString(GL11.GL_VENDOR));
                preview=new Preview();mc.setScreen(preview);preview.setState(STATES[0]);
                log("Production renderer="+mc.getEntityRenderDispatcher().getRenderer(preview.entities[0]).getClass().getName());
            }
            if(shotDue)return;
            for(var entity:preview.entities)((net.minecraft.world.entity.Entity)entity).tickCount++;
            if(++ticks==TIMES[phase])shotDue=true;
        } catch(Throwable error) {failed=true;try{log("FAIL "+error);}catch(Exception ignored){}error.printStackTrace();mc.stop();}
    }
    @SubscribeEvent public static void rendered(TickEvent.RenderTickEvent event) {
        if(event.phase==TickEvent.Phase.END&&Files.exists(root().resolve("enable-native-world-qa"))) {
            worldRendered();return;
        }
        if(event.phase!=TickEvent.Phase.END||!shotDue||preview==null)return;
        var mc=Minecraft.getInstance();
        try {
            String name=String.format("native_%02d_%s.png",phase,LABELS[phase]);
            Screenshot.grab(mc.gameDirectory,name,mc.getMainRenderTarget(),message->{});
            var renderer=(MingGuLongRenderer)mc.getEntityRenderDispatcher().getRenderer(preview.entities[0]);
            int finite=0;
            var poseDump=new com.google.gson.JsonObject();
            double animationTime=software.bernie.geckolib.core.molang.MolangParser.INSTANCE.getVariable("query.anim_time").get();
            poseDump.addProperty("animation",preview.entities[0].getAnimationName());
            poseDump.addProperty("animation_time_seconds",animationTime);
            var boneDump=new com.google.gson.JsonObject();poseDump.add("bones",boneDump);
            for(var bone:renderer.getGeoModel().getAnimationProcessor().getRegisteredBones()) {
                if(!Float.isFinite(bone.getRotX())||!Float.isFinite(bone.getRotY())||!Float.isFinite(bone.getRotZ())
                    ||!Float.isFinite(bone.getScaleX())||!Float.isFinite(bone.getScaleY())||!Float.isFinite(bone.getScaleZ())
                    ||!Float.isFinite(bone.getPosX())||!Float.isFinite(bone.getPosY())||!Float.isFinite(bone.getPosZ()))
                    throw new AssertionError("Nonfinite bone "+bone.getName());
                finite++;
                var values=new com.google.gson.JsonArray();
                for(float value:new float[]{bone.getRotX(),bone.getRotY(),bone.getRotZ(),bone.getPosX(),bone.getPosY(),bone.getPosZ(),bone.getScaleX(),bone.getScaleY(),bone.getScaleZ()})values.add(value);
                boneDump.add(bone.getName(),values);
            }
            Files.writeString(root().resolve(name+".bones.json"),poseDump.toString());
            log("CAPTURE "+name+" animation="+preview.entities[0].getAnimationName()+" ticks="+ticks+" anim_seconds="+animationTime+" finite_bones="+finite);
            shotDue=false;ticks=0;
            if(++phase==STATES.length){log("CAPTURES_COMPLETE (visual inspection required)");finish=1;}
            else preview.setState(STATES[phase]);
        }catch(Throwable error){try{log("FAIL "+error);}catch(Exception ignored){}error.printStackTrace();mc.stop();}
    }
    private static void worldTick() {
        var mc=Minecraft.getInstance();if(mc.player==null||mc.getSingleplayerServer()==null||mc.getOverlay()!=null)return;
        try {
            if(finish>0){if(++finish>10)mc.stop();return;}
            if(!worldScheduled) {
                worldScheduled=true;mc.options.hideGui=true;mc.options.pauseOnLostFocus=false;mc.setScreen(null);
                var id=mc.player.getUUID();
                mc.getSingleplayerServer().execute(()->{
                    var p=mc.getSingleplayerServer().getPlayerList().getPlayer(id);var level=p.serverLevel();
                    level.setDayTime(6000);level.setWeatherParameters(6000,0,false,false);
                    level.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_DOMOBSPAWNING).set(false,level.getServer());
                    var old=new java.util.ArrayList<net.minecraft.world.entity.Entity>();level.getAllEntities().forEach(old::add);
                    for(var e:old)if(e instanceof net.minecraft.world.entity.Mob)e.discard();
                    for(int x=-18;x<=18;x++)for(int z=-18;z<=18;z++)level.setBlockAndUpdate(new net.minecraft.core.BlockPos(x,100,z),net.minecraft.world.level.block.Blocks.SMOOTH_STONE.defaultBlockState());
                    p.setGameMode(net.minecraft.world.level.GameType.CREATIVE);p.getAbilities().flying=true;p.onUpdateAbilities();
                    p.teleportTo(level,0,102,14,180,0);
                    var dragon=StarcrownDoll.MINGGULONG.get().create(level);
                    ((net.minecraft.world.entity.Entity)dragon).moveTo(0,101,0,0,0);
                    ((net.minecraft.world.entity.Mob)dragon).setNoAi(true);level.addFreshEntity(dragon);
                });
                log("WORLD_QA production server entity; GPU="+GL11.glGetString(GL11.GL_RENDERER));
            }
            if(++worldTicks==80)shotDue=true;
        }catch(Throwable e){failed=true;try{log("FAIL world "+e);}catch(Exception ignored){}e.printStackTrace();mc.stop();}
    }
    private static void worldRendered() {
        if(!shotDue||failed)return;var mc=Minecraft.getInstance();
        try {
            String name="native_world_"+new String[]{"front","side","back"}[worldPhase]+".png";
            Screenshot.grab(mc.gameDirectory,name,mc.getMainRenderTarget(),message->{});log("WORLD_CAPTURE "+name);
            shotDue=false;worldTicks=0;
            if(++worldPhase==3){log("WORLD_CAPTURES_COMPLETE (visual inspection required)");finish=1;return;}
            var id=mc.player.getUUID();int view=worldPhase;
            mc.getSingleplayerServer().execute(()->{var p=mc.getSingleplayerServer().getPlayerList().getPlayer(id);
                if(view==1)p.teleportTo(p.serverLevel(),14,102,0,90,0);else p.teleportTo(p.serverLevel(),0,102,-14,0,0);
            });
        }catch(Throwable e){failed=true;try{log("FAIL world render "+e);}catch(Exception ignored){}e.printStackTrace();mc.stop();}
    }
    private static final class Preview extends Screen {
        final MingGuLongEntity[] entities=new MingGuLongEntity[4];
        Preview(){super(Component.literal("Native BB JSON / Minecraft production renderer"));}
        @SuppressWarnings("unchecked") void setState(int state) throws Exception {
            var mc=Minecraft.getInstance();var field=MingGuLongEntity.class.getDeclaredField("ANIMATION");field.setAccessible(true);
            var key=(EntityDataAccessor<Integer>)field.get(null);
            for(int i=0;i<4;i++) {
                var e=StarcrownDoll.MINGGULONG.get().create(mc.level);
                var base=(net.minecraft.world.entity.Entity)e;base.setPos(0,0,0);base.setOnGround(true);
                ((net.minecraft.world.entity.Mob)e).setNoAi(true);
                base.getEntityData().set(key,state);entities[i]=e;
            }
        }
        @Override public boolean isPauseScreen(){return false;}
        @Override public void render(GuiGraphics g,int mx,int my,float partial) {
            g.fill(0,0,width,height,0xff26313b);
            g.drawCenteredString(font,title,width/2,15,0xffffff);
            g.drawCenteredString(font,Component.literal(LABELS[Math.min(phase,LABELS.length-1)]+" / tick "+ticks),width/2,32,0xffd09c);
            float cellW=width/2f,cellH=(height-60)/2f;
            String[] views={"Front","Left","Right","Back"};float[] yaws={0,90,-90,180};
            for(int i=0;i<4;i++) {
                float x=cellW*(i%2),y=60+cellH*(i/2);
                g.drawCenteredString(font,Component.literal(views[i]),(int)(x+cellW/2),(int)(y+5),0xcad7e2);
                draw(g,entities[i],x+cellW/2,y+cellH*.82f,Math.min(cellW/13f,cellH/8f),yaws[i]);
            }
            super.render(g,mx,my,partial);
        }
        private void draw(GuiGraphics g,MingGuLongEntity entity,float x,float y,float scale,float yaw) {
            if(entity==null)return;
            var mc=Minecraft.getInstance();g.flush();var p=g.pose();p.pushPose();
            p.translate(x,y,250);p.scale(scale,-scale,scale);p.mulPose(Axis.XP.rotationDegrees(8));p.mulPose(Axis.YP.rotationDegrees(yaw));
            Lighting.setupForEntityInInventory();RenderSystem.enableDepthTest();
            var dispatch=mc.getEntityRenderDispatcher();dispatch.setRenderShadow(false);
            dispatch.render(entity,0,0,0,0,0,p,mc.renderBuffers().bufferSource(),15728880);
            mc.renderBuffers().bufferSource().endBatch();dispatch.setRenderShadow(true);p.popPose();
            Lighting.setupFor3DItems();g.flush();
        }
    }
}
