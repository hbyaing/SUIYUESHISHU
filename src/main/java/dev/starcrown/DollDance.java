package dev.starcrown;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import java.util.*;

/** Per-level live sources. No persistent AI flags or goal definitions are modified. */
@Mod.EventBusSubscriber(modid=StarcrownDoll.ID)
public final class DollDance {
 private static final Map<Level,Set<BlockPos>> SOURCES=Collections.synchronizedMap(new WeakHashMap<>());
 private static final Map<Level,List<BlockPos>> SOURCE_LISTS=Collections.synchronizedMap(new WeakHashMap<>());
 private static final Map<Mob,Vec3> LOCKS=new WeakHashMap<>();
 public static void sourceChanged(StarcrownDoll.DollEntity doll){
  Level level=doll.getLevel();if(level==null)return;
  Set<BlockPos> sources=SOURCES.computeIfAbsent(level,l->new HashSet<>());
  boolean changed=doll.isPlaying()&&!doll.isRemoved()?sources.add(doll.getBlockPos().immutable()):sources.remove(doll.getBlockPos());
  if(changed)SOURCE_LISTS.remove(level);
 }
 private static List<BlockPos> sources(Level level){return SOURCE_LISTS.computeIfAbsent(level,l->List.copyOf(SOURCES.getOrDefault(l,Set.of())));}
 public static boolean controlled(Mob mob){return mob.isAlive()&&nearSource(mob.level(),mob.level().isClientSide?mob.position():LOCKS.getOrDefault(mob,mob.position()));}
 public static boolean nearSource(Level level,Vec3 point){
  return nearSource(level,point,false);
 }
 public static boolean nearYule(Level level,Vec3 point){return nearSource(level,point,true);}
 public static boolean nearCalm(Level level,Vec3 point){
  for(var pos:sources(level))if(point.distanceToSqr(Vec3.atCenterOf(pos))<=144&&level.hasChunkAt(pos)
   &&level.getBlockEntity(pos) instanceof StarcrownDoll.DollEntity doll&&doll.isPlaying()&&!doll.isRemoved()
   &&doll.getBlockState().is(StarcrownDoll.CALM_DOLL.get()))return true;
  return false;
 }
 public static Set<net.minecraft.world.entity.LivingEntity> feastTargets(Level level){
  Set<net.minecraft.world.entity.LivingEntity> result=new HashSet<>();
  for(BlockPos pos:sources(level))if(level.hasChunkAt(pos)&&level.getBlockEntity(pos) instanceof StarcrownDoll.DollEntity doll&&doll.isPlaying()&&!doll.isRemoved()&&doll.getBlockState().is(StarcrownDoll.YULE_DOLL.get()))
   result.addAll(level.getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class,new AABB(pos).inflate(16),e->e.isAlive()&&!e.isSpectator()&&e.position().distanceToSqr(Vec3.atCenterOf(pos))<=256));
  return result;
 }
 private static boolean nearSource(Level level,Vec3 point,boolean yuleOnly){
  for(BlockPos pos:sources(level)) {
   if(!level.hasChunkAt(pos))continue;
   if(level.getBlockEntity(pos) instanceof StarcrownDoll.DollEntity doll&&!doll.isRemoved()&&doll.isPlaying()
    &&(yuleOnly?doll.getBlockState().is(StarcrownDoll.YULE_DOLL.get()):!doll.getBlockState().is(StarcrownDoll.CALM_DOLL.get()))
    &&point.distanceToSqr(Vec3.atCenterOf(pos))<=256)return true;
  }
  return false;
 }
 private static void hold(Mob mob){
  Vec3 anchor=LOCKS.computeIfAbsent(mob,m->m.position());
  DivineEternity.stop(mob);mob.setDeltaMovement(Vec3.ZERO);mob.fallDistance=0;
  if(mob.position().distanceToSqr(anchor)>1.0e-10)mob.setPos(anchor.x,anchor.y,anchor.z);
 }
 public static void refresh(Level level){
  if(level.isClientSide)return;
  LOCKS.entrySet().removeIf(e->e.getKey().level()==level&&(!e.getKey().isAlive()||!controlled(e.getKey())));
 }
 @SubscribeEvent(priority=EventPriority.HIGHEST)
 public static void livingTick(LivingEvent.LivingTickEvent event){
  if(event.getEntity() instanceof Mob mob&&controlled(mob)){
   if(!mob.level().isClientSide)hold(mob);else mob.setDeltaMovement(Vec3.ZERO);
   event.setCanceled(true);
  }
 }
 @SubscribeEvent public static void levelTick(TickEvent.LevelTickEvent event){
  Level level=event.level;if(level.isClientSide||event.phase!=TickEvent.Phase.START)return;
  refresh(level);
  Set<Mob> seen=Collections.newSetFromMap(new IdentityHashMap<>());
  for(BlockPos pos:sources(level)){
   if(!level.hasChunkAt(pos))continue;
   if(!(level.getBlockEntity(pos) instanceof StarcrownDoll.DollEntity doll)||doll.isRemoved()||!doll.isPlaying()){SOURCES.get(level).remove(pos);SOURCE_LISTS.remove(level);continue;}
   if(doll.getBlockState().is(StarcrownDoll.CALM_DOLL.get()))continue;
   for(Mob mob:level.getEntitiesOfClass(Mob.class,new AABB(pos).inflate(16)))if(seen.add(mob)&&controlled(mob))hold(mob);
  }
 }
 static Set<net.minecraft.world.entity.LivingEntity> calmFeastTargets(Level level){
  Set<net.minecraft.world.entity.LivingEntity> result=new HashSet<>();
  for(var pos:sources(level))if(level.hasChunkAt(pos)&&level.getBlockEntity(pos) instanceof StarcrownDoll.DollEntity doll
   &&doll.isPlaying()&&!doll.isRemoved()&&doll.getBlockState().is(StarcrownDoll.CALM_DOLL.get())){
   var center=Vec3.atCenterOf(pos);
   result.addAll(level.getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class,new AABB(pos).inflate(12),
    e->e.isAlive()&&!e.isSpectator()&&e.distanceToSqr(center)<=144&&(e instanceof net.minecraft.world.entity.player.Player
      ||e instanceof net.minecraft.world.entity.OwnableEntity pet&&pet.getOwnerUUID()!=null)));
  }
  return result;
 }
}
