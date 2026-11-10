package dev.starcrownqa;
import dev.starcrown.*;
import java.nio.file.*;
import java.util.*;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.effect.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.event.TickEvent;
@Mod.EventBusSubscriber(modid="starcrownqa")
public final class GenerosityQa {
 static int tick;static volatile boolean done,failed;static ServerPlayer p;
 static void log(String s){try{Files.writeString(Path.of("generosity-results.txt"),s+"\n",StandardOpenOption.CREATE,StandardOpenOption.APPEND);}catch(Exception e){throw new RuntimeException(e);}}
 static void check(boolean b,String s){if(!b)throw new AssertionError(s);log("PASS "+s);}
 static List<ItemEntity> gifts(ServerLevel l){return l.getEntitiesOfClass(ItemEntity.class,p.getBoundingBox().inflate(16),i->i.getPersistentData().hasUUID(YuleGenerosity.DONOR));}
 static void clear(ServerLevel l){gifts(l).forEach(Entity::discard);}
 static long emc(ItemStack s)throws Exception{var api=Class.forName("moze_intel.projecte.api.proxy.IEMCProxy");return (Long)api.getMethod("getValue",ItemStack.class).invoke(api.getField("INSTANCE").get(null),s);}
 @SubscribeEvent public static void level(TickEvent.LevelTickEvent event){
  if(event.phase!=TickEvent.Phase.END||!(event.level instanceof ServerLevel l)||l!=l.getServer().overworld()||!Files.exists(Path.of("enable-generosity-qa"))||l.getServer().getPlayerList().getPlayers().isEmpty()||done||failed)return;
  try{tick++;if(tick!=30)return;Files.writeString(Path.of("generosity-results.txt"),"");if(Files.exists(Path.of("verify-generosity-persistence"))){p=l.getServer().getPlayerList().getPlayers().get(0);check(GenerosityRewards.get(l.getServer()).has(p.getUUID())&&p.getMaxHealth()==40,"player permanent doubling survives full game restart");check(p.getEffect(MobEffects.HERO_OF_THE_VILLAGE)!=null&&p.getEffect(MobEffects.HERO_OF_THE_VILLAGE).isInfiniteDuration(),"permanent Hero X restored after restart");done=true;log("SERVER_PASS");return;}p=l.getServer().getPlayerList().getPlayers().get(0);DivineAnchor.unlock(p);p.setItemSlot(EquipmentSlot.HEAD,ItemStack.EMPTY);DivineWill.update(p,false);p.removeAllEffects();p.getInventory().clearContent();p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);for(int x=-8;x<=8;x++)for(int z=-8;z<=8;z++)l.setBlockAndUpdate(new net.minecraft.core.BlockPos(x,89,z),net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());p.teleportTo(l,.5,90,.5,180,10);p.getInventory().selected=0;
   p.getInventory().items.set(10,new ItemStack(Items.APPLE,7));p.setItemSlot(EquipmentSlot.HEAD,new ItemStack(StarcrownDoll.YULE_ITEM.get()));
   check(p.getInventory().items.get(10).getCount()==7,"main inventory stays available");
   boolean installed=GenerosityEmc.installed();check(installed==Files.exists(Path.of("expect-projecte")),"optional dependency presence matches test mode");
   var prices=GenerosityPrices.get(l.getServer());
   for(var item:net.minecraftforge.registries.ForgeRegistries.ITEMS)if(item!=Items.AIR)check(prices.price(item)>0,"registered item is priced: "+net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(item));
   for(int n=0;n<80;n++){var before=new ItemStack(Items.COBBLESTONE,17);var after=YuleGenerosity.upgrade(before);check(after.getCount()==17&&prices.price(after.getItem())>prices.price(before.getItem()),"same-count higher-value conversion "+n);}
   var snapshot=GenerosityPrices.load(prices.save(new CompoundTag()));check(snapshot.price(Items.DIAMOND)==prices.price(Items.DIAMOND),"hidden prices persist");
   clear(l);p.setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(Items.COBBLESTONE,17));
   check(p.drop(false)&&gifts(l).isEmpty(),"ordinary drop has no gift metadata");
   DivineSeven.action(p,22,0);var q=gifts(l);
   check(q.size()==1&&q.get(0).getItem().getCount()==1&&prices.price(q.get(0).getItem().getItem())>prices.price(Items.COBBLESTONE),
         "deliberate gift uses the saved valuation");
   clear(l);
   clear(l);var donor=UUID.randomUUID();var other=new ItemEntity(l,p.getX(),p.getY(),p.getZ(),new ItemStack(Items.APPLE));YuleGenerosity.mark(other,donor);l.addFreshEntity(other);other.setNoPickUpDelay();other.playerTouch(p);check(p.getPersistentData().getBoolean(YuleGenerosity.BLESSED),"actual player pickup grants blessing");check(GenerosityRewards.get(l.getServer()).has(donor),"offline donor reward saved");check(p.getEffect(MobEffects.HERO_OF_THE_VILLAGE).getAmplifier()==9&&p.getEffect(MobEffects.HERO_OF_THE_VILLAGE).isInfiniteDuration(),"permanent Hero X applied");check(p.getMaxHealth()==40,"health doubles once");YuleGenerosity.blessing(p);check(p.getMaxHealth()==40,"repeated blessing does not stack");p.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1024);check(p.getMaxHealth()==2048,"effective attribute cap doubles");p.getAttribute(Attributes.MAX_HEALTH).setBaseValue(20);
   var cow=EntityType.COW.create(l);cow.moveTo(4,90,4);l.addFreshEntity(cow);var mobGift=new ItemEntity(l,4,90,4,new ItemStack(Items.APPLE));YuleGenerosity.mark(mobGift,p.getUUID());l.addFreshEntity(mobGift);cow.take(mobGift,1);check(cow.getMaxHealth()==20&&cow.getEffect(MobEffects.HERO_OF_THE_VILLAGE).isInfiniteDuration(),"mob collection grants permanent reward to mob and donor");
   var saved=((net.minecraft.world.level.saveddata.SavedData)GenerosityRewards.get(l.getServer())).save(new CompoundTag());check(GenerosityRewards.load(saved).has(donor)&&GenerosityRewards.load(saved).has(p.getUUID()),"permanent player reward survives serialization");
   p.setItemSlot(EquipmentSlot.HEAD,ItemStack.EMPTY);check(p.inventoryMenu.slots.stream().filter(s->s.container==p.getInventory()&&s.getSlotIndex()==10).allMatch(s->s.mayPlace(new ItemStack(Items.DIRT))),"main inventory remains accessible");
   log("SERVER_PASS");done=true;
  }catch(Throwable t){log("FAIL "+t);t.printStackTrace();failed=true;}
 }
 @Mod.EventBusSubscriber(modid="starcrownqa",value=net.minecraftforge.api.distmarker.Dist.CLIENT)
 public static class Client{
  static int ticks;static boolean released,finished;
  @SubscribeEvent public static void render(TickEvent.RenderTickEvent e){if(e.phase!=TickEvent.Phase.END||!Files.exists(Path.of("enable-generosity-qa")))return;var mc=net.minecraft.client.Minecraft.getInstance();mc.options.pauseOnLostFocus=false;if(mc.player!=null&&!released){released=true;mc.setScreen(null);mc.mouseHandler.releaseMouse();log("CLIENT_READY");}if(failed&&!finished){finished=true;mc.stop();}if(done&&mc.player!=null&&!finished){log("CLIENT_HEALTH "+mc.player.getMaxHealth());if(mc.player.getMaxHealth()==40){finished=true;log("PASS client receives blessing and doubled health");log("ALL_PASS");mc.stop();}}}
  @SubscribeEvent public static void tick(TickEvent.ClientTickEvent e)throws Exception{if(e.phase!=TickEvent.Phase.END||!Files.exists(Path.of("enable-generosity-qa")))return;var mc=net.minecraft.client.Minecraft.getInstance();mc.options.pauseOnLostFocus=false;if(mc.player==null)return;if(++ticks%20==0)log("CLIENT_TICK "+ticks+" done="+done+" hp="+mc.player.getMaxHealth());if(ticks==1){mc.setScreen(null);mc.mouseHandler.releaseMouse();}if(failed){mc.stop();return;}if(done&&ticks>70&&!finished){finished=true;check(mc.player.getMaxHealth()==40,"client receives blessing and doubled health");log("ALL_PASS");mc.stop();}}
 }
}
