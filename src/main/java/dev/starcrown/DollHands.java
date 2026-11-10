package dev.starcrown;

import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** The palm and doll share a view-space root so equip/swing cannot separate them. */
@Mod.EventBusSubscriber(modid=StarcrownDoll.ID,value=Dist.CLIENT)
public final class DollHands {
 @SubscribeEvent public static void render(RenderHandEvent event){
  if(Minecraft.getInstance().player!=null&&RiceFeast.active(Minecraft.getInstance().player))return;
  if(!StarcrownDoll.isDoll(event.getItemStack()))return;
  var mc=Minecraft.getInstance();var player=mc.player;if(player==null)return;
  event.setCanceled(true);
  HumanoidArm arm=event.getHand()==InteractionHand.MAIN_HAND?player.getMainArm():player.getMainArm().getOpposite();
  int side=arm==HumanoidArm.RIGHT?1:-1;
  var pose=event.getPoseStack();var buffers=event.getMultiBufferSource();int light=event.getPackedLight();
  pose.pushPose();
  pose.translate(side*.46,-.31-event.getEquipProgress()*.6-Math.sin(event.getSwingProgress()*Math.PI)*.06,-.92);
  if((!player.isInvisible()||DivineWill.wearing(player))&&mc.getEntityRenderDispatcher().getRenderer(player) instanceof PlayerRenderer renderer){
   pose.pushPose();pose.translate(0,-.12,.12);pose.mulPose(Axis.XP.rotationDegrees(-75));
   pose.translate(side*.3125,-.68,0);
   if(arm==HumanoidArm.RIGHT)renderer.renderRightHand(pose,buffers,light,player);else renderer.renderLeftHand(pose,buffers,light,player);
   pose.popPose();
  }
  pose.pushPose();pose.mulPose(Axis.YP.rotationDegrees(side==1?155:205));pose.scale(.4f,.4f,.4f);pose.translate(-.5,0,-.5);
  var stack=event.getItemStack();
  if(stack.is(StarcrownDoll.SELF_ITEM.get())){
   SelfDollRenderer.renderForPlayer(player,pose,buffers,light,OverlayTexture.NO_OVERLAY,DollAnimation.jumpAmount(player,event.getPartialTick()));
  }else{
   DollRenderer.renderBody(StarcrownDoll.dollState(stack),pose,buffers,light,OverlayTexture.NO_OVERLAY,DollAnimation.jumpAmount(player,event.getPartialTick()));
   pose.translate(.5,0,.5);DollRenderer.renderEffects((player.level().getGameTime()+event.getPartialTick())/20.,pose,buffers,StarcrownDoll.isChengran(stack));
  }
  pose.popPose();pose.popPose();
 }
}
