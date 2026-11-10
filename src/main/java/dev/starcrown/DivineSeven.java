package dev.starcrown;

import java.util.*;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.effect.*;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.*;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.*;
import net.minecraftforge.event.entity.EntityTeleportEvent;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.fml.common.Mod;

/** Server-authoritative rules, verdicts and ally rescue. */
@Mod.EventBusSubscriber(modid=StarcrownDoll.ID)
public final class DivineSeven {
 public static final int POTION_LEVEL_ACTION=9, DISABLE_POTIONS_ACTION=14;
 public static final String ROOT="starcrown_seven",MARK="starcrown_verdict";
 public static CompoundTag settings(Player p){if(!p.getPersistentData().contains(ROOT))p.getPersistentData().put(ROOT,new CompoundTag());return p.getPersistentData().getCompound(ROOT);}
 public static int miningIndex(Player p){return Math.max(0,Math.min(3,settings(p).getInt("mining_range")));}
 public static int miningRadius(Player p){return miningIndex(p)+1;}
 public static double speed(Player p){return new double[]{1,1.5,2,3,5}[Math.max(0,Math.min(4,settings(p).contains("speed")?settings(p).getInt("speed"):1))];}
 public static void release(ServerPlayer p){var data=settings(p);data.putBoolean("domain",false);data.putBoolean("restoring",false);data.remove("last_position");}
 public static boolean ally(ServerPlayer p,LivingEntity e){return e==p||p.isAlliedTo(e)||(e instanceof TamableAnimal a&&p.getUUID().equals(a.getOwnerUUID()))||(e instanceof net.minecraft.world.entity.animal.horse.AbstractHorse a&&p.getUUID().equals(a.getOwnerUUID()))||settings(p).getCompound("friends").contains(e.getUUID().toString());}
 public static boolean hostile(ServerPlayer p,Entity e){if(e instanceof LivingEntity l){if(ally(p,l)||e instanceof Player)return false;return e instanceof Enemy||owner(l)==p||(e instanceof Mob m&&m.getTarget()!=null&&ally(p,m.getTarget()));}return false;}
 public static ServerPlayer owner(LivingEntity target){if(!target.getPersistentData().contains(MARK))return null;var tag=target.getPersistentData().getCompound(MARK);if(!tag.hasUUID("owner")||!(target.level() instanceof ServerLevel l))return null;var player=l.getPlayerByUUID(tag.getUUID("owner"));return player instanceof ServerPlayer p&&p.level()==l&&DivineWill.wearing(p)&&!ally(p,target)?p:null;}
 public static void mark(ServerPlayer p,LivingEntity target){if(target instanceof Player||ally(p,target))return;var root=target.getPersistentData();if(owner(target)!=p){var tag=new CompoundTag();tag.putUUID("owner",p.getUUID());tag.putFloat("minimum",target.getHealth());root.put(MARK,tag);}}
 public static ServerPlayer domain(Entity e){if(!(e.level() instanceof ServerLevel level))return null;for(var p:level.players())if(DivineWill.wearing(p)&&settings(p).getBoolean("domain")&&p.distanceToSqr(e)<=4096)return p;return null;}
 public static boolean ruled(Entity e){var p=domain(e);return p!=null&&hostile(p,e);}
 public static float healthWrite(LivingEntity e,float value){var p=owner(e);if(p!=null){var tag=e.getPersistentData().getCompound(MARK);if(tag.getBoolean("defeated"))return 0;float cap=tag.getFloat("minimum");if(tag.getBoolean("retaliation")&&Float.isFinite(value))return Math.min(value,cap);}if(e.isAddedToWorld()&&ruled(e)&&Float.isFinite(value))return Math.min(value,e.getHealth());return value;}
 public static boolean rescue(LivingEntity e){if(!(e.level() instanceof ServerLevel level))return false;
  for(var p:level.players())if(p!=e&&DivineWill.wearing(p)&&p.distanceToSqr(e)<=4096&&ally(p,e)&&(!(e instanceof Player)||!settings(p).getBoolean("players_off"))){
   e.getPersistentData().putLong("starcrown_ally_invulnerable",level.getGameTime()+60);e.setHealth(Math.max(1,Math.min(6,e.getMaxHealth())));e.deathTime=0;e.clearFire();e.setTicksFrozen(0);
   for(var effect:new ArrayList<>(e.getActiveEffects()))if(effect.getEffect().getCategory()!=MobEffectCategory.BENEFICIAL)e.removeEffect(effect.getEffect());
   e.addEffect(new MobEffectInstance(MobEffects.REGENERATION,900,1));e.addEffect(new MobEffectInstance(MobEffects.ABSORPTION,100,1));e.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE,800,0));level.broadcastEntityEvent(e,(byte)35);return true;
  }return false;
 }
 @SubscribeEvent(priority=EventPriority.HIGHEST,receiveCanceled=true) public static void attacked(LivingAttackEvent e){
  var victim=e.getEntity();var source=e.getSource().getEntity();attackAttempt(victim,source);
  if(victim.level() instanceof ServerLevel level&&victim.getPersistentData().getLong("starcrown_ally_invulnerable")>level.getGameTime())e.setCanceled(true);
  if(source!=null&&ruled(source))e.setCanceled(true);
 }
 public static void attackAttempt(LivingEntity victim,Entity source){if(source!=null)DollAuthority.onAttack(source,victim);if(victim instanceof ServerPlayer p&&DivineWill.wearing(p)&&source instanceof LivingEntity l){mark(p,l);if(owner(l)==p)l.getPersistentData().getCompound(MARK).putBoolean("retaliation",true);}}
 @SubscribeEvent(priority=EventPriority.LOWEST) public static void damage(LivingDamageEvent e){
  var target=e.getEntity();if(DivineCost.pulsing())return;if(e.getSource().getEntity() instanceof ServerPlayer p&&DivineWill.wearing(p)&&!(target instanceof Player)&&!ally(p,target)){
   var tag=target.getPersistentData().getCompound(MARK);if(owner(target)==p&&tag.getBoolean("retaliation")){int hits=tag.getInt("hits")+1;tag.putInt("hits",hits);target.setAbsorptionAmount(0);target.stopUsingItem();float bonus=target.getMaxHealth()*.25f;e.setAmount(hits>=4?Math.max(e.getAmount(),target.getHealth()+target.getMaxHealth()):e.getAmount()+bonus);}
   DivineCost.prepare(target,p);
   if(owner(target)!=p){mark(p,target);tag=target.getPersistentData().getCompound(MARK);}tag.putDouble("damage",Math.min(1e12,tag.getDouble("damage")+Math.max(0,e.getAmount())));tag.putFloat("minimum",Math.max(0,target.getHealth()-e.getAmount()));
  }
 }
 @SubscribeEvent(priority=EventPriority.HIGHEST) public static void death(LivingDeathEvent e){if(rescue(e.getEntity())){e.setCanceled(true);return;}var p=owner(e.getEntity());if(p!=null){e.getEntity().getPersistentData().getCompound(MARK).putBoolean("defeated",true);DivineHistory.get(p.serverLevel()).defeat(e.getEntity().getUUID());}}
 @SubscribeEvent(priority=EventPriority.HIGHEST) public static void heal(LivingHealEvent e){if((owner(e.getEntity())!=null&&e.getEntity().getPersistentData().getCompound(MARK).getBoolean("retaliation"))||ruled(e.getEntity()))e.setCanceled(true);}
 @SubscribeEvent(priority=EventPriority.HIGHEST) public static void totem(LivingUseTotemEvent e){if(owner(e.getEntity())!=null||ruled(e.getEntity()))e.setCanceled(true);}
 @SubscribeEvent(priority=EventPriority.HIGHEST) public static void teleport(EntityTeleportEvent e){if(ruled(e.getEntity()))e.setCanceled(true);}
 @SubscribeEvent(priority=EventPriority.HIGHEST) public static void summon(MobSpawnEvent.FinalizeSpawn e){if(e.getSpawnType()==MobSpawnType.MOB_SUMMONED&&ruled(e.getEntity()))e.setSpawnCancelled(true);}
 @SubscribeEvent public static void join(net.minecraftforge.event.entity.EntityJoinLevelEvent e){if(e.getLevel() instanceof ServerLevel l&&e.getEntity() instanceof LivingEntity&&(DivineHistory.get(l).defeated(e.getEntity().getUUID())||(!e.loadedFromDisk()&&ruled(e.getEntity()))))e.setCanceled(true);}
 @SubscribeEvent public static void tick(TickEvent.PlayerTickEvent e){if(e.phase!=TickEvent.Phase.END||!(e.player instanceof ServerPlayer p)||!DivineWill.wearing(p))return;
  if(p.tickCount%5==0){var box=p.getBoundingBox().inflate(64);if(settings(p).getBoolean("domain")){
   for(var entity:p.serverLevel().getEntities(p,box)){if(p.distanceToSqr(entity)>4096)continue;if(entity instanceof Projectile shot&&shot.getOwner()!=p&&(!(shot.getOwner() instanceof LivingEntity l)||!ally(p,l)))shot.discard();else if(hostile(p,entity)){entity.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);if(entity instanceof LivingEntity l){l.stopUsingItem();l.setSprinting(false);l.setAbsorptionAmount(0);}if(entity instanceof Mob m)m.getNavigation().stop();}}
  }
  for(var target:p.serverLevel().getEntitiesOfClass(LivingEntity.class,box))if(owner(target)==p){var tag=target.getPersistentData().getCompound(MARK);tag.putFloat("minimum",Math.min(tag.getFloat("minimum"),target.getHealth()));target.setAbsorptionAmount(0);}
  }
  DivineTravel.tick(p);
 }
 public static LivingEntity aimed(ServerPlayer p){var eye=p.getEyePosition();var end=eye.add(p.getLookAngle().scale(64));LivingEntity best=null;double nearest=4096;var wall=p.level().clip(new net.minecraft.world.level.ClipContext(eye,end,net.minecraft.world.level.ClipContext.Block.COLLIDER,net.minecraft.world.level.ClipContext.Fluid.NONE,p));nearest=eye.distanceToSqr(wall.getLocation());for(var e:p.serverLevel().getEntitiesOfClass(LivingEntity.class,p.getBoundingBox().expandTowards(p.getLookAngle().scale(64)).inflate(1))){if(e==p)continue;var hit=e.getBoundingBox().inflate(.2).clip(eye,end);if(hit.isPresent()&&eye.distanceToSqr(hit.get())<nearest){nearest=eye.distanceToSqr(hit.get());best=e;}}return best;}
 public static void action(ServerPlayer p,int action,int value){if(action>=CalmYule.PEACE_ACTION){if(value==0)CalmYule.action(p,action);return;}if(TwilightCrown.wearing(p)){TwilightCrown.action(p,action,value);return;}if(!DivineWill.wearing(p))return;var data=settings(p);String message="";switch(action){
  case 0 -> {data.putBoolean("domain",!data.getBoolean("domain"));message="万象领域："+(data.getBoolean("domain")?"开启":"关闭");}
  case 1 -> {data.putInt("speed",Math.max(0,Math.min(4,value)));DivineWill.update(p,true);message="移动速度 ×"+speed(p);}
  case 2 -> {PotionFilter.set(p,net.minecraftforge.registries.ForgeRegistries.MOB_EFFECTS.getKey(net.minecraft.world.effect.MobEffects.NIGHT_VISION),!PotionFilter.enabled(p,net.minecraft.world.effect.MobEffects.NIGHT_VISION));message="夜视："+(PotionFilter.enabled(p,net.minecraft.world.effect.MobEffects.NIGHT_VISION)?"开启":"关闭");}
  case 3 -> {data.putBoolean("area_mine",!data.getBoolean("area_mine"));message="范围采掘："+(data.getBoolean("area_mine")?"开启":"关闭");}
  case 4 -> {message=DivineTravel.blink(p)?"群星已为你开路":"没有找到已加载的安全落点";}
  case 5 -> {message=DivineTravel.save(p,Math.max(0,Math.min(7,value)))?"神性锚点已记录":"当前位置不安全";}
  case 6 -> {message=DivineTravel.anchor(p,Math.max(0,Math.min(7,value)))?"已返回神性锚点":"锚点未记录或落点不安全";}
  case 7 -> {message=DivineTravel.back(p,false)?"已返回传送前位置":"没有安全的返回位置";}
  case 8 -> {message=DivineTravel.back(p,true)?"已返回死亡地点附近":"没有可用的死亡地点";}
  case POTION_LEVEL_ACTION -> {if(value<PotionFilter.MIN_LEVEL||value>PotionFilter.MAX_LEVEL)return;PotionFilter.setLevel(p,value);message="正面药水获取等级 "+value;}
  case 11 -> {data.putBoolean("players_off",!data.getBoolean("players_off"));message="玩家庇护："+(!data.getBoolean("players_off")?"开启":"关闭");}
  case 12 -> {var target=aimed(p);if(target!=null){var friends=data.getCompound("friends");String key=target.getUUID().toString();if(friends.contains(key)){friends.remove(key);message="已移出庇护名单";}else{friends.putBoolean(key,true);message="已加入庇护名单";}data.put("friends",friends);}else message="请瞄准64格内的生物或玩家";}
  case 13 -> {var target=aimed(p);if(target!=null&&owner(target)==p){target.setHealth(target.getPersistentData().getCompound(MARK).getFloat("minimum"));message="岁月已兑现战果";}else message="请瞄准已记录的敌人";}
  case DISABLE_POTIONS_ACTION -> {if(value!=0)return;PotionFilter.disableAll(p);message="所有正面药水效果已关闭";}
  case 18 -> {data.putInt("mining_range",Math.max(0,Math.min(3,value)));int size=miningRadius(p)*2+1;message="采掘范围："+size+"×"+size+"×"+size;}
  case 17 -> {var escrow=p.getPersistentData().getList("starcrown_confiscated_dolls",net.minecraft.nbt.Tag.TAG_COMPOUND);int count=0;for(int i=0;i<escrow.size();){var item=net.minecraft.world.item.ItemStack.of(escrow.getCompound(i).getCompound("item"));if(item.isEmpty()){escrow.remove(i);continue;}if(!p.getInventory().add(item)){escrow.getCompound(i).put("item",item.save(new net.minecraft.nbt.CompoundTag()));break;}escrow.remove(i);count++;}p.getPersistentData().put("starcrown_confiscated_dolls",escrow);message="已领取 "+count+" 个被没收的玩偶";}
  case 15 -> {int count=0;for(var target:p.serverLevel().getEntitiesOfClass(Mob.class,p.getBoundingBox().inflate(64)))if(p.distanceToSqr(target)<=4096&&hostile(p,target)){var direction=target.position().subtract(p.position());if(direction.lengthSqr()<.01)direction=new Vec3(1,0,0);var destination=p.position().add(direction.normalize().scale(68));if(DivineTravel.safe(target,p.serverLevel(),destination)){target.moveTo(destination.x,destination.y,destination.z);count++;}}message="已驱逐 "+count+" 个敌人";}
  default -> {return;}
 }p.displayClientMessage(Component.literal(message),true);}
}
