package dev.starcrown;
import java.util.*;
import net.minecraft.server.level.*;
import net.minecraft.world.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.item.*;
import net.minecraft.world.effect.*;
import net.minecraft.world.level.storage.loot.*;
import net.minecraft.world.level.storage.loot.parameters.*;
import net.minecraftforge.event.entity.living.*;
import net.minecraftforge.event.entity.player.*;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.fml.common.Mod;
@Mod.EventBusSubscriber(modid=StarcrownDoll.ID)
public final class YuleCharity {
 public static final String DEATH="starcrowndoll_death_loot",PACT="starcrowndoll_charity_owner",CLOCK="starcrowndoll_charity_ticks";
 public static final int RADIUS=16,RESISTANCE_TICKS=1200;
 public static boolean active(Entity e){return YuleHumility.active(e);}
 public static void clearLegacyMark(ItemStack stack){
  if(!stack.hasTag()||!stack.getTag().contains(DEATH))return;
  stack.getTag().remove(DEATH);
  if(stack.getTag().isEmpty())stack.setTag(null);
 }
 public static boolean ordinary(LivingEntity e){return e instanceof AbstractVillager||e instanceof Animal&&!(e instanceof Enemy)&&e.getMobType()!=MobType.UNDEAD&&!YuleChastity.purifiable(e);}
 public static boolean canUse(Player p,Entity e,ItemStack hand){
  if(!(active(p)||CalmYule.active(p))||!hand.isEmpty()||!(e instanceof LivingEntity living)||!living.isAlive())return false;
  if(p.isShiftKeyDown()&&ordinary(living))return true;
  if(living instanceof net.minecraft.world.item.trading.Merchant&&!p.isShiftKeyDown())return false;
  return living.getHealth()<living.getMaxHealth()&&(!CalmYule.active(p)||healable(p,living));
 }
 private static boolean healable(Player player,LivingEntity target){
  return target instanceof Player&&player.isAlliedTo(target)
     ||target instanceof OwnableEntity pet&&player.getUUID().equals(pet.getOwnerUUID());
 }
 public static InteractionResult use(Player p,Entity e,InteractionHand hand){
  if(!canUse(p,e,p.getItemInHand(hand)))return InteractionResult.PASS;
  if(!(p instanceof ServerPlayer player))return InteractionResult.SUCCESS;
  var target=(LivingEntity)e;
  boolean calm=CalmYule.active(player);
  if(player.isShiftKeyDown()&&ordinary(target)&&(calm||target.getHealth()>=target.getMaxHealth())){
   if(calm)calmPact(player,target);
   else{
    var data=target.getPersistentData();
    if(data.hasUUID(PACT)&&data.getUUID(PACT).equals(p.getUUID())){data.remove(PACT);data.remove(CLOCK);}
    else{data.putUUID(PACT,p.getUUID());data.putInt(CLOCK,0);}
    hearts(player,target);
   }
   return InteractionResult.CONSUME;
  }
  if(calm){
   var settings=CalmYule.settings(player);
   long time=TwilightCrown.now(player.serverLevel());
   if(settings.getLong("calm_heal_ready")>time){
    message(player,"仁爱治疗冷却中");
    return InteractionResult.CONSUME;
   }
   target.setHealth(Math.min(target.getMaxHealth(),target.getHealth()+target.getMaxHealth()*.4f));
   target.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE,200,1));
   settings.putLong("calm_heal_ready",time+500);
  }else{
   target.setHealth(target.getMaxHealth());
   target.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE,RESISTANCE_TICKS,4));
  }
  hearts(player,target);
  return InteractionResult.CONSUME;
 }
 private static void hearts(ServerPlayer player,LivingEntity target){
  player.serverLevel().sendParticles(net.minecraft.core.particles.ParticleTypes.HEART,target.getX(),target.getY()+target.getBbHeight(),target.getZ(),4,.25,.2,.25,.01);
 }
 private static void message(ServerPlayer player,String text){player.displayClientMessage(net.minecraft.network.chat.Component.literal(text),true);}
 private static final String CALM_PACTS="calm_pacts";
 private static void calmPact(ServerPlayer player,LivingEntity target){
  var settings=CalmYule.settings(player);
  var pacts=settings.getCompound(CALM_PACTS);
  String id=target.getUUID().toString();
  if(pacts.contains(id)){
   pacts.remove(id);message(player,"已解除此仁爱之约");
  }else if(pacts.size()>=3){
   message(player,"清欢契约已满");return;
  }else{
   pacts.putUUID(id,target.getUUID());
   long now=TwilightCrown.now(player.serverLevel());
   if(pacts.size()==1)settings.putLong("calm_pact_ready",Math.max(now+1200,settings.getLong("calm_pact_ready")));
   message(player,"已缔结仁爱之约");hearts(player,target);
  }
  settings.put(CALM_PACTS,pacts);
 }
 static void calmGifts(ServerPlayer player){
  var settings=CalmYule.settings(player);
  var pacts=settings.getCompound(CALM_PACTS);
  long now=TwilightCrown.now(player.serverLevel());
  if(pacts.isEmpty()||settings.getLong("calm_pact_ready")>now)return;
  var ordered=new ArrayList<>(pacts.getAllKeys());
  Collections.sort(ordered);
  int start=Math.floorMod(settings.getInt("calm_pact_cursor"),ordered.size());
  for(int i=0;i<ordered.size();i++){
   int index=(start+i)%ordered.size();
   var key=ordered.get(index);
   if(!pacts.hasUUID(key)||!(player.serverLevel().getEntity(pacts.getUUID(key)) instanceof Mob mob)
      ||!mob.isAlive()||player.distanceToSqr(mob)>RADIUS*RADIUS||!ordinary(mob))continue;
   var candidates=loot(mob,player,2);
   if(candidates.isEmpty())continue;
   var gift=candidates.get(player.getRandom().nextInt(candidates.size())).copyWithCount(1);
   YuleGenerosity.redirect(player,gift);
   settings.putInt("calm_pact_cursor",index+1);
   settings.putLong("calm_pact_ready",now+1200);
   player.getInventory().setChanged();player.inventoryMenu.broadcastChanges();
   return;
  }
 }
 @SubscribeEvent(priority=EventPriority.LOWEST) public static void clearDeadPact(LivingDeathEvent event){
  var target=event.getEntity();
  if(!(target.level() instanceof ServerLevel level)||!(target instanceof Mob)||!ordinary(target))return;
  for(var player:level.players()){
   var root=CalmYule.existingSettings(player);
   root.getCompound(CALM_PACTS).remove(target.getUUID().toString());
  }
 }
 @SubscribeEvent(priority=EventPriority.HIGHEST) public static void interact(PlayerInteractEvent.EntityInteract e){if(canUse(e.getEntity(),e.getTarget(),e.getItemStack())){var result=use(e.getEntity(),e.getTarget(),e.getHand());e.setCanceled(true);e.setCancellationResult(result);}}
 @SubscribeEvent(priority=EventPriority.HIGHEST) public static void specific(PlayerInteractEvent.EntityInteractSpecific e){if(canUse(e.getEntity(),e.getTarget(),e.getItemStack())){var result=use(e.getEntity(),e.getTarget(),e.getHand());e.setCanceled(true);e.setCancellationResult(result);}}
 private static List<ItemStack> loot(Mob mob,ServerPlayer player,int offers){
  var result=new ArrayList<ItemStack>();
  if(mob instanceof AbstractVillager villager){
   int checked=0;
   for(var offer:villager.getOffers()){
    if(checked++>=offers)break;
    if(!offer.getResult().isEmpty())result.add(offer.getResult().copy());
   }
  }else if(mob instanceof Animal){
   var level=(ServerLevel)mob.level();
   var params=new LootParams.Builder(level).withParameter(LootContextParams.THIS_ENTITY,mob)
      .withParameter(LootContextParams.ORIGIN,mob.position()).withParameter(LootContextParams.DAMAGE_SOURCE,level.damageSources().generic())
      .withOptionalParameter(LootContextParams.LAST_DAMAGE_PLAYER,player).withLuck(player.getLuck()).create(LootContextParamSets.ENTITY);
   for(var stack:level.getServer().getLootData().getLootTable(mob.getLootTable()).getRandomItems(params)){
    if(stack.isEmpty())continue;
    var copy=stack.copy();clearLegacyMark(copy);result.add(copy);
   }
  }
  return result;
 }
 public static List<ItemStack> gifts(Mob mob,ServerPlayer player){
  return YuleDiligence.improveGifts(loot(mob,player,Integer.MAX_VALUE),player,mob instanceof AbstractVillager);
 }
 public static void produce(Mob mob){if(!(mob.level() instanceof ServerLevel level)||!mob.isAlive()||!ordinary(mob))return;var data=mob.getPersistentData();if(!data.hasUUID(PACT))return;var owner=level.getPlayerByUUID(data.getUUID(PACT));if(!(owner instanceof ServerPlayer p)||!active(p)||!p.isAlive()||p.distanceToSqr(mob)>RADIUS*RADIUS)return;int ticks=data.getInt(CLOCK)+1;if(ticks<20){data.putInt(CLOCK,ticks);return;}data.putInt(CLOCK,0);for(var stack:gifts(mob,p))if(!stack.isEmpty())YuleGenerosity.redirect(p,stack);p.getInventory().setChanged();p.inventoryMenu.broadcastChanges();}
 @SubscribeEvent public static void living(LivingEvent.LivingTickEvent e){if(e.getEntity() instanceof Mob mob)produce(mob);}
 private YuleCharity(){}
}
