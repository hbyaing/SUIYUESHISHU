package dev.starcrown;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.math.Axis;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderTooltipEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Fixed studio presentation; no controls or gameplay state are introduced. */
@Mod.EventBusSubscriber(modid=StarcrownDoll.ID,value=Dist.CLIENT)
public final class YuleItemTooltip {
 static final int[] COLORS={0xffc4de,0xffdeaa,0xffffff,0xffd1e5,0xeac387,0xffffff};
 static final int[] TEXT_COLORS={0xff90c9,0xffd078,0xc6a5ff,0xffa3cb,0xf4cc86};
 private static final ResourceLocation ART=new ResourceLocation(StarcrownDoll.ID,"textures/gui/yule_poses.png");
 private static final ResourceLocation FISH=new ResourceLocation(StarcrownDoll.ID,"textures/item/fishing_god_fish.png");
 private static final long EPOCH=System.nanoTime();
 @SubscribeEvent public static void render(RenderTooltipEvent.Pre event){
  if(!event.getItemStack().is(StarcrownDoll.YULE_ITEM.get()))return;
  event.setCanceled(true);
  if(TooltipReading.pinned())return;
  draw(event.getGraphics(),event.getX(),event.getY(),event.getScreenWidth(),event.getScreenHeight(),false,event.getItemStack().getHoverName());
 }
 public static float raisedAlpha(double t){double phase=t%4.4;return (float)(phase<2.2?Math.max(0,1-phase/.45):Math.min(1,(phase-2.2)/.45));}
 public static void draw(GuiGraphics g,int mx,int my,int sw,int sh,boolean expanded,Component name){
  TooltipReading.track(mx,my,name);
  var font=Minecraft.getInstance().font;
  int width=Math.max(90,Math.min(expanded?340:226,sw-24));
  var layout=TooltipLayouts.get("yule",width,expanded,name,()->{
  List<FormattedCharSequence> rows=new ArrayList<>();
  rows.addAll(font.split(name.copy().withStyle(s->s.withBold(false)),width));
  rows.addAll(font.split(Component.translatable("tooltip.starcrowndoll.yule.divinity"),width));
  rows.addAll(font.split(Component.translatable("tooltip.starcrowndoll.yule.lore"),width));
  int headerLines=rows.size();
  if(expanded){
   List<Component> descriptions=new ArrayList<>();DollClient.yuleTooltip(descriptions,true);
   for(var c:descriptions){rows.addAll(font.split(c,width));}
  }
  rows.addAll(font.split(Component.translatable("tooltip.starcrowndoll.shift"),width));
  return new TooltipLayouts.Layout<>(List.copyOf(rows),headerLines);
  });
  var reading=TooltipReading.select(layout.lines(),layout.header(),sh,"yule",expanded);var lines=reading.lines();
  int rowHeight=TooltipReading.ROW_HEIGHT;
  int w=width+16,h=16+lines.size()*rowHeight+(expanded?15:0);
  float scale=1;
  float x=Math.max(8,Math.min(sw-w*scale-8,mx+12));
  if(mx+12+w*scale>sw-8)x=Math.max(8,mx-w*scale-12);
  float y=Math.max(8,Math.min(sh-h*scale-8,my-10));
  double t=(System.nanoTime()-EPOCH)/1e9;
  g.pose().pushPose();g.pose().translate(0,0,400);
  try{
   float ph=Math.min(sh*.92f,Math.max(180,w*1.28f))*1.3f;
   ph=Math.min(sh-4,ph);float pw=ph*.75f;
   float px=Math.max(2,Math.min(sw-pw-2,x+(w*scale-pw)/2));
   float py=Math.max(2,Math.min(sh-ph-2,y-ph*.49f));
   float mix=raisedAlpha(t);
   pose(g,px-7,py+2,pw,ph,t-.22,.2f,mix);
   pose(g,px,py,pw,ph,t,1,mix);
   g.pose().pushPose();g.pose().translate(x,y,2);g.pose().scale(scale,scale,1);
   g.fillGradient(0,0,w,h,0xad523046,0xad392436);
   frame(g,w,h,t);
   int ly=7;
   for(int i=0;i<lines.size();i++){
    if(expanded)TooltipReading.text(g,lines.get(i),8,ly,TEXT_COLORS,t);
    else DollTooltip.gradientLine(g,lines.get(i),8,ly,3,t*(.5/.36),scale,x,y);
    ly+=rowHeight;
   }
   if(expanded)g.drawString(font,TooltipReading.footer(reading),8,h-13,0xfff4db,false);
   g.pose().popPose();g.flush();
  }finally{g.setColor(1,1,1,1);RenderSystem.defaultBlendFunc();g.pose().popPose();}
 }
 private static void pose(GuiGraphics g,float x,float y,float w,float h,double t,float alpha,float mix){
  g.pose().pushPose();g.pose().translate(x+w/2,y+h*.85,0);
  g.pose().mulPose(Axis.ZP.rotationDegrees((float)((1-Math.cos(t*Math.PI*2/3))*1.5)));
  blit(g,-w/2,-h*.85f,w,h,0,alpha*(1-mix));blit(g,-w/2,-h*.85f,w,h,1,alpha*mix);g.pose().popPose();
 }
    private static void blit(GuiGraphics g, float x, float y, float w, float h, int pose, float alpha) {
        if (alpha <= 0.0f) {
            return;
        }
        g.pose().pushPose();
        g.pose().translate(x, y, 0.0f);
        g.pose().scale(w / 768.0f, h / 1024.0f, 1.0f);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        g.setColor(1.0f, 1.0f, 1.0f, alpha);
        g.blit(ART, 0, 0, (float)(pose * 768), 0.0f, 768, 1024, 1536, 1024);
        g.pose().pushPose();
        g.pose().translate((pose == 0 ? 0.6 : 0.5) * 768.0, (pose == 0 ? 0.46 : 0.16) * 1024.0, 0.0);
        g.pose().mulPose(Axis.ZP.rotationDegrees(pose == 0 ? 53.0f : 45.0f));
        float fishWidth = (pose == 0 ? 0.58f : 0.56f) * 768.0f;
        g.pose().scale(fishWidth / 1280.0f, fishWidth / 1280.0f, 1.0f);
        g.blit(FISH, -640, -640, 0.0f, 0.0f, 1280, 1280, 1280, 1280);
        g.pose().popPose();
        if (pose == 0) {
            YuleItemTooltip.hand(g, pose, 0.37f, 0.49f, 0.53f, 0.61f);
            YuleItemTooltip.hand(g, pose, 0.69f, 0.49f, 0.84f, 0.61f);
        } else {
            YuleItemTooltip.hand(g, pose, 0.17f, 0.18f, 0.33f, 0.3f);
            YuleItemTooltip.hand(g, pose, 0.6f, 0.17f, 0.76f, 0.29f);
        }
        g.flush();
        g.setColor(1.0f, 1.0f, 1.0f, 1.0f);
        g.pose().popPose();
    }

    private static void hand(GuiGraphics g, int pose, float left, float top, float right, float bottom) {
        int x = Math.round(left * 768.0f);
        int y = Math.round(top * 1024.0f);
        int w = Math.round(right * 768.0f) - x;
        int h = Math.round(bottom * 1024.0f) - y;
        g.blit(ART, x, y, (float)(pose * 768 + x), (float)y, w, h, 1536, 1024);
    }

 private static void frame(GuiGraphics g,int w,int h,double t){
  g.drawManaged(()->{
  // Full-width horizontal strips own all four corners; vertical strips meet them exactly.
  int outerWidth=w+4,perimeter=2*(outerWidth+h);
  for(int d=0;d<perimeter;d++){
   int x,y,dx,dy;
   if(d<outerWidth){x=d-2;y=-2;dx=1;dy=2;}
   else if(d<outerWidth+h){x=w;y=d-outerWidth;dx=2;dy=1;}
   else if(d<2*outerWidth+h){x=w+1-(d-outerWidth-h);y=h;dx=1;dy=2;}
   else{x=-2;y=h-1-(d-2*outerWidth-h);dx=2;dy=1;}
   int c=0xff000000|DollTooltip.sample(COLORS,(float)(d/(double)perimeter-t/3.8));
   g.fill(x,y,x+dx,y+dy,c);
  }
  int inner=0xff000000|DollTooltip.sample(COLORS,(float)(t/3));
  g.fill(2,2,w-2,3,inner);g.fill(2,h-3,w-2,h-2,inner);g.fill(2,3,3,h-3,inner);g.fill(w-3,3,w-2,h-3,inner);
  for(int x:new int[]{10,w-24}){
   g.fill(x,-4,x+6,1,0xffe9b3cb);g.fill(x+7,-4,x+13,1,0xffffe9e0);g.fill(x+5,-3,x+8,2,0xffedc385);
  }
  g.fill(2,h-5,4,h-3,0xfffff2dd);g.fill(w-4,h-5,w-2,h-3,0xfffff2dd);
  });
 }
}
