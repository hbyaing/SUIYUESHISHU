package dev.starcrownqa;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.math.Axis;
import dev.starcrown.*;
import java.nio.file.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.world.entity.*;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.opengl.GL11;

/** Separate opt-in QA JAR. No input synthesis or interaction with a user's client. */
@Mod.EventBusSubscriber(modid="starcrownqa",value=Dist.CLIENT)
public final class MingGuLongLoopWingQa {
    private static final int[] KEYS={0,3,4,6,7,5,6};
    private static final int[] LENGTHS={30,100,70,380,110,52,45};
    private static final String[] NAMES={"wing_top","walk","run","glide","hover","takeoff","takeoff_to_glide"};
    private static final int[] WORLD_SHOTS={20,30,36,45,70,150,220,240,245,250,259,270,288};
    private static int phase,ticks,worldTicks,lastPoseTick=-1,loops;
    private static boolean failed,world,worldSetup,due,completed;
    private static double lastTime=-1;
    private static Preview preview;
    private static boolean quick(){return Files.exists(root().resolve("enable-wing-vfx-final-qa"));}
    private static Path root(){return Minecraft.getInstance().gameDirectory.toPath();}
    private static boolean enabled(){return Files.exists(root().resolve("enable-loop-wing-qa"));}
    private static void log(String text) throws Exception {Files.writeString(root().resolve("loop-wing-results.txt"),text+"\n",StandardOpenOption.CREATE,StandardOpenOption.APPEND);}
    private static void fail(Throwable e){failed=true;e.printStackTrace();try{log("FAIL "+e);}catch(Exception ignored){}Minecraft.getInstance().stop();}
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event){
        if(event.phase!=TickEvent.Phase.END||!enabled()||failed||completed)return;var mc=Minecraft.getInstance();
        if(mc.level==null||mc.player==null||mc.getOverlay()!=null)return;
        try {
            if(world){worldTick();return;}
            if(preview==null){
                mc.options.guiScale().set(1);mc.resizeDisplay();mc.options.pauseOnLostFocus=false;
                mc.getSingleplayerServer().execute(()->{
                    var level=mc.getSingleplayerServer().overworld();
                    var old=new java.util.ArrayList<Entity>();level.getAllEntities().forEach(old::add);
                    for(var e:old)if(e instanceof Mob||e instanceof MingGuLongVortex||e instanceof MingGuLongProjectile)e.discard();
                });
                log("GPU="+GL11.glGetString(GL11.GL_RENDERER));preview=new Preview();mc.setScreen(preview);preview.setState(KEYS[0]);
            }
            if(quick()){
                // Keep the exact same idle pose for before/after wing views.
                ticks++;due=ticks==20;return;
            }
            ((Entity)preview.entity).tickCount++;ticks++;
            due=phase==0?ticks==LENGTHS[phase]:ticks%3==0||ticks>=LENGTHS[phase];
        }catch(Throwable e){fail(e);}
    }
    @SubscribeEvent public static void rendered(TickEvent.RenderTickEvent event){
        if(event.phase!=TickEvent.Phase.END||!enabled()||failed||completed)return;var mc=Minecraft.getInstance();
        try {
            if(world){
                for(int n:WORLD_SHOTS)if(worldTicks==n&&lastPoseTick!=n){lastPoseTick=n;capture("vfx_world_"+n+".png");log("VFX_CAPTURE tick="+n);
                    for(var e:mc.level.entitiesForRendering())if(e instanceof MingGuLongEntity dragon) {
                        var model=((MingGuLongRenderer)mc.getEntityRenderDispatcher().getRenderer(dragon)).getGeoModel();
                        var root=model.getBone("Root").orElseThrow();
                        log("VFX_DRAGON key="+dragon.getAnimationKey()+" age="+dragon.getHuangwuAge(0)+" root="+root.getPosX()+","+root.getPosY()+","+root.getPosZ()+" pos="+((Entity)dragon).position()+" anchor="+dragon.getHuangwuChargePosition()+" yaw="+((LivingEntity)dragon).yBodyRot);
                    }}
                if(worldTicks>=300){completed=true;log("COMPLETE loops, wing views, live server VFX (visual review required)");mc.stop();}return;
            }
            if(quick()){
                if(!due)return;due=false;
                capture("final_wing_"+new String[]{"before_top","after_top","left","right","back"}[phase]+".png");
                if(++phase==5){world=true;lastPoseTick=-1;mc.setScreen(null);return;}
                ticks=0;return;
            }
            if(preview==null||lastPoseTick==ticks)return;lastPoseTick=ticks;
            var renderer=(MingGuLongRenderer)mc.getEntityRenderDispatcher().getRenderer(preview.entity);
            var rootBone=renderer.getGeoModel().getBone("Root").orElseThrow();
            double t=software.bernie.geckolib.core.molang.MolangParser.INSTANCE.getVariable("query.anim_time").get();
            if(phase>0&&Math.abs(rootBone.getPosX())+Math.abs(rootBone.getPosY())+Math.abs(rootBone.getPosZ())>1e-5)throw new AssertionError("Locomotion root travel leaked");
            if(lastTime>=0&&t+1e-5<lastTime){loops++;log("LOOP "+NAMES[phase]+" index="+loops+" tick="+ticks+" before="+lastTime+" after="+t);}
            lastTime=t;
            var hip=renderer.getGeoModel().getBone("Bip001").orElseThrow();
            log("POSE "+NAMES[phase]+" tick="+ticks+" t="+t+" root="+rootBone.getPosX()+","+rootBone.getPosY()+","+rootBone.getPosZ()+" hip="+hip.getPosX()+","+hip.getPosY()+","+hip.getPosZ());
            if(due){capture(String.format("loop_%02d_%s_%03d.png",phase,NAMES[phase],ticks));due=false;}
            if(ticks>=LENGTHS[phase]){
                log("PHASE_COMPLETE "+NAMES[phase]+" observed_loops="+loops);
                if(phase>=1&&phase<=4&&loops<3)throw new AssertionError("Not enough full loops: "+NAMES[phase]+" "+loops);
                if(++phase==KEYS.length){world=true;ticks=0;lastPoseTick=-1;mc.setScreen(null);return;}
                ticks=0;lastPoseTick=-1;lastTime=-1;loops=0;preview.setState(KEYS[phase]);
            }
        }catch(Throwable e){fail(e);}
    }
    private static void capture(String name){var mc=Minecraft.getInstance();Screenshot.grab(mc.gameDirectory,name,mc.getMainRenderTarget(),message->{});}
    private static void worldTick() throws Exception {
        var mc=Minecraft.getInstance();if(mc.getSingleplayerServer()==null)return;
        if(!worldSetup){
            worldSetup=true;mc.options.hideGui=true;var id=mc.player.getUUID();
            mc.getSingleplayerServer().execute(()->{
                try{
                    var player=mc.getSingleplayerServer().getPlayerList().getPlayer(id);var level=player.serverLevel();
                    level.setDayTime(6000);level.setWeatherParameters(6000,0,false,false);
                    level.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_DOMOBSPAWNING).set(false,level.getServer());
                    var old=new java.util.ArrayList<Entity>();level.getAllEntities().forEach(old::add);for(var entity:old)if(entity instanceof Mob||entity instanceof MingGuLongVortex||entity instanceof MingGuLongProjectile)entity.discard();
                    for(int x=-20;x<=20;x++)for(int z=-20;z<=30;z++)level.setBlockAndUpdate(new net.minecraft.core.BlockPos(x,100,z),net.minecraft.world.level.block.Blocks.SMOOTH_STONE.defaultBlockState());
                    player.setGameMode(net.minecraft.world.level.GameType.CREATIVE);player.getAbilities().flying=true;player.onUpdateAbilities();
                    player.teleportTo(level,5,104.3,8,148,17);
                    var dragon=StarcrownDoll.MINGGULONG.get().create(level);((Entity)dragon).moveTo(0,101,0,0,0);level.addFreshEntity(dragon);
                    var cow=EntityType.COW.create(level);cow.moveTo(0,101,16,0,0);cow.setNoAi(true);level.addFreshEntity(cow);
                    var valid=MingGuLongEntity.class.getDeclaredMethod("isValidTarget",LivingEntity.class);valid.setAccessible(true);
                    if(!(boolean)valid.invoke(dragon,cow))throw new AssertionError("Passive cow not targetable");
                    var sheep=EntityType.SHEEP.create(level);if(!(boolean)valid.invoke(dragon,sheep))throw new AssertionError("Passive sheep not targetable");
                    var zombie=EntityType.ZOMBIE.create(level);if(!(boolean)valid.invoke(dragon,zombie))throw new AssertionError("Hostile target not targetable");
                    if((boolean)valid.invoke(dragon,dragon)||(boolean)valid.invoke(dragon,player))throw new AssertionError("Self/creative exclusion lost");
                    var scan=MingGuLongEntity.class.getDeclaredMethod("refreshTarget");scan.setAccessible(true);scan.invoke(dragon);
                    if(((Mob)dragon).getTarget()!=cow)throw new AssertionError("Actual scan did not select passive cow");
                    log("PASS actual scan targets passive cow; sheep/zombie valid; self/creative excluded");
                    verifyVortex(level);
                    var saw=MingGuLongEntity.class.getDeclaredField("sawTarget");saw.setAccessible(true);saw.setBoolean(dragon,true);
                    var start=MingGuLongEntity.class.getDeclaredMethod("startHuangwu");start.setAccessible(true);start.invoke(dragon);
                    log("WORLD_SKILL_START server_tick="+level.getGameTime());
                }catch(Throwable error){fail(error);}
            });
        }
        worldTicks++;
        if(worldTicks==80){var p=mc.player.getUUID();mc.getSingleplayerServer().execute(()->{var player=mc.getSingleplayerServer().getPlayerList().getPlayer(p);player.teleportTo(player.serverLevel(),4,103.5,21,140,12);});}
    }
    private static void verifyVortex(net.minecraft.server.level.ServerLevel level) throws Exception {
        var owner=StarcrownDoll.MINGGULONG.get().create(level);
        ((Entity)owner).moveTo(50,101,50,0,0);((Mob)owner).setNoAi(true);level.addFreshEntity(owner);
        var center=new net.minecraft.world.phys.Vec3(50,101,50);
        var core=EntityType.COW.create(level);core.moveTo(50.5,101,50,0,0);core.setNoAi(true);
        core.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(1000);core.setHealth(1000);level.addFreshEntity(core);
        var pull=EntityType.COW.create(level);pull.moveTo(53,101,50,0,0);pull.setNoAi(true);level.addFreshEntity(pull);
        var outside=EntityType.COW.create(level);outside.moveTo(55.5,101,50,0,0);outside.setNoAi(true);level.addFreshEntity(outside);
        var field=new MingGuLongVortex(StarcrownDoll.MINGGULONG_VORTEX.get(),level,owner,center,123);
        level.addFreshEntity(field);
        float hp=((LivingEntity)owner).getHealth();
        ((Entity)field).tick();((Entity)field).tick();
        if(Math.abs(core.getHealth()-960)>0.01)throw new AssertionError("Core must deal 20 HP on each tick");
        if(pull.getDeltaMovement().x>=0||outside.getDeltaMovement().lengthSqr()!=0)throw new AssertionError("9-block pull boundary");
        if(((LivingEntity)owner).getHealth()!=hp||((Entity)owner).getDeltaMovement().lengthSqr()!=0)throw new AssertionError("Dragon self immunity");
        for(int i=2;i<200;i++){core.setHealth(1000);((Entity)field).tick();if(core.getHealth()!=980)throw new AssertionError("Missing tick damage at "+i);}
        if(field.getVisualAge()!=200)throw new AssertionError("10-second field duration");
        core.setHealth(1000);((Entity)field).tick();if(core.getHealth()!=1000)throw new AssertionError("Damage continued after 200 ticks");
        for(int i=201;i<239;i++)((Entity)field).tick();
        if(!((Entity)field).isRemoved())throw new AssertionError("Vortex did not expire after aftermath");
        ((Entity)owner).discard();core.discard();pull.discard();outside.discard();
        log("PASS live server vortex: 200 active ticks, 20 HP/tick, pull boundary, dragon immune, aftermath expiry=239");
    }
    private static final class Preview extends Screen {
        MingGuLongEntity entity;
        software.bernie.geckolib.renderer.GeoEntityRenderer<MingGuLongEntity> baseline;
        Preview(){super(Component.literal("1.20.38 / production renderer"));}
        @SuppressWarnings("unchecked")void setState(int key)throws Exception{
            entity=StarcrownDoll.MINGGULONG.get().create(Minecraft.getInstance().level);((Entity)entity).setPos(0,0,0);
            var field=MingGuLongEntity.class.getDeclaredField("ANIMATION");field.setAccessible(true);
            ((Entity)entity).getEntityData().set((EntityDataAccessor<Integer>)field.get(null),key);
        }
        @Override public boolean isPauseScreen(){return false;}
        @Override public void render(GuiGraphics g,int mx,int my,float partial){
            g.fill(0,0,width,height,0xff34424c);g.drawCenteredString(font,Component.literal((quick()?new String[]{"Before / top","After / top","Left","Right","Back"}[Math.min(phase,4)]:NAMES[Math.min(phase,NAMES.length-1)])+" / tick "+ticks),width/2,20,0xffffff);
            float yaw=quick()?new float[]{180,180,90,-90,180}[Math.min(phase,4)]:180;
            float pitch=quick()&&phase>=2?8:60;
            draw(g,width*.32f,height*.65f,Math.min(width*.105f,height*.17f),yaw,pitch);
            draw(g,width*.80f,height*.72f,Math.min(width*.067f,height*.11f),90,8);
            super.render(g,mx,my,partial);
        }
        private void draw(GuiGraphics g,float x,float y,float scale,float yaw,float pitch){
            if(entity==null)return;var mc=Minecraft.getInstance();g.flush();var pose=g.pose();pose.pushPose();
            pose.translate(x,y,250);pose.scale(scale,-scale,scale);pose.mulPose(Axis.XP.rotationDegrees(pitch));pose.mulPose(Axis.YP.rotationDegrees(yaw));
            Lighting.setupForEntityInInventory();RenderSystem.enableDepthTest();var dispatcher=mc.getEntityRenderDispatcher();dispatcher.setRenderShadow(false);
            if(quick()&&phase==0){
                if(baseline==null){
                    var context=new net.minecraft.client.renderer.entity.EntityRendererProvider.Context(dispatcher,mc.getItemRenderer(),mc.getBlockRenderer(),null,mc.getResourceManager(),mc.getEntityModels(),mc.font);
                    var model=new software.bernie.geckolib.model.GeoModel<MingGuLongEntity>(){
                        public net.minecraft.resources.ResourceLocation getModelResource(MingGuLongEntity e){return new net.minecraft.resources.ResourceLocation("starcrownqa","geo/minggulong-before.geo.json");}
                        public net.minecraft.resources.ResourceLocation getTextureResource(MingGuLongEntity e){return new net.minecraft.resources.ResourceLocation(StarcrownDoll.ID,"textures/entity/minggulong.png");}
                        public net.minecraft.resources.ResourceLocation getAnimationResource(MingGuLongEntity e){return new net.minecraft.resources.ResourceLocation(StarcrownDoll.ID,"animations/minggulong.animation.json");}
                    };
                    baseline=new software.bernie.geckolib.renderer.GeoEntityRenderer<>(context,model);
                }
                baseline.render(entity,0,0,pose,mc.renderBuffers().bufferSource(),15728880);
            }else dispatcher.render(entity,0,0,0,0,0,pose,mc.renderBuffers().bufferSource(),15728880);
            mc.renderBuffers().bufferSource().endBatch();dispatcher.setRenderShadow(true);
            pose.popPose();Lighting.setupFor3DItems();g.flush();
        }
    }
}
