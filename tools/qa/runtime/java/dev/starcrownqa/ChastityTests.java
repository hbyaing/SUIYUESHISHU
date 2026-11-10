package dev.starcrownqa;
import dev.starcrown.*;
import net.minecraft.gametest.framework.*;
import net.minecraftforge.gametest.*;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.animal.*;
import net.minecraft.world.entity.animal.horse.*;
import net.minecraft.world.entity.npc.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.alchemy.*;
import net.minecraft.world.effect.*;
import net.minecraft.world.*;
import net.minecraft.world.level.block.*;
import net.minecraft.core.BlockPos;
@GameTestHolder("starcrownqa") @PrefixGameTestTemplate(false)
public final class ChastityTests {
 static ServerPlayer player(GameTestHelper h){var l=h.getLevel();var p=new ServerPlayer(l.getServer(),l,new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(),"chastity-test"));var c=new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);new io.netty.channel.embedded.EmbeddedChannel(c);c.setProtocol(net.minecraft.network.ConnectionProtocol.PLAY);p.connection=new net.minecraft.server.network.ServerGamePacketListenerImpl(l.getServer(),c,p);p.moveTo(h.absolutePos(new BlockPos(7,100,7)),0,0);l.addNewPlayer(p);return p;}
 static void equip(ServerPlayer p){p.setItemSlot(EquipmentSlot.HEAD,new ItemStack(StarcrownDoll.YULE_ITEM.get()));}
 static void close(ServerPlayer p){p.setItemSlot(EquipmentSlot.HEAD,ItemStack.EMPTY);p.serverLevel().removePlayerImmediately(p,Entity.RemovalReason.DISCARDED);p.connection.connection.disconnect(net.minecraft.network.chat.Component.literal("done"));}
 static <T extends Mob>T spawn(EntityType<T> type,ServerPlayer p,double x,double y,double z){var e=type.create(p.serverLevel());e.moveTo(p.getX()+x,p.getY()+y,p.getZ()+z,0,0);p.serverLevel().addFreshEntity(e);return e;}
 @GameTest(template="empty",timeoutTicks=100) public static void statusImmunity(GameTestHelper h){var p=player(h);try{
  p.addEffect(new MobEffectInstance(MobEffects.POISON,1000));equip(p);YuleChastity.tick(new net.minecraftforge.event.TickEvent.PlayerTickEvent(net.minecraftforge.event.TickEvent.Phase.START,p));h.assertTrue(!p.hasEffect(MobEffects.POISON),"existing negative effect removed on equip");
  for(var effect:new MobEffect[]{MobEffects.WITHER,MobEffects.POISON,MobEffects.DARKNESS,MobEffects.DIG_SLOWDOWN,BenefitFixture.BAD,BenefitFixture.CURSE}){h.assertTrue(!p.addEffect(new MobEffectInstance(effect,1000)),"negative or tagged curse rejected: "+effect);p.forceAddEffect(new MobEffectInstance(effect,1000),null);h.assertTrue(!p.hasEffect(effect),"forced negative application rejected");}
  h.assertTrue(p.addEffect(new MobEffectInstance(BenefitFixture.NEUTRAL,1000)),"neutral effects allowed");h.assertTrue(p.addEffect(new MobEffectInstance(BenefitFixture.BENEFIT,1000)),"ordinary mod benefit retained");p.setItemSlot(EquipmentSlot.HEAD,ItemStack.EMPTY);h.assertTrue(p.addEffect(new MobEffectInstance(BenefitFixture.BAD,1000)),"unequip releases immunity");h.succeed();
 }finally{close(p);}}
 @GameTest(template="empty",timeoutTicks=100) public static void ordinaryInteractions(GameTestHelper h){
  var p=player(h);var cow=spawn(EntityType.COW,p,2,0,0);var wolf=spawn(EntityType.WOLF,p,3,0,0);var villager=spawn(EntityType.VILLAGER,p,-3,0,0);
  try{
   equip(p);p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.WHEAT,3));p.interactOn(cow,InteractionHand.MAIN_HAND);
   h.assertTrue(cow.isInLove()&&p.getMainHandItem().getCount()==2,"breeding consumes ordinary food and works");
   wolf.tame(p);h.assertTrue(wolf.isTame()&&p.getUUID().equals(wolf.getOwnerUUID()),"pet taming works");
   var potion=PotionUtils.setPotion(new ItemStack(Items.POTION),Potions.NIGHT_VISION);
   p.setItemInHand(InteractionHand.MAIN_HAND,potion);p.startUsingItem(InteractionHand.MAIN_HAND);
   h.assertTrue(p.isUsingItem(),"drinking starts");Items.POTION.finishUsingItem(potion,p.serverLevel(),p);
   h.assertTrue(p.hasEffect(MobEffects.NIGHT_VISION),"positive potion applies");
   p.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);
   h.assertTrue(!YuleCharity.canUse(p,villager,ItemStack.EMPTY),"ordinary empty hand is free for merchant interaction");
   var menu=new net.minecraft.world.inventory.MerchantMenu(29,p.getInventory(),villager);
   h.assertTrue(menu.getSlot(2).mayPickup(p),"trade output is available");h.succeed();
  }finally{cow.discard();wolf.discard();villager.discard();close(p);}
 }
 @GameTest(template="empty",timeoutTicks=100) public static void waterAndLavaWalking(GameTestHelper h){var p=player(h);var level=p.serverLevel();var center=p.blockPosition();var water=center.offset(2,-1,0);var lava=center.offset(-3,-1,0);var edge=center.offset(12,-1,0);var outside=center.offset(13,-1,0);var logged=center.offset(0,-1,3);try{equip(p);level.setBlockAndUpdate(center.below(),Blocks.STONE.defaultBlockState());for(var pos:new BlockPos[]{water,edge,outside})level.setBlockAndUpdate(pos,Blocks.WATER.defaultBlockState());level.setBlockAndUpdate(lava,Blocks.LAVA.defaultBlockState());level.setBlockAndUpdate(logged,Blocks.OAK_FENCE.defaultBlockState().setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.WATERLOGGED,true));for(var pos:new BlockPos[]{water,lava,edge,outside,logged})level.setBlockAndUpdate(pos.above(),Blocks.AIR.defaultBlockState());String diagnostic="above="+level.getBlockState(water.above())+",box="+p.getBoundingBox()+",pos="+water;YuleChastity.tick(new net.minecraftforge.event.TickEvent.PlayerTickEvent(net.minecraftforge.event.TickEvent.Phase.START,p));
  h.assertTrue(level.getBlockState(water).is(Blocks.FROSTED_ICE)&&level.getBlockState(edge).is(Blocks.FROSTED_ICE),"Frost Walker X radius12: water="+level.getBlockState(water)+",edge="+level.getBlockState(edge)+",pY="+p.getY()+",edgeY="+edge.getY()+",active="+YuleChastity.active(p)+","+diagnostic);h.assertTrue(level.getBlockState(lava).is(Blocks.OBSIDIAN),"lava becomes obsidian");h.assertTrue(level.getBlockState(outside).is(Blocks.WATER)&&level.getBlockState(logged).is(Blocks.OAK_FENCE),"outside radius and waterlogged blocks preserved");p.setItemSlot(EquipmentSlot.HEAD,ItemStack.EMPTY);level.setBlockAndUpdate(water,Blocks.WATER.defaultBlockState());YuleChastity.tick(new net.minecraftforge.event.TickEvent.PlayerTickEvent(net.minecraftforge.event.TickEvent.Phase.START,p));h.assertTrue(level.getBlockState(water).is(Blocks.WATER),"unequip stops freezing");h.succeed();
 }finally{for(var pos:new BlockPos[]{water,lava,edge,outside,logged,center.below()})level.setBlockAndUpdate(pos,Blocks.AIR.defaultBlockState());close(p);}}
 @GameTest(template="empty",timeoutTicks=100) public static void holyPurification(GameTestHelper h){var p=player(h);equip(p);var mobs=new java.util.ArrayList<Mob>();try{
  var zombie=spawn(EntityType.ZOMBIE,p,2,0,0);zombie.setInvulnerable(true);mobs.add(zombie);mobs.add(spawn(EntityType.SKELETON,p,5,0,0));mobs.add(spawn(EntityType.WITHER,p,0,0,3));mobs.add(spawn(EntityType.BLAZE,p,-3,0,0));mobs.add(spawn(EntityType.STRIDER,p,0,0,-3));mobs.add(spawn(EntityType.GOAT,p,0,0,2));var outside=spawn(EntityType.SKELETON,p,5.1,0,0);var cow=spawn(EntityType.COW,p,1,0,0);var vertical=spawn(EntityType.BLAZE,p,0,5.1,0);
  try{h.assertTrue(zombie.isAddedToWorld(),"nearby undead is not converted before purification");YuleChastity.purify(p);for(var mob:mobs)h.assertTrue(!mob.isAlive(),"undead/Nether/tagged creature instantly dies despite damage cap: "+mob.getType());h.assertTrue(outside.isAlive()&&cow.isAlive()&&vertical.isAlive(),"five-block sphere preserves outside and ordinary animals");p.setItemSlot(EquipmentSlot.HEAD,ItemStack.EMPTY);outside.moveTo(p.getX()+1,p.getY(),p.getZ());YuleChastity.purify(p);h.assertTrue(outside.isAlive(),"unequip stops purification");h.succeed();}finally{outside.discard();cow.discard();vertical.discard();}
 }finally{mobs.forEach(Entity::discard);close(p);}}
}
