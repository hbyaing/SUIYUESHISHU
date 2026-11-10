package dev.starcrownqa;
import dev.starcrown.*;
import java.nio.file.*;
import net.minecraft.core.*;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.animal.*;
import net.minecraft.world.entity.monster.*;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.item.*;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.*;
import net.minecraft.world.effect.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.event.TickEvent;
@Mod.EventBusSubscriber(modid="starcrownqa")
public final class ToleranceQa {
 static int tick;static volatile boolean done,failed;static Skeleton existing;static ServerPlayer wearer;static BlockPos lava=new BlockPos(6,81,-2);
 static void log(String s){try{Files.writeString(Path.of("tolerance-results.txt"),s+"\n",StandardOpenOption.CREATE,StandardOpenOption.APPEND);}catch(Exception e){throw new RuntimeException(e);}}
 static void check(boolean b,String s){if(!b)throw new AssertionError(s);log("PASS "+s);}
 static Cow cow(ServerLevel l,double x,double z){var c=EntityType.COW.create(l);c.moveTo(x,81,z);c.setNoAi(true);l.addFreshEntity(c);return c;}
 static void wear(boolean active){wearer.setItemSlot(EquipmentSlot.HEAD,active?new ItemStack(StarcrownDoll.YULE_ITEM.get()):ItemStack.EMPTY);}
 @SubscribeEvent public static void level(TickEvent.LevelTickEvent event){
  if(event.phase!=TickEvent.Phase.END||!(event.level instanceof ServerLevel l)||l!=l.getServer().overworld()||!Files.exists(Path.of("enable-tolerance-qa"))||l.getServer().getPlayerList().getPlayers().isEmpty()||done||failed)return;
  try{tick++;
   if(tick==20){Files.writeString(Path.of("tolerance-results.txt"),"");wearer=l.getServer().getPlayerList().getPlayers().get(0);DivineAnchor.unlock(wearer);wear(false);wearer.removeAllEffects();wearer.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);wearer.teleportTo(l,.5,81,4.5,180,10);
    for(int x=-6;x<=120;x++)for(int z=-5;z<=8;z++){l.setBlockAndUpdate(new BlockPos(x,80,z),Blocks.STONE.defaultBlockState());for(int y=81;y<86;y++)l.setBlockAndUpdate(new BlockPos(x,y,z),Blocks.AIR.defaultBlockState());}l.setDayTime(6000);
    existing=EntityType.SKELETON.create(l);existing.moveTo(3,81,3);existing.setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(Items.BOW));l.addFreshEntity(existing);wear(true);
    var zombie=EntityType.ZOMBIE.create(l);zombie.moveTo(4,81,4);zombie.setCustomName(net.minecraft.network.chat.Component.literal("tolerance-zombie"));check(!l.addFreshEntity(zombie),"new zombie replaced in join event");check(l.getEntitiesOfClass(Villager.class,zombie.getBoundingBox().inflate(1)).stream().anyMatch(v->v.getCustomName()!=null&&v.getCustomName().getString().equals("tolerance-zombie")),"zombie instantly restored as named villager");
    var skeleton=EntityType.SKELETON.create(l);skeleton.moveTo(5,81,3);skeleton.setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(Items.BOW));l.addFreshEntity(skeleton);check(YuleTolerance.friendly(skeleton)&&skeleton.getMainHandItem().isEmpty()&&!skeleton.isNoAi(),"new skeleton disarmed and friendly without freezing AI");
    var outside=EntityType.ZOMBIE.create(l);outside.moveTo(49,81,4.5);l.addFreshEntity(outside);check(!YuleTolerance.friendly(outside)&&!outside.isRemoved(),"outside 48-block radius unchanged");outside.discard();
    var c=cow(l,2,2);float hp=c.getHealth();
    check(c.hurt(l.damageSources().playerAttack(wearer),4)&&c.getHealth()==hp-4,"outgoing attack preserves full damage");
    var pursuer=EntityType.ZOMBIE.create(l);pursuer.moveTo(100,81,3);l.addFreshEntity(pursuer);pursuer.setTarget(skeleton);check(pursuer.getTarget()==null&&!skeleton.canBeSeenAsEnemy(),"pacified mobs no longer treated as enemies by other AI");pursuer.discard();
    skeleton.setTarget(c);check(skeleton.getTarget()==null&&!skeleton.doHurtTarget(c),"pacified mob cannot target or melee anyone");check(!c.hurt(l.damageSources().mobAttack(skeleton),20)&&c.getHealth()==hp-4,"pacified mob damage sources harmless");
    wearer.setHealth(10);float before=wearer.getHealth();check(!wearer.hurt(l.damageSources().mobAttack(skeleton),100)&&wearer.getHealth()==before,"incoming attack canceled before health loss");for(var who:new LivingEntity[]{wearer,skeleton})check(who.getEffect(MobEffects.REGENERATION).getAmplifier()==9&&who.getEffect(MobEffects.ABSORPTION).getAmplifier()==9&&who.getEffect(MobEffects.REGENERATION).getDuration()==200,"both sides receive Regeneration X and Absorption X for 10 seconds");
    wearer.setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(Items.LAVA_BUCKET));
    check(((BucketItem)Items.LAVA_BUCKET).emptyContents(wearer,l,lava,null,new ItemStack(Items.LAVA_BUCKET)),"lava placement works");
    var lavaCow=cow(l,6.5,-1.5);float lavaHp=lavaCow.getHealth();
    lavaCow.lavaHurt();check(lavaCow.getHealth()<lavaHp,"placed lava deals normal damage");
    wearer.setItemSlot(EquipmentSlot.MAINHAND,ItemStack.EMPTY);
   }
   if(tick==80){check(YuleTolerance.friendly(existing)&&existing.getMainHandItem().isEmpty(),"existing hostile mob pacified");done=true;log("SERVER_PASS");}
  }catch(Throwable t){log("FAIL "+t);t.printStackTrace();failed=true;}
 }
 @Mod.EventBusSubscriber(modid="starcrownqa",value=net.minecraftforge.api.distmarker.Dist.CLIENT)
 public static class Client {
  static int ticks;static boolean finished;
  @SubscribeEvent public static void tick(TickEvent.ClientTickEvent e)throws Exception{
   if(e.phase!=TickEvent.Phase.END||!Files.exists(Path.of("enable-tolerance-qa")))return;var mc=net.minecraft.client.Minecraft.getInstance();mc.options.pauseOnLostFocus=false;if(mc.player==null)return;ticks++;
   if(ticks==1){mc.setScreen(null);mc.mouseHandler.releaseMouse();}
   if(failed){mc.stop();return;}
   if(done&&!finished){finished=true;check(net.minecraft.network.chat.Component.translatable("tooltip.starcrowndoll.yule.tolerance.name").getString().equals("Tolerance")||net.minecraft.network.chat.Component.translatable("tooltip.starcrowndoll.yule.tolerance.name").getString().equals("宽容"),"localized second virtue registered");log("ALL_PASS");mc.stop();}
  }
 }
}
