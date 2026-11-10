package dev.starcrownqa;
import dev.starcrown.*;
import net.minecraft.gametest.framework.*;
import net.minecraftforge.gametest.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.*;
import net.minecraft.world.effect.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.registries.ForgeRegistries;
@GameTestHolder("starcrownqa") @PrefixGameTestTemplate(false)
public final class PotionFilterTests {
 @GameTest(template="empty",timeoutTicks=100)
 public static void filterApplyPersistenceAndRelease(GameTestHelper h){
  var level=h.getLevel();var p=new ServerPlayer(level.getServer(),level,new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(),"potion-filter"));var c=new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);new io.netty.channel.embedded.EmbeddedChannel(c);c.setProtocol(net.minecraft.network.ConnectionProtocol.PLAY);p.connection=new net.minecraft.server.network.ServerGamePacketListenerImpl(level.getServer(),c,p);level.addNewPlayer(p);
  try{
   p.setItemSlot(EquipmentSlot.HEAD,new ItemStack(StarcrownDoll.ITEM.get()));p.tickCount=20;DivineWill.update(p,true);
   var luck=ForgeRegistries.MOB_EFFECTS.getKey(MobEffects.LUCK);h.assertTrue(p.hasEffect(MobEffects.LUCK),"default benefits granted");
   h.assertTrue(p.getEffect(MobEffects.LUCK).getAmplifier()==254&&p.getEffect(MobEffects.NIGHT_VISION).getAmplifier()==254,"default grants level 255");
   PotionFilter.set(p,luck,false);h.assertTrue(!p.hasEffect(MobEffects.LUCK),"disable removes the current grant immediately");
   DivineWill.update(p,true);h.assertTrue(!p.hasEffect(MobEffects.LUCK),"automatic grant respects filter");
   var settings=DivineSeven.settings(p).copy();h.assertTrue(settings.getCompound("potion_disabled").getBoolean(luck.toString()),"filter stored in clone-safe player settings");
   var serialized=settings.toString();h.assertTrue(net.minecraft.nbt.TagParser.parseTag(serialized).equals(settings),"settings survive serialization");
   PotionFilter.set(p,luck,true);h.assertTrue(p.hasEffect(MobEffects.LUCK),"enable restores automatic benefit");
   var future=ForgeRegistries.MOB_EFFECTS.getKey(CombatQa.FUTURE.get());PotionFilter.set(p,future,false);h.assertTrue(!p.hasEffect(CombatQa.FUTURE.get()),"third-party effect is filterable");
   var night=ForgeRegistries.MOB_EFFECTS.getKey(MobEffects.NIGHT_VISION);PotionFilter.set(p,night,false);h.assertTrue(!p.hasEffect(MobEffects.NIGHT_VISION),"night vision filtering");DivineSeven.action(p,2,0);h.assertTrue(PotionFilter.enabled(p,MobEffects.NIGHT_VISION)&&p.hasEffect(MobEffects.NIGHT_VISION),"existing night button shares settings");
   PotionFilter.set(p,luck,false);DivineAnchor.unlock(p);p.setItemSlot(EquipmentSlot.HEAD,ItemStack.EMPTY);DivineWill.update(p,false);h.assertTrue(p.addEffect(new MobEffectInstance(MobEffects.LUCK,1200)),"unequip releases filtering");
   p.setItemSlot(EquipmentSlot.HEAD,new ItemStack(StarcrownDoll.ITEM.get()));DivineWill.update(p,true);h.assertTrue(p.getEffect(MobEffects.LUCK).getAmplifier()==0&&!PotionFilter.enabled(p,MobEffects.LUCK),"re-equip preserves the external effect and filter choice");
   var poison=ForgeRegistries.MOB_EFFECTS.getKey(MobEffects.POISON);
   var before=DivineSeven.settings(p).copy();
   PotionFilter.set(p,poison,false);
   h.assertTrue(before.equals(DivineSeven.settings(p))&&!PotionFilter.disabled(p).contains(poison.toString()),"negative effects do not have a filter setting");
   PotionFilter.set(p,future,true);
   p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST,1200,2));
   h.assertTrue(p.hasEffect(CombatQa.FUTURE.get())&&p.hasEffect(MobEffects.DAMAGE_BOOST),"automatic and externally applied benefits start active");
   DivineSeven.action(p,DivineSeven.DISABLE_POTIONS_ACTION,0);
   h.assertTrue(p.getActiveEffects().size()==2&&p.hasEffect(MobEffects.LUCK)&&p.getEffect(MobEffects.DAMAGE_BOOST).getAmplifier()==2,"one action clears the grants and preserves external potions");
   p.forceAddEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST,1200),null);
   DivineWill.update(p,true);
   h.assertTrue(p.getEffect(MobEffects.DAMAGE_BOOST).getAmplifier()==0&&!p.hasEffect(MobEffects.NIGHT_VISION),"externally forced effects still work with all grants off");
   PotionFilter.set(p,future,true);
   h.assertTrue(p.hasEffect(CombatQa.FUTURE.get())&&!p.hasEffect(MobEffects.NIGHT_VISION),"a single mod effect can be enabled after bulk disable");
   DivineSeven.action(p,DivineSeven.POTION_LEVEL_ACTION,7);
   h.assertTrue(PotionFilter.level(p)==7&&p.getEffect(CombatQa.FUTURE.get()).getAmplifier()==6,"slider immediately lowers the active grant");
   h.assertTrue(p.getEffect(MobEffects.LUCK).getAmplifier()==0,"slider leaves external amplifier alone");
   DivineSeven.action(p,2,0);
   h.assertTrue(p.getEffect(MobEffects.NIGHT_VISION).getAmplifier()==6,"night vision shares the level setting");
   p.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION,800,1));
   h.assertTrue(p.getEffect(MobEffects.NIGHT_VISION).getAmplifier()==1,"external potion takes precedence over a stronger grant");
   DivineSeven.action(p,DivineSeven.DISABLE_POTIONS_ACTION,0);
   h.assertTrue(p.getActiveEffects().size()==3&&!PotionFilter.enabled(p,CombatQa.FUTURE.get())&&p.hasEffect(MobEffects.NIGHT_VISION),"bulk disable clears grants and preserves active external effects");
   h.assertTrue(!p.addEffect(new MobEffectInstance(MobEffects.POISON,1200)),"negative effect immunity is still active");
   h.succeed();
  }catch(Exception ex){throw new RuntimeException(ex);}finally{DivineAnchor.unlock(p);p.setItemSlot(EquipmentSlot.HEAD,ItemStack.EMPTY);level.removePlayerImmediately(p,Entity.RemovalReason.DISCARDED);c.disconnect(net.minecraft.network.chat.Component.literal("done"));}
 }
}
