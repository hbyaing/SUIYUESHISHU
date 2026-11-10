package dev.starcrown;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.math.Axis;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderTooltipEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Client-only presentation; gameplay and other items retain their existing behavior. */
@Mod.EventBusSubscriber(modid=StarcrownDoll.ID,value=Dist.CLIENT)
public final class DollTooltip {
 private static final ResourceLocation PORTRAIT=new ResourceLocation(StarcrownDoll.ID,"textures/gui/chengran_portrait.png");
 private static final long EPOCH=System.nanoTime();
 private static final int[] STARS={0x78dcff,0xb59aff,0xff91d7,0x91c9ff},NEON={0x64ffff,0x87b0ff,0xff71df,0xcc91ff},GOD={0xffe18b,0xfffaf0,0xff969c};
 public static final String[] KEYS={"skill1","skill1.effect","skill1.protect","skill2","skill2.travel","skill2.build","skill2.immune","skill2.benefits","skill3","skill3.effect","skill4","skill4.effect","skill4.authority","skill5","skill5.effect","skill5.domain","skill6","skill6.effect","skill7","skill7.effect","skill7.verdict","anchor","controls","wear","music","place"};
 private static final String[] TWILIGHT_KEYS={"skill1","skill1.effect","skill2","skill2.effect","skill3","skill3.effect","skill4","skill4.effect","skill5","skill5.effect","skill6","skill6.effect","skill7","skill7.effect","controls","wear","music","place"};
 static String[] keys(boolean twilight){return twilight?TWILIGHT_KEYS:KEYS;}
 static String translation(String key,boolean twilight){return "tooltip.starcrowndoll."+(twilight&&(key.endsWith(".effect")||key.equals("divinity"))?"twilight.":"")+key;}
 private record Line(FormattedCharSequence text,int palette){}
 @SubscribeEvent(priority=EventPriority.HIGHEST) public static void render(RenderTooltipEvent.Pre event){
  if(!StarcrownDoll.isChengran(event.getItemStack()))return;
  event.setCanceled(true);
  if(TooltipReading.pinned())return;
  draw(event.getGraphics(),event.getX(),event.getY(),event.getScreenWidth(),event.getScreenHeight(),false,event.getItemStack().getHoverName(),event.getItemStack().is(StarcrownDoll.TWILIGHT_ITEM.get()));
 }
 public static double sway(double seconds){return (1-Math.cos(seconds*Math.PI*2/2.8))*.5;}
 public static void draw(GuiGraphics g,int mouseX,int mouseY,int screenW,int screenH,boolean expanded,Component name){
  draw(g,mouseX,mouseY,screenW,screenH,expanded,name,false);
 }
 static void draw(GuiGraphics g,int mouseX,int mouseY,int screenW,int screenH,boolean expanded,Component name,boolean twilight){
  TooltipReading.track(mouseX,mouseY,name);
  var font=Minecraft.getInstance().font;
  int maxWidth=Math.max(90,Math.min(expanded?330:230,screenW-24));
  String variant=twilight?"twilight":"chengran";
  String[] keys=keys(twilight);
  var layout=TooltipLayouts.get(variant,maxWidth,expanded,name,()->{
  List<Line> lines=new ArrayList<>();
  add(lines,name.getString(),false,0,maxWidth);
  if(expanded)add(lines,"以神戴冠",false,0,maxWidth);
  add(lines,Component.translatable(translation("divinity",twilight)).getString(),false,2,maxWidth);
  add(lines,tr("lore"),expanded,1,maxWidth);
  int headerLines=lines.size();
  if(expanded)for(int i=0;i<keys.length;){
   String key=keys[i++];boolean heading=key.matches("skill[1-7]");
   add(lines,tr(key),heading,heading?1:0,maxWidth);
   if(heading){
    StringBuilder paragraph=new StringBuilder();
    while(i<keys.length&&keys[i].startsWith(key+".")){if(paragraph.length()>0)paragraph.append(' ');paragraph.append(Component.translatable(translation(keys[i++],twilight)).getString());}
    if(paragraph.length()>0)add(lines,paragraph.toString(),false,0,maxWidth);
   }
  }
  else add(lines,tr("shift"),false,0,maxWidth);
  return new TooltipLayouts.Layout<>(List.copyOf(lines),headerLines);
  });
  var reading=TooltipReading.select(layout.lines(),layout.header(),screenH,variant,expanded);var lines=reading.lines();
  int textW=1;for(Line line:lines)textW=Math.max(textW,font.width(line.text));
  int rowHeight=TooltipReading.ROW_HEIGHT;
  int panelW=textW+14,panelH=lines.size()*rowHeight+12+(expanded?15:0);
  float scale=1;
  float w=panelW*scale,h=panelH*scale;
  float x=mouseX+12,y=mouseY-10;
  if(x+w>screenW-6)x=mouseX-12-w;
  x=Math.max(6,Math.min(screenW-w-6,x));y=Math.max(6,Math.min(screenH-h-6,y));
  double t=(System.nanoTime()-EPOCH)/1.0e9;
  g.pose().pushPose();g.pose().translate(0,0,400);
  try{
   float portraitH=Math.min(screenH*.92f,Math.max(180,w*1.52f)),portraitW=portraitH*2/3;
   float px=Math.max(2,Math.min(screenW-portraitW-2,x+(w-portraitW)/2));
   float py=Math.max(2,Math.min(screenH-portraitH-2,y-portraitH*.515f));
   for(int i=5;i>=1;i--){double delay=i*.22,past=t-delay,wave=Math.sin(past*1.65),drift=Math.cos(past*1.1);
    float alpha=(float)(.38*Math.pow(.76,i-1)*(.82+.18*Math.sin(t*1.65-delay)));
    portrait(g,px+(float)sway(past)*portraitW*.019f-portraitW*(float)(.008*i+.004*i*wave),py+portraitH*.0018f*i*(float)drift,portraitW,portraitH,(float)(sway(past)*.075-.0035*i+Math.sin(t*1.65-delay)*.0025*i),alpha);
   }
   portrait(g,px+(float)sway(t)*portraitW*.019f,py,portraitW,portraitH,(float)sway(t)*.075f,1);
   g.pose().pushPose();g.pose().translate(x,y,2);g.pose().scale(scale,scale,1);
   g.fillGradient(0,0,panelW,panelH,0x9817102b,0x8c100d25);
   g.drawManaged(()->{
   // Translucent nebula patches under sparse interior stars.
   for(int band=0;band<14;band++){int left=(int)(panelW*(.08+.055*band)),top=(int)(panelH*(.35+.13*Math.sin(band*.45+t*.08)));
    g.fillGradient(left,Math.max(1,top-12),Math.min(panelW-1,left+20),Math.min(panelH-1,top+18),0x078c49c3,0x03416dbb);}
   for(int i=0;i<85;i++){int sx=1+(int)(fract(i*.61803398875+t*.0015)*(panelW-2)),sy=1+(int)(fract(i*.41421356237)*(panelH-2));int alpha=(int)(255*(.16+.28*(.5+.5*Math.sin(t*(.7+i%3*.2)+i))));g.fill(sx,sy,sx+1,sy+1,(alpha<<24)|(i%4==0?0xa5c6ff:0xeee0ff));}
   frame(g,panelW,panelH,t);
   });
   g.pose().translate(0,0,2);int lineY=6;for(Line line:lines){if(expanded)TooltipReading.text(g,line.text,7,lineY,line.palette==2?GOD:line.palette==1?NEON:STARS,t);else gradientLine(g,line.text,7,lineY,line.palette,t,scale,x,y);lineY+=rowHeight;}
   if(expanded)g.drawString(font,TooltipReading.footer(reading),7,panelH-13,0xece5ff,false);
   g.pose().popPose();g.flush();
  }finally{RenderSystem.setShaderColor(1,1,1,1);RenderSystem.defaultBlendFunc();g.pose().popPose();}
 }
 private static String tr(String key){return Component.translatable("tooltip.starcrowndoll."+key).getString();}
 private static double fract(double v){return v-Math.floor(v);}
 private static void add(List<Line> lines,String text,boolean bold,int palette,int width){
  var c=Component.literal(text).withStyle(Style.EMPTY.withBold(bold));
  for(var row:Minecraft.getInstance().font.split(c,width))lines.add(new Line(row,palette));
 }
 private static void portrait(GuiGraphics g,float x,float y,float w,float h,float rotation,float alpha){
  g.pose().pushPose();g.pose().translate(x+w/2,y+h/2,0);g.pose().mulPose(Axis.ZP.rotation(rotation));g.pose().scale(w/512,h/768,1);
  RenderSystem.enableBlend();RenderSystem.defaultBlendFunc();g.setColor(1,1,1,alpha);g.blit(PORTRAIT,-256,-384,0,0,512,768,512,768);g.flush();g.setColor(1,1,1,1);g.pose().popPose();
 }
 static void frame(GuiGraphics g,int w,int h,double t){
  g.fillGradient(-2,-2,w+2,0,0xff50478e,0xff9853ba);g.fillGradient(-2,h,w+2,h+2,0xff50478e,0xff9853ba);
  g.fillGradient(-2,0,0,h,0xff50478e,0xff9853ba);g.fillGradient(w,0,w+2,h,0xff50478e,0xff9853ba);
  int perimeter=2*(w+h);
  for(int i=0;i<120;i++){double d=(fract(i*.61803398875)*perimeter+t*14)%perimeter;int sx,sy;if(d<w){sx=(int)d;sy=-1;}else if(d<w+h){sx=w;sy=(int)d-w;}else if(d<2*w+h){sx=w-(int)(d-w-h);sy=h;}else{sx=-1;sy=h-(int)(d-2*w-h);}
   int alpha=(int)(255*(.35+.65*Math.pow(.5+.5*Math.sin(t*(1+i%3*.3)+i*2.4),3)));int color=(alpha<<24)|(i%4==0?0xefd5ff:i%3==0?0x99cbff:0xffffff);g.fill(sx,sy,sx+1,sy+1,color);
   if(i%19==0){g.fill(sx-1,sy,sx+2,sy+1,color);g.fill(sx,sy-1,sx+1,sy+2,color);}
  }
 }
 static void gradientLine(GuiGraphics g,FormattedCharSequence line,int x,int y,int palette,double time,float scale,float originX,float originY){
  var font=Minecraft.getInstance().font;int total=Math.max(1,font.width(line));int[] colors=palette==3?YuleItemTooltip.TEXT_COLORS:palette==2?GOD:palette==1?NEON:STARS;
  // Horizontal one-pixel slices give glyphs a real diagonal gradient, including their vertical strokes.
  double dx=total*.65,dy=dx+8,denominator=dx*dx+dy*dy;
  if(ModernUiCompat.draw(g,line,x,y,(gx,gy)->sample(colors,(float)((gx*dx+(gy+8)*dy)/denominator-time*.36))))return;
  var glyphs=TooltipLayouts.glyphs(line);
  for(int row=0;row<10;row++){
   int top=(int)Math.floor(originY+(y+row)*scale),bottom=(int)Math.ceil(originY+(y+row+1)*scale);
   g.enableScissor((int)Math.floor(originX+x*scale),top,(int)Math.ceil(originX+(x+total+2)*scale),bottom);
   final int scan=row;
   FormattedCharSequence colored=sink->{for(var glyph:glyphs){float u=(float)((glyph.x()*dx+(scan+8)*dy)/denominator-time*.36);if(!sink.accept(glyph.index(),glyph.style().withColor(sample(colors,u)),glyph.code()))return false;}return true;};
   g.drawString(font,colored,x,y,0xffffff,false);
   g.disableScissor();
  }
 }
 static int sample(int[] colors,float u){float v=(float)(u-Math.floor(u))*colors.length;int i=(int)v;float f=v-i;int a=colors[i%colors.length],b=colors[(i+1)%colors.length];return ((int)(((a>>16)&255)*(1-f)+((b>>16)&255)*f)<<16)|((int)(((a>>8)&255)*(1-f)+((b>>8)&255)*f)<<8)|(int)((a&255)*(1-f)+(b&255)*f);}
}
