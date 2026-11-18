package dev.starcrownqa;

import dev.eavfx.EaServer;
import dev.starcrown.EaSkill;
import dev.starcrown.EaSkillKeys;
import dev.starcrown.treasury.TreasuryClient;
import dev.starcrown.treasury.TreasuryImpactRenderer;
import dev.starcrown.treasury.TreasuryItems;
import dev.starcrown.treasury.TreasuryLayout;
import dev.starcrown.treasury.TreasuryImpacts;
import dev.starcrown.treasury.TreasuryNetwork;
import dev.starcrown.treasury.TreasuryVolley;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.client.Camera;
import net.minecraft.client.CameraType;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.Difficulty;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Ravager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

@Mod.EventBusSubscriber(modid="starcrownqa",value=Dist.CLIENT)
public final class ArsenalQa {
    private static final Vec3 BASE=new Vec3(.5,101,.5);
    private static final Method CAMERA=Arrays.stream(Camera.class.getDeclaredMethods())
            .filter(m->m.getReturnType()==void.class && Arrays.equals(m.getParameterTypes(),new Class<?>[]{Vec3.class}))
            .findFirst().orElseThrow();
    private static final Method MOUSE_PRESS=Arrays.stream(MouseHandler.class.getDeclaredMethods())
            .filter(m->!m.isSynthetic() && m.getReturnType()==void.class
                    && Arrays.equals(m.getParameterTypes(),new Class<?>[]{long.class,int.class,int.class,int.class}))
            .findFirst().orElseThrow();
    static { CAMERA.setAccessible(true); MOUSE_PRESS.setAccessible(true); }
    private static boolean setup,visible,finished,restoring,normalShot,fullShot,flightShot,impactShot,redirected;
    private static volatile boolean ready,monitor,serverNormalComplete,serverFullComplete,steered;
    private static volatile Vec3 impactSpot;
    private static Vec3 shownImpact;
    private static long firstImpactFrame=-1;
    private static boolean visualRecheck;
    private static boolean targetedChain;
    private static boolean openingStudy;
    private static boolean frontalOpening,cleanRound,fullRound,originalBob;
    private static boolean streamStudy,modelCountsMatch=true;
    private static final StreamProbe stream=new StreamProbe();
    private static final Map<UUID,Integer> clientBatches=new HashMap<>();
    private static final Vec3 STREAM_EYE=BASE.add(1.1,6.3,12.6),STREAM_AIM=BASE.add(0,5.9,-4.8);
    private static int streamBucket=-1,streamFrames,firstStreamMs=-1,lastStreamMs,streamPresses;
    private static UUID firstRound,secondRound;
    private static long secondStart=-1;
    private static boolean witnessedClosure,secondHasTwenty,letGo,observedPhysicalHold;
    private static final Set<UUID> serverRounds=new HashSet<>();
    private static volatile int actualRounds;
    private static volatile boolean continuedWithOriginalEa,serverStillHolding;
    private static int lastRoundBucket=-1;
    private static Vec3 openingEye,openingAim;
    private static long openingStart=-1;
    private static int lastOpenBucket=-1,firstOpenMs=-1,openFrames,openQuarter;
    private static volatile int practiceId=-1;
    private static Ravager practiceTarget;
    private static volatile boolean boundOnServer;
    private static final Set<ChunkPos> forcedChunks=new HashSet<>();
    private static volatile long serverNormalStart=-1;
    private static volatile boolean chargingObserved,cancelObserved,releasedObserved;
    private static volatile boolean angleActive;
    private static volatile float demoYaw,demoPitch;
    private static ItemStack originalEa;
    private static int menuTicks;
    private static boolean sawNormal,sawFull;
    private static Vec3 initialHeading;
    private static int clock,stage,entered,lastMotion=-1,originalFov;
    private static int failures;
    private static CameraType originalCamera;
    private static boolean originalHud;
    private static String still;
    private static long normalStart=-1,fullStart=-1,wallBefore;
    private static Vec3 closeGate=BASE.add(0,6,-4);
    private static Vec3 fullEye=BASE.add(0,10,25),fullAim=BASE.add(0,10,-4);

    private static Object field(Object object,String key) {
        try{
            Class<?> type=object instanceof Class<?> cls?cls:object.getClass();
            Field f=type.getDeclaredField(key);
            f.setAccessible(true);
            return f.get(object instanceof Class<?>?null:object);
        }catch(ReflectiveOperationException ex){throw new IllegalStateException(ex);}
    }
    private static Map<?,?> active(Class<?> cls){return (Map<?,?>)field(cls,"ACTIVE");}
    private static Class<?> visualClass(){
        try{return Class.forName("dev.starcrown.treasury.TreasuryVolleyClient");}
        catch(ClassNotFoundException ex){throw new IllegalStateException(ex);}
    }
    private static TreasuryNetwork.Cast cast(int mode){
        for(Object visual:active(TreasuryClient.class).values()){
            var cast=(TreasuryNetwork.Cast)field(visual,"cast");
            if(cast.mode()==mode)return cast;
        }
        return null;
    }
    private static synchronized void check(String label,boolean value) {
        if(!value)failures++;
        System.out.println("ARSENAL QA "+label+"="+value);
    }
    private static ItemStack stack(String name){
        return new ItemStack(ForgeRegistries.ITEMS.getValue(new ResourceLocation("starcrowndoll",name)));
    }
    private static void server(Consumer<ServerPlayer> action){
        var mc=Minecraft.getInstance();
        var server=mc.getSingleplayerServer();
        var id=mc.player.getUUID();
        server.execute(()->action.accept(server.getPlayerList().getPlayer(id)));
    }
    private static long terrain(ServerLevel level){
        long hash=17;
        for(int x=-10;x<=22;x++)for(int y=100;y<=109;y++)for(int z=28;z<=31;z++)
            hash=hash*31+level.getBlockState(new BlockPos(x,y,z)).hashCode();
        return hash;
    }
    private static void prepare(Minecraft mc){
        setup=true;
        var dir=mc.gameDirectory.toPath();
        visible=Files.exists(dir.resolve("visible-arsenal-qa"));
        visualRecheck=Files.exists(dir.resolve("arsenal-visual-recheck"));
        targetedChain=Files.exists(dir.resolve("arsenal-chain-target"));
        openingStudy=Files.exists(dir.resolve("arsenal-opening-study"));
        frontalOpening=Files.exists(dir.resolve("arsenal-frontal-opening"));
        streamStudy=Files.exists(dir.resolve("arsenal-stream-study"));
        if(frontalOpening || streamStudy){
            originalBob=mc.options.bobView().get();
            mc.options.bobView().set(false);
        }
        if(visible)org.lwjgl.glfw.GLFW.glfwSetWindowTitle(mc.getWindow().getWindow(),"Minecraft · 乖离剑 Ea 试射");
        originalCamera=mc.options.getCameraType();
        originalFov=mc.options.fov().get();
        originalHud=mc.options.hideGui;
        mc.setScreen(null);
        mc.player.getInventory().selected=0;
        mc.options.hideGui=true;
        mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
        mc.options.fov().set(62);
        server(p->{
            var level=p.serverLevel();
            for(int cx=-2;cx<=2;cx++)for(int cz=-1;cz<=2;cz++){
                ChunkPos pos=new ChunkPos(cx,cz);
                if(!level.getForcedChunks().contains(pos.toLong()) && level.setChunkForced(cx,cz,true))forcedChunks.add(pos);
                level.getChunk(cx,cz);
            }
            for(Ravager old:level.getEntitiesOfClass(Ravager.class,new AABB(BASE,BASE.add(0,0,20)).inflate(3),
                    mob->mob.getTags().contains("starcrownqa_practice_target")))old.discard();
            level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false,level.getServer());
            level.setDayTime(13200);
            level.setWeatherParameters(10000,0,false,false);
            for(int x=-4;x<=4;x++)for(int z=-3;z<=31;z++){
                var block=Math.abs(x)==4?Blocks.CUT_SANDSTONE:Blocks.SMOOTH_SANDSTONE;
                if(Math.abs(x)==3 && z%7==0)block=Blocks.SEA_LANTERN;
                level.setBlockAndUpdate(new BlockPos(x,100,z),block.defaultBlockState());
            }
            for(int x=-10;x<=22;x++)for(int y=100;y<=109;y++){
                var block=y==100 || y==109 || x%8==0?Blocks.CUT_SANDSTONE:Blocks.LIGHT_GRAY_STAINED_GLASS;
                level.setBlockAndUpdate(new BlockPos(x,y,30),block.defaultBlockState());
            }
            p.setGameMode(GameType.CREATIVE);
            p.teleportTo(level,BASE.x,BASE.y,BASE.z,0,0);
            p.getInventory().selected=0;
            originalEa=stack("ea");
            p.getInventory().setItem(0,originalEa);
            p.inventoryMenu.broadcastChanges();
            wallBefore=terrain(level);
            angleActive=true;
            monitor=true;
            ready=true;
        });
    }
    private static void step(int next){stage=next;entered=clock;}
    private static void use(Minecraft mc){mc.gameMode.useItem(mc.player,InteractionHand.MAIN_HAND);}
    private static void mouseUse(Minecraft mc,boolean down){
        try{
            MOUSE_PRESS.invoke(mc.mouseHandler,mc.getWindow().getWindow(),mc.options.keyUse.getKey().getValue(),
                    down?org.lwjgl.glfw.GLFW.GLFW_PRESS:org.lwjgl.glfw.GLFW.GLFW_RELEASE,0);
        }catch(ReflectiveOperationException ex){throw new IllegalStateException(ex);}
    }
    private static void look(Minecraft mc,float yaw,float pitch){
        mc.player.setYRot(yaw);
        mc.player.setYHeadRot(yaw);
        mc.player.setXRot(pitch);
    }
    private static boolean holdingSkill(Minecraft mc,EaSkill skill){
        ItemStack held=mc.player.getMainHandItem();
        return held.is(stack("ea").getItem()) && EaSkill.selected(held)==skill;
    }
    private static void clickSkill(){
        KeyMapping.click(EaSkillKeys.CYCLE.getKey());
    }
    private static void leaveWorld(Minecraft mc){
        if(!setup || finished || !visible || ++menuTicks<20)return;
        monitor=false;
        angleActive=false;
        restoreView(mc);
        finished=true;
        try{Files.writeString(mc.gameDirectory.toPath().resolve("arsenal-qa.left-world"),"Viewer left the demonstration world.");}
        catch(java.io.IOException ex){throw new IllegalStateException(ex);}
    }
    private static boolean proceed(Button b){
        return b.getMessage().getContents() instanceof TranslatableContents t && t.getKey().equals("gui.proceed")
                || Set.of("Proceed","继续","仍要继续","仍然继续","继续进行").contains(b.getMessage().getString());
    }
    @SubscribeEvent public static void client(TickEvent.ClientTickEvent event) {
        if(event.phase!=TickEvent.Phase.END || finished)return;
        var mc=Minecraft.getInstance();
        if(!setup && !Files.exists(mc.gameDirectory.toPath().resolve("enable-arsenal-qa")))return;
        if(mc.getOverlay()!=null)return;
        if(mc.screen instanceof ConfirmScreen){
            mc.screen.children().stream().filter(Button.class::isInstance).map(Button.class::cast)
                    .filter(ArsenalQa::proceed).findFirst().ifPresent(Button::onPress);
            return;
        }
        if(mc.player==null || mc.level==null || mc.gameMode==null || mc.getSingleplayerServer()==null){leaveWorld(mc);return;}
        menuTicks=0;
        if(restoring)return;
        if(!setup){prepare(mc);return;}
        if(!ready || mc.screen!=null)return;
        if(angleActive){look(mc,demoYaw,demoPitch);mc.player.getInventory().selected=0;}
        clock++;
        int elapsed=clock-entered;
        if(elapsed>240){
            check("stage_"+stage+"_timeout",false);
            finish(mc);
            return;
        }
        if(stage==0){
            if(elapsed<64 || !holdingSkill(mc,EaSkill.ENUMA))return;
            check("fresh_ea_defaults_to_enuma",true);
            mc.options.keyUse.setDown(true);
            use(mc);
            step(10);
        }else if(stage==10 && chargingObserved && elapsed>=4){
            stillOnce("arsenal-ea-chant.png");
            mc.options.keyUse.setDown(false);
            mc.gameMode.releaseUsingItem(mc.player);
            step(11);
        }else if(stage==11 && cancelObserved && elapsed>=10){
            check("original_ea_begins_and_cancels_safely",chargingObserved && !releasedObserved);
            clickSkill();
            step(12);
        }else if(stage==12 && holdingSkill(mc,EaSkill.GATE_NORMAL) && elapsed>=3){
            check("skill_key_selects_normal",true);
            if(streamStudy){
                mouseUse(mc,true);
                streamPresses++;
            }else if(frontalOpening){
                mc.options.keyUse.setDown(true);
                KeyMapping.click(mc.options.keyUse.getKey());
            }else use(mc);
            step(1);
        }else if(stage==1){
            var c=cast(0);
            if(c==null)return;
            normalStart=c.start();
            firstRound=c.id();
            step(2);
        }else if(stage==2){
            long age=mc.level.getGameTime()-normalStart;
            if(age>=14 && !normalShot){normalShot=inspect(mc,0);}
            if(age>=20 && normalShot && !flightShot && !redirected && age<26)stillOnce("arsenal-normal.png");
            if(age>=26 && !redirected)redirected=true;
            if(age>=32 && !flightShot){stillOnce("arsenal-flight.png");flightShot=true;}
            watchImpact(mc);
            if(streamStudy)observeStream(mc,age);
            else if(frontalOpening)observeHold(mc,age);
            boolean normalDone=streamStudy ? age>=112 && serverNormalComplete && active(visualClass()).isEmpty()
                    && cast(0)==null : age>=(frontalOpening?2*normalCycle()+12:108);
            if(normalDone || streamStudy && age>=168){
                if(streamStudy)verifyStream(mc);
                if(frontalOpening){
                    check("single_hold_spans_two_distinct_twenty_gate_casts",observedPhysicalHold && witnessedClosure
                            && secondHasTwenty && continuedWithOriginalEa && secondStart-normalStart==normalCycle());
                    check("release_finishes_second_without_third",letGo && actualRounds==2 && !serverStillHolding
                            && TreasuryClient.activeGateCount()==0 && cast(0)==null);
                    System.out.println("ARSENAL QA observed normal rounds="+actualRounds+" first="+firstRound+" second="+secondRound
                            +" delta="+(secondStart-normalStart)+" holdingAfterRelease="+serverStillHolding);
                }
                if(openingStudy){
                    System.out.println("ARSENAL QA fixed camera opening frames="+openFrames+" firstMs="+firstOpenMs+" lastBucket="+lastOpenBucket);
                    check("opening_fixed_view_covers_growth",openFrames>=9 && firstOpenMs>=0 && firstOpenMs<250
                            && lastOpenBucket>=20 && openQuarter==3);
                    if(frontalOpening)check("front_view_clean_and_expanded_gate_recorded",cleanRound && fullRound);
                }
                check("normal_weapon_models_received",normalShot);
                check("flight_responded_to_aim",steered);
                check("wall_impact_rendered",impactShot && impactSpot!=null
                        && shownImpact!=null && shownImpact.distanceTo(impactSpot)<4);
                check("normal_volley_ended",serverNormalComplete);
                clickSkill();
                step(3);
            }
        }else if(stage==3 && elapsed>=10 && holdingSkill(mc,EaSkill.GATE_FULL)){
            check("skill_key_selects_full",true);
            if(streamStudy)mouseUse(mc,true);
            else use(mc);
            step(6);
        }else if(stage==6){
            var c=cast(1);
            if(c==null)return;
            fullStart=c.start();
            if(streamStudy){
                check("fresh_use_key_rearms_same_ea_in_full_mode",mc.options.keyUse.isDown()
                        && mc.player.isUsingItem() && holdingSkill(mc,EaSkill.GATE_FULL));
                mouseUse(mc,false);
            }
            step(7);
        }else if(stage==7){
            long age=mc.level.getGameTime()-fullStart;
            if(age>=15 && !fullShot)fullShot=inspect(mc,1);
            if(age>=20 && age<26 && fullShot)stillOnce("arsenal-full.png");
            if(age>=108 && serverFullComplete){
                check("full_weapon_models_received",fullShot);
                check("no_live_gates_or_visual_shots",TreasuryClient.activeGateCount()==0 && active(visualClass()).isEmpty());
                if(targetedChain)server(p->practiceTarget(p,8));
                clickSkill();
                step(13);
            }
        }else if(stage==13 && elapsed>=4 && holdingSkill(mc,EaSkill.CHAINS)){
            if(targetedChain && !(mc.level.getEntity(practiceId) instanceof Ravager))return;
            use(mc);
            step(14);
        }else if(stage==14 && elapsed>=10 && TreasuryClient.activeChainCount()>0){
            if(targetedChain){
                var cast=cast(2);
                var target=mc.level.getEntity(practiceId);
                if(cast==null || mc.level.getGameTime()-cast.start()<38 || !boundOnServer || !(target instanceof Ravager mob)
                        || TreasuryClient.bindingStrength(mob,mc.getFrameTime())<.75f)return;
                int gates=TreasuryClient.activeChainGateCount();
                check("ea_chain_binds_visible_ravager_with_surrounding_gates",gates==5 || gates==6);
            }
            check("same_ea_casts_chains",true);
            stillOnce("arsenal-ea-chains.png");
            mc.options.keyUse.setDown(false);
            if(mc.player.isUsingItem())mc.gameMode.releaseUsingItem(mc.player);
            step(targetedChain?18:15);
        }else if(stage==18 && elapsed>=8){
            use(mc);
            step(19);
        }else if(stage==19 && elapsed>=18 && !boundOnServer && TreasuryClient.activeChainCount()==0){
            var target=mc.level.getEntity(practiceId);
            check("second_ea_right_click_unbinds_target",target instanceof Ravager mob
                    && TreasuryClient.bindingStrength(mob,mc.getFrameTime())==0);
            if(mc.player.isUsingItem())mc.gameMode.releaseUsingItem(mc.player);
            step(15);
        }else if(stage==15 && elapsed>=4){
            clickSkill();
            step(16);
        }else if(stage==16 && elapsed>=3 && holdingSkill(mc,EaSkill.ENUMA)){
            check("skill_key_wraps_to_enuma",true);
            clickSkill();
            step(17);
        }else if(stage==17 && elapsed>=4 && holdingSkill(mc,EaSkill.GATE_NORMAL)){
            finish(mc);
        }
    }
    private static final Set<String> requested=new HashSet<>();
    private static int normalCycle(){return TreasuryVolley.cycleTicks(20);}
    private record WaitingBlade(UUID batch,Object blade) {}
    private static final class StreamProbe {
        final Map<UUID,Integer> batches=new HashMap<>();
        final List<WaitingBlade> loaded=new ArrayList<>();
        UUID original;
        long start,firstRefill=-1,secondGeneration=-1,released=-1;
        int heldShots,heldBirths,refillTicks,singleBirthTicks,maxBirths,loadedOnRelease,shotsAfter,birthsAfter;
        boolean matched=true,overlap,allBornLoaded=true;

        // Observe state transitions on the actual blades, even if the flight group is removed this tick.
        synchronized void sample(ServerPlayer owner){
            long now=owner.serverLevel().getGameTime();
            boolean held=serverStillHolding && EaSkill.selected(owner.getMainHandItem())==EaSkill.GATE_NORMAL;
            if(original!=null && released<0 && !held){
                released=now;
                loadedOnRelease=loaded.size();
                System.out.printf(Locale.ROOT,"ARSENAL QA stream release age=%d loaded=%d%n",now-start,loadedOnRelease);
            }
            int fired=0,firedFromRefills=0;
            var pending=loaded.iterator();
            while(pending.hasNext()){
                var prior=pending.next();
                if((int)field(prior.blade(),"state")==0)continue;
                fired++;
                if(!prior.batch().equals(original))firedFromRefills++;
                pending.remove();
            }
            boolean originalLoaded=loaded.stream().anyMatch(blade->blade.batch().equals(original));
            int newborn=0;
            for(Object volley:active(TreasuryVolley.class).values()){
                var cast=(TreasuryNetwork.Cast)field(volley,"cast");
                if(cast.mode()!=0 || batches.containsKey(cast.id()))continue;
                Object[] blades=(Object[])field(volley,"blades");
                batches.put(cast.id(),blades.length);
                if(original==null){
                    original=cast.id();
                    start=cast.start();
                    matched&=blades.length==20 && held;
                }else{
                    newborn+=blades.length;
                    matched&=cast.start()==now;
                }
                for(Object blade:blades){
                    allBornLoaded&=(int)field(blade,"state")==0;
                    loaded.add(new WaitingBlade(cast.id(),blade));
                }
            }
            if(original==null)return;
            if(released>=0){
                birthsAfter+=newborn;
                shotsAfter+=fired;
                return;
            }
            heldShots+=fired;
            heldBirths+=newborn;
            if(held && (newborn!=fired || loaded.size()!=20)){
                matched=false;
                System.out.printf(Locale.ROOT,"ARSENAL QA stream mismatch age=%d fired=%d born=%d loaded=%d%n",
                        now-start,fired,newborn,loaded.size());
            }
            if(newborn==0)return;
            refillTicks++;
            if(newborn==1)singleBirthTicks++;
            maxBirths=Math.max(maxBirths,newborn);
            if(firstRefill<0){
                firstRefill=now;
                overlap=originalLoaded && fired>0;
                System.out.printf(Locale.ROOT,"ARSENAL QA first refill age=%d fired=%d born=%d originalStillLoaded=%s%n",
                        now-start,fired,newborn,overlap);
            }
            if(secondGeneration<0 && firedFromRefills>0 && fired==newborn){
                secondGeneration=now;
                System.out.printf(Locale.ROOT,"ARSENAL QA refill weapon launched age=%d fired=%d fromRefills=%d born=%d%n",
                        now-start,fired,firedFromRefills,newborn);
            }
        }
    }

    private static void observeStream(Minecraft mc,long age){
        if(age<TreasuryVolley.launchAge(0,20) && mc.options.keyUse.isDown() && mc.player.isUsingItem()
                && mc.player.getUseItem().is(originalEa.getItem()))observedPhysicalHold=true;
        var volleys=active(visualClass());
        for(Object visual:active(TreasuryClient.class).values()){
            var cast=(TreasuryNetwork.Cast)field(visual,"cast");
            if(cast.mode()!=0 || clientBatches.containsKey(cast.id()))continue;
            Object volley=volleys.get(cast.id());
            if(volley==null)continue;
            Object[] shots=(Object[])field(volley,"shots");
            int gates=cast.gates().size();
            clientBatches.put(cast.id(),gates);
            modelCountsMatch&=shots.length==gates;
            for(Object shot:shots){
                modelCountsMatch&=!((ItemStack)field(shot,"stack")).isEmpty();
                if(!cast.id().equals(firstRound))modelCountsMatch&=(int)field(shot,"openingDelay")==0;
            }
        }
        if(!letGo && age>=64){
            mouseUse(mc,false);
            letGo=true;
        }
    }

    private static void verifyStream(Minecraft mc){
        synchronized(stream){
            System.out.printf(Locale.ROOT,"ARSENAL QA stream ticks=%d heldShots=%d heldBirths=%d maxBornPerTick=%d "
                            +"firstMs=%d secondGenerationMs=%d releaseMs=%d releaseLoaded=%d drainShots=%d laterBirths=%d clientBatches=%d%n",
                    stream.refillTicks,stream.heldShots,stream.heldBirths,stream.maxBirths,(stream.firstRefill-stream.start)*50,
                    (stream.secondGeneration-stream.start)*50,(stream.released-stream.start)*50,stream.loadedOnRelease,
                    stream.shotsAfter,stream.birthsAfter,clientBatches.size());
            check("single_key_press_refills_each_actual_launch_same_tick",streamPresses==1 && observedPhysicalHold
                    && stream.matched && stream.allBornLoaded && stream.refillTicks>=3 && stream.singleBirthTicks>0
                    && stream.maxBirths<20 && stream.heldBirths==stream.heldShots && stream.heldBirths>20);
            check("first_refill_overlaps_loaded_original_and_refills_also_replenish",stream.overlap
                    && stream.firstRefill>stream.start && stream.secondGeneration>stream.firstRefill);
            check("client_received_each_partial_batch_with_matching_gate_and_weapon_counts",
                    modelCountsMatch && stream.batches.equals(clientBatches));
            check("release_stops_births_while_all_loaded_weapons_fire_and_drain",letGo && !serverStillHolding
                    && !mc.options.keyUse.isDown() && stream.released>stream.secondGeneration && stream.birthsAfter==0
                    && stream.loadedOnRelease==20 && stream.shotsAfter==stream.loadedOnRelease && stream.loaded.isEmpty()
                    && serverNormalComplete && TreasuryClient.activeGateCount()==0 && active(visualClass()).isEmpty());
        }
        check("continuous_fixed_camera_covers_launch_refill_and_release",streamFrames>=18
                && firstStreamMs>=0 && firstStreamMs<950 && lastStreamMs>=5000);
    }

    private static void observeHold(Minecraft mc,long age){
        if(mc.player.isUsingItem() && mc.options.keyUse.isDown() && mc.player.getUseItem().is(originalEa.getItem()))
            observedPhysicalHold=true;
        var c=cast(0);
        int gates=TreasuryClient.activeGateCount();
        if(!witnessedClosure && age>=TreasuryVolley.closeEndAge(19,20) && age<normalCycle() && c==null && gates==0){
            witnessedClosure=true;
            stillOnce("round-first-closed.png");
        }
        if(c!=null && !c.id().equals(firstRound) && secondRound==null){
            secondRound=c.id();
            secondStart=c.start();
            secondHasTwenty=gates==20 && !active(TreasuryClient.class).containsKey(firstRound);
        }
        if(secondStart>=0 && mc.level.getGameTime()-secondStart>=16 && c!=null && c.id().equals(secondRound))
            stillOnce("round-second-open.png");
        if(!letGo && (secondStart>=0 && mc.level.getGameTime()-secondStart>=18 || age>normalCycle()+28)){
            mc.options.keyUse.setDown(false);
            letGo=true;
        }
    }
    private static void watchImpact(Minecraft mc){
        if(TreasuryImpactRenderer.activeCount()==0)return;
        Vec3 center=TreasuryImpactRenderer.latestPoint();
        if(center==null || Math.abs(center.z-30)>2 || center.y<100 || center.y>110)return;
        long now=mc.level.getGameTime();
        if(shownImpact==null){
            shownImpact=center;
            firstImpactFrame=now;
            System.out.println("ARSENAL QA received visible wall blast at "+center);
        }
        if(center.distanceToSqr(shownImpact)>9)return;
        long age=now-firstImpactFrame;
        if(age>=3 && !requested.contains("arsenal-impact-03.png"))stillOnce("arsenal-impact-03.png");
        else if(age>=5 && !requested.contains("arsenal-impact.png")){
            stillOnce("arsenal-impact.png");
            impactShot=requested.contains("arsenal-impact.png");
        }else if(age>=8 && !requested.contains("arsenal-impact-08.png"))stillOnce("arsenal-impact-08.png");
        else if(age>=11 && !requested.contains("arsenal-impact-11.png"))stillOnce("arsenal-impact-11.png");
    }

    private static void stillOnce(String name){
        if(still==null && requested.add(name))still=name;
    }
    private static boolean inspect(Minecraft mc,int mode){
        var cast=cast(mode);
        if(cast==null)return false;
        var volley=active(visualClass()).get(cast.id());
        if(volley==null)return false;
        Object[] shots=(Object[])field(volley,"shots");
        int expected=mode==0?20:100,present=0;
        var types=new HashSet<ResourceLocation>();
        boolean baseSword=false,nbtSword=false;
        double closest=Double.POSITIVE_INFINITY;
        double top=BASE.y,bottom=Double.MAX_VALUE,left=1e9,right=-1e9;
        for(Object shot:shots){
            var stack=(ItemStack)field(shot,"stack");
            if(!stack.isEmpty())present++;
            var key=ForgeRegistries.ITEMS.getKey(stack.getItem());
            types.add(key);
            if(stack.is(Items.IRON_SWORD)){
                if(stack.hasTag() && stack.getTag().getInt("CustomModelData")==12063)nbtSword=true;else baseSword=true;
            }
            var point=(Vec3)field(shot,"gate");
            if(mode==0){
                double weight=point.distanceToSqr(BASE.add(0,6,-4));
                if(stack.is(Items.BOW) || stack.is(Items.CROSSBOW))weight+=16;
                if(weight<closest){closest=weight;closeGate=point;}
            }
            left=Math.min(left,point.x-1.65);right=Math.max(right,point.x+1.65);
            bottom=Math.min(bottom,point.y-1.65);top=Math.max(top,point.y+1.65);
        }
        if(mode==1){
            double tan=Math.tan(Math.toRadians(mc.options.fov().get()/2.0));
            double aspect=(double)mc.getWindow().getWidth()/mc.getWindow().getHeight();
            double distance=1.13*Math.max((top-bottom)/(2*tan),(right-left)/(2*tan*aspect))+1;
            fullAim=new Vec3((left+right)/2,(top+bottom)/2,BASE.z-4);
            fullEye=frontalOpening || streamStudy?fullAim.add(distance*.17,distance*.04,distance*1.10):fullAim.add(0,0,distance);
        }
        System.out.println("ARSENAL QA mode="+mode+" gates="+TreasuryClient.activeGateCount()+" weapons="+present+" types="+types);
        check("mode_"+mode+"_one_weapon_per_gate",TreasuryClient.activeGateCount()==expected && shots.length==expected && present==expected);
        check("mode_"+mode+"_multiple_weapons_no_ea",types.size()>=6 && !types.contains(new ResourceLocation("starcrowndoll","ea")));
        if(mode==1)check("synced_default_and_nbt_model",baseSword && nbtSword);
        return true;
    }
    @SubscribeEvent(priority=EventPriority.HIGHEST)
    public static void enforceAim(TickEvent.ServerTickEvent event){
        if(!monitor || !angleActive)return;
        var players=event.getServer().getPlayerList().getPlayers();
        if(players.size()!=1)return;
        ServerPlayer p=players.get(0);
        for(Object volley:active(TreasuryVolley.class).values()){
            var cast=(TreasuryNetwork.Cast)field(volley,"cast");
            if(cast.mode()==0 && serverNormalStart<0)serverNormalStart=cast.start();
        }
        long age=p.serverLevel().getGameTime()-serverNormalStart;
        int turnEnd=streamStudy?51:(frontalOpening?62:96);
        boolean turn=serverNormalStart>=0 && age>=26 && age<turnEnd;
        demoYaw=turn?-25:0;
        demoPitch=turn?-3:0;
        p.setYRot(demoYaw);
        p.setYHeadRot(demoYaw);
        p.setXRot(demoPitch);
        if(p.position().distanceToSqr(BASE)>.12){
            p.teleportTo(p.serverLevel(),BASE.x,BASE.y,BASE.z,demoYaw,demoPitch);
        }
        Object charge=((Map<?,?>)field(EaServer.class,"CASTS")).get(p.getUUID());
        if(charge!=null){
            int phase=(int)field(charge,"phase");
            chargingObserved|=phase==0;
            cancelObserved|=phase==2;
            releasedObserved|=phase==1;
        }
    }
    @SubscribeEvent(priority=EventPriority.LOWEST)
    public static void serverProbe(TickEvent.ServerTickEvent event){
        if(!monitor || event.phase!=TickEvent.Phase.END)return;
        if(practiceTarget!=null)boundOnServer=TreasuryItems.isBound(practiceTarget);
        if(frontalOpening || streamStudy){
            var players=event.getServer().getPlayerList().getPlayers();
            serverStillHolding=players.size()==1 && players.get(0).isUsingItem() && players.get(0).getUseItem()==originalEa;
            if(streamStudy && players.size()==1)stream.sample(players.get(0));
        }
        boolean normal=false,full=false;
        for(Object volley:active(TreasuryVolley.class).values()){
            var cast=(TreasuryNetwork.Cast)field(volley,"cast");
            var owner=event.getServer().getPlayerList().getPlayer(cast.caster());
            if(owner==null)continue;
            int mode=cast.mode();
            if(mode==0){
                normal=true;sawNormal=true;
                if(frontalOpening && serverRounds.add(cast.id())){
                    actualRounds=serverRounds.size();
                    if(actualRounds==2)continuedWithOriginalEa=serverStillHolding && owner.getMainHandItem()==originalEa;
                }
            }
            else {full=true;sawFull=true;}
            if(mode!=0)continue;
            long age=owner.serverLevel().getGameTime()-cast.start();
            Object[] blades=(Object[])field(volley,"blades");
            Object first=blades[0];
            if((!streamStudy || cast.start()==serverNormalStart) && ((Integer)field(first,"state"))==1){
                Vec3 heading=((Vec3)field(first,"velocity")).normalize();
                if(initialHeading==null && age>=23 && owner.getYRot()>-10)initialHeading=heading;
                if(initialHeading!=null && !steered && owner.getYRot()<-17 && heading.x-initialHeading.x>.10){
                    steered=true;
                    System.out.printf(Locale.ROOT,"ARSENAL QA steering blade=0 age=%d yaw=%.2f dx=%.3f%n",
                            age,owner.getYRot(),heading.x-initialHeading.x);
                }
            }
            if(impactSpot==null){
                for(Object blade:blades){
                    if(((Integer)field(blade,"state"))!=2)continue;
                    Vec3 end=(Vec3)field(blade,"position");
                    if(Math.abs(end.z-30)<3.4 && end.y>=100 && end.y<111){
                        impactSpot=new Vec3(end.x,end.y,29.8);
                        System.out.println("ARSENAL QA finished blade near impact wall age="+age+" position="+end);
                        break;
                    }
                }
            }
        }
        if(sawNormal)serverNormalComplete=!normal;
        if(sawFull && !full)serverFullComplete=true;
    }
    private static void probeDamage(ServerPlayer p){
        ServerLevel level=p.serverLevel();
        var mob=EntityType.RAVAGER.create(level);
        mob.setNoAi(true);
        mob.setSilent(true);
        mob.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100);
        mob.getAttribute(Attributes.ARMOR).setBaseValue(30);
        mob.setHealth(100);
        mob.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE,300,4,false,false));
        mob.moveTo(17.5,101,27.5,180,0);
        level.addFreshEntity(mob);
        Vec3 center=mob.getBoundingBox().getCenter();
        TreasuryImpacts.hit(p,center,mob,12063L);
        float first=mob.getHealth();
        TreasuryImpacts.hit(p,center,mob,12064L);
        float second=mob.getHealth();
        System.out.println("ARSENAL QA armor="+mob.getArmorValue()+" health=100/"+first+"/"+second);
        check("two_independent_20_damage_hits_through_armor_and_resistance",
                mob.getArmorValue()>=20 && Math.abs(first-80)<.01 && Math.abs(second-60)<.01);
        mob.discard();
        check("impact_range_terrain_preserved",terrain(level)==wallBefore);
    }
    private static void practiceTarget(ServerPlayer p,double distance){
        var level=p.serverLevel();
        if(practiceTarget==null || !practiceTarget.isAlive()){
            if(level.getDifficulty()==Difficulty.PEACEFUL)level.getServer().setDifficulty(Difficulty.NORMAL,true);
            var mob=EntityType.RAVAGER.create(level);
            mob.setNoAi(true);
            mob.setSilent(true);
            mob.setPersistenceRequired();
            mob.addTag("starcrownqa_practice_target");
            mob.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200);
            mob.setHealth(200);
            mob.moveTo(BASE.x,BASE.y,BASE.z+distance,180,0);
            level.addFreshEntity(mob);
            practiceTarget=mob;
            practiceId=mob.getId();
        }else practiceTarget.teleportTo(BASE.x,BASE.y,BASE.z+distance);
    }
    private static void restoreView(Minecraft mc){
        mc.options.keyShift.setDown(false);
        mc.options.keyUse.setDown(false);
        mc.options.hideGui=originalHud;
        mc.options.setCameraType(originalCamera);
        mc.options.fov().set(originalFov);
        if(frontalOpening || streamStudy)mc.options.bobView().set(originalBob);
    }
    private static void releaseChunks(ServerLevel level){
        for(ChunkPos forced:forcedChunks)level.setChunkForced(forced.x,forced.z,false);
        forcedChunks.clear();
    }
    @SubscribeEvent public static void stopping(net.minecraftforge.event.server.ServerStoppingEvent event){
        if(!forcedChunks.isEmpty())releaseChunks(event.getServer().overworld());
    }
    private static void finish(Minecraft mc){
        restoring=true;
        monitor=false;
        angleActive=false;
        restoreView(mc);
        look(mc,0,0);
        server(p->{
            if(visualRecheck)check("impact_range_terrain_preserved",terrain(p.serverLevel())==wallBefore);
            else probeDamage(p);
            releaseChunks(p.serverLevel());
            p.getInventory().selected=0;
            check("one_physical_ea_through_all_skills",p.getMainHandItem()==originalEa);
            check("ea_ready_to_practice_normal",EaSkill.selected(p.getMainHandItem())==EaSkill.GATE_NORMAL);
            // The tested stack remains equipped, with the real synchronized normal mode selected.
            p.releaseUsingItem();
            for(int i=0;i<4 && EaSkill.selected(p.getMainHandItem())!=EaSkill.GATE_NORMAL;i++)EaSkill.cycle(p,InteractionHand.MAIN_HAND);
            p.inventoryMenu.broadcastChanges();
            p.teleportTo(p.serverLevel(),BASE.x,BASE.y,BASE.z,0,0);
            practiceTarget(p,19);
            mc.execute(()->{
                finished=true;
                String result="ARSENAL QA COMPLETE failures="+failures;
                try{Files.writeString(mc.gameDirectory.toPath().resolve("arsenal-qa.complete"),result+System.lineSeparator());}
                catch(java.io.IOException ex){throw new IllegalStateException(ex);}
                System.out.println(result);
                if(visible){
                    String key=EaSkillKeys.CYCLE.getTranslatedKeyMessage().getString();
                    mc.player.displayClientMessage(Component.literal("试射结束。手中 Ea 已切到王之财宝普通模式，右键可再试。按 "+key+" 切换天地乖离、普通王财、全力王财和天之锁。"),false);
                }else mc.stop();
            });
        });
    }
    @SubscribeEvent public static void camera(ViewportEvent.ComputeCameraAngles event){
        if(!ready || restoring || finished)return;
        var mc=Minecraft.getInstance();
        if(mc.level==null)return;
        if(openingStudy && openingStart<0 && (stage==1 || stage==2))selectOpeningView();
        long age=normalStart<0?-1:mc.level.getGameTime()-normalStart;
        Vec3 eye,aim;
        if(stage>=3 && stage<=7){
            eye=fullEye;aim=fullAim;
        }else if(targetedChain && stage>=13){
            eye=BASE.add(6.6,4.55,1.6);aim=BASE.add(0,1.7,8);
        }else if(stage>=13){
            eye=BASE.add(6,4,6);aim=BASE.add(0,1.7,10);
        }else if(stage>=10){
            eye=BASE.add(4.4,3.4,5.2);aim=BASE.add(0,1.5,0);
        }else if(streamStudy && (stage==1 || stage==2)){
            eye=STREAM_EYE;aim=STREAM_AIM;
            event.setRoll(0);
        }else if(openingStudy && openingStart>=0 && mc.level.getGameTime()-openingStart<27){
            eye=openingEye;aim=openingAim;
            if(frontalOpening)event.setRoll(0);
        }else if(frontalOpening && age>=55 && age<normalCycle()+30){
            eye=BASE.add(3.0,7.3,14.6);aim=BASE.add(0,5.7,-4.5);
        }else if(shownImpact!=null){
            eye=shownImpact.add(-3.8,2.0,-8.8);aim=shownImpact.add(0,.1,0);
        }else if(age>=28){
            // From the flank, the forward and sideways components of a turn are both visible.
            eye=BASE.add(15,8,14);aim=BASE.add(1,4,8);
        }else if(age>=12){
            eye=closeGate.add(3.1,1.25,5.25);aim=closeGate.add(0,.05,.55);
        }else{
            eye=BASE.add(2,6.5,9.8);aim=BASE.add(0,5.5,-4);
        }
        Vec3 look=aim.subtract(eye);
        try{CAMERA.invoke(event.getCamera(),eye);}
        catch(ReflectiveOperationException ex){throw new IllegalStateException(ex);}
        event.setYaw((float)Math.toDegrees(Math.atan2(-look.x,look.z)));
        event.setPitch((float)Math.toDegrees(Math.atan2(-look.y,look.horizontalDistance())));
    }
    private static void selectOpeningView(){
        var cast=cast(0);
        if(cast==null)return;
        var gates=TreasuryLayout.gates(20,cast.id().getLeastSignificantBits());
        // The leading gate always starts at tick zero. Radius-relative framing keeps it the same size for every seed.
        TreasuryLayout.Gate hero=gates.get(0);
        Vec3 center=TreasuryVolley.gateCenter(cast,hero);
        Vec3 forward=cast.forward().normalize();
        Vec3 right=forward.cross(new Vec3(0,1,0)).normalize();
        double radius=hero.radius();
        openingEye=center.add(forward.scale(radius*3.8));
        openingAim=center;
        if(frontalOpening){
            System.out.printf(Locale.ROOT,"ARSENAL QA frontal opening gate=0 radius=%.4f distance=%.4f eye=%s center=%s%n",
                    radius,openingEye.distanceTo(center),openingEye,center);
        }else{
            openingEye=openingEye.add(right.scale(radius*1.30)).add(0,radius*.48,0);
            openingAim=center.add(forward.scale(.14*radius));
        }
        openingStart=cast.start();
    }
    private static void openingFrame(Minecraft mc,int ms,String motionAlias){
        int bucket=ms/50;
        if(bucket==lastOpenBucket)return;
        lastOpenBucket=bucket;
        if(firstOpenMs<0)firstOpenMs=ms;
        openFrames++;
        var aliases=new ArrayList<String>(2);
        if(motionAlias!=null)aliases.add(motionAlias);
        if(frontalOpening && !cleanRound && ms>=275 && ms<400){
            aliases.add("open-round-clean.png");
            cleanRound=true;
            System.out.println("ARSENAL QA clean frontal opening at "+ms+"ms");
        }
        if(frontalOpening && !fullRound && ms>=900){
            aliases.add("open-round-full.png");
            fullRound=true;
            System.out.println("ARSENAL QA expanded frontal opening at "+ms+"ms");
        }
        int threshold=switch(openQuarter){case 0->150;case 1->450;case 2->900;default->Integer.MAX_VALUE;};
        if(ms>=threshold)aliases.add("open-q"+(++openQuarter)+".png");
        String name=String.format(Locale.ROOT,"open-motion-%04d.png",ms);
        saveFrameCopies(mc,name,aliases);
    }
    private static void saveFrameCopies(Minecraft mc,String name,List<String> aliases){
        Screenshot.grab(mc.gameDirectory,name,mc.getMainRenderTarget(),message->{
            var screens=mc.gameDirectory.toPath().resolve("screenshots");
            for(String alias:aliases){
                try{Files.copy(screens.resolve(name),screens.resolve(alias),StandardCopyOption.REPLACE_EXISTING);}
                catch(java.io.IOException ex){check("copy_"+alias,false);System.out.println("ARSENAL QA "+ex);}
            }
        });
    }
    @SubscribeEvent public static void render(TickEvent.RenderTickEvent event){
        if(event.phase!=TickEvent.Phase.END || !ready || restoring || finished)return;
        var mc=Minecraft.getInstance();
        if(mc.level==null || mc.screen!=null)return;
        long start=openingStudy && openingStart>=0?openingStart:normalStart;
        if(streamStudy && (stage==1 || stage==2)){
            if(start<0){
                var cast=cast(0);
                if(cast!=null)start=cast.start();
            }
            if(start>=0){
                int ms=Math.round((mc.level.getGameTime()-start+mc.getFrameTime())*50f);
                if(ms>=0 && ms<5300 && ms/80>streamBucket){
                    streamBucket=ms/80;
                    if(firstStreamMs<0)firstStreamMs=ms;
                    lastStreamMs=ms;
                    streamFrames++;
                    String name=String.format(Locale.ROOT,"stream-motion-%04d.png",ms);
                    Screenshot.grab(mc.gameDirectory,name,mc.getMainRenderTarget(),message->{});
                }
            }
        }else if((stage==2 || openingStudy && stage==1) && start>=0){
            int ms=Math.round((mc.level.getGameTime()-start+mc.getFrameTime())*50f);
            String round=null;
            if(frontalOpening && ms>=55*50 && ms<(normalCycle()+30)*50 && ms/80>lastRoundBucket){
                lastRoundBucket=ms/80;
                round=String.format(Locale.ROOT,"round-motion-%04d.png",ms);
            }
            String motion=null;
            if(ms>=0 && ms<5000 && ms/80>lastMotion){
                lastMotion=ms/80;
                motion=String.format(Locale.ROOT,"arsenal-motion-%04d.png",ms);
            }
            if(round!=null)saveFrameCopies(mc,round,motion==null?List.of():List.of(motion));
            else if(openingStudy && openingStart>=0 && ms>=0 && ms<1150 && ms/50!=lastOpenBucket)
                openingFrame(mc,ms,motion);
            else if(motion!=null)Screenshot.grab(mc.gameDirectory,motion,mc.getMainRenderTarget(),message->{});
        }
        if(still!=null){
            String name=still;
            still=null;
            Screenshot.grab(mc.gameDirectory,name,mc.getMainRenderTarget(),message->{});
        }
    }
    private ArsenalQa(){}
}
