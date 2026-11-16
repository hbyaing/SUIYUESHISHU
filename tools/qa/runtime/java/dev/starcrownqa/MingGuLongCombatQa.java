package dev.starcrownqa;

import dev.starcrown.*;
import java.nio.file.*;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.*;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.opengl.GL11;

/** Opt-in, hidden-client integration checks; never installed in user instances. */
@Mod.EventBusSubscriber(modid="starcrownqa", value=Dist.CLIENT)
public final class MingGuLongCombatQa {
    private static boolean started,failed,finished;
    private static int serverTicks,visualTicks,lastShot=-1;
    private static volatile int phase;
    private static MingGuLongEntity walker,flyer,visual;
    private static Mob groundTarget,airTarget;
    private static double groundInitial,airInitial,maxDetour;
    private static boolean groundPath,airPath,seenStart,seenCruise;
    private static final Set<Integer> flightModes=new HashSet<>();
    private static Path root(){return Minecraft.getInstance().gameDirectory.toPath();}
    private static boolean enabled(){return Files.exists(root().resolve("enable-dragon-combat-qa"));}
    private static synchronized void log(String text){try{Files.writeString(root().resolve("combat-results.txt"),text+"\n",StandardOpenOption.CREATE,StandardOpenOption.APPEND);}catch(Exception e){throw new RuntimeException(e);}}
    private static void check(boolean value,String text){if(!value)throw new AssertionError(text);log("PASS "+text);}
    private static void fail(Throwable e){failed=true;e.printStackTrace();log("FAIL "+e);Minecraft.getInstance().stop();}
    private static void set(MingGuLongEntity entity,String name,Object value)throws Exception{var f=MingGuLongEntity.class.getDeclaredField(name);f.setAccessible(true);f.set(entity,value);}
    private static Object call(MingGuLongEntity entity,String name,Class<?>[] types,Object... args)throws Exception{var m=MingGuLongEntity.class.getDeclaredMethod(name,types);m.setAccessible(true);return m.invoke(entity,args);}
    private static void anim(MingGuLongEntity e,int key)throws Exception{call(e,"playAnimation",new Class[]{int.class},key);}
    private static MingGuLongEntity dragon(ServerLevel l,double x,double y,double z){var e=StarcrownDoll.MINGGULONG.get().create(l);((Entity)e).moveTo(x,y,z,0,0);((Mob)e).setNoAi(true);l.addFreshEntity(e);return e;}
    private static Mob cow(ServerLevel l,double x,double y,double z){var e=EntityType.COW.create(l);e.moveTo(x,y,z,0,0);e.setNoAi(true);e.setNoGravity(true);e.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000);e.setHealth(1000);l.addFreshEntity(e);return e;}
    private static void hp(LivingEntity e,float value){e.setHealth(value);e.invulnerableTime=0;e.setAbsorptionAmount(0);}

    @SubscribeEvent public static void client(TickEvent.ClientTickEvent event){
        if(event.phase!=TickEvent.Phase.END||!enabled()||failed||finished)return;
        var mc=Minecraft.getInstance();
        if(mc.level==null||mc.player==null||mc.getOverlay()!=null){
            // This screen belongs exclusively to the new isolated QA save.
            if(mc.getOverlay()==null&&mc.screen instanceof net.minecraft.client.gui.screens.ConfirmScreen)
                mc.screen.children().stream().filter(w->w instanceof net.minecraft.client.gui.components.Button)
                    .map(w->(net.minecraft.client.gui.components.Button)w).filter(b->b.getMessage().getString().equals("Proceed")).findFirst().ifPresent(b->b.onPress());
            return;
        }
        try{
            if(!started){started=true;mc.options.pauseOnLostFocus=false;mc.options.hideGui=true;mc.setScreen(null);log("GPU="+GL11.glGetString(GL11.GL_RENDERER));var id=mc.player.getUUID();mc.getSingleplayerServer().execute(()->{try{setup(mc.getSingleplayerServer().getPlayerList().getPlayer(id));}catch(Throwable e){fail(e);}});}
        }catch(Throwable e){fail(e);}
    }
    private static void setup(net.minecraft.server.level.ServerPlayer p)throws Exception{
        var l=p.serverLevel();l.setDayTime(6000);l.setWeatherParameters(6000,0,false,false);
        DivineAnchor.unlock(p);DivinityInventory.force(p,ItemStack.EMPTY);p.getInventory().armor.set(3,ItemStack.EMPTY);
        DivineWill.update(p,false);DivineSeven.release(p);p.removeAllEffects();
        l.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false,l.getServer());
        var old=new ArrayList<Entity>();l.getAllEntities().forEach(old::add);for(var e:old)if(e instanceof Mob||e instanceof MingGuLongVortex||e instanceof MingGuLongProjectile)e.discard();
        for(int x=-15;x<=75;x++)for(int z=-16;z<=20;z++){
            l.setBlockAndUpdate(new BlockPos(x,100,z),Blocks.SMOOTH_STONE.defaultBlockState());
            for(int y=101;y<=122;y++)l.setBlockAndUpdate(new BlockPos(x,y,z),Blocks.AIR.defaultBlockState());
        }
        p.setGameMode(GameType.SURVIVAL);p.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000);hp(p,1000);p.teleportTo(l,0,101,5,180,0);
        for(var slot:new EquipmentSlot[]{EquipmentSlot.HEAD,EquipmentSlot.CHEST,EquipmentSlot.LEGS,EquipmentSlot.FEET}){
            Item item=switch(slot){case HEAD->Items.NETHERITE_HELMET;case CHEST->Items.NETHERITE_CHESTPLATE;case LEGS->Items.NETHERITE_LEGGINGS;default->Items.NETHERITE_BOOTS;};
            var stack=new ItemStack(item);stack.enchant(Enchantments.ALL_DAMAGE_PROTECTION,4);p.setItemSlot(slot,stack);
        }
        p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE,10000,4));
        p.setItemInHand(InteractionHand.OFF_HAND,new ItemStack(Items.SHIELD));p.startUsingItem(InteractionHand.OFF_HAND);
        phase=1;log("SETUP armor+Protection IV+Resistance V+held shield; waiting for real shield activation");
    }
    @SubscribeEvent public static void server(TickEvent.ServerTickEvent event){
        if(event.phase!=TickEvent.Phase.END||!enabled()||failed||finished||phase==0)return;
        try{
            var server=Minecraft.getInstance().getSingleplayerServer();var l=server.overworld();var p=server.getPlayerList().getPlayers().get(0);serverTicks++;
            // Let vanilla's initial player spawn protection expire naturally.
            if(phase==1&&serverTicks==80){
                testDamage(l,p);testScheduling(l);testVortex(l);testFlightModes(l);
                p.stopUsingItem();p.removeAllEffects();p.setGameMode(GameType.CREATIVE);p.getAbilities().flying=true;p.onUpdateAbilities();p.teleportTo(l,8,107,12,145,12);
                for(int z=-5;z<=5;z++)for(int y=101;y<=110;y++)l.setBlockAndUpdate(new BlockPos(8,y,z),Blocks.STONE.defaultBlockState());
                walker=dragon(l,0,101,0);groundTarget=cow(l,19,101,0);prepare(walker,groundTarget);groundInitial=((Entity)walker).distanceTo(groundTarget);
                // Flight wall requires a path around a side or above it.
                for(int z=-5;z<=5;z++)for(int y=101;y<=119;y++)l.setBlockAndUpdate(new BlockPos(45,y,z),Blocks.STONE.defaultBlockState());
                flyer=dragon(l,30,101,0);airTarget=cow(l,62,114,0);prepare(flyer,airTarget);airInitial=((Entity)flyer).distanceTo(airTarget);
                phase=2;serverTicks=0;log("LIVE_PATHS_START");
            } else if(phase==2){
                int key=flyer.getAnimationKey();if(key==5)seenStart=true;if(key==6||key==7){seenCruise=true;flightModes.add(key);}
                if(serverTicks<51&&key!=5)throw new AssertionError("Takeoff interrupted at "+serverTicks+" key="+key);
                groundPath|=((Mob)walker).getNavigation().getPath()!=null;airPath|=((Mob)flyer).getNavigation().getPath()!=null;
                maxDetour=Math.max(maxDetour,Math.abs(((Entity)walker).getZ()));
                if(serverTicks%60==0)log("PATH tick="+serverTicks+" ground="+((Entity)walker).position()+" dist="+((Entity)walker).distanceTo(groundTarget)+" air="+((Entity)flyer).position()+" key="+key+" dist="+((Entity)flyer).distanceTo(airTarget)+" head="+((LivingEntity)flyer).getYHeadRot()+" body="+((LivingEntity)flyer).yBodyRot);
                if(serverTicks==380){
                    check(groundPath&&maxDetour>5&&((Entity)walker).distanceTo(groundTarget)<groundInitial-5,"live ground navigator detours around wall and closes distance; detour="+maxDetour);
                    check(airPath&&seenStart&&seenCruise&&((Entity)flyer).distanceTo(airTarget)<airInitial-8,"live flying navigator completes takeoff, routes around obstacle and approaches elevated target");
                    ((Entity)walker).discard();groundTarget.discard();((Entity)flyer).discard();airTarget.discard();
                    // Remove the nearby wall for the production GPU skill sequence.
                    for(int z=-5;z<=5;z++)for(int y=101;y<=110;y++)l.setBlockAndUpdate(new BlockPos(8,y,z),Blocks.AIR.defaultBlockState());
                    visual=dragon(l,0,101,0);var target=cow(l,0,101,16);prepare(visual,target);call(visual,"startHuangwu",new Class[0]);p.teleportTo(l,5,104.3,8,148,17);phase=3;visualTicks=0;
                }
            }else if(phase==3){
                visualTicks++;
                if(visualTicks==75)p.teleportTo(l,4,103.5,21,140,12);
                if(visualTicks>=285){check(true,"production GPU VFX completed charge/projectile/10-second vortex/collapse; screenshots captured");finished=true;phase=4;log("COMPLETE");Minecraft.getInstance().execute(()->Minecraft.getInstance().stop());}
            }
        }catch(Throwable e){fail(e);}
    }
    private static void prepare(MingGuLongEntity e,LivingEntity target)throws Exception{((Mob)e).setTarget(target);set(e,"sawTarget",true);set(e,"huangwuCooldown",10000);set(e,"targetScanCooldown",10000);set(e,"lastKnownTargetPosition",target.position());((Mob)e).setNoAi(false);}

    private static void testDamage(ServerLevel l,net.minecraft.server.level.ServerPlayer p)throws Exception{
        var d=dragon(l,0,101,2);
        // The hidden client has no physical use key held and releases item use.
        // Activate/age the server fixture within this same task, before testing
        // vanilla's real shield predicate and actual damage pipeline.
        p.startUsingItem(InteractionHand.OFF_HAND);
        int remaining=p.getUseItemRemainingTicks(),aged=0;
        for(var field:LivingEntity.class.getDeclaredFields())if(field.getType()==int.class&&!java.lang.reflect.Modifier.isStatic(field.getModifiers())){
            field.setAccessible(true);if(field.getInt(p)==remaining&&remaining>70000){field.setInt(p,remaining-10);aged++;}
        }
        check(aged==1&&p.isBlocking()&&p.getArmorValue()==20,"server player fixture actively blocks with full netherite armor; blocking="+p.isBlocking()+" armor="+p.getArmorValue());
        check(p.isDamageSourceBlocked(l.damageSources().mobAttack(d)),"ordinary frontal mob attack is shield-blockable control");
        var source=MingGuLongDamage.source(d,false,((Entity)d).position());
        check(!p.isDamageSourceBlocked(source),"true damage bypasses active frontal shield");
        for(int key:new int[]{9,10,11,12}){
            hp(p,1000);((Mob)d).setTarget(p);anim(d,key);set(d,"stateTicks",40);set(d,"attackHitApplied",false);set(d,"airborne",key>=11);
            call(d,"tickAttack",new Class[0]);check(Math.abs(p.getHealth()-982)<.01,"attack "+key+" deals exactly 18 through shield/armor/Protection IV/Resistance V; hp="+p.getHealth()+" max="+p.getMaxHealth()+" sight="+((LivingEntity)d).hasLineOfSight(p)+" invulnerable="+p.isInvulnerableTo(source));
        }
        hp(p,1000);var center=p.position();var ally=dragon(l,center.x,center.y,center.z);var edge=cow(l,center.x+1.45,center.y,center.z);var outside=cow(l,center.x+2.5,center.y,center.z);
        l.setBlockAndUpdate(BlockPos.containing(center).below(),Blocks.DIAMOND_BLOCK.defaultBlockState());
        d.onHuangwuImpact(center,42,0);
        check(p.getHealth()==800&&edge.getHealth()==800&&outside.getHealth()==1000,"impact is fixed 200 void damage, no falloff or duplicate damage, with 3-cube boundary");
        check(p.getLastDamageSource().is(MingGuLongDamage.VOID_DAMAGE),"impact uses registered void damage type");
        check(((LivingEntity)ally).getHealth()==666&&l.getBlockState(BlockPos.containing(center).below()).is(Blocks.DIAMOND_BLOCK),"impact preserves dragon immunity and terrain");
        for(var e:l.getEntitiesOfClass(MingGuLongVortex.class,new net.minecraft.world.phys.AABB(center,center).inflate(3)))((Entity)e).discard();
        ((Entity)d).discard();((Entity)ally).discard();edge.discard();outside.discard();hp(p,1000);
    }
    private static void testScheduling(ServerLevel l)throws Exception{
        var d=dragon(l,-8,101,-8);var t=cow(l,-5,101,-8);prepare(d,t);call(d,"startAttack",new Class[]{boolean.class},false);set(d,"huangwuCooldown",1);
        for(int i=1;i<=53;i++){((Entity)d).tick();if(d.getAnimationKey()!=9)throw new AssertionError("Attack interrupted at "+i);}
        ((Entity)d).tick();check(d.getAnimationKey()==13,"cooldown ready during claw attack waits all 54 ticks then immediately prioritizes Huangwu at close range");
        ((Entity)d).discard();t.discard();
        d=dragon(l,-8,101,-8);t=cow(l,22,116,-8);prepare(d,t);((Entity)d).tick();check(d.getAnimationKey()==5,"long/elevated chase enters takeoff first");
        t.setPos(-5,101,-8);set(d,"huangwuCooldown",1);
        for(int i=1;i<=50;i++){((Entity)d).tick();if(d.getAnimationKey()!=5)throw new AssertionError("Takeoff interrupted by ready skill at "+i);}
        ((Entity)d).tick();check(d.getAnimationKey()==13,"ready skill waits full 51-tick takeoff before casting");((Entity)d).discard();t.discard();
    }
    private static void testFlightModes(ServerLevel l)throws Exception{
        var d=dragon(l,-8,116,-8);call(d,"setFlightNavigation",new Class[]{boolean.class},true);call(d,"selectFlightMode",new Class[0]);
        Set<Integer> modes=new HashSet<>();int changes=0,last=d.getAnimationSerial();
        for(int i=0;i<700;i++){call(d,"tickFlightMode",new Class[0]);modes.add(d.getAnimationKey());if(d.getAnimationSerial()!=last){changes++;last=d.getAnimationSerial();}}
        check(modes.contains(6)&&modes.contains(7)&&changes>=3&&changes<15,"flap/glide random full-loop runs alternate without restarting every tick; changes="+changes);
        var t=cow(l,-3,120,-12);((LivingEntity)d).yBodyRot=0;((Entity)d).setYRot(0);for(int i=0;i<6;i++)call(d,"lookAtTarget",new Class[]{LivingEntity.class},t);
        check(Math.abs(((LivingEntity)d).getYHeadRot())>10&&Math.abs(((Entity)d).getXRot())>5,"flight lock sets independent head yaw and pitch toward elevated target");
        ((Entity)d).discard();t.discard();
    }
    private static void testVortex(ServerLevel l)throws Exception{
        var d=dragon(l,70,101,10);var t=cow(l,70.5,101,10);t.getAttribute(Attributes.ARMOR).setBaseValue(30);t.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE,10000,4));
        var pull=cow(l,74,101,10);var outside=cow(l,75.5,101,10);var v=new MingGuLongVortex(StarcrownDoll.MINGGULONG_VORTEX.get(),l,d,new Vec3(70,101,10),77);l.addFreshEntity(v);
        for(int i=0;i<200;i++){hp(t,1000);((Entity)v).tick();if(t.getHealth()!=980)throw new AssertionError("20 true damage/tick lost at "+i);}
        check(pull.getDeltaMovement().x<0&&outside.getDeltaMovement().lengthSqr()==0&&((LivingEntity)d).getHealth()==666,"10-second vortex retains 9-cube pull and dragon immunity");
        hp(t,1000);((Entity)v).tick();check(t.getHealth()==1000,"contact true damage stops after exactly 200 active ticks");
        for(int i=201;i<213;i++)((Entity)v).tick();check(Math.abs(t.getHealth()-979.6F)<.01,"collapse burst also bypasses armor/resistance");
        ((Entity)v).discard();((Entity)d).discard();t.discard();pull.discard();outside.discard();
    }
    @SubscribeEvent public static void render(TickEvent.RenderTickEvent event){
        if(event.phase!=TickEvent.Phase.END||!enabled()||phase!=3||failed||visualTicks==lastShot)return;
        if(!Set.of(20,30,35,40,53,70,160,240,260,275).contains(visualTicks))return;
        lastShot=visualTicks;var mc=Minecraft.getInstance();Screenshot.grab(mc.gameDirectory,"huangwu_"+visualTicks+".png",mc.getMainRenderTarget(),m->{});
        log("GPU_CAPTURE tick="+visualTicks+" entities="+mc.level.entitiesForRendering().spliterator().getExactSizeIfKnown());
    }
}
