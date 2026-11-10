package dev.starcrownqa;

import dev.starcrown.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.boss.enderdragon.phases.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.SculkShriekerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraftforge.gametest.*;

@GameTestHolder("starcrownqa")
@PrefixGameTestTemplate(false)
public final class HumilityTests {
 private static ServerPlayer wearer(GameTestHelper helper){
  var level=helper.getLevel();var p=new ServerPlayer(level.getServer(),level,new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(),"humility-test"));
  var connection=new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
  new io.netty.channel.embedded.EmbeddedChannel(connection);
  connection.setProtocol(net.minecraft.network.ConnectionProtocol.PLAY);
  p.connection=new net.minecraft.server.network.ServerGamePacketListenerImpl(level.getServer(),connection,p);
  p.moveTo(helper.absolutePos(new BlockPos(2,2,2)),0,0);level.addNewPlayer(p);
  p.setItemSlot(EquipmentSlot.HEAD,new ItemStack(StarcrownDoll.YULE_ITEM.get()));p.refreshDimensions();return p;
 }
 private static void cleanup(ServerPlayer p){p.setItemSlot(EquipmentSlot.HEAD,ItemStack.EMPTY);p.serverLevel().removePlayerImmediately(p,Entity.RemovalReason.DISCARDED);p.connection.connection.disconnect(net.minecraft.network.chat.Component.literal("Test complete"));}
 private static void near(GameTestHelper h,float actual,float expected,String name){h.assertTrue(Math.abs(actual-expected)<.001f,name+": "+actual+" expected "+expected);}
 @GameTest(template="empty",timeoutTicks=100)
 public static void damageAndKill(GameTestHelper h){
  var p=wearer(h);
  try{
   p.setHealth(20);p.hurt(p.damageSources().genericKill(),Float.MAX_VALUE);near(h,p.getHealth(),19,"lethal damage capped");
   p.invulnerableTime=0;p.hurt(p.damageSources().fellOutOfWorld(),Float.POSITIVE_INFINITY);near(h,p.getHealth(),18,"void damage capped");
   p.kill();near(h,p.getHealth(),17,"kill becomes one-point strike");h.assertTrue(p.isAlive()&&!p.isRemoved(),"kill did not delete player");
   p.setHealth(0);near(h,p.getHealth(),16,"direct zero-health write capped");
   p.die(p.damageSources().genericKill());near(h,p.getHealth(),16,"forced die blocked while health remains");
   var victim=h.spawn(EntityType.PIG,5,2,5);victim.hurt(p.damageSources().playerAttack(p),4);
   near(h,victim.getHealth(),victim.getMaxHealth()-4,"outgoing attack remains at full strength");
   p.setItemSlot(EquipmentSlot.HEAD,ItemStack.EMPTY);p.setHealth(20);p.invulnerableTime=0;p.hurt(p.damageSources().genericKill(),4);near(h,p.getHealth(),16,"unequip releases cap");
   h.succeed();
  }finally{cleanup(p);}
 }
 @GameTest(template="empty",timeoutTicks=100)
 public static void sizeAndEquipment(GameTestHelper h){
  var p=wearer(h);
  try{
   for(var pose:new Pose[]{Pose.STANDING,Pose.CROUCHING,Pose.SWIMMING}){p.setPose(pose);p.refreshDimensions();near(h,p.getBbHeight(),p.getDimensions(pose).height,"normal pose height "+pose);}
   p.getPersistentData().putBoolean("starcrowndoll_humility_size",true);YuleHumility.updateSize(p);h.assertTrue(!p.getPersistentData().contains("starcrowndoll_humility_size"),"legacy size marker removed");
   var sword=new ItemStack(Items.DIAMOND_SWORD);sword.setHoverName(net.minecraft.network.chat.Component.literal("kept-item"));
   p.setItemSlot(EquipmentSlot.MAINHAND,sword);
   p.setItemSlot(EquipmentSlot.CHEST,new ItemStack(Items.NETHERITE_CHESTPLATE));
   h.assertTrue(p.getMainHandItem()==sword&&p.getItemBySlot(EquipmentSlot.CHEST).is(Items.NETHERITE_CHESTPLATE),"high-tier equipment remains usable");
   p.setPose(Pose.STANDING);p.setItemSlot(EquipmentSlot.HEAD,ItemStack.EMPTY);p.refreshDimensions();near(h,p.getBbHeight(),1.8f,"size restored on unequip");
   h.succeed();
  }finally{cleanup(p);}
 }
 @GameTest(template="empty",timeoutTicks=100)
 public static void perceptionAndBosses(GameTestHelper h){
  var p=wearer(h);
  try{
   var zombie=h.spawn(EntityType.ZOMBIE,5,2,5);
   h.assertTrue(!p.canBeSeenAsEnemy(),"player enemy visibility hidden");
   h.assertTrue(h.getLevel().getNearestPlayer(p.getX(),p.getY(),p.getZ(),3,entity->entity==p)==null,"unfiltered nearest-player query hidden");
   h.assertTrue(!TargetingConditions.forNonCombat().test(zombie,p),"non-combat sensing hidden");
   h.assertTrue(!zombie.getSensing().hasLineOfSight(p),"cached line of sight hidden");
   zombie.setTarget(p);h.assertTrue(zombie.getTarget()==null,"explicit mob target rejected");
   var warden=h.spawn(EntityType.WARDEN,6,2,6);warden.getBrain().setMemory(MemoryModuleType.ATTACK_TARGET,p);
   h.assertTrue(!warden.getBrain().hasMemoryValue(MemoryModuleType.ATTACK_TARGET),"brain attack memory rejected");
   var wither=h.spawn(EntityType.WITHER,8,2,8);wither.setAlternativeTarget(1,p.getId());h.assertTrue(wither.getAlternativeTarget(1)==0,"wither head target rejected");
   var dragon=EntityType.ENDER_DRAGON.create(h.getLevel());
   var phase=dragon.getPhaseManager().getPhase(EnderDragonPhase.STRAFE_PLAYER);phase.setTarget(p);
   h.assertTrue(SculkShriekerBlockEntity.tryGetPlayer(p)==null,"shrieker cannot identify player");
   p.setItemSlot(EquipmentSlot.HEAD,ItemStack.EMPTY);h.assertTrue(p.canBeSeenAsEnemy(),"normal visibility restored");
   zombie.setTarget(p);h.assertTrue(zombie.getTarget()==p,"normal target acquisition restored");h.succeed();
  }finally{cleanup(p);}
 }
 @GameTest(template="empty",timeoutTicks=100)
 public static void pressurePlates(GameTestHelper h){
  var p=wearer(h);var local=new BlockPos(2,2,2);var pos=h.absolutePos(local);var level=h.getLevel();
  try{
   h.assertTrue(!p.isIgnoringBlockTriggers(),"redstone triggers accept the wearer");
   level.setBlockAndUpdate(pos.below(),Blocks.STONE.defaultBlockState());
   for(var block:new Block[]{Blocks.STONE_PRESSURE_PLATE,Blocks.LIGHT_WEIGHTED_PRESSURE_PLATE,Blocks.HEAVY_WEIGHTED_PRESSURE_PLATE}){
    level.setBlockAndUpdate(pos,block.defaultBlockState());p.moveTo(pos.getX()+.5,pos.getY(),pos.getZ()+.5,0,0);
    block.entityInside(level.getBlockState(pos),level,pos,p);
    var state=level.getBlockState(pos);
    h.assertTrue(state.is(block),"plate remains placed");
    h.assertTrue(!state.hasProperty(PressurePlateBlock.POWERED)||state.getValue(PressurePlateBlock.POWERED),"ordinary plate activates");
    h.assertTrue(!state.hasProperty(WeightedPressurePlateBlock.POWER)||state.getValue(WeightedPressurePlateBlock.POWER)>0,"weighted plate activates");
   }
   level.setBlockAndUpdate(pos,Blocks.TRIPWIRE.defaultBlockState());Blocks.TRIPWIRE.entityInside(level.getBlockState(pos),level,pos,p);
   h.assertTrue(level.getBlockState(pos).getValue(TripWireBlock.POWERED),"tripwire activates");
   p.setItemSlot(EquipmentSlot.HEAD,ItemStack.EMPTY);level.setBlockAndUpdate(pos,Blocks.STONE_PRESSURE_PLATE.defaultBlockState());
   Blocks.STONE_PRESSURE_PLATE.entityInside(level.getBlockState(pos),level,pos,p);
   h.assertTrue(level.getBlockState(pos).getValue(PressurePlateBlock.POWERED),"ordinary player activates plate");
   h.succeed();
  }finally{cleanup(p);}
 }
 @GameTest(template="empty",timeoutTicks=100)
 public static void vibration(GameTestHelper h){
  var p=wearer(h);var pos=p.blockPosition();var level=h.getLevel();var heard=new int[]{0};
  var listener=new net.minecraft.world.level.gameevent.GameEventListener(){
   public net.minecraft.world.level.gameevent.PositionSource getListenerSource(){return new net.minecraft.world.level.gameevent.BlockPositionSource(pos);}
   public int getListenerRadius(){return 16;}
   public boolean handleGameEvent(net.minecraft.server.level.ServerLevel l,net.minecraft.world.level.gameevent.GameEvent event,net.minecraft.world.level.gameevent.GameEvent.Context context,net.minecraft.world.phys.Vec3 source){heard[0]++;return true;}
  };
  var registry=level.getChunkAt(pos).getListenerRegistry(net.minecraft.core.SectionPos.blockToSectionCoord(pos.getY()));registry.register(listener);
  try{
   level.gameEvent(net.minecraft.world.level.gameevent.GameEvent.STEP,p.position(),net.minecraft.world.level.gameevent.GameEvent.Context.of(p));
   h.assertTrue(heard[0]==1,"unrelated game-event listener still receives steps");
   var sensor=new net.minecraft.world.level.block.entity.SculkSensorBlockEntity(pos.offset(1,0,0),Blocks.SCULK_SENSOR.defaultBlockState());
   h.assertTrue(!sensor.getVibrationUser().canReceiveVibration(level,pos,net.minecraft.world.level.gameevent.GameEvent.STEP,net.minecraft.world.level.gameevent.GameEvent.Context.of(p)),"sculk vibration is suppressed");
   p.setItemSlot(EquipmentSlot.HEAD,ItemStack.EMPTY);
   level.gameEvent(net.minecraft.world.level.gameevent.GameEvent.STEP,p.position(),net.minecraft.world.level.gameevent.GameEvent.Context.of(p));
   h.assertTrue(heard[0]==2,"vibration listener hears normal footsteps");h.succeed();
  }finally{registry.unregister(listener);cleanup(p);}
 }
 @GameTest(template="empty",timeoutTicks=100)
 public static void finiteLife(GameTestHelper h){
  var p=wearer(h);
  try{
   while(p.getHealth()>1)p.setHealth(0);
   p.kill();near(h,p.getHealth(),0,"last health point can be exhausted");h.assertTrue(p.isDeadOrDying(),"single-hit cap is not infinite health");h.succeed();
  }finally{cleanup(p);}
 }
}
