package dev.starcrown;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderBlockScreenEffectEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
@Mod.EventBusSubscriber(modid=StarcrownDoll.ID,value=Dist.CLIENT)
public final class DivineVisibilityClient {
 @SubscribeEvent(priority=net.minecraftforge.eventbus.api.EventPriority.LOWEST,receiveCanceled=true)
 public static void hand(net.minecraftforge.client.event.RenderHandEvent event){
  var player=net.minecraft.client.Minecraft.getInstance().player;
  if(player!=null&&DivineWill.wearing(player)&&!StarcrownDoll.isDoll(event.getItemStack())&&!RiceFeast.active(player)
          &&!FateClient.controlsHand(player,event.getHand(),event.getPartialTick()))event.setCanceled(false);
 }
 @SubscribeEvent(priority=net.minecraftforge.eventbus.api.EventPriority.LOWEST,receiveCanceled=true)
 public static void player(net.minecraftforge.client.event.RenderPlayerEvent.Pre event){
  if(DivineWill.wearing(event.getEntity())){
   event.setCanceled(false);
   if(!DollCameraVisibility.isFirstPersonBodyPass(event.getEntity()))event.getRenderer().getModel().setAllVisible(true);
  }
 }
 @SubscribeEvent(priority=net.minecraftforge.eventbus.api.EventPriority.LOWEST,receiveCanceled=true)
 public static void living(net.minecraftforge.client.event.RenderLivingEvent.Pre<?,?> event){
  if(event.getEntity() instanceof net.minecraft.world.entity.player.Player p&&DivineWill.wearing(p)){
   event.setCanceled(false);
   // Preserve visibility through invisibility effects, but respect the casting arm mask.
   if(!DollCameraVisibility.isFirstPersonBodyPass(p)&&event.getRenderer().getModel() instanceof net.minecraft.client.model.PlayerModel<?> model)model.setAllVisible(true);
  }
 }
 @SubscribeEvent public static void overlay(RenderBlockScreenEffectEvent event){
  if(event.getOverlayType()==RenderBlockScreenEffectEvent.OverlayType.FIRE&&DivineWill.wearing(event.getPlayer()))event.setCanceled(true);
 }
}
