package dev.starcrownqa;

import dev.starcrown.*;
import java.nio.file.*;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.effect.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.opengl.GL11;

/** Integration checks run only in a dedicated hidden test client. */
@Mod.EventBusSubscriber(modid="starcrownqa",value=Dist.CLIENT)
public final class MingGuLongLifeStealQa {
    private static boolean started,failed,finished,cancelAttack;
    private static volatile int stage;
    private static volatile int ticks,captures;
    private static MingGuLongEntity visualOwner;
    private static MingGuLongVortex visualVortex;
    private static float[] scales;
    private static float releaseScale;
    private static Path root(){return Minecraft.getInstance().gameDirectory.toPath();}
    private static boolean enabled(){return Files.exists(root().resolve("enable-dragon-lifesteal-qa"));}
    private static synchronized void log(String s){try{Files.writeString(root().resolve("lifesteal-results.txt"),s+"\n",StandardOpenOption.CREATE,StandardOpenOption.APPEND);}catch(Exception e){throw new RuntimeException(e);}}
    private static void check(boolean ok,String s){if(!ok)throw new AssertionError(s);log("PASS "+s);}
    private static void close(float actual,float expected,String text){check(Math.abs(actual-expected)<.015F,text+" actual="+actual+" expected="+expected);}
    private static void fail(Throwable error){failed=true;error.printStackTrace();log("FAIL "+error);Minecraft.getInstance().stop();}
    private static void set(Object owner,Class<?> type,String name,Object value)throws Exception{var f=type.getDeclaredField(name);f.setAccessible(true);f.set(owner,value);}
    private static Object call(MingGuLongEntity e,String name,Class<?>[] types,Object... args)throws Exception{var m=MingGuLongEntity.class.getDeclaredMethod(name,types);m.setAccessible(true);return m.invoke(e,args);}
    private static MingGuLongEntity dragon(ServerLevel l,double x,double y,double z){var d=StarcrownDoll.MINGGULONG.get().create(l);((Entity)d).moveTo(x,y,z,0,0);((Mob)d).setNoAi(true);l.addFreshEntity(d);return d;}
    private static Mob cow(ServerLevel l,double x,double y,double z){var c=EntityType.COW.create(l);c.moveTo(x,y,z,0,0);c.setNoAi(true);c.setNoGravity(true);c.getAttribute(Attributes.MAX_HEALTH).setBaseValue(10000);c.setHealth(10000);l.addFreshEntity(c);return c;}
    private static void reset(LivingEntity e,float hp){e.setHealth(hp);e.invulnerableTime=0;e.setAbsorptionAmount(0);}

    @SubscribeEvent public static void cancel(LivingAttackEvent event){if(enabled()&&cancelAttack&&event.getSource().getEntity() instanceof MingGuLongEntity)event.setCanceled(true);}
    @SubscribeEvent public static void client(TickEvent.ClientTickEvent event){
        if(event.phase!=TickEvent.Phase.END||!enabled()||failed||finished)return;var mc=Minecraft.getInstance();
        if(mc.level==null||mc.player==null||mc.getOverlay()!=null){
            if(mc.getOverlay()==null&&mc.screen instanceof net.minecraft.client.gui.screens.ConfirmScreen)mc.screen.children().stream()
                .filter(w->w instanceof net.minecraft.client.gui.components.Button).map(w->(net.minecraft.client.gui.components.Button)w)
                .filter(b->b.getMessage().getString().equals("Proceed")).findFirst().ifPresent(b->b.onPress());
            return;
        }
        try{if(!started){started=true;mc.options.pauseOnLostFocus=false;mc.options.hideGui=true;mc.setScreen(null);
            log("GPU="+GL11.glGetString(GL11.GL_RENDERER));
            var field=MingGuLongHuangwuVfx.class.getDeclaredField("STAGE_SCALE");field.setAccessible(true);scales=(float[])field.get(null);releaseScale=scales[2];
            check(releaseScale>.798938F&&Math.abs(releaseScale*2.73F-5F)<.0001,"landing-only nominal density diameter=5 blocks; scale="+releaseScale);
            check(Math.abs(scales[0]-3.8567253F)<.0001&&Math.abs(scales[1]-6.249984F)<.0001,"charge/projectile scales unchanged");
            check(mc.getItemColors().getColor(new ItemStack(StarcrownDoll.MINGGULONG_SPAWN_EGG.get()),0)==0xFFFFFF,"spawn egg tint is neutral; authored black/red pixels retained");
            var lift=MingGuLongHuangwuVfx.class.getDeclaredMethod("landingGroundLift",float.class);lift.setAccessible(true);
            close((float)lift.invoke(null,releaseScale)+releaseScale*(.72F/16F-.88F),0,"scaled ground vortex stays at actual impact plane");
            mc.getSingleplayerServer().execute(()->{try{setup();}catch(Throwable e){fail(e);}});
        }}catch(Throwable e){fail(e);}
    }
    private static void setup()throws Exception{
        var server=Minecraft.getInstance().getSingleplayerServer();var l=server.overworld();var p=server.getPlayerList().getPlayers().get(0);
        DivineAnchor.unlock(p);DivinityInventory.force(p,ItemStack.EMPTY);p.getInventory().armor.set(3,ItemStack.EMPTY);DivineWill.update(p,false);DivineSeven.release(p);p.removeAllEffects();p.setGameMode(GameType.CREATIVE);
        p.getAbilities().flying=true;p.onUpdateAbilities();l.setDayTime(6000);l.setWeatherParameters(6000,0,false,false);l.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false,server);
        var old=new ArrayList<Entity>();l.getAllEntities().forEach(old::add);for(var e:old)if(e instanceof Mob||e instanceof MingGuLongVortex||e instanceof MingGuLongProjectile)e.discard();
        for(int x=-14;x<=24;x++)for(int z=-14;z<=14;z++){l.setBlockAndUpdate(new BlockPos(x,100,z),Blocks.SMOOTH_STONE.defaultBlockState());for(int y=101;y<=113;y++)l.setBlockAndUpdate(new BlockPos(x,y,z),Blocks.AIR.defaultBlockState());}
        tests(l);
        // Five-block reference square on the floor, using the same world camera.
        for(int x=-2;x<=2;x++)for(int z=-2;z<=2;z++)l.setBlockAndUpdate(new BlockPos(x,100,z),((x+z)&1)==0?Blocks.POLISHED_ANDESITE.defaultBlockState():Blocks.SMOOTH_STONE.defaultBlockState());
        for(int[] marker:new int[][]{{-3,-3},{-3,3},{3,-3},{3,3}})l.setBlockAndUpdate(new BlockPos(marker[0],100,marker[1]),Blocks.GOLD_BLOCK.defaultBlockState());
        visualOwner=dragon(l,-5,101,-3);visualVortex=new MingGuLongVortex(StarcrownDoll.MINGGULONG_VORTEX.get(),l,visualOwner,new Vec3(0,101,0),991);l.addFreshEntity(visualVortex);
        p.teleportTo(l,4.8,104.4,6.5,144,18);stage=1;ticks=0;
    }
    private static void tests(ServerLevel l)throws Exception{
        var d=dragon(l,14,101,0);var t=cow(l,17,101,0);
        for(int key:new int[]{9,10,11,12}){
            reset(d,100);reset(t,1000);((Mob)d).setTarget(t);call(d,"playAnimation",new Class[]{int.class},key);set(d,MingGuLongEntity.class,"stateTicks",40);set(d,MingGuLongEntity.class,"attackHitApplied",false);set(d,MingGuLongEntity.class,"airborne",key>=11);
            call(d,"tickAttack",new Class[0]);close(t.getHealth(),982,"claw "+key+" health loss");close(((LivingEntity)d).getHealth(),107.2F,"claw "+key+" heals 40 percent");
        }
        reset(d,100);reset(t,1000);((Mob)d).doHurtTarget(t);close(((LivingEntity)d).getHealth(),107.2F,"generic mob attack entry also grants life steal");
        reset(d,100);reset(t,5);MingGuLongDamage.hurt(d,t,200);close(((LivingEntity)d).getHealth(),102,"lethal overkill heals from 5 actual HP, not requested 200");t.discard();t=cow(l,17,101,0);
        reset(d,100);t.setInvulnerable(true);MingGuLongDamage.hurt(d,t,20);close(((LivingEntity)d).getHealth(),100,"immune target grants no healing");t.setInvulnerable(false);
        cancelAttack=true;try{MingGuLongDamage.hurt(d,t,20);}finally{cancelAttack=false;}close(((LivingEntity)d).getHealth(),100,"Forge-canceled attack grants no healing");
        reset(t,1000);t.setAbsorptionAmount(40);MingGuLongDamage.hurt(d,t,20);close(t.getHealth(),1000,"absorption-only hit removes no health");close(((LivingEntity)d).getHealth(),100,"absorption-only hit grants no healing");
        reset(d,665);reset(t,1000);MingGuLongDamage.hurt(d,t,20);close(((LivingEntity)d).getHealth(),666,"life steal capped at dragon maximum health");
        reset(d,100);reset(t,1000);MingGuLongDamage.impact(l,d,t.position());close(t.getHealth(),800,"void explosion still deals fixed 200");close(((LivingEntity)d).getHealth(),180,"void explosion heals 80 for actual 200 damage");
        var second=cow(l,17.8,101,0);reset(t,1000);reset(d,100);MingGuLongDamage.impact(l,d,t.position());close(((LivingEntity)d).getHealth(),260,"multi-target impact heals 40 percent of each target actual damage");second.discard();
        var same=dragon(l,17,101,0);reset(d,100);MingGuLongDamage.hurt(d,same,200);close(((LivingEntity)same).getHealth(),666,"same-species immunity retained");close(((LivingEntity)d).getHealth(),100,"dragon immunity cannot generate healing");((Entity)same).discard();
        reset(t,1000);t.getAttribute(Attributes.ARMOR).setBaseValue(30);t.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE,10000,4));
        var field=new MingGuLongVortex(StarcrownDoll.MINGGULONG_VORTEX.get(),l,d,t.position(),77);l.addFreshEntity(field);
        for(int i=0;i<200;i++){reset(d,100);t.setHealth(1000);float before=t.getHealth();((Entity)field).tick();if(Math.abs(t.getHealth()-(before-20))>.015||Math.abs(((LivingEntity)d).getHealth()-108)>.015)throw new AssertionError("continuous tick "+i+" target="+t.getHealth()+" owner="+((LivingEntity)d).getHealth());}
        check(true,"all 200 consecutive vortex ticks remove exactly 20 health and heal 8, through armor and Resistance V");
        float before=t.getHealth();reset(d,100);((Entity)field).tick();close(t.getHealth(),before,"no ongoing damage on tick 201");close(((LivingEntity)d).getHealth(),100,"no ongoing healing on tick 201");
        for(int i=201;i<213;i++)((Entity)field).tick();close(t.getHealth(),before-20.4F,"collapse burst damage preserved");close(((LivingEntity)d).getHealth(),108.16F,"collapse burst also heals 40 percent");((Entity)field).discard();t.discard();
        reset(d,500);check(((Entity)d).fireImmune(),"registered dragon entity type is fire immune");
        for(var source:new net.minecraft.world.damagesource.DamageSource[]{l.damageSources().inFire(),l.damageSources().onFire(),l.damageSources().lava(),l.damageSources().hotFloor()}){((LivingEntity)d).hurt(source,200);close(((LivingEntity)d).getHealth(),500,"fire-tagged damage blocked: "+source.getMsgId());}
        ((Entity)d).setSecondsOnFire(10);check(!((Entity)d).isOnFire()&&!((Entity)d).displayFireAnimation()&&((Entity)d).getRemainingFireTicks()<=0,"ignition does not burn or render flames");
        ((Entity)d).setRemainingFireTicks(100);((Entity)d).tick();close(((LivingEntity)d).getHealth(),500,"forced fire tick cannot cause damage");check(((Entity)d).getRemainingFireTicks()<=0&&!((Entity)d).displayFireAnimation(),"forced fire state cleared even with NoAI");
        ((LivingEntity)d).hurt(l.damageSources().generic(),10);check(((LivingEntity)d).getHealth()<500,"non-fire damage still applies");((Entity)d).discard();
    }
    @SubscribeEvent public static void server(TickEvent.ServerTickEvent event){
        if(event.phase!=TickEvent.Phase.END||!enabled()||failed||finished||stage==0)return;
        try{ticks++;var f=MingGuLongVortex.class.getDeclaredField("AGE");f.setAccessible(true);
            ((Entity)visualVortex).getEntityData().set((net.minecraft.network.syncher.EntityDataAccessor<Integer>)f.get(null),60);
            if(ticks==74&&(captures&2)==0)ticks=73;
            if(ticks==109&&(captures&4)==0)ticks=108;
            if(ticks==134&&(captures&8)==0)ticks=133;
            if(ticks==75){var server=Minecraft.getInstance().getSingleplayerServer();var p=server.getPlayerList().getPlayers().get(0);p.teleportTo(p.serverLevel(),0,109.5,1.5,180,80);}
            if(ticks==110)Minecraft.getInstance().execute(()->Minecraft.getInstance().setScreen(new EggScreen()));
            if(ticks>=135){finished=true;scales[2]=releaseScale;log("COMPLETE life steal, 200 ticks, fire immunity, landing GPU views, actual egg item renderer");Minecraft.getInstance().execute(()->Minecraft.getInstance().stop());}
        }catch(Throwable e){fail(e);}
    }
    @SubscribeEvent public static void render(TickEvent.RenderTickEvent event){
        if(event.phase!=TickEvent.Phase.END||!enabled()||failed||finished||stage!=1)return;
        String shot=null;int bit=0;
        if(ticks>=65&&ticks<75&&(captures&2)==0){shot="landing_after.png";bit=2;}
        else if(ticks>=100&&ticks<110&&(captures&4)==0){shot="landing_top_5blocks.png";bit=4;}
        else if(ticks>=125&&(captures&8)==0){shot="spawn_egg_ingame.png";bit=8;}
        if(shot!=null){captures|=bit;var mc=Minecraft.getInstance();Screenshot.grab(mc.gameDirectory,shot,mc.getMainRenderTarget(),m->{});log("GPU_CAPTURE "+shot+" landingScale="+scales[2]);}
    }
    private static final class EggScreen extends Screen{
        EggScreen(){super(Component.literal("Production spawn egg renderer"));}
        @Override public boolean isPauseScreen(){return false;}
        @Override public void render(GuiGraphics g,int mx,int my,float partial){
            g.fill(0,0,width,height,0xff252a32);g.drawCenteredString(font,"ETERNAL DRAGON / BLACK + RED SPAWN EGG",width/2,35,0xffffff);
            var stack=new ItemStack(StarcrownDoll.MINGGULONG_SPAWN_EGG.get());
            for(int i=0;i<3;i++){int scale=new int[]{2,5,12}[i],x=width/5+i*width/4,y=height/2;
                g.fill(x-10,y-10,x+16*scale+10,y+16*scale+10,0xff8b8b8b);g.pose().pushPose();g.pose().translate(x,y,0);g.pose().scale(scale,scale,1);g.renderItem(stack,0,0);g.pose().popPose();}
            super.render(g,mx,my,partial);
        }
    }
}
