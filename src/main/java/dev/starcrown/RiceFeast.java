package dev.starcrown;

import java.util.*;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Music supplies virtual servings; equipment and inventory are never replaced. */
@Mod.EventBusSubscriber(modid=StarcrownDoll.ID)
public final class RiceFeast {
 private static final Map<LivingEntity,Integer> CHEW=new WeakHashMap<>();
 public static boolean active(LivingEntity entity){return entity.isAlive()&&!entity.isSpectator()&&DollDance.nearYule(entity.level(),entity.position());}
 public static boolean calmVisual(LivingEntity entity){return entity.isAlive()&&!entity.isSpectator()&&DollDance.nearCalm(entity.level(),entity.position());}
 @SubscribeEvent public static void tick(TickEvent.LevelTickEvent event){
  if(event.phase!=TickEvent.Phase.END||!(event.level instanceof ServerLevel level))return;
  Set<LivingEntity> current=Collections.newSetFromMap(new IdentityHashMap<>());
  for(LivingEntity living:DollDance.feastTargets(level)){
   current.add(living);int ticks=CHEW.getOrDefault(living,0)+1;
   if(ticks%4==0){
    level.sendParticles(new ItemParticleOption(ParticleTypes.ITEM,new ItemStack(StarcrownDoll.WHITE_RICE.get())),living.getX(),living.getEyeY()-.12,living.getZ(),4,.12,.08,.12,.025);
    level.playSound(null,living.blockPosition(),SoundEvents.GENERIC_EAT,SoundSource.NEUTRAL,.35f,.9f+level.random.nextFloat()*.2f);
   }
   if(ticks>=32){
    new ItemStack(StarcrownDoll.WHITE_RICE.get()).finishUsingItem(level,living);
    // Dancing mobs skip LivingEntity ticks, so resolve their one-tick food effect here.
    if(living instanceof net.minecraft.world.entity.Mob){
     var effect=living.getEffect(net.minecraft.world.effect.MobEffects.HEAL);
     if(effect!=null&&effect.getAmplifier()==28&&effect.getDuration()==1){
      effect.getEffect().applyEffectTick(living,effect.getAmplifier());
      living.removeEffect(net.minecraft.world.effect.MobEffects.HEAL);
     }
    }
    ticks=0;
   }
   CHEW.put(living,ticks);
  }
  CHEW.keySet().removeIf(e->e.level()==level&&!current.contains(e));
  if(level.getGameTime()%20==0){
   long now=TwilightCrown.now(level);
   for(var target:DollDance.calmFeastTargets(level)){
    var data=target.getPersistentData();
    if(data.getLong("starcrown_calm_feast_ready")>now)continue;
    data.putLong("starcrown_calm_feast_ready",now+1200);
    target.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.REGENERATION,160,1));
    level.sendParticles(new ItemParticleOption(ParticleTypes.ITEM,new ItemStack(StarcrownDoll.WHITE_RICE.get())),target.getX(),target.getEyeY()-.12,target.getZ(),4,.12,.08,.12,.025);
    level.playSound(null,target.blockPosition(),SoundEvents.GENERIC_EAT,SoundSource.NEUTRAL,.35f,1);
   }
  }
 }
}
