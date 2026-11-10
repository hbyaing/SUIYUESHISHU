package dev.starcrownqa;
import dev.starcrown.*;
import net.minecraft.gametest.framework.*;
import net.minecraftforge.gametest.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.*;
@GameTestHolder("starcrownqa") @PrefixGameTestTemplate(false)
public final class CostTests {
 private static ServerPlayer player(GameTestHelper h){var l=h.getLevel();var p=new ServerPlayer(l.getServer(),l,new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(),"cost-test"));var c=new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);new io.netty.channel.embedded.EmbeddedChannel(c);c.setProtocol(net.minecraft.network.ConnectionProtocol.PLAY);p.connection=new net.minecraft.server.network.ServerGamePacketListenerImpl(l.getServer(),c,p);p.moveTo(h.absolutePos(new net.minecraft.core.BlockPos(2,3,2)),0,0);l.addNewPlayer(p);p.setItemSlot(EquipmentSlot.HEAD,new ItemStack(StarcrownDoll.ITEM.get()));return p;}
 private static void cleanup(ServerPlayer p,LivingEntity target){DivineCost.clear(target);DivineAnchor.unlock(p);p.setItemSlot(EquipmentSlot.HEAD,ItemStack.EMPTY);DivineWill.update(p,false);p.serverLevel().removePlayerImmediately(p,Entity.RemovalReason.DISCARDED);target.discard();}
 @GameTest(template="empty",timeoutTicks=260) public static void witherHundredPulses(GameTestHelper h){var p=player(h);var boss=net.minecraft.world.entity.EntityType.WITHER.create(h.getLevel());boss.setNoAi(true);boss.moveTo(p.getX()+4,p.getY(),p.getZ());h.getLevel().addFreshEntity(boss);
  float before=boss.getHealth();boss.hurt(p.damageSources().playerAttack(p),.005f);float actual=before-boss.getHealth();var tag=boss.getPersistentData().getCompound(DivineCost.KEY);
  h.assertTrue(actual>0&&Math.abs(tag.getFloat("damage")-actual)<.0001,"records actual health loss after armor");h.assertTrue(!boss.hasEffect(DivineEffects.COST.get())&&boss.getPersistentData().contains(DivineCost.KEY),"wither potion immunity does not block server marker");boss.getPersistentData().getCompound(DivineSeven.MARK).putBoolean("retaliation",true);
  long end=tag.getLong("until");float start=boss.getHealth();h.runAfterDelay(202,()->{try{float loss=start-boss.getHealth();h.assertTrue(Math.abs(loss-actual*20*100)<.05,"exactly 100 pulses at 20 times actual damage; loss="+loss);h.assertTrue(!boss.getPersistentData().contains(DivineCost.KEY),"marker expires instead of being refreshed by its own pulses");h.assertTrue(h.getLevel().getGameTime()>=end,"full ten-second window elapsed");h.succeed();}finally{cleanup(p,boss);}});
 }
 @GameTest(template="empty",timeoutTicks=100) public static void refreshAndCancel(GameTestHelper h){var p=player(h);var cow=EntityType.COW.create(h.getLevel());cow.moveTo(p.getX()+3,p.getY(),p.getZ());h.getLevel().addFreshEntity(cow);try{cow.hurt(p.damageSources().playerAttack(p),.1f);var first=cow.getPersistentData().getCompound(DivineCost.KEY);h.assertTrue(Math.abs(first.getFloat("damage")-1)<.001,"records the empowered hit");cow.invulnerableTime=0;cow.hurt(p.damageSources().playerAttack(p),.2f);var next=cow.getPersistentData().getCompound(DivineCost.KEY);h.assertTrue(Math.abs(next.getFloat("damage")-2)<.001&&next.getInt("pulses")==0,"new hit replaces rather than stacks timer");DivineAnchor.unlock(p);p.setItemSlot(EquipmentSlot.HEAD,ItemStack.EMPTY);DivineCost.tick(cow);h.assertTrue(!cow.getPersistentData().contains(DivineCost.KEY),"unequip cancels remaining pulses");h.succeed();}finally{cleanup(p,cow);}}
}
