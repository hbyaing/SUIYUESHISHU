package dev.starcrown;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.*;
import net.minecraft.world.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.effect.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.enchantment.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.*;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.fml.common.Mod;
@Mod.EventBusSubscriber(modid=StarcrownDoll.ID)
public final class YuleDiligence {
 public static final String COUNT="starcrowndoll_diligence_units";
 public static final UUID HEALTH=UUID.fromString("86a0918b-b3c1-4bf4-87a2-fce02f850e51"),LUCK=UUID.fromString("76ae3eb9-640d-463c-b276-b7531963bab9");
 public static final int XP=1000;
 private static final Map<Player,State> STATES=Collections.synchronizedMap(new WeakHashMap<>());
 public static final class State {public final List<BlockEvent.EntityPlaceEvent> pending=new ArrayList<>();public Vec3 last;public Object dimension;public double distance;public boolean calm;public long workUntil=Long.MIN_VALUE;public MobEffectInstance haste,speed;public final Map<MobEffect,MobEffectInstance> before=new HashMap<>();public final Map<MobEffect,Long> beforeAt=new HashMap<>();}
 public static State state(Player p){return STATES.computeIfAbsent(p,k->new State());}
 public static boolean active(Entity e){return YuleHumility.active(e);}
 public static long units(Player p){return Math.max(0,p.getPersistentData().getLong(COUNT));}
 public static void refresh(Player p){long n=units(p);if(n==0)return;modifier(p,Attributes.MAX_HEALTH,HEALTH,n*.1);modifier(p,Attributes.LUCK,LUCK,n*.01);}
 private static void modifier(Player p,Attribute a,UUID id,double amount){var value=p.getAttribute(a);if(value==null)return;var old=value.getModifier(id);if(old!=null&&old.getAmount()==amount)return;value.removeModifier(id);value.addPermanentModifier(new AttributeModifier(id,"Diligence permanent growth",amount,AttributeModifier.Operation.ADDITION));}
 /** Calculate only marked growth attributes beyond vanilla's range, preserving every other modifier. */
 public static double attributeValue(AttributeInstance a,double fallback){if(a.getModifier(HEALTH)==null&&a.getModifier(LUCK)==null)return fallback;double base=a.getBaseValue();for(var v:a.getModifiers(AttributeModifier.Operation.ADDITION))base+=v.getAmount();double result=base;for(var v:a.getModifiers(AttributeModifier.Operation.MULTIPLY_BASE))result+=base*v.getAmount();for(var v:a.getModifiers(AttributeModifier.Operation.MULTIPLY_TOTAL))result*=1+v.getAmount();return Double.isFinite(result)?Math.max(fallback,result):Double.MAX_VALUE;}
 public static void reward(ServerPlayer p,int count){if(!active(p)||count<=0)return;long old=units(p),next=old>Long.MAX_VALUE-count?Long.MAX_VALUE:old+count;p.getPersistentData().putLong(COUNT,next);refresh(p);long points=(long)XP*count;int room=Integer.MAX_VALUE-Math.max(0,p.totalExperience);if(room>0&&p.experienceLevel<Integer.MAX_VALUE)p.giveExperiencePoints((int)Math.min(points,room));var s=state(p);s.workUntil=p.level().getGameTime()+2;}
 private static MobEffectInstance boost(Player p,MobEffect type,int amplifier,MobEffectInstance owned){var s=state(p);var current=p.getEffect(type);if(current==owned&&current!=null&&current.getAmplifier()==amplifier&&current.getDuration()>6)return current;if(current!=null&&(current.getAmplifier()>amplifier||current.getAmplifier()==amplifier&&current.getDuration()>12)){s.before.remove(type);s.beforeAt.remove(type);return null;}if(current!=null&&current!=owned&&!s.before.containsKey(type)){s.before.put(type,new MobEffectInstance(current));s.beforeAt.put(type,p.level().getGameTime());}var effect=new MobEffectInstance(type,12,amplifier,true,false,true);p.addEffect(effect);return p.getEffect(type);}
 private static void release(Player p,MobEffect type,MobEffectInstance owned){var s=state(p);var before=s.before.remove(type);var time=s.beforeAt.remove(type);int amplifier=type==MobEffects.DIG_SPEED?(s.calm?3:9):(s.calm?1:2);if(owned!=null&&p.getEffect(type)==owned&&owned.getAmplifier()==amplifier&&owned.getDuration()<=12&&!owned.isInfiniteDuration()){p.removeEffect(type);if(before!=null){var tag=before.save(new net.minecraft.nbt.CompoundTag());var back=MobEffectInstance.load(DivineWill.elapsedEffect(tag,Math.max(0,p.level().getGameTime()-(time==null?p.level().getGameTime():time))));if(back!=null&&(back.isInfiniteDuration()||back.getDuration()>0))p.addEffect(back);}}}
 private static final String CALM_COUNT="calm_work";
 private static final UUID CALM_HEALTH=UUID.fromString("d1094acc-8cd2-4077-b99c-c89165571919"),CALM_LUCK=UUID.fromString("d1094acc-8cd2-4077-b99c-c89165571920");
 static void refreshCalm(Player player){
  long level=Math.min(10,CalmYule.existingSettings(player).getLong(CALM_COUNT)/100);
  if(level>0){modifier(player,Attributes.MAX_HEALTH,CALM_HEALTH,level);modifier(player,Attributes.LUCK,CALM_LUCK,level*.25);}
 }
 static void rewardCalm(ServerPlayer player,int count){
  if(count<=0)return;
  var data=CalmYule.settings(player);
  long old=data.getLong(CALM_COUNT),next=old>Long.MAX_VALUE-count?Long.MAX_VALUE:old+count;
  data.putLong(CALM_COUNT,next);
  if(next/100!=old/100)refreshCalm(player);
  long gained=(next/10-old/10)*20;
  if(gained>0)player.giveExperiencePoints((int)Math.min(gained,Integer.MAX_VALUE-Math.max(0,player.totalExperience)));
 }
 public static void mined(ServerPlayer player,boolean natural){
  if(active(player)){reward(player,1);return;}
  if(!CalmYule.active(player))return;
  state(player).workUntil=player.level().getGameTime()+100;
  if(natural&&!player.isCreative())rewardCalm(player,1);
 }
 private static void releaseWork(Player player,State state){
  release(player,MobEffects.DIG_SPEED,state.haste);release(player,MobEffects.MOVEMENT_SPEED,state.speed);
  state.haste=null;state.speed=null;
 }
 public static void tickPlayer(ServerPlayer player){
  flush(player);
  var state=STATES.get(player);
  boolean calm=CalmYule.active(player);
  if(!(calm||active(player))||!player.isAlive()){
   if(state!=null){releaseWork(player,state);STATES.remove(player);}
   return;
  }
  if(state==null)state=state(player);
  if(state.calm!=calm){releaseWork(player,state);state.distance=0;state.last=null;state.calm=calm;}
  var position=player.position();
  boolean sameWorld=state.last!=null&&state.dimension==player.level().dimension();
  var delta=sameWorld?position.subtract(state.last):Vec3.ZERO;
  state.last=position;state.dimension=player.level().dimension();
  double length=delta.lengthSqr();
  boolean moving=sameWorld&&!player.isPassenger()&&length>1e-6&&length<=64;
  boolean working=(moving||player.swinging)&&!player.isSleeping();
  long now=player.level().getGameTime();
  if(calm&&working)state.workUntil=now+100;
  if(moving&&(!calm||player.onGround()&&!player.getAbilities().flying&&!player.isFallFlying()&&!player.isInWater()&&!player.isCreative())){
   int stride=calm?16:1;
   state.distance+=Math.sqrt(delta.x*delta.x+delta.z*delta.z);
   int units=(int)(state.distance/stride);
   if(units>0){
    state.distance-=units*stride;
    if(calm)rewardCalm(player,units);else reward(player,units);
   }
  }else if(!sameWorld||length>64||player.isPassenger())state.distance=0;
  if((working||now<=state.workUntil)&&!player.isSleeping()){
   state.haste=boost(player,MobEffects.DIG_SPEED,calm?3:9,state.haste);
   state.speed=boost(player,MobEffects.MOVEMENT_SPEED,calm?1:2,state.speed);
  }else releaseWork(player,state);
 }
 @SubscribeEvent public static void tick(TickEvent.PlayerTickEvent e){if(e.phase==TickEvent.Phase.END&&e.player instanceof ServerPlayer p)tickPlayer(p);}
 @SubscribeEvent public static void clone(PlayerEvent.Clone e){e.getEntity().getPersistentData().putLong(COUNT,units(e.getOriginal()));refresh(e.getEntity());}
 @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent e){refresh(e.getEntity());refreshCalm(e.getEntity());}
 @SubscribeEvent(priority=EventPriority.LOWEST) public static void mine(PlayerInteractEvent.LeftClickBlock e){if(!(e.getEntity() instanceof ServerPlayer p)||!active(p)||e.getAction()!=PlayerInteractEvent.LeftClickBlock.Action.START||!p.serverLevel().mayInteract(p,e.getPos())||!p.canReach(e.getPos(),1)||p.level().getBlockState(e.getPos()).isAir()||e.getUseBlock()==Event.Result.DENY||e.getUseItem()==Event.Result.DENY)return;p.gameMode.destroyBlock(e.getPos());e.setCanceled(true);p.connection.send(new net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket(p.level(),e.getPos()));}
 @SubscribeEvent(priority=EventPriority.LOWEST) public static void placed(BlockEvent.EntityPlaceEvent e){if(e.getEntity() instanceof ServerPlayer p&&(active(p)||CalmYule.active(p)))state(p).pending.add(e);}
 public static void flush(ServerPlayer p){var s=STATES.get(p);if(s==null||s.pending.isEmpty())return;var pending=new ArrayList<>(s.pending);s.pending.clear();for(var event:pending)if(!event.isCanceled()){int count=event instanceof BlockEvent.EntityMultiPlaceEvent multi?multi.getReplacedBlockSnapshots().size():1;if(CalmYule.active(p))s.workUntil=p.level().getGameTime()+100;else reward(p,count);}}
 @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent e){var state=STATES.get(e.getEntity());if(state!=null)releaseWork(e.getEntity(),state);STATES.remove(e.getEntity());}
 public static List<ItemStack> improveGifts(List<ItemStack> input,ServerPlayer p,boolean villager){double luck=Math.max(0,p.getLuck());if(luck==0)return input;var result=new ArrayList<ItemStack>();double bonus=luck/(luck+100);for(var original:input){var stack=original.copy();if(villager){var enchants=EnchantmentHelper.getEnchantments(stack);if(!enchants.isEmpty()){var improved=new LinkedHashMap<Enchantment,Integer>();for(var entry:enchants.entrySet())improved.put(entry.getKey(),Math.max(entry.getValue(),(int)Math.min(entry.getKey().getMaxLevel(),entry.getValue()+Math.floor(luck/10))));EnchantmentHelper.setEnchantments(improved,stack);}else if(stack.isEnchantable()||stack.is(Items.BOOK))stack=EnchantmentHelper.enchantItem(p.getRandom(),stack,(int)Math.min(30,Math.max(1,luck)),true);}result.add(stack);if(p.getRandom().nextDouble()<bonus)result.add(stack.copy());}return result;}
 private YuleDiligence(){}
}
