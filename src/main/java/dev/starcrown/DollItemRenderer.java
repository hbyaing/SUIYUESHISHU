package dev.starcrown;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.*;
import net.minecraft.world.item.*;
import com.mojang.blaze3d.vertex.PoseStack;
public class DollItemRenderer extends BlockEntityWithoutLevelRenderer {
 static DollItemRenderer INSTANCE;
 public static DollItemRenderer instance(){if(INSTANCE==null)INSTANCE=new DollItemRenderer();return INSTANCE;}
 DollItemRenderer(){super(Minecraft.getInstance().getBlockEntityRenderDispatcher(),Minecraft.getInstance().getEntityModels());}
 @Override public void renderByItem(ItemStack stack,ItemDisplayContext context,PoseStack p,MultiBufferSource b,int light,int overlay){
  var mc=Minecraft.getInstance();
  boolean thirdPersonHand=context==ItemDisplayContext.THIRD_PERSON_LEFT_HAND||context==ItemDisplayContext.THIRD_PERSON_RIGHT_HAND;
  float raised=thirdPersonHand?DollAnimation.jumpAmount(DollAnimation.renderedPlayer(),mc.getFrameTime()):0;
  if(stack.is(StarcrownDoll.SELF_ITEM.get())){
   if(context!=ItemDisplayContext.HEAD){
    var player=thirdPersonHand&&DollAnimation.renderedPlayer() instanceof AbstractClientPlayer holder?holder:mc.player;
    SelfDollRenderer.renderForPlayer(player,p,b,light,overlay,raised);
   }
   return;
  }
  p.pushPose();
  if(context==ItemDisplayContext.GUI&&StarcrownDoll.isYule(stack))DollRenderer.renderInventoryBody(StarcrownDoll.dollState(stack),p,b,light,overlay);
  else DollRenderer.renderBody(StarcrownDoll.dollState(stack),p,b,light,overlay,raised);
  p.translate(.5,0,.5);var level=Minecraft.getInstance().level;double t=level==null?0:(level.getGameTime()+Minecraft.getInstance().getFrameTime())/20.;
  DollRenderer.renderEffects(t,p,b,StarcrownDoll.isChengran(stack));p.popPose();
 }
}
