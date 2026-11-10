package dev.starcrown;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.world.entity.Mob;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.ArrayDeque;

@Mod.EventBusSubscriber(modid=StarcrownDoll.ID,value=Dist.CLIENT)
public final class DanceVisuals {
 // Pair each pushed pose with its render event; do not disturb other renderers' transforms.
 private record RenderPose(net.minecraft.world.entity.LivingEntity entity,PoseStack pose){}
 private static final ArrayDeque<RenderPose> POSES=new ArrayDeque<>();
 public static void animate(PoseStack pose,double ticks){
  double wave=Math.sin(ticks*Math.PI/8);
  float height=(float)(.76+.24*wave);
  float width=(float)(1+.12*(1-wave));
  pose.translate(0,.025*(1+wave),0);pose.scale(width,height,width);
 }
 public static void animateMob(PoseStack pose,double ticks){
  animate(pose,ticks);
  pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees((float)((ticks%40)*9)));
 }
 @SubscribeEvent(priority=net.minecraftforge.eventbus.api.EventPriority.LOWEST)
 public static void before(RenderLivingEvent.Pre<?,?> event){
  if(event.getEntity() instanceof Mob mob&&(DollDance.controlled(mob)||DollDance.nearCalm(mob.level(),mob.position()))){
   PoseStack pose=event.getPoseStack();pose.pushPose();POSES.push(new RenderPose(mob,pose));
   animateMob(pose,mob.level().getGameTime()+event.getPartialTick());
  }
 }
 @SubscribeEvent(priority=net.minecraftforge.eventbus.api.EventPriority.HIGHEST)
 public static void after(RenderLivingEvent.Post<?,?> event){
  if(!POSES.isEmpty()&&POSES.peek().entity()==event.getEntity()&&POSES.peek().pose()==event.getPoseStack()){POSES.pop().pose().popPose();}
 }
}
