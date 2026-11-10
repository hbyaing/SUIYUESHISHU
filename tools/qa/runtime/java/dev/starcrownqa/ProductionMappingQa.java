package dev.starcrownqa;
import java.nio.file.*;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.*;
import dev.starcrown.*;
@Mod.EventBusSubscriber(modid="starcrownqa",value=net.minecraftforge.api.distmarker.Dist.CLIENT)
public final class ProductionMappingQa {
 static boolean started;static int ticks;
 static void result(String text){try{Files.writeString(Path.of("production-mapping-results.txt"),text);}catch(Exception ex){throw new RuntimeException(ex);}}
 @SubscribeEvent public static void tick(TickEvent.ClientTickEvent e){if(e.phase!=TickEvent.Phase.END||!Files.exists(Path.of("enable-production-mapping-qa")))return;var mc=Minecraft.getInstance();if(mc.player==null||mc.getSingleplayerServer()==null)return;if(!started){started=true;var id=mc.player.getUUID();mc.getSingleplayerServer().execute(()->{try{var p=mc.getSingleplayerServer().getPlayerList().getPlayer(id);var level=p.serverLevel();DivineAnchor.unlock(p);p.getInventory().clearContent();p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);p.setItemSlot(EquipmentSlot.HEAD,new ItemStack(StarcrownDoll.YULE_ITEM.get()));p.setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(Items.IRON_PICKAXE));if(!YuleHumility.active(p))throw new AssertionError("Yule equip blocked: "+p.getItemBySlot(EquipmentSlot.HEAD));var pos=p.blockPosition().offset(2,1,0);var old=level.getBlockState(pos);long before=YuleDiligence.units(p);try{level.setBlockAndUpdate(pos,net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());var mode=new net.minecraft.server.level.ServerPlayerGameMode(p);mode.changeGameModeForPlayer(net.minecraft.world.level.GameType.SURVIVAL);if(!mode.destroyBlock(pos))throw new AssertionError("native destroyBlock returned false");if(YuleDiligence.units(p)!=before+1)throw new AssertionError("native diligence reward not recorded");result("PASS production Forge world loaded; game-mode constructor, native destroyBlock and diligence reward verified.\nALL_PASS\n");}finally{level.setBlockAndUpdate(pos,old);p.setItemSlot(EquipmentSlot.HEAD,ItemStack.EMPTY);}}catch(Throwable ex){result("FAIL "+ex);ex.printStackTrace();}});}if(++ticks>40&&Files.exists(Path.of("production-mapping-results.txt")))mc.stop();}
}
