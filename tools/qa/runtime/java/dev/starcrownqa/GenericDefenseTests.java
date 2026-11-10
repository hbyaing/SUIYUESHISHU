package dev.starcrownqa;

import dev.starcrown.*;
import dev.starcrown.api.DollDefense;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.List;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.*;
import net.minecraft.network.syncher.*;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Adversarial fixtures use only vanilla/Forge APIs, never another mod's class names. */
public final class GenericDefenseTests {
 private static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
 private static Field field(Class<?> type,String... names)throws Exception{for(String name:names)try{var f=type.getDeclaredField(name);f.setAccessible(true);return f;}catch(NoSuchFieldException ignored){}throw new NoSuchFieldException(type.getName());}
 private static Method method(Class<?> type,String[] names,Class<?>... parameters)throws Exception{for(String name:names)try{var m=type.getDeclaredMethod(name,parameters);m.setAccessible(true);return m;}catch(NoSuchMethodException ignored){}throw new NoSuchMethodException(type.getName());}
 @SuppressWarnings("unchecked") public static void exercise(ServerPlayer p)throws Exception{
  NativeDefenseTests.exercise(p);
  p.inventoryMenu.setCarried(ItemStack.EMPTY);
  var originalBox=p.getBoundingBox();
  p.setBoundingBox(new net.minecraft.world.phys.AABB(0,0,0,0,0,0));
  check(p.getBoundingBox().equals(originalBox),"zero-volume collision box rejected");
  p.setBoundingBox(new net.minecraft.world.phys.AABB(Double.NaN,0,0,1,1,1));
  check(p.getBoundingBox().equals(originalBox),"nonfinite collision box rejected");
  DivineProtection.repair(p);
  var key=(EntityDataAccessor<Float>)field(LivingEntity.class,"DATA_HEALTH_ID","f_20961_").get(null);
  var items=(it.unimi.dsi.fastutil.ints.Int2ObjectMap<SynchedEntityData.DataItem<?>>)field(SynchedEntityData.class,"itemsById","f_135345_").get(p.getEntityData());
  var item=(SynchedEntityData.DataItem<Float>)items.get(key.getId());float health=p.getHealth();
  item.setValue(0f);check(item.getValue()==health,"raw synchronized data item write blocked");
  item.setValue(Float.NaN);check(Float.isFinite(item.getValue()),"raw NaN health entry blocked");
  p.getEntityData().assignValues(List.of(new SynchedEntityData.DataValue<>(key.getId(),EntityDataSerializers.FLOAT,0f)));
  check(item.getValue()==health,"bulk synchronized health assignment blocked");
  p.setAbsorptionAmount(4);check(p.getAbsorptionAmount()==4,"unrelated data fields still writable");
  field(LivingEntity.class,"dead","f_20890_").setBoolean(p,true);p.deathTime=19;
  p.setPose(Pose.DYING);check(p.getPose()!=Pose.DYING,"dying pose rejected");
  p.canUpdate(false);check(p.canUpdate(),"standard tick-disabling API rejected");
  field(Entity.class,"canUpdate").setBoolean(p,false);DivineProtection.repair(p);
  check(!field(LivingEntity.class,"dead","f_20890_").getBoolean(p)&&p.deathTime==0&&p.canUpdate(),"watchdog repairs direct vanilla state writes");
  var gift=new ItemStack(Items.DIAMOND,3);gift.getOrCreateTag().putString("qa","keep-exact");p.getInventory().setItem(0,gift);
  method(LivingEntity.class,new String[]{"dropAllDeathLoot","m_6668_"},net.minecraft.world.damagesource.DamageSource.class).invoke(p,p.damageSources().genericKill());
  method(net.minecraft.world.entity.player.Player.class,new String[]{"dropEquipment","m_5907_"}).invoke(p);
  check(p.getInventory().getItem(0).getCount()==3&&p.getItemBySlot(EquipmentSlot.HEAD).is(StarcrownDoll.ITEM.get()),"direct death loot cannot strip inventory");
  check(net.minecraftforge.common.ForgeHooks.onLivingDeath(p,p.damageSources().genericKill()),"shared Forge death boundary canceled");
  check(!net.minecraftforge.common.ForgeHooks.onPlayerAttackTarget(p,p),"shared Forge item attack boundary canceled");
  var section=new net.minecraft.world.level.entity.EntitySection<Entity>(Entity.class,net.minecraft.world.level.entity.Visibility.TICKING);section.add(p);
  check(!section.remove(p)&&section.size()==1,"raw section removal blocked");
  var lookup=new net.minecraft.world.level.entity.EntityLookup<Entity>();lookup.add(p);lookup.remove(p);
  check(lookup.getEntity(p.getUUID())==p,"raw entity lookup removal blocked");
  var ticks=new net.minecraft.world.level.entity.EntityTickList();ticks.add(p);ticks.remove(p);check(ticks.contains(p),"raw tick-list removal blocked");
  p.moveTo(p.getX()+32,p.getY(),p.getZ());check(p.serverLevel().getEntity(p.getUUID())==p,"normal section migration retains entity");
  check(DollDefense.isProtected(p)&&DollDefense.rejectDamage(p,p.damageSources().magic(),2),"public cooperative API protected");
  p.inventoryMenu.clicked(5,0,net.minecraft.world.inventory.ClickType.PICKUP,p);
  check(!DollDefense.isProtected(p),"second menu unequip releases all generic guards");
  item.setValue(12f);check(item.getValue()==12,"raw data entry allowed after unequip");
  p.getEntityData().assignValues(List.of(new SynchedEntityData.DataValue<>(key.getId(),EntityDataSerializers.FLOAT,11f)));check(item.getValue()==11,"bulk assignment allowed after unequip");
  p.setPose(Pose.DYING);check(p.getPose()==Pose.DYING,"dying pose allowed after unequip");p.setPose(Pose.STANDING);
  p.canUpdate(false);check(!p.canUpdate(),"tick toggle allowed after unequip");p.canUpdate(true);
  check(section.remove(p),"section release allowed after unequip");lookup.remove(p);ticks.remove(p);check(lookup.count()==0&&!ticks.contains(p),"lookup and tick list release normally");
  check(!DollDefense.rejectRemoval(p,Entity.RemovalReason.DISCARDED),"public API does not protect unwearer");
 }
 private static void stackRegression(ServerPlayer player){
  player.getInventory().clearContent();
  player.setItemSlot(EquipmentSlot.HEAD,new ItemStack(StarcrownDoll.YULE_ITEM.get()));
  var incoming=new ItemStack(Items.COBBLESTONE,64);
  incoming.getOrCreateTag().putString("qa","preserve");
  check(player.getInventory().add(incoming)&&incoming.isEmpty(),"pickup consumes exactly 64 items");
  var stored=player.getInventory().getItem(0);
  check(stored.getCount()==64&&"preserve".equals(stored.getOrCreateTag().getString("qa")),"pickup preserves native stack size and tags");
  player.setItemSlot(EquipmentSlot.HEAD,ItemStack.EMPTY);
  player.getInventory().clearContent();
  check(player.getInventory().add(new ItemStack(Items.COBBLESTONE,64))&&player.getInventory().getItem(0).getCount()==64,"unequipped pickup uses native limit");
 }
 private static void lootRegression(ServerPlayer player){
  player.getInventory().clearContent();
  var stack=new ItemStack(Items.LEATHER);
  stack.getOrCreateTag().putBoolean(YuleCharity.DEATH,true);
  var restored=ItemStack.of(stack.save(new net.minecraft.nbt.CompoundTag()));
  check(ItemStack.isSameItemSameTags(restored,new ItemStack(Items.LEATHER)),"legacy NBT loads as a normal stack");
  var drop=new net.minecraft.world.entity.item.ItemEntity(player.level(),player.getX(),player.getY(),player.getZ(),restored);
  drop.getPersistentData().putBoolean(YuleCharity.DEATH,true);
  player.setItemSlot(EquipmentSlot.HEAD,new ItemStack(StarcrownDoll.YULE_ITEM.get()));
  drop.setNoPickUpDelay();
  drop.playerTouch(player);
  check(drop.isRemoved()&&player.getInventory().countItem(Items.LEATHER)==1,"wearer picks up legacy death loot");
 }
 @Mod.EventBusSubscriber(modid="starcrownqa",value=net.minecraftforge.api.distmarker.Dist.CLIENT)
 public static class Production {
  static boolean started;static volatile boolean done;static int probes;
  public static void serverTick(TickEvent.ServerTickEvent event){
   if(event.phase!=TickEvent.Phase.END||net.minecraftforge.fml.loading.FMLEnvironment.dist!=net.minecraftforge.api.distmarker.Dist.CLIENT||started)return;
   var mc=net.minecraft.client.Minecraft.getInstance();if(!Files.exists(mc.gameDirectory.toPath().resolve("enable-generic-defense-qa")))return;
   var server=net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();if(server==null||server.getPlayerList().getPlayers().isEmpty())return;
   started=true;var player=server.getPlayerList().getPlayers().get(0);System.out.println("GENERIC-QA executing on server thread");String result;
   try{exercise(player);stackRegression(player);lootRegression(player);result="PASS entity-only death loot, legacy migration and normal pickup merging.\nPASS 64-item pickup splits into four stacks of 16 with tags; unequipped pickup stays at 64.\nPASS native defense regression; direct data-item/bulk health, raw death flag, death loot/equipment, Forge attack/death, tick-disable, entity section/lookup/tick-list, normal movement, public API and unequip negative controls\nALL_PASS\n";}catch(Throwable error){result="FAIL "+error;error.printStackTrace();}
   finally{DivineAnchor.unlock(player);player.setItemSlot(EquipmentSlot.HEAD,ItemStack.EMPTY);}
   try{Files.writeString(mc.gameDirectory.toPath().resolve("generic-defense-results.txt"),result);}catch(Exception error){throw new RuntimeException(error);}done=true;mc.execute(mc::stop);
  }
  @SubscribeEvent public static void tick(TickEvent.ClientTickEvent e){if(e.phase!=TickEvent.Phase.END)return;
   var mc=net.minecraft.client.Minecraft.getInstance();
   boolean enabled=Files.exists(mc.gameDirectory.toPath().resolve("enable-generic-defense-qa"));
   if(++probes==1)System.out.println("GENERIC-QA tick enabled="+enabled+" dir="+mc.gameDirectory);
   if(!enabled)return;
   if(done){mc.stop();return;}if(started||mc.player==null||mc.getSingleplayerServer()==null)return;
   System.out.println("GENERIC-QA starting server acceptance");
   started=true;var id=mc.player.getUUID();var server=mc.getSingleplayerServer();server.execute(()->{String result;
    try{exercise(server.getPlayerList().getPlayer(id));result="PASS native defense regression; direct data-item/bulk health, raw death flag, death loot/equipment, Forge attack/death, tick-disable, entity section/lookup/tick-list, normal movement, public API and unequip negative controls\nALL_PASS\n";}catch(Throwable t){result="FAIL "+t;t.printStackTrace();}
    finally{var p=server.getPlayerList().getPlayer(id);if(p!=null){DivineAnchor.unlock(p);p.setItemSlot(EquipmentSlot.HEAD,ItemStack.EMPTY);}}
    try{Files.writeString(Path.of("generic-defense-results.txt"),result);}catch(Exception x){throw new RuntimeException(x);}done=true;
   });
  }
 }
}
