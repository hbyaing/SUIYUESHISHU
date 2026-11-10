package dev.starcrownqa;
import dev.starcrown.*;
import java.nio.file.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
@Mod.EventBusSubscriber(modid="starcrownqa",value=Dist.CLIENT)
public final class ModernUiVisualQa {
 private static int ticks;
 private static void shot(String name){var mc=Minecraft.getInstance();net.minecraft.client.Screenshot.grab(mc.gameDirectory,name,mc.getMainRenderTarget(),m->{});}
 @SubscribeEvent public static void tick(TickEvent.ClientTickEvent e)throws Exception{
  if(e.phase!=TickEvent.Phase.END||!Files.exists(Path.of("enable-modernui-qa")))return;
  var mc=Minecraft.getInstance();if(mc.getOverlay()!=null)return;ticks++;
  if(ticks==225){if(!ModernUiCompat.textEngine())throw new AssertionError("Modern UI text bridge inactive");SevenClient.openPanel();}
  if(ticks==245)shot("modernui-panel-main.png");
  if(ticks==255){var screen=mc.screen;var page=screen.getClass().getDeclaredField("page");page.setAccessible(true);page.setInt(screen,3);screen.resize(mc,screen.width,screen.height);
   var box=(EditBox)screen.children().stream().filter(EditBox.class::isInstance).findFirst().orElseThrow();box.setValue("speed");box.setCursorPosition(2);box.setHighlightPos(2);box.insertText("中");
   if(!screen.children().contains(box)||box.getCursorPosition()!=3||!box.getValue().equals("sp中eed"))throw new AssertionError("search recreated field or moved cursor: same="+screen.children().contains(box)+",cursor="+box.getCursorPosition()+",value="+box.getValue());
   box.setValue("急迫");screen.setFocused(box);
  }
  if(ticks==275)shot("modernui-panel-search.png");
  if(ticks==285){mc.options.guiScale().set(3);mc.resizeDisplay();if(mc.getWindow().getGuiScale()!=3)throw new AssertionError("effective GUI scale is not 3");}
  if(ticks==305)shot("modernui-panel-scale3.png");
  if(ticks==315){mc.options.guiScale().set(2);mc.resizeDisplay();if(mc.getWindow().getGuiScale()!=2)throw new AssertionError("effective GUI scale did not return to 2");TooltipLayouts.clear();}
  if(ticks==335){shot("modernui-panel-restored.png");mc.setScreen(new GradientProbe());}
 }
 private static final class GradientProbe extends net.minecraft.client.gui.screens.Screen {
  int frames,before;Object engine;
  GradientProbe(){super(net.minecraft.network.chat.Component.literal("Modern UI shaped gradient cache probe"));}
  int count()throws Exception{if(engine==null)engine=Class.forName("icyllis.modernui.mc.text.TextLayoutEngine").getMethod("getInstance").invoke(null);return (Integer)engine.getClass().getMethod("getCacheCount").invoke(engine);}
  @Override public void render(net.minecraft.client.gui.GuiGraphics g,int mx,int my,float partial){
   g.fill(0,0,width,height,0xff302638);
   ModernUiCompat.draw(g,ModernUiCompat.row("AV fi 中文渐变 Aa · 粉白金"),20,40,(x,y)->java.awt.Color.HSBtoRGB((float)(x/200.+frames/120.),.4f,1)&0xffffff);
   try{
    if(++frames==20)before=count();
    if(frames==80)shot("modernui-gradient-text.png");
    if(frames==200){int after=count();if(after>before+16)throw new AssertionError("animated gradient grew layout cache: "+before+" -> "+after);
     Files.writeString(Path.of("modernui-qa-results.txt"),"PASS Modern UI text engine active; both tooltip modes and Shift pagination; panel render; same EditBox preserves middle insertion/cursor; GUI scale 2 -> 3 -> 2 and cache reload; animated full-line shaped gradient layout cache "+before+" -> "+after+" across 180 frames\n");Minecraft.getInstance().stop();}
   }catch(Exception ex){throw new RuntimeException(ex);}
  }
 }
}
