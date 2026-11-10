package dev.starcrownqa;
import dev.starcrown.*;
import net.minecraft.gametest.framework.*;
import net.minecraftforge.gametest.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.animal.*;
import net.minecraft.world.entity.npc.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.item.trading.*;
import net.minecraft.world.effect.*;
import net.minecraft.world.*;
import net.minecraft.nbt.CompoundTag;
@GameTestHolder("starcrownqa") @PrefixGameTestTemplate(false)
public final class CharityTests {
 @GameTest(template="empty",timeoutTicks=100) public static void healingAndPact(GameTestHelper h){var p=ChastityTests.player(h);var cow=ChastityTests.spawn(EntityType.COW,p,2,0,0);var v=ChastityTests.spawn(EntityType.VILLAGER,p,-2,0,0);try{ChastityTests.equip(p);p.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);cow.setHealth(2);h.assertTrue(p.interactOn(cow,InteractionHand.MAIN_HAND).consumesAction(),"empty hand interaction consumed");h.assertTrue(cow.getHealth()==cow.getMaxHealth(),"cow fully healed");var effect=cow.getEffect(MobEffects.DAMAGE_RESISTANCE);h.assertTrue(effect!=null&&effect.getAmplifier()==4&&effect.getDuration()==1200,"Resistance V lasts60 seconds");p.setShiftKeyDown(true);p.interactOn(cow,InteractionHand.MAIN_HAND);h.assertTrue(cow.getPersistentData().getUUID(YuleCharity.PACT).equals(p.getUUID()),"animal pact established without taming");h.assertTrue(p.interactOn(v,InteractionHand.MAIN_HAND).consumesAction()&&v.getPersistentData().hasUUID(YuleCharity.PACT),"village pact uses deliberate sneaking interaction");var tag=new CompoundTag();cow.saveWithoutId(tag);h.assertTrue(tag.getCompound("ForgeData").hasUUID(YuleCharity.PACT),"pact serialized with entity");p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.WHEAT));h.assertTrue(!YuleCharity.canUse(p,cow,p.getMainHandItem()),"nonempty hand cannot use creator hand");p.setItemSlot(EquipmentSlot.HEAD,ItemStack.EMPTY);cow.getPersistentData().remove(YuleCharity.PACT);p.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);p.interactOn(cow,InteractionHand.MAIN_HAND);h.assertTrue(!cow.getPersistentData().hasUUID(YuleCharity.PACT),"unequip disables pact");h.succeed();}finally{cow.discard();v.discard();ChastityTests.close(p);}}
 @GameTest(template="empty",timeoutTicks=100) public static void ordinaryLootAndExperience(GameTestHelper h){
  var p=ChastityTests.player(h);var cow=ChastityTests.spawn(EntityType.COW,p,2,0,0);var level=p.serverLevel();
  var item=new ItemEntity(level,p.getX(),p.getY(),p.getZ(),new ItemStack(Items.LEATHER));
  var orb=new ExperienceOrb(level,p.getX(),p.getY(),p.getZ(),7);
  try{
   ChastityTests.equip(p);p.setHealth(20);cow.setHealth(0);cow.die(p.damageSources().generic());
   h.assertTrue(p.getHealth()==20,"nearby death costs no health");
   item.getPersistentData().putBoolean(YuleCharity.DEATH,true);
   orb.getPersistentData().putBoolean(YuleCharity.DEATH,true);
   level.addFreshEntity(item);level.addFreshEntity(orb);
   item.setNoPickUpDelay();item.playerTouch(p);p.takeXpDelay=0;orb.playerTouch(p);
   h.assertTrue(item.isRemoved()&&p.getInventory().countItem(Items.LEATHER)==1&&p.totalExperience==7,
     "old provenance markers do not restrict native item or orb pickup");h.succeed();
  }finally{cow.discard();item.discard();orb.discard();ChastityTests.close(p);}
 }
 @GameTest(template="empty",timeoutTicks=100) public static void liveGiftsAndPause(GameTestHelper h){var p=ChastityTests.player(h);var cow=ChastityTests.spawn(EntityType.COW,p,2,0,0);var v=ChastityTests.spawn(EntityType.VILLAGER,p,-2,0,0);try{ChastityTests.equip(p);p.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);p.setShiftKeyDown(true);p.interactOn(cow,InteractionHand.MAIN_HAND);p.interactOn(v,InteractionHand.MAIN_HAND);var book=new ItemStack(Items.ENCHANTED_BOOK);net.minecraft.world.item.EnchantedBookItem.addEnchantment(book,new net.minecraft.world.item.enchantment.EnchantmentInstance(net.minecraft.world.item.enchantment.Enchantments.MENDING,1));var offers=new MerchantOffers();offers.add(new MerchantOffer(new ItemStack(Items.EMERALD,8),book,3,1,.05f));v.getOffers().clear();v.getOffers().addAll(offers);for(int i=0;i<19;i++)YuleCharity.produce(v);h.assertTrue(!p.getInventory().contains(Items.ENCHANTED_BOOK.getDefaultInstance()),"no gift before one second");YuleCharity.produce(v);ItemStack found=ItemStack.EMPTY;for(int i=0;i<9;i++)if(p.getInventory().getItem(i).is(Items.ENCHANTED_BOOK))found=p.getInventory().getItem(i);h.assertTrue(!found.isEmpty()&&found.hasTag()&&!found.getTag().contains(YuleCharity.DEATH),"current trade product and enchantments gifted");h.assertTrue(offers.get(0).getUses()==0,"free gift does not spend trade stock");var animalGifts=YuleCharity.gifts(cow,p);h.assertTrue(animalGifts.stream().anyMatch(s->s.is(Items.BEEF)),"actual animal loot table used without death");for(var stack:animalGifts)h.assertTrue(!stack.hasTag()||!stack.getTag().contains(YuleCharity.DEATH),"live animal gift not flagged death loot");h.assertTrue(cow.isAlive()&&v.isAlive()&&p.getHealth()==20,"gifts do not kill source or trigger sorrow");v.getPersistentData().putInt(YuleCharity.CLOCK,0);p.setItemSlot(EquipmentSlot.HEAD,ItemStack.EMPTY);for(int i=0;i<25;i++)YuleCharity.produce(v);h.assertTrue(v.getPersistentData().getInt(YuleCharity.CLOCK)==0,"unequip pauses production");ChastityTests.equip(p);v.moveTo(p.getX()+17,p.getY(),p.getZ());YuleCharity.produce(v);h.assertTrue(v.getPersistentData().getInt(YuleCharity.CLOCK)==0,"outside radius pauses production");h.succeed();}finally{cow.discard();v.discard();ChastityTests.close(p);}}
}
