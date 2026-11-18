package dev.eavfx;

import com.mojang.logging.LogUtils;
import dev.starcrown.EaSkill;
import dev.starcrown.StarcrownDoll;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterShadersEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid=StarcrownDoll.ID,value=Dist.CLIENT)
public final class EaClient {
    private static final Map<Long,Visual> CASTS=new LinkedHashMap<>();
    private static final Map<Long,Long> CHANT_CUES=new LinkedHashMap<>();
    private static ClientLevel lastLevel;

    private static final class Visual {
        final EaNetwork.CastPacket packet;
        final long receivedTick;
        LivingEntity trackedOwner;
        boolean chantStarted;
        FollowingSound chant,spin,blast;
        Visual(EaNetwork.CastPacket packet,ClientLevel level){
            this.packet=packet;
            receivedTick=level.getGameTime();
            trackedOwner=level.getEntity(packet.entityId()) instanceof LivingEntity owner?owner:null;
        }
        void startSpin(Minecraft mc){
            if(spin!=null||trackedOwner==null||!trackedOwner.isAlive())return;
            spin=new FollowingSound(mc.level,packet,StarcrownDoll.EA_SPIN.get(),true);
            mc.getSoundManager().play(spin);
        }
        void stop(Minecraft mc){
            if(chant!=null)chant.cancel(mc);
            if(blast!=null)blast.cancel(mc);
            stopSpin(mc);
        }
        void stopSpin(Minecraft mc){
            if(spin!=null){spin.cancel(mc);spin=null;}
        }
    }
    private static final class FollowingSound extends AbstractTickableSoundInstance {
        private final ClientLevel level;
        private final EaNetwork.CastPacket packet;
        FollowingSound(ClientLevel level,EaNetwork.CastPacket packet,SoundEvent event,boolean loop){
            super(event,SoundSource.PLAYERS,RandomSource.create());
            this.level=level;
            this.packet=packet;
            looping=loop;
            delay=0;
            volume=loop?.90f:1f;
            pitch=1.0f;
            x=packet.origin().x;
            y=packet.origin().y;
            z=packet.origin().z;
            var owner=level.getEntity(packet.entityId());
            if(owner!=null){x=owner.getX();y=owner.getEyeY();z=owner.getZ();}
        }
        void cancel(Minecraft mc){
            stop();
            mc.getSoundManager().stop(this);
        }
        @Override public void tick(){
            var owner=level.getEntity(packet.entityId());
            long age=level.getGameTime()-packet.startTick();
            int limit=packet.phase()==1?EaAudioConfig.RELEASE_SOUND_TICKS:EaServer.CHARGE_TICKS+EaAudioConfig.MAX_CHANT_TICKS+10;
            if(Minecraft.getInstance().level!=level||age>=limit
                    ||owner==null||!owner.isAlive()){stop();return;}
            x=owner.getX();y=owner.getEyeY();z=owner.getZ();
            if(looping){
                float duck=net.minecraft.util.Mth.clamp((age-EaServer.CHARGE_TICKS+3)/7f,0,1);
                volume=.90f-.62f*duck;
            }
        }
    }

    public static void accept(EaNetwork.CastPacket packet){
        var mc=Minecraft.getInstance();
        if(mc.level==null||!mc.level.dimension().location().equals(packet.dimension()))return;
        if(lastLevel!=mc.level)reset(mc);
        Visual previous=CASTS.get(packet.castId());
        boolean retracked=previous!=null&&packet.phase()==0&&previous.packet.phase()==0
                &&previous.trackedOwner!=null&&previous.trackedOwner!=mc.level.getEntity(packet.entityId());
        if(previous!=null&&!retracked
                &&(packet.phase()<=previous.packet.phase()||packet.startTick()<previous.packet.startTick()))return;
        if(previous!=null)previous.stop(mc);
        if(packet.phase()==0){
            var older=CASTS.values().iterator();
            while(older.hasNext()){
                Visual entry=older.next();
                if(entry.packet.phase()!=0||entry.packet.entityId()!=packet.entityId())continue;
                if(entry.packet.castId()>packet.castId())return;
                entry.stop(mc);
                older.remove();
            }
        }
        Visual current=new Visual(packet,mc.level);
        CASTS.put(packet.castId(),current);
        while(CASTS.size()>32)CASTS.remove(CASTS.keySet().iterator().next()).stop(mc);
        if(packet.phase()==0)current.startSpin(mc);
        if(packet.phase()==1&&mc.level.getGameTime()-packet.startTick()<10){
            current.blast=new FollowingSound(mc.level,packet,StarcrownDoll.EA_BLAST.get(),false);
            mc.getSoundManager().play(current.blast);
        }
    }

    private static boolean usingEnuma(LivingEntity owner){
        if(owner==null || !owner.isAlive() || !owner.isUsingItem())return false;
        var held=owner.getUseItem();
        return held.is(StarcrownDoll.EA.get()) && EaSkill.selected(held)==EaSkill.ENUMA;
    }

    public static float ageFor(LivingEntity owner){
        return chargeAge(owner,false);
    }

    public static float effectAgeFor(LivingEntity owner){
        return chargeAge(owner,true);
    }

    private static float chargeAge(LivingEntity owner,boolean effectTime){
        var mc=Minecraft.getInstance();
        if(mc.level==null||mc.level!=lastLevel||!usingEnuma(owner))return -1;
        for(Visual visual:CASTS.values()){
            var packet=visual.packet;
            if(packet.phase()==0&&packet.entityId()==owner.getId()
                    &&(visual.trackedOwner==null||visual.trackedOwner==owner)){
                float age=Math.max(0,mc.level.getGameTime()-packet.startTick()+mc.getFrameTime());
                if(age>=EaServer.CHARGE_TICKS+packet.chantTicks())continue;
                return effectTime?EaChargeStorm.sequenceAge(age,packet.chantTicks()):age;
            }
        }
        return -1;
    }

    public static float visualTick(){
        var mc=Minecraft.getInstance();
        return mc.level==null?0:(mc.level.getGameTime()%100000)+mc.getFrameTime();
    }

    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event){
        if(event.phase!=TickEvent.Phase.END)return;
        var mc=Minecraft.getInstance();
        if(lastLevel!=mc.level)reset(mc);
        if(mc.level==null||mc.isPaused())return;
        long time=mc.level.getGameTime();
        CHANT_CUES.values().removeIf(expiration->time>expiration);
        var iterator=CASTS.values().iterator();
        while(iterator.hasNext()){
            Visual visual=iterator.next();
            var packet=visual.packet;
            long age=time-packet.startTick();
            int limit=switch(packet.phase()){case 0->EaServer.CHARGE_TICKS+packet.chantTicks()+10;
                case 1->EaAudioConfig.RELEASE_SOUND_TICKS;default->EaServer.CANCEL_TICKS;};
            if(age>=limit){visual.stop(mc);iterator.remove();continue;}
            if(packet.phase()==0){
                var entity=mc.level.getEntity(packet.entityId());
                if(visual.trackedOwner!=null&&(entity!=visual.trackedOwner||!visual.trackedOwner.isAlive())
                        ||entity==null&&time-visual.receivedTick>10){
                    visual.stop(mc);iterator.remove();continue;
                }
                if(entity instanceof LivingEntity owner){visual.trackedOwner=owner;visual.startSpin(mc);}
            }
            if(packet.phase()!=0||visual.chantStarted||age<EaServer.CHARGE_TICKS)continue;
            var source=mc.level.getEntity(packet.entityId());
            if(source==null)continue;
            visual.chantStarted=true;
            if(age>EaServer.CHARGE_TICKS+3)continue;
            if(CHANT_CUES.putIfAbsent(packet.castId(),packet.startTick()+EaServer.CHARGE_TICKS+3)!=null)continue;
            visual.chant=new FollowingSound(mc.level,packet,StarcrownDoll.EA_CHANT.get(),false);
            mc.getSoundManager().play(visual.chant);
        }
        EaChantMusic.update(mc, hasAudibleChant(mc));
    }

    private static boolean hasAudibleChant(Minecraft mc){
        if(mc.options.getSoundSourceVolume(SoundSource.MASTER)<=0 || mc.options.getSoundSourceVolume(SoundSource.PLAYERS)<=0)
            return false;
        var listener=mc.gameRenderer.getMainCamera().getPosition();
        for(Visual visual:CASTS.values()){
            var chant=visual.chant;
            if(chant==null || !mc.getSoundManager().isActive(chant))continue;
            double range=chant.getSound().getAttenuationDistance()*Math.max(1f,chant.getVolume());
            if(listener.distanceToSqr(chant.getX(),chant.getY(),chant.getZ())<range*range)return true;
        }
        return false;
    }

    private static void reset(Minecraft mc){
        CASTS.values().forEach(visual->visual.stop(mc));
        CASTS.clear();
        CHANT_CUES.clear();
        EaChantMusic.update(mc, false);
        lastLevel=mc.level;
    }

    @SubscribeEvent public static void render(RenderLevelStageEvent event){
        if(event.getStage()!=RenderLevelStageEvent.Stage.AFTER_PARTICLES||lastLevel==null
                ||Minecraft.getInstance().level!=lastLevel)return;
        var frames=new ArrayList<EaFrame>();
        double time=lastLevel.getGameTime()+event.getPartialTick();
        for(Visual visual:CASTS.values()){
            var packet=visual.packet;
            float age=(float)(time-packet.startTick());
            if(packet.phase()==0&&age>=0&&age<EaServer.CHARGE_TICKS+packet.chantTicks()
                    &&lastLevel.getEntity(packet.entityId()) instanceof LivingEntity owner&&usingEnuma(owner)
                    &&(visual.trackedOwner==null||visual.trackedOwner==owner)){
                EaChargeStorm.world(event,owner,age,EaChargeStorm.sequenceAge(age,packet.chantTicks()),packet.seed());
            }
            if(packet.phase()==1&&age>=0&&age<EaAudioConfig.RELEASE_SOUND_TICKS)
                frames.add(new EaFrame(packet.castId(),packet.entityId(),packet.origin(),packet.direction(),
                        1,age,1,packet.maxLength(),packet.seed()));
        }
        if(frames.isEmpty())return;
        var eye=event.getCamera().getPosition();
        frames.sort(Comparator.comparingDouble(frame->frame.origin().distanceToSqr(eye)));
        EaWorldEffects.render(event,frames);
    }

    @Mod.EventBusSubscriber(modid=StarcrownDoll.ID,value=Dist.CLIENT,bus=Mod.EventBusSubscriber.Bus.MOD)
    public static final class Resources {
        @SubscribeEvent public static void shaders(RegisterShadersEvent event){
            try{EaRibbon.registerShader(event);EaWorldEffects.registerShader(event);}
            catch(java.io.IOException|RuntimeException error){LogUtils.getLogger().warn("Ea beam shader unavailable",error);}
            EaChargeShader.register(event);
            EaChargeStorm.register(event);
        }
    }
}
