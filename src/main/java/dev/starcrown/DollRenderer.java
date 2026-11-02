package dev.starcrown;
import com.mojang.blaze3d.vertex.*;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.blockentity.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
@Mod.EventBusSubscriber(modid=StarcrownDoll.ID,bus=Mod.EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
public class DollRenderer implements BlockEntityRenderer<StarcrownDoll.DollEntity>{
 static final ResourceLocation TEX=new ResourceLocation(StarcrownDoll.ID,"textures/block/white.png");
 public DollRenderer(BlockEntityRendererProvider.Context c){}
 @SubscribeEvent public static void setup(EntityRenderersEvent.RegisterRenderers e){e.registerBlockEntityRenderer(StarcrownDoll.TYPE.get(),DollRenderer::new);}
 @SubscribeEvent public static void blinkModels(net.minecraftforge.client.event.ModelEvent.RegisterAdditional e){
  for(String prefix:new String[]{"doll","yule"})for(String part:new String[]{"body","right_arm","left_arm","head","head_half","head_closed"})e.register(new ResourceLocation(StarcrownDoll.ID,"block/"+prefix+"_part_"+part));
  e.register(new ResourceLocation(StarcrownDoll.ID,"block/doll_blink_half"));
  e.register(new ResourceLocation(StarcrownDoll.ID,"block/doll_blink_closed"));
 }
 public static int blinkFrame(double ticks){
  double phase=((ticks%96)+96)%96;
  if(phase<1.5||phase>=4.5&&phase<6)return 1;
  return phase<4.5?2:0;
 }
 public void render(StarcrownDoll.DollEntity be,float partial,PoseStack p,MultiBufferSource buffers,int light,int overlay){
 double t=(be.getLevel().getGameTime()+partial)/20.;p.pushPose();p.translate(.5,0,.5);
 if(be.isPlaying())DanceVisuals.animate(p,be.getLevel().getGameTime()+partial);
 p.pushPose();p.translate(-.5,0,-.5);renderBody(be.getBlockState(),p,buffers,light,overlay);p.popPose();
 p.mulPose(Axis.YP.rotationDegrees(180-be.getBlockState().getValue(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING).toYRot()));renderEffects(t,p,buffers,!be.getBlockState().is(StarcrownDoll.YULE_DOLL.get()));p.popPose();}
 public static void renderBody(net.minecraft.world.level.block.state.BlockState state,PoseStack pose,MultiBufferSource buffers,int light,int overlay){renderBody(state,pose,buffers,light,overlay,0);}
 public static void renderBody(net.minecraft.world.level.block.state.BlockState state,PoseStack pose,MultiBufferSource buffers,int light,int overlay,float raised){
  var mc=net.minecraft.client.Minecraft.getInstance();
  double ticks=mc.level==null?net.minecraft.Util.getMillis()/50.0:mc.level.getGameTime()+mc.getFrameTime();
  int frame=blinkFrame(ticks);
  pose.pushPose();pose.translate(.5,0,.5);
  pose.mulPose(Axis.YP.rotationDegrees(180-state.getValue(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING).toYRot()));pose.translate(-.5,0,-.5);
  part("body",state,pose,buffers,light,overlay);
  boolean holdingFish=state.is(StarcrownDoll.YULE_DOLL.get());
  pose.pushPose();if(holdingFish){pose.translate(.5,9./16,10./16);pose.mulPose(Axis.YP.rotationDegrees((float)(Math.sin(ticks*.03)*1.4324)));pose.mulPose(Axis.XP.rotationDegrees(-6));pose.mulPose(Axis.ZP.rotationDegrees(-7));pose.translate(-.5,-9./16,-10./16);}else DollAnimation.headMotion(pose,ticks,.5,9./16,10./16);
  part(frame==0?"head":frame==1?"head_half":"head_closed",state,pose,buffers,light,overlay);pose.popPose();
  pose.pushPose();
  if(holdingFish){pose.translate(0,Math.sin(ticks*.09)*.004,0);pose.translate(.5,9./16,10./16);pose.mulPose(Axis.XP.rotationDegrees(35*raised));pose.translate(-.5,-9./16,-10./16);}
  for(boolean right:new boolean[]{true,false}){
   pose.pushPose();double x=right?5./16:11./16;
   pose.translate(x+(holdingFish?(right?.04:-.07):0),9./16-(holdingFish?.025:0),10./16-(holdingFish?.025:0));
   if(holdingFish){pose.mulPose(Axis.ZP.rotationDegrees(right?28:-28));pose.mulPose(Axis.XP.rotationDegrees(50));}
   else pose.mulPose(Axis.ZP.rotationDegrees((right?-135:135)*raised));
   pose.translate(-x,-9./16,-10./16);
   part(right?"right_arm":"left_arm",state,pose,buffers,light,overlay);pose.popPose();
  }
  if(holdingFish){pose.pushPose();pose.translate(.5,7.35/16,3.9/16);pose.mulPose(Axis.YP.rotationDegrees(15));pose.mulPose(Axis.ZP.rotationDegrees(73));pose.mulPose(Axis.YP.rotationDegrees(180));pose.scale(.72f,.72f,.72f);mc.getItemRenderer().renderStatic(new net.minecraft.world.item.ItemStack(StarcrownDoll.FISHING_GOD_FISH.get()),net.minecraft.world.item.ItemDisplayContext.NONE,light,overlay,pose,buffers,mc.level,0);pose.popPose();}
  pose.popPose();
  pose.popPose();
 }
 private static void part(String name,net.minecraft.world.level.block.state.BlockState state,PoseStack pose,MultiBufferSource buffers,int light,int overlay){
  var mc=net.minecraft.client.Minecraft.getInstance();boolean yule=state.is(StarcrownDoll.YULE_DOLL.get());var model=mc.getModelManager().getModel(new ResourceLocation(StarcrownDoll.ID,"block/"+(yule?"yule":"doll")+"_part_"+name));
  mc.getBlockRenderer().getModelRenderer().renderModel(pose.last(),buffers.getBuffer(yule?RenderType.translucent():RenderType.cutout()),state,model,1,1,1,light,overlay);
 }
 public static void renderEffects(double t,PoseStack p,MultiBufferSource buffers){renderEffects(t,p,buffers,true);}
 public static void renderEffects(double t,PoseStack p,MultiBufferSource buffers,boolean crown){
 if(!crown)return;
 p.pushPose();DollAnimation.headMotion(p,t*20,0,9./16,.125);
 if(crown)CrownMesh.render(p,buffers);
 VertexConsumer v=buffers.getBuffer(RenderType.entityTranslucentEmissive(TEX));
 for(int k=0;k<2;k++)for(int i=0;i<128;i++){
 double a=i*Math.PI/64+t*(k==0?.18:-.13),b=a+Math.PI/64,r=.36+k*.045;
 line(v,p,(float)(Math.cos(a)*r),(float)(1.16+k*.14+Math.sin(a)*.035),(float)(.125+Math.sin(a)*r),(float)(Math.cos(b)*r),(float)(1.16+k*.14+Math.sin(b)*.035),(float)(.125+Math.sin(b)*r),.003f,.8f,.4f,1,.9f);
 }
 for(int i=0;i<12;i++){
 double a=i*Math.PI/6+t*.07;float x=(float)(Math.cos(a)*.43),z=(float)(.125+Math.sin(a)*.43),y=(float)(.55+(i%4)*.2+.02*Math.sin(t+i)),s=.025f+(i%3)*.008f;
 line(v,p,x,y,z,x,1.28f,z,.001f,.8f,.4f,1,.45f);
 line(v,p,x-s,y,z,x+s,y,z,.003f,1,.7f,1,.9f);
 line(v,p,x,y-s*1.8f,z,x,y+s*1.8f,z,.003f,1,.7f,1,.9f);
 line(v,p,x,y,z-s,x,y,z+s,.003f,1,.7f,1,.9f);
 }
 VertexConsumer glow=buffers.getBuffer(RenderType.entityTranslucentEmissive(new ResourceLocation(StarcrownDoll.ID,"textures/block/aura.png")));
 for(int i=0;i<12;i++){
 double a=i*Math.PI/6+t*.07;float x=(float)(Math.cos(a)*.43),z=(float)(.125+Math.sin(a)*.43),y=(float)(.55+(i%4)*.2+.02*Math.sin(t+i));
 p.pushPose();p.translate(x,y,z);p.mulPose(new org.joml.Quaternionf().setFromUnnormalized(p.last().normal()).conjugate());p.mulPose(net.minecraft.client.Minecraft.getInstance().getEntityRenderDispatcher().cameraOrientation());
 float size=.075f+(i%3)*.018f,alpha=(float)(.65+.2*Math.sin(t*2+i));
 sprite(glow,p,-size,-size,0,1,alpha);sprite(glow,p,size,-size,1,1,alpha);sprite(glow,p,size,size,1,0,alpha);sprite(glow,p,-size,size,0,0,alpha);p.popPose();
 }
 p.popPose();
 }
 static void sprite(VertexConsumer v,PoseStack p,float x,float y,float u,float w,float a){v.vertex(p.last().pose(),x,y,0).color(1,1,1,a).uv(u,w).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(15728880).normal(p.last().normal(),0,0,1).endVertex();}
 static void line(VertexConsumer v,PoseStack p,float x,float y,float z,float X,float Y,float Z,float w,float r,float g,float b,float a){
 for(int plane=0;plane<2;plane++){
 float dx=plane==0?w:0,dy=plane==0?0:w;
 vert(v,p,x-dx,y-dy,z,r,g,b,a);vert(v,p,X-dx,Y-dy,Z,r,g,b,a);vert(v,p,X+dx,Y+dy,Z,r,g,b,a);vert(v,p,x+dx,y+dy,z,r,g,b,a);
 vert(v,p,x+dx,y+dy,z,r,g,b,a);vert(v,p,X+dx,Y+dy,Z,r,g,b,a);vert(v,p,X-dx,Y-dy,Z,r,g,b,a);vert(v,p,x-dx,y-dy,z,r,g,b,a);
 }}
 static void vert(VertexConsumer v,PoseStack p,float x,float y,float z,float r,float g,float b,float a){v.vertex(p.last().pose(),x,y,z).color(r,g,b,a).uv(.5f,.5f).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(15728880).normal(p.last().normal(),0,1,0).endVertex();}
}


