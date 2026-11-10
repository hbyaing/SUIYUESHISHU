package dev.starcrownqa;

import dev.starcrown.*;
import java.lang.reflect.Method;
import java.nio.file.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.*;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraftforge.gametest.*;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Same acceptance exercise runs in GameTest and the reobfuscated production client. */
@GameTestHolder("starcrownqa") @PrefixGameTestTemplate(false)
public final class NativeDefenseTests {
 private static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
 private static Method method(Class<?> type,String[] names,Class<?>... args)throws Exception{
  for(String name:names)try{var m=type.getDeclaredMethod(name,args);m.setAccessible(true);return m;}catch(NoSuchMethodException ignored){}
  throw new NoSuchMethodException(type.getName()+java.util.Arrays.toString(names));
 }
 public static void exercise(ServerPlayer p)throws Exception{
  DivineAnchor.unlock(p);p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
  p.getInventory().clearContent();p.setItemSlot(EquipmentSlot.HEAD,new ItemStack(StarcrownDoll.ITEM.get()));DivineAnchor.observe(p);
  p.setAbsorptionAmount(6);float expected=DivineProtection.displayHealth(p);
  for(float damage:new float[]{1,Float.MAX_VALUE,Float.POSITIVE_INFINITY,Float.NaN}){
   check(!p.hurt(p.damageSources().genericKill(),damage),"native hurt must reject "+damage);
   check(p.getHealth()==expected&&p.getAbsorptionAmount()==6,"no health or absorption side effects");
  }
  var hurt=method(net.minecraft.world.entity.player.Player.class,new String[]{"actuallyHurt","m_6475_"},DamageSource.class,float.class);
  hurt.invoke(p,p.damageSources().fellOutOfWorld(),1000f);
  check(p.getHealth()==expected&&p.getAbsorptionAmount()==6,"direct internal damage rejected");
  check(DivinePool.get(p.getServer()).entry(p.getUUID()).lastPath.equals("Player.actuallyHurt"),"internal hook actually executed");
  var deathTick=method(LivingEntity.class,new String[]{"tickDeath","m_6153_"});
  p.deathTime=19;deathTick.invoke(p);
  check(p.deathTime==0&&!p.isDeadOrDying()&&p.isAlive()&&!p.isRemoved(),"death progression stopped");
  var enemy=EntityType.ZOMBIE.create(p.serverLevel());enemy.moveTo(p.position());p.serverLevel().addFreshEntity(enemy);
  try{p.hurt(p.damageSources().mobAttack(enemy),8);check(DivineSeven.owner(enemy)==p&&enemy.getPersistentData().getCompound(DivineSeven.MARK).getBoolean("retaliation"),"pre-event block preserves retaliation");}finally{enemy.discard();}
  p.getInventory().armor.set(3,ItemStack.EMPTY);p.hurt(p.damageSources().magic(),100);
  check(DivineAnchor.locked(p)&&p.isAlive(),"raw deletion cannot disable native defense");DivineAnchor.repair(p);
  p.inventoryMenu.clicked(5,0,net.minecraft.world.inventory.ClickType.PICKUP,p);
  check(!DivineProtection.active(p)&&p.getItemBySlot(EquipmentSlot.HEAD).isEmpty(),"normal menu unequip releases defense");
  p.setAbsorptionAmount(0);p.setHealth(20);p.invulnerableTime=0;
  hurt.invoke(p,p.damageSources().magic(),4f);
  check(p.getHealth()<20,"internal damage restored immediately after unequip");
  p.setItemSlot(EquipmentSlot.HEAD,new ItemStack(Items.IRON_HELMET));p.setHealth(20);p.invulnerableTime=0;
  // Bypass vanilla login spawn protection so this tests our equipment scope, not the login grace period.
  check(p.hurt(p.damageSources().fellOutOfWorld(),2)&&p.getHealth()<20,"ordinary helmet remains vulnerable");
  p.setItemSlot(EquipmentSlot.HEAD,new ItemStack(StarcrownDoll.ITEM.get()));DivineAnchor.observe(p);
  check(!DivineProtection.blockRemoval(p,Entity.RemovalReason.CHANGED_DIMENSION,"qa"),"dimension transition permitted");
  check(!DivineProtection.blockRemoval(p,Entity.RemovalReason.UNLOADED_WITH_PLAYER,"qa"),"logout unload permitted");
 }
 private static ServerPlayer player(GameTestHelper h){
  var level=h.getLevel();var p=new ServerPlayer(level.getServer(),level,new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(),"native-defense"));
  var c=new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);new io.netty.channel.embedded.EmbeddedChannel(c);c.setProtocol(net.minecraft.network.ConnectionProtocol.PLAY);
  p.connection=new net.minecraft.server.network.ServerGamePacketListenerImpl(level.getServer(),c,p);p.moveTo(h.absolutePos(new net.minecraft.core.BlockPos(2,3,2)),0,0);level.addNewPlayer(p);return p;
 }
 @GameTest(template="empty",timeoutTicks=100) public static void nativeDamageAndRelease(GameTestHelper h)throws Exception{
  var p=player(h);try{exercise(p);h.succeed();}finally{DivineAnchor.unlock(p);p.setItemSlot(EquipmentSlot.HEAD,ItemStack.EMPTY);p.serverLevel().removePlayerImmediately(p,Entity.RemovalReason.DISCARDED);p.connection.connection.disconnect(net.minecraft.network.chat.Component.literal("done"));}
 }
 @Mod.EventBusSubscriber(modid="starcrownqa",value=net.minecraftforge.api.distmarker.Dist.CLIENT)
 public static class Production {
  static boolean started;static volatile boolean done;
  @SubscribeEvent public static void tick(TickEvent.ClientTickEvent e){
   if(e.phase!=TickEvent.Phase.END||!Files.exists(Path.of("enable-native-defense-qa")))return;
   var mc=net.minecraft.client.Minecraft.getInstance();if(done){mc.stop();return;}if(started||mc.player==null||mc.getSingleplayerServer()==null)return;
   started=true;var id=mc.player.getUUID();var server=mc.getSingleplayerServer();server.execute(()->{String result;
    try{exercise(server.getPlayerList().getPlayer(id));result="PASS native hurt/nonfinite amounts, internal damage, death tick, retaliation, raw deletion, menu unequip, ordinary player, lifecycle exemptions\nALL_PASS\n";}catch(Throwable t){result="FAIL "+t;t.printStackTrace();}
    finally{var p=server.getPlayerList().getPlayer(id);if(p!=null){DivineAnchor.unlock(p);p.setItemSlot(EquipmentSlot.HEAD,ItemStack.EMPTY);}}
    try{Files.writeString(Path.of("native-defense-results.txt"),result);}catch(Exception x){throw new RuntimeException(x);}done=true;
   });
  }
 }
}
