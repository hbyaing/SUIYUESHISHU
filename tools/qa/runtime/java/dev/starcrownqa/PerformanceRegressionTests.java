package dev.starcrownqa;
import dev.starcrown.*;
import net.minecraft.gametest.framework.*;
import net.minecraftforge.gametest.*;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.*;
import net.minecraft.world.effect.MobEffects;
import net.minecraftforge.event.entity.living.MobEffectEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
@GameTestHolder("starcrownqa") @PrefixGameTestTemplate(false)
public final class PerformanceRegressionTests {
 @GameTest(template="empty",timeoutTicks=100) public static void musicSourceCacheReleasesImmediately(GameTestHelper h){
  var p=ChastityTests.player(h);var level=p.serverLevel();var pos=p.blockPosition().offset(4,0,0);var before=level.getBlockState(pos);
  var mob=ChastityTests.spawn(net.minecraft.world.entity.EntityType.PIG,p,2,0,0);
  try{
   level.setBlockAndUpdate(pos,StarcrownDoll.YULE_DOLL.get().defaultBlockState());
   var doll=(StarcrownDoll.DollEntity)level.getBlockEntity(pos);
   h.assertTrue(!DollDance.nearYule(level,mob.position()),"silent source cache starts empty");
   doll.toggleMusic();h.assertTrue(DollDance.nearYule(level,mob.position())&&DollDance.controlled(mob),"start invalidates empty cache immediately");
   DollDance.feastTargets(level);doll.toggleMusic();
   h.assertTrue(!DollDance.controlled(mob)&&!DollDance.nearYule(level,mob.position()),"stop invalidates populated cache immediately");
   doll.toggleMusic();level.removeBlock(pos,false);h.assertTrue(!DollDance.controlled(mob),"breaking source releases control immediately");h.succeed();
  }finally{mob.discard();level.setBlockAndUpdate(pos,before);ChastityTests.close(p);}
 }
 @GameTest(template="empty",timeoutTicks=100) public static void movedStacksReleaseCachedOwnership(GameTestHelper h){
  var a=ChastityTests.player(h);var b=ChastityTests.player(h);
  try{
   ChastityTests.equip(a);ChastityTests.equip(b);YuleTemperance.bind(a);YuleTemperance.bind(b);var stack=new ItemStack(Items.IRON_PICKAXE);
   a.getInventory().items.set(0,stack);h.assertTrue(YuleTemperance.owner(stack)==a&&YuleTemperance.durable(stack),"held tool warms durability cache");
   a.getInventory().items.set(0,ItemStack.EMPTY);h.assertTrue(!YuleTemperance.durable(stack),"removed tool releases durability protection");
   b.getInventory().items.set(3,stack);h.assertTrue(YuleTemperance.owner(stack)==b&&!YuleTemperance.durable(stack),"stored tool rebinds without held protection");
   b.getInventory().items.set(3,ItemStack.EMPTY);b.containerMenu.setCarried(stack);h.assertTrue(YuleTemperance.owner(stack)==b,"cursor move rebinds");
   b.setItemSlot(EquipmentSlot.HEAD,ItemStack.EMPTY);h.assertTrue(YuleTemperance.owner(stack)==null,"unequip invalidates warm cache");h.succeed();
  }finally{ChastityTests.close(a);ChastityTests.close(b);}
 }
 @GameTest(template="empty",timeoutTicks=100) public static void diligenceAvoidsDuplicateEffectUpdates(GameTestHelper h){
  var p=ChastityTests.player(h);
  class Updates {int count;@SubscribeEvent public void added(MobEffectEvent.Added e){if(e.getEntity()==p&&(e.getEffectInstance().getEffect()==MobEffects.DIG_SPEED||e.getEffectInstance().getEffect()==MobEffects.MOVEMENT_SPEED))count++;}}
  var observer=new Updates();net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(observer);
  try{
   ChastityTests.equip(p);p.swinging=true;for(int i=0;i<10;i++)YuleDiligence.tickPlayer(p);
   h.assertTrue(observer.count==2,"stable boosts are installed once, not resubmitted on every tick");
   h.assertTrue(p.hasEffect(MobEffects.DIG_SPEED)&&p.hasEffect(MobEffects.MOVEMENT_SPEED),"both boosts remain active");
   p.swinging=false;YuleDiligence.tickPlayer(p);
   h.assertTrue(!p.hasEffect(MobEffects.DIG_SPEED)&&!p.hasEffect(MobEffects.MOVEMENT_SPEED),"stopping still removes both boosts immediately");h.succeed();
  }finally{net.minecraftforge.common.MinecraftForge.EVENT_BUS.unregister(observer);ChastityTests.close(p);}
 }
}
