package dev.starcrown;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.math.Axis;
public final class SevenLook {
 private static final ResourceLocation PORTRAIT=new ResourceLocation(StarcrownDoll.ID,"textures/gui/chengran_portrait.png");
 private static final long EPOCH=System.nanoTime();
 public static double time(){return (System.nanoTime()-EPOCH)/1e9;}
 private static double fract(double v){return v-Math.floor(v);}
 public static void text(GuiGraphics g,String text,int x,int y,boolean rgb){var font=Minecraft.getInstance().font;double t=SevenAppearance.flow?time()*SevenAppearance.textSpeed*.32:0;if(ModernUiCompat.draw(g,ModernUiCompat.row(text),x,y,(gx,gy)->{float phase=(float)fract(gx*.008+y*.004-t);return rgb?java.awt.Color.HSBtoRGB(phase,.48f,1)&0xffffff:blend(0xbceaff,0xf4c4ff,(float)(.5+.5*Math.sin(phase*Math.PI*2)));}))return;int offset=0;for(int code:text.codePoints().toArray()){String letter=new String(Character.toChars(code));float phase=(float)fract(offset*.008+y*.004-t);int color=rgb?java.awt.Color.HSBtoRGB(phase,.48f,1)&0xffffff:blend(0xbceaff,0xf4c4ff,(float)(.5+.5*Math.sin(phase*Math.PI*2)));g.drawString(font,letter,x+offset,y,color,false);offset+=font.width(letter);}}
 public static void godText(GuiGraphics g,String text,int x,int y){var font=Minecraft.getInstance().font;int offset=0;double t=SevenAppearance.flow?time()*SevenAppearance.textSpeed*.32:0;int[] colors={0xffe18b,0xfffaf0,0xff969c};if(ModernUiCompat.draw(g,ModernUiCompat.row(text),x,y,(gx,gy)->DollTooltip.sample(colors,(float)(gx*.018-t))))return;for(int code:text.codePoints().toArray()){String letter=new String(Character.toChars(code));double v=fract(offset*.018-t)*3;int i=(int)v;g.drawString(font,letter,x+offset,y,blend(colors[i%3],colors[(i+1)%3],(float)(v-i)),false);offset+=font.width(letter);}}
 private static int blend(int a,int b,float f){return((int)(((a>>16)&255)*(1-f)+((b>>16)&255)*f)<<16)|((int)(((a>>8)&255)*(1-f)+((b>>8)&255)*f)<<8)|(int)((a&255)*(1-f)+(b&255)*f);}
 private static void portrait(GuiGraphics g,float x,float y,float w,float h,double t,float alpha){g.pose().pushPose();g.pose().translate(x+w/2,y+h/2,0);g.pose().mulPose(Axis.ZP.rotation((float)(DollTooltip.sway(t)*.075)));g.pose().scale(w/512,h/768,1);RenderSystem.enableBlend();RenderSystem.defaultBlendFunc();g.setColor(1,1,1,alpha);g.blit(PORTRAIT,-256,-384,0,0,512,768,512,768);g.flush();g.setColor(1,1,1,1);g.pose().popPose();}
 private static ResourceLocation nebula;
 private static void cloud(GuiGraphics g,float x,float y,float w,float h,float red,float green,float blue,float alpha){
  if(nebula==null){var image=new com.mojang.blaze3d.platform.NativeImage(64,64,false);for(int iy=0;iy<64;iy++)for(int ix=0;ix<64;ix++){double dx=(ix-31.5)/32,dy=(iy-31.5)/32,r=Math.sqrt(dx*dx+dy*dy);int a=(int)(255*Math.pow(Math.max(0,1-r),2));image.setPixelRGBA(ix,iy,(a<<24)|0xffffff);}var texture=new net.minecraft.client.renderer.texture.DynamicTexture(image);texture.setFilter(true,false);nebula=Minecraft.getInstance().getTextureManager().register("starcrown_panel_nebula",texture);}
  g.pose().pushPose();g.pose().translate(x,y,0);g.pose().scale(w/64,h/64,1);RenderSystem.enableBlend();RenderSystem.defaultBlendFunc();g.setColor(red,green,blue,alpha);g.blit(nebula,0,0,0,0,64,64,64,64);g.flush();g.setColor(1,1,1,1);g.pose().popPose();
 }
 private static void star(GuiGraphics g,int x,int y,int color){g.fill(x,y-3,x+1,y-2,color);g.fill(x-1,y-2,x+2,y-1,color);g.fill(x-3,y-1,x+4,y,color);g.fill(x-2,y,x+3,y+1,color);g.fill(x-2,y+1,x+3,y+2,color);g.fill(x-3,y+2,x-1,y+3,color);g.fill(x+2,y+2,x+4,y+3,color);}
 public static void backdrop(GuiGraphics g,int sw,int sh,int x,int y,int w,int h){double t=time();g.fillGradient(0,0,sw,sh,0xee090e24,0xee071426);
  for(int i=0;i<7;i++)cloud(g,(float)(sw*(i*.17-.18)+Math.sin(t*.025+i)*sw*.04),(float)(sh*(.1+.2*Math.sin(i*.9+t*.018))),sw*.6f,sh*.85f,i%2==0?.52f:.2f,.28f,i%2==0?.85f:.68f,.55f);
  for(int i=0;i<370;i++){int sx=(int)(fract(i*.61803398875+t*.0015)*sw),sy=(int)(fract(i*.41421356237)*sh);int a=(int)(Math.min(230,SevenAppearance.stars*120*(.65+.35*Math.sin(t+i))));g.fill(sx,sy,sx+1,sy+1,(a<<24)|0xc5b5ed);}
  float ph=(float)(Math.min(sh*.95,560)*SevenAppearance.size),pw=ph*2/3,px=x-pw*.18f,py=(sh-ph)/2;
  if(SevenAppearance.showCharacter){if(SevenAppearance.trail)for(int i=4;i>=1;i--){double past=t*SevenAppearance.speed-i*.22;portrait(g,px-i*5+(float)DollTooltip.sway(past)*pw*.019f,py,pw,ph,past,(float)(SevenAppearance.character*SevenAppearance.trailStrength*Math.pow(.76,i-1)));}portrait(g,px+(float)DollTooltip.sway(t*SevenAppearance.speed)*pw*.019f,py,pw,ph,t*SevenAppearance.speed,(float)SevenAppearance.character);}
  int alpha=(int)(SevenAppearance.opacity*255);g.fillGradient(x,y,x+w,y+h,(alpha<<24)|0x18102c,(alpha<<24)|0x101023);
  g.enableScissor(x+1,y+1,x+w-1,y+h-1);
  for(int i=0;i<6;i++)cloud(g,(float)(x+w*(i*.18-.2)+Math.sin(t*.035+i)*w*.04),(float)(y+h*(.1+.2*Math.sin(i*1.4+t*.012))),w*.76f,h*.85f,i%2==0?.63f:.28f,.32f,.8f,.5f);
  for(int i=0;i<200;i++){int sx=x+1+(int)(fract(i*.618+t*.0008)*(w-2)),sy=y+1+(int)(fract(i*.414)*(h-2));int a=(int)(SevenAppearance.stars*(18+15*Math.sin(t+i)));g.fill(sx,sy,sx+1,sy+1,(a<<24)|0xcab4eb);}
  g.disableScissor();
  for(int band=1;band<=Math.ceil(SevenAppearance.glow/3);band++){int a=(int)(Math.max(0,17-band)*1.1);g.renderOutline(x-band,y-band,w+band*2,h+band*2,(a<<24)|0xa777e3);}
  g.renderOutline(x,y,w,h,0xbbaf88d8);int perimeter=2*(w+h);for(int i=0;i<90;i++){double d=fract(i*.61803398875)*perimeter;int sx,sy;if(d<w){sx=x+(int)d;sy=y;}else if(d<w+h){sx=x+w;sy=y+(int)d-w;}else if(d<2*w+h){sx=x+w-(int)(d-w-h);sy=y+h;}else{sx=x;sy=y+h-(int)(d-2*w-h);}int a=(int)(Math.min(255,SevenAppearance.stars*(90+110*(.5+.5*Math.sin(t*(.7+i%3*.2)+i)))));int c=(a<<24)|0xe4c7ff;if(i%17==0)star(g,sx,sy,c);else g.fill(sx,sy,sx+1,sy+1,c);}
  for(int[] corner:new int[][]{{x+3,y+3},{x+w-4,y+3},{x+3,y+h-4},{x+w-4,y+h-4}})star(g,corner[0],corner[1],0xeedfc5ff);
  RenderSystem.setShaderColor(1,1,1,1);RenderSystem.defaultBlendFunc();
 }
 public static void icon(GuiGraphics g,int x,int y){g.fill(x,y,x+27,y+40,0x882b1c40);g.renderOutline(x,y,27,40,0xffb18aff);portrait(g,x+1,y+1,25,38,0,1);}
 public static Button card(int x,int y,int w,int h,String title,String description,String icon,java.util.function.BooleanSupplier enabled,boolean toggle,Runnable run){return new Button(x,y,w,h,Component.literal(title),b->run.run(),supplier->supplier.get()){
  @Override public void renderWidget(GuiGraphics g,int mx,int my,float partial){var font=Minecraft.getInstance().font;boolean hot=isHoveredOrFocused(),on=enabled.getAsBoolean();g.fill(getX(),getY(),getX()+width,getY()+height,hot?0xaa32233f:0x991b152a);g.renderOutline(getX(),getY(),width,height,hot?0xffa587c7:0x665b466e);boolean compact=height<33;int iy=getY()+(height-18)/2;g.fill(getX()+7,iy,getX()+25,iy+18,0x554b2d66);g.renderOutline(getX()+7,iy,18,18,0x887a569e);g.drawString(font,icon,getX()+12,iy+5,0xdac1fa,false);int tx=getX()+33;g.drawString(font,font.plainSubstrByWidth(title,width-70),tx,getY()+(compact?(height-8)/2:7),0xefddff,false);if(!compact)g.drawString(font,font.plainSubstrByWidth(description,width-70),tx,getY()+23,0xa396b6,false);if(toggle){int bx=getX()+width-29,by=getY()+(height-10)/2;g.fill(bx,by,bx+20,by+10,on?0xff8e63b9:0xff484052);g.fill(bx+(on?12:2),by+2,bx+(on?18:8),by+8,on?0xfff0ddff:0xffa197b0);}else g.drawString(font,"↗",getX()+width-20,getY()+(height-8)/2,0xcfb4eb,false);}
 };}
 public static Button button(int x,int y,int w,int h,String label,Runnable run,java.util.function.BooleanSupplier selected){return new Button(x,y,w,h,Component.literal(label),b->run.run(),supplier->supplier.get()){@Override public void renderWidget(GuiGraphics g,int mx,int my,float partial){boolean hot=isHoveredOrFocused(),on=selected.getAsBoolean();g.fill(getX(),getY(),getX()+getWidth(),getY()+getHeight(),on?0x664d326f:hot?0x88513969:0x66201a32);g.renderOutline(getX(),getY(),getWidth(),getHeight(),on?0xffb392df:hot?0xffa587c7:0x665b466e);var font=Minecraft.getInstance().font;String text=font.plainSubstrByWidth(getMessage().getString(),getWidth()-12);g.drawString(font,text,getX()+(getWidth()-font.width(text))/2,getY()+(getHeight()-8)/2,active?0xe8daf6:0x807488,false);}};}
 public static AbstractSliderButton slider(int x,int y,int w,String label,double initial,double min,double max,java.util.function.DoubleConsumer setter){
  return slider(x,y,w,label,initial,min,max,v->String.format(java.util.Locale.ROOT,"%.2f",v),setter);
 }
 public static AbstractSliderButton slider(int x,int y,int w,String label,double initial,double min,double max,java.util.function.DoubleFunction<String> format,java.util.function.DoubleConsumer setter){return new AbstractSliderButton(x,y,w,24,Component.literal(label),(initial-min)/(max-min)){
  @Override protected void updateMessage(){setMessage(Component.literal(label+" "+format.apply(min+value*(max-min))));}
  @Override protected void applyValue(){setter.accept(min+value*(max-min));}
  @Override public void renderWidget(GuiGraphics g,int mx,int my,float partial){g.fill(getX(),getY(),getX()+width,getY()+height,0x88201a32);g.renderOutline(getX(),getY(),width,height,0x886b5384);int bx=getX()+3+(int)(value*(width-6));g.fill(getX()+3,getY()+height-5,bx,getY()+height-3,0xffb798da);updateMessage();g.drawString(Minecraft.getInstance().font,getMessage(),getX()+6,getY()+5,0xe8daf6,false);}
 };}
 private SevenLook(){}
}
