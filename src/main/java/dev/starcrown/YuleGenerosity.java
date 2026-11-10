package dev.starcrown;
import java.util.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.server.level.*;
import net.minecraft.world.item.*;
import net.minecraft.world.effect.*;
import net.minecraft.nbt.*;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.item.ItemTossEvent;
import net.minecraftforge.event.entity.living.*;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.fml.common.Mod;
@Mod.EventBusSubscriber(modid=StarcrownDoll.ID)
public final class YuleGenerosity {
 public static final String BLESSED="starcrowndoll_generosity_blessed",DONOR="starcrowndoll_gift_donor",CALM_DONOR="starcrowndoll_calm_gift_donor";
 private static final ThreadLocal<LivingEntity> GRANT=new ThreadLocal<>();
 private static final Map<AttributeInstance,Boolean> BOOSTED=Collections.synchronizedMap(new WeakHashMap<>());
 private static final Map<LivingEntity,Integer> BOUND=Collections.synchronizedMap(new WeakHashMap<>());
 private static final Map<ServerPlayer,Integer> SENT=Collections.synchronizedMap(new WeakHashMap<>());
 public static boolean active(Entity e){return YuleHumility.active(e);}
 public static boolean boosted(AttributeInstance instance){return BOOSTED.containsKey(instance);}
 public static void bind(LivingEntity e){
  if(!e.getEntityData().get(GenerosityData.BLESSED))return;
  if(BOUND.containsKey(e)&&e.tickCount%100!=0)return;
  for(var attribute:net.minecraftforge.registries.ForgeRegistries.ATTRIBUTES){var instance=e.getAttribute(attribute);if(instance!=null)BOOSTED.put(instance,true);}
  BOUND.put(e,e.tickCount);
 }
 public static void mark(ItemEntity gift,UUID owner){gift.getPersistentData().putUUID(DONOR,owner);gift.setThrower(owner);}
 public static ItemStack upgrade(ItemStack original){
  Item result=GenerosityUpgrades.result(original);
  if(result==null||result==original.getItem())return original.copy();
  var upgraded=new ItemStack(result,original.getCount());if(original.hasTag())upgraded.setTag(original.getTag().copy());return upgraded;
 }
 static boolean gift(ServerPlayer player,boolean calm){
  var settings=CalmYule.settings(player);
  long now=TwilightCrown.now(player.serverLevel());
  if(calm&&settings.getLong("calm_gift_ready")>now){
   player.displayClientMessage(net.minecraft.network.chat.Component.literal("赠礼冷却中"),true);
   return false;
  }
  var inventory=player.getInventory();
  int slot=inventory.selected;
  var selected=inventory.getItem(slot);
  if(selected.isEmpty())return false;
  int count=calm?selected.getCount():1;
  var original=selected.copyWithCount(count);
  var output=calm?original.copy():upgrade(original);
  var gift=new ItemEntity(player.level(),player.getX(),player.getEyeY()-.3,player.getZ(),output);
  gift.setPickUpDelay(40);
  gift.setDeltaMovement(player.getLookAngle().scale(.3).add(0,.08,0));
  if(calm){gift.getPersistentData().putUUID(CALM_DONOR,player.getUUID());gift.setThrower(player.getUUID());}
  else mark(gift,player.getUUID());
  if(net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(new ItemTossEvent(gift,player))
    ||calm&&!ItemStack.matches(gift.getItem(),original))return false;
  if(inventory.getItem(slot)!=selected||selected.getCount()<count||!ItemStack.isSameItemSameTags(selected,original))return false;
  selected.shrink(count);
  if(!player.serverLevel().addFreshEntity(gift)){
   if(inventory.getItem(slot)==selected)selected.grow(count);
   else redirect(player,original);
   return false;
  }
  if(calm)settings.putLong("calm_gift_ready",now+2400);
  inventory.setChanged();player.containerMenu.broadcastChanges();
  return true;
 }
 private static boolean dropOverflow(ServerPlayer player,ItemStack stack){
  var item=new ItemEntity(player.level(),player.getX(),player.getY(),player.getZ(),stack.copy());
  item.setPickUpDelay(20);
  return player.serverLevel().addFreshEntity(item);
 }
 private static final String PENDING="starcrowndoll_pending_scatter";
 private static void escrow(ServerPlayer player,ItemStack stack){
  var list=player.getPersistentData().getList(PENDING,Tag.TAG_COMPOUND);
  list.add(stack.save(new CompoundTag()));
  player.getPersistentData().put(PENDING,list);
 }
 public static void redirect(ServerPlayer player,ItemStack incoming){
  var stack=incoming.copy();
  player.getInventory().add(stack);
  if(!stack.isEmpty()&&!dropOverflow(player,stack))escrow(player,stack);
 }
 private static void settleEscrow(ServerPlayer player){
  var pending=player.getPersistentData().getList(PENDING,Tag.TAG_COMPOUND);
  if(pending.isEmpty())return;
  var remaining=new ListTag();
  for(var saved:pending){
   var stack=ItemStack.of((CompoundTag)saved);
   player.getInventory().add(stack);
   if(!stack.isEmpty()&&!dropOverflow(player,stack))remaining.add(stack.save(new CompoundTag()));
  }
  if(remaining.isEmpty())player.getPersistentData().remove(PENDING);
  else player.getPersistentData().put(PENDING,remaining);
 }
 public static void blessing(LivingEntity e){
  if(e.level().isClientSide)return;
  e.getPersistentData().putBoolean(BLESSED,true);e.getEntityData().set(GenerosityData.BLESSED,true);bind(e);
  var current=e.getEffect(MobEffects.HERO_OF_THE_VILLAGE);
  if(current==null||current.getAmplifier()<9||current.getAmplifier()==9&&!current.isInfiniteDuration()){
   GRANT.set(e);try{e.addEffect(new MobEffectInstance(MobEffects.HERO_OF_THE_VILLAGE,-1,9,false,true,true));}finally{GRANT.remove();}
  }
  if(e instanceof ServerPlayer p)GenerosityRewards.get(p.getServer()).grant(p.getUUID());
 }
 public static void received(LivingEntity receiver,ItemEntity gift,int count){
  if(receiver.level().isClientSide||count<=0)return;
  var data=gift.getPersistentData();
  if(data.hasUUID(CALM_DONOR)){
   if(!(receiver instanceof ServerPlayer player))return;
   var donor=data.getUUID(CALM_DONOR);
   if(donor.equals(player.getUUID()))return;
   data.remove(CALM_DONOR);
   long until=TwilightCrown.now(player.serverLevel())+1200;
   calmBlessing(player,1200);
   var server=player.getServer();
   var giver=server.getPlayerList().getPlayer(donor);
   if(giver==null&&player.serverLevel().getPlayerByUUID(donor) instanceof ServerPlayer local)giver=local;
   if(giver!=null)calmBlessing(giver,1200);
   else GenerosityRewards.get(server).grantCalm(donor,until);
   return;
  }
  if(!data.hasUUID(DONOR))return;
  UUID donor=data.getUUID(DONOR);if(donor.equals(receiver.getUUID()))return;
  blessing(receiver);var server=receiver.level().getServer();if(server!=null){GenerosityRewards.get(server).grant(donor);var online=server.getPlayerList().getPlayer(donor);if(online!=null)blessing(online);}
 }
 private static void calmBlessing(ServerPlayer player,int duration){player.addEffect(new MobEffectInstance(MobEffects.HERO_OF_THE_VILLAGE,duration,1));}
 @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent event){
  if(event.getEntity() instanceof ServerPlayer player){
   long ticks=GenerosityRewards.get(player.getServer()).claimCalm(player.getUUID())-TwilightCrown.now(player.serverLevel());
   if(ticks>0)calmBlessing(player,(int)Math.min(1200,ticks));
  }
 }
 @SubscribeEvent(priority=EventPriority.LOWEST) public static void applicable(MobEffectEvent.Applicable event){if(GRANT.get()==event.getEntity()&&event.getEffectInstance().getEffect()==MobEffects.HERO_OF_THE_VILLAGE)event.setResult(Event.Result.ALLOW);}
 @SubscribeEvent public static void joined(EntityJoinLevelEvent event){if(event.getLevel() instanceof ServerLevel&&event.getEntity() instanceof LivingEntity living&&(living.getPersistentData().getBoolean(BLESSED)||living instanceof ServerPlayer p&&GenerosityRewards.get(p.getServer()).has(p.getUUID())))blessing(living);}
 @SubscribeEvent public static void clone(PlayerEvent.Clone event){
  var original=event.getOriginal().getPersistentData();var clone=event.getEntity().getPersistentData();
  if(original.getBoolean(BLESSED))clone.putBoolean(BLESSED,true);
  if(original.contains(PENDING))clone.put(PENDING,original.getList(PENDING,Tag.TAG_COMPOUND).copy());
 }
 @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent event){if(event.getEntity() instanceof ServerPlayer p)SENT.remove(p);}
 @SubscribeEvent(priority=EventPriority.LOWEST,receiveCanceled=true) public static void living(LivingEvent.LivingTickEvent event){var e=event.getEntity();if(e.level().isClientSide){bind(e);return;}if(!(e instanceof Player)&&e.getPersistentData().getBoolean(BLESSED))blessing(e);}
 @SubscribeEvent public static void player(TickEvent.PlayerTickEvent event){
  if(event.phase!=TickEvent.Phase.END)return;var p=event.player;if(p.level().isClientSide){bind(p);return;}if(!(p instanceof ServerPlayer server))return;
  if(p.getPersistentData().getBoolean(BLESSED)||GenerosityRewards.get(server.getServer()).has(p.getUUID())){
   blessing(p);
   // Vanilla entity trackers broadcast metadata to observers, not the owning player.
   var last=SENT.get(server);if(server.connection!=null&&(last==null||p.tickCount-last>=100||p.tickCount<last)){
    server.connection.send(new net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket(p.getId(),List.of(net.minecraft.network.syncher.SynchedEntityData.DataValue.create(GenerosityData.BLESSED,true))));SENT.put(server,p.tickCount);
   }
  }
  if(p.tickCount%20==0)settleEscrow(server);
 }
}
