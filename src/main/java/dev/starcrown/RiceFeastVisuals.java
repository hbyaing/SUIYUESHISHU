package dev.starcrown;

import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.InteractionHand;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid=StarcrownDoll.ID,value=Dist.CLIENT)
public final class RiceFeastVisuals {
 @SubscribeEvent(priority=EventPriority.LOWEST)
 public static void living(RenderLivingEvent.Post<?,?> event){
  var entity=event.getEntity();if(!(RiceFeast.active(entity)||RiceFeast.calmVisual(entity))||entity.isInvisible())return;
  var pose=event.getPoseStack();double time=entity.level().getGameTime()+event.getPartialTick();
  pose.pushPose();
  if(entity instanceof net.minecraft.world.entity.Mob)DanceVisuals.animateMob(pose,time);
  pose.mulPose(Axis.YP.rotationDegrees(-entity.getYHeadRot()));
  pose.translate(0,entity.getEyeHeight()-.22+Math.abs(Math.sin(time*1.5))*.05,.28);
  pose.scale(.42f,.42f,.42f);
  Minecraft.getInstance().getItemRenderer().renderStatic(new ItemStack(StarcrownDoll.WHITE_RICE.get()),ItemDisplayContext.FIXED,event.getPackedLight(),OverlayTexture.NO_OVERLAY,pose,event.getMultiBufferSource(),entity.level(),entity.getId());
  pose.popPose();
 }
 @SubscribeEvent(priority=EventPriority.LOWEST,receiveCanceled=true)
 public static void hand(RenderHandEvent event){
  var mc=Minecraft.getInstance();if(mc.player==null)return;
  boolean original=RiceFeast.active(mc.player);
  if(!original&&!RiceFeast.calmVisual(mc.player))return;
  if(original)event.setCanceled(true);
  if(event.getHand()!=InteractionHand.MAIN_HAND)return;
  var pose=event.getPoseStack();double time=mc.level.getGameTime()+event.getPartialTick();
  pose.pushPose();pose.translate(.12,-.22+Math.abs(Math.sin(time*1.5))*.035,-.48);pose.mulPose(Axis.XP.rotationDegrees(12));pose.scale(.6f,.6f,.6f);
  if(DivineWill.wearing(mc.player)&&mc.getEntityRenderDispatcher().getRenderer(mc.player) instanceof net.minecraft.client.renderer.entity.player.PlayerRenderer renderer){
   pose.pushPose();pose.translate(.18,-.13,.16);pose.mulPose(Axis.XP.rotationDegrees(-75));pose.translate(.3125,-.68,0);
   renderer.renderRightHand(pose,event.getMultiBufferSource(),event.getPackedLight(),mc.player);pose.popPose();
  }
  mc.getItemRenderer().renderStatic(new ItemStack(StarcrownDoll.WHITE_RICE.get()),ItemDisplayContext.FIRST_PERSON_RIGHT_HAND,event.getPackedLight(),OverlayTexture.NO_OVERLAY,pose,event.getMultiBufferSource(),mc.level,0);
  pose.popPose();
 }
}
