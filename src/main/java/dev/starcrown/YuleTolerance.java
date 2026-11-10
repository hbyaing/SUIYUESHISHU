package dev.starcrown;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.monster.*;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.damagesource.*;
import net.minecraft.world.effect.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.*;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.level.*;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid=StarcrownDoll.ID)
public final class YuleTolerance {
 public static final String FRIEND="starcrowndoll_peaceful",HARMLESS="starcrowndoll_harmless",OWNER="starcrowndoll_tolerance_owner";
 public static final int RADIUS=48,GRACE_TICKS=200;
 private static final ThreadLocal<LivingEntity> GRACE=new ThreadLocal<>();
 public static boolean active(Entity e){return YuleHumility.active(e);}
 public static boolean friendly(Entity e){return e!=null&&e.getPersistentData().getBoolean(FRIEND);}
 public static boolean inside(ServerLevel level,Entity entity){for(var p:level.players())if(active(p)&&CalmYule.peaceEnabled(p)){double dx=p.getX()-entity.getX(),dz=p.getZ()-entity.getZ();if(dx*dx+dz*dz<=RADIUS*RADIUS)return true;}return false;}
 public static boolean hostile(Mob mob){return mob instanceof Enemy||mob.getType().getCategory()==MobCategory.MONSTER;}
 public static void grace(LivingEntity entity){if(entity!=null&&entity.isAlive()){GRACE.set(entity);try{entity.addEffect(new MobEffectInstance(MobEffects.REGENERATION,GRACE_TICKS,9,false,true,true));entity.addEffect(new MobEffectInstance(MobEffects.ABSORPTION,GRACE_TICKS,9,false,true,true));}finally{GRACE.remove();}}}
 @SubscribeEvent(priority=EventPriority.LOWEST) public static void applicable(MobEffectEvent.Applicable event){if(GRACE.get()==event.getEntity()&&(event.getEffectInstance().getEffect()==MobEffects.REGENERATION||event.getEffectInstance().getEffect()==MobEffects.ABSORPTION))event.setResult(Event.Result.ALLOW);}
 public static boolean blockDamage(Entity victim,DamageSource source){
  if(victim.level().isClientSide)return false;
  Entity attacker=source.getEntity();
  if(attacker instanceof Projectile shot&&shot.getOwner()!=null)attacker=shot.getOwner();
  if(active(victim)&&attacker instanceof LivingEntity living&&living!=victim){
   grace((LivingEntity)victim);grace(living);return true;
  }
  return friendly(attacker)||friendly(source.getDirectEntity());
 }
 public static void pacify(Mob mob){
  mob.getPersistentData().putBoolean(FRIEND,true);mob.setTarget(null);mob.setLastHurtByMob(null);mob.setAggressive(false);mob.setCanPickUpLoot(true);
  mob.targetSelector.removeAllGoals(g->true);
  mob.goalSelector.removeAllGoals(g->g instanceof net.minecraft.world.entity.ai.goal.MeleeAttackGoal||g instanceof net.minecraft.world.entity.ai.goal.RangedAttackGoal||g instanceof net.minecraft.world.entity.ai.goal.RangedBowAttackGoal||g instanceof net.minecraft.world.entity.ai.goal.RangedCrossbowAttackGoal);
  var gear=mob.getPersistentData().getCompound("starcrowndoll_peace_weapons");
  for(var slot:List.of(EquipmentSlot.MAINHAND,EquipmentSlot.OFFHAND)){var stack=mob.getItemBySlot(slot);if(!stack.isEmpty()){if(!gear.contains(slot.getName()))gear.put(slot.getName(),stack.save(new net.minecraft.nbt.CompoundTag()));mob.setItemSlot(slot,ItemStack.EMPTY);}}
  mob.getPersistentData().put("starcrowndoll_peace_weapons",gear);clearBrain(mob);
 }
 public static void clearBrain(Mob mob){
  mob.setTarget(null);mob.setLastHurtByMob(null);mob.setAggressive(false);
  var brain=mob.getBrain();for(var memory:List.of(MemoryModuleType.ATTACK_TARGET,MemoryModuleType.HURT_BY_ENTITY,MemoryModuleType.ANGRY_AT))brain.eraseMemory(memory);
  if(mob instanceof NeutralMob neutral){neutral.setRemainingPersistentAngerTime(0);neutral.setPersistentAngerTarget(null);}
 }
 public static Villager villager(Zombie zombie,ServerLevel level){
  var v=EntityType.VILLAGER.create(level);if(v==null)return null;
  v.moveTo(zombie.getX(),zombie.getY(),zombie.getZ(),zombie.getYRot(),zombie.getXRot());v.setBaby(zombie.isBaby());v.setCustomName(zombie.getCustomName());v.setCustomNameVisible(zombie.isCustomNameVisible());v.setPersistenceRequired();
  if(zombie instanceof ZombieVillager zv)v.setVillagerData(zv.getVillagerData());
  v.getPersistentData().putBoolean(FRIEND,true);return v;
 }
 @SubscribeEvent(priority=EventPriority.HIGHEST) public static void joined(EntityJoinLevelEvent event){
  if(!(event.getLevel() instanceof ServerLevel level))return;var e=event.getEntity();
  if(e instanceof Mob mob){
   if(YuleChastity.inside(mob))return;
   if(friendly(mob)){pacify(mob);return;}
   if(hostile(mob)&&inside(level,mob)){
    if(mob instanceof Zombie z&&!(mob instanceof ZombifiedPiglin)){var v=villager(z,level);if(v!=null){event.setCanceled(true);level.addFreshEntity(v);return;}}
    pacify(mob);
   }
  }
 }
 @SubscribeEvent(priority=EventPriority.HIGHEST) public static void hit(LivingAttackEvent event){if(blockDamage(event.getEntity(),event.getSource()))event.setCanceled(true);}
 @SubscribeEvent(priority=EventPriority.LOWEST) public static void hurt(LivingHurtEvent event){if(blockDamage(event.getEntity(),event.getSource())){event.setAmount(0);event.setCanceled(true);}}
 @SubscribeEvent(priority=EventPriority.LOWEST) public static void damage(LivingDamageEvent event){if(blockDamage(event.getEntity(),event.getSource())){event.setAmount(0);event.setCanceled(true);}}
 @SubscribeEvent(priority=EventPriority.LOWEST) public static void target(LivingChangeTargetEvent event){if(friendly(event.getEntity())||friendly(event.getNewTarget()))event.setNewTarget(null);}
 @SubscribeEvent public static void grief(net.minecraftforge.event.entity.EntityMobGriefingEvent event){if(friendly(event.getEntity()))event.setResult(Event.Result.DENY);}
 @SubscribeEvent public static void living(LivingEvent.LivingTickEvent event){if(!event.getEntity().level().isClientSide&&event.getEntity() instanceof Mob mob&&friendly(mob))clearBrain(mob);}
 @SubscribeEvent public static void player(TickEvent.PlayerTickEvent event){
  if(event.phase!=TickEvent.Phase.END||!(event.player instanceof net.minecraft.server.level.ServerPlayer p)||!active(p)||!CalmYule.peaceEnabled(p)||p.tickCount%20!=0)return;
  var level=p.serverLevel();var box=new AABB(p.getX()-RADIUS,level.getMinBuildHeight(),p.getZ()-RADIUS,p.getX()+RADIUS,level.getMaxBuildHeight(),p.getZ()+RADIUS);
  for(var mob:level.getEntitiesOfClass(Mob.class,box,m->hostile(m)&&!friendly(m)&&inside(level,m)&&!YuleChastity.inside(m))){
   if(mob instanceof Zombie z&&!(mob instanceof ZombifiedPiglin)){var v=villager(z,level);if(v!=null&&level.addFreshEntity(v)){mob.discard();continue;}}
   pacify(mob);
  }
 }
}
