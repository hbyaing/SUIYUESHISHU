package dev.starcrown;

import java.util.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.memory.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.*;
import net.minecraftforge.common.TierSortingRegistry;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityEvent;
import net.minecraftforge.event.entity.living.*;
import net.minecraftforge.event.entity.player.*;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.fml.common.Mod;

/** The equipped Yule doll is the authority; no permanent player attributes or AI flags are changed. */
@Mod.EventBusSubscriber(modid=StarcrownDoll.ID)
public final class YuleHumility {

 private static final String SIZE_STATE="starcrowndoll_humility_size";
 public static boolean active(Entity entity){return entity instanceof Player p&&p.getInventory()!=null&&DivinityInventory.equipped(p).is(StarcrownDoll.YULE_ITEM.get());}
 public static boolean quietSource(Entity entity){return active(entity)||(entity instanceof Projectile projectile&&active(projectile.getOwner()))||(entity!=null&&active(entity.getControllingPassenger()));}
 public static float capped(float amount){return Float.isFinite(amount)?Math.min(1,Math.max(0,amount)):1;}
 public static float damage(LivingEntity victim,DamageSource source,float amount){return active(victim)?capped(amount):amount;}
 public static float healthWrite(LivingEntity entity,float next){
  if(!active(entity))return next;
  float current=entity.getHealth();if(!Float.isFinite(current)||current<=0)return Float.isFinite(next)?next:0;
  return Float.isFinite(next)?Math.max(next,Math.max(0,current-1)):Math.max(0,current-1);
 }
 public static boolean blockDeath(Entity entity){return active(entity)&&((LivingEntity)entity).getHealth()>0;}
 public static boolean kill(Entity entity){
  if(!(entity instanceof ServerPlayer p)||!active(p))return false;
  p.setHealth(Math.max(0,p.getHealth()-1));
  if(p.getHealth()<=0)p.die(p.damageSources().genericKill());
  return true;
 }
 private static boolean concealedMemory(Object value){
  if(value instanceof Entity entity)return active(entity);
  if(value instanceof DamageSource source)return active(source.getEntity());
  if(value instanceof UUID id){var server=net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();return server!=null&&active(server.getPlayerList().getPlayer(id));}
  return false;
 }
 public static Optional<?> memory(Optional<?> input){
  if(input.isEmpty()||!(input.get() instanceof ExpirableValue<?> entry))return input;
  var value=entry.getValue();if(concealedMemory(value))return Optional.empty();
  if(value instanceof List<?> list){var filtered=list.stream().filter(e->!concealedMemory(e)).toList();if(filtered.size()!=list.size())return Optional.of(ExpirableValue.of(filtered,entry.getTimeToLive()));}
  return input;
 }
 public static void clearTarget(Mob mob){
  if(active(mob.getTarget()))mob.setTarget(null);
  var brain=mob.getBrain();
  for(var type:List.of(MemoryModuleType.ATTACK_TARGET,MemoryModuleType.HURT_BY_ENTITY,MemoryModuleType.NEAREST_VISIBLE_PLAYER,MemoryModuleType.NEAREST_VISIBLE_ATTACKABLE_PLAYER,MemoryModuleType.AVOID_TARGET,MemoryModuleType.ANGRY_AT)){
   if(brain.hasMemoryValue(type)&&brain.getMemory(type).filter(YuleHumility::concealedMemory).isPresent())brain.eraseMemory(type);
  }
 }
 @SubscribeEvent(priority=EventPriority.LOWEST) public static void hurt(LivingHurtEvent event){event.setAmount(damage(event.getEntity(),event.getSource(),event.getAmount()));}
 @SubscribeEvent(priority=EventPriority.LOWEST) public static void damage(LivingDamageEvent event){event.setAmount(damage(event.getEntity(),event.getSource(),event.getAmount()));}
 @SubscribeEvent(priority=EventPriority.LOWEST) public static void target(LivingChangeTargetEvent event){if(active(event.getNewTarget()))event.setNewTarget(null);}
 public static void updateSize(Player player){
  var data=player.getPersistentData();
  if(data.getBoolean(SIZE_STATE)){data.remove(SIZE_STATE);player.refreshDimensions();}
 }
 @SubscribeEvent public static void tick(TickEvent.PlayerTickEvent event){
  if(event.phase!=TickEvent.Phase.START)return;var player=event.player;updateSize(player);
 }
}
