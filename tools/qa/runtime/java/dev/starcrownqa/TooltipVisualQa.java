package dev.starcrownqa;
import dev.starcrown.*;
import java.nio.file.*;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.event.TickEvent;
@Mod.EventBusSubscriber(modid="starcrownqa",value=Dist.CLIENT)
public final class TooltipVisualQa {
 private static int ticks;
 @SubscribeEvent public static void tick(TickEvent.ClientTickEvent e){
  if(e.phase!=TickEvent.Phase.END||!Files.exists(Path.of("enable-tooltip-visual-qa")))return;
  var mc=net.minecraft.client.Minecraft.getInstance();if(mc.getOverlay()!=null)return;

  ticks++;
  if(ticks==5)mc.setScreen(new Preview(false));
  if(ticks==35||ticks==65||ticks==130||ticks==170){
   net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(new net.minecraftforge.client.event.ScreenEvent.KeyPressed.Pre(mc.screen,340,0,0));
   net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(new net.minecraftforge.client.event.ScreenEvent.KeyReleased.Post(mc.screen,340,0,0));
   if(TooltipReading.pinned()!=(ticks==35||ticks==130))throw new AssertionError("Shift lock/release toggle failed at "+ticks);
  }
  if(ticks==45||ticks==145){
   var scroll=new net.minecraftforge.client.event.ScreenEvent.MouseScrolled.Pre(mc.screen,10,10,-1);
   net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(scroll);
   if(!scroll.isCanceled())throw new AssertionError("Pinned reading did not handle pagination");
  }
  if(ticks==30||ticks==55||ticks==100||ticks==160)net.minecraft.client.Screenshot.grab(mc.gameDirectory,"tooltip-"+ticks+".png",mc.getMainRenderTarget(),m->{});
  if(ticks==70)mc.setScreen(new Preview(true));
  if(ticks==120)mc.setScreen(new YulePreview(false));
  if(ticks==180)mc.setScreen(new YulePreview(true));
  if(ticks==200)net.minecraft.client.Screenshot.grab(mc.gameDirectory,"yule-expanded.png",mc.getMainRenderTarget(),m->{});
  if(ticks==220){try{if(ModernUiCompat.textEngine()!=Files.exists(Path.of("expect-modernui")))throw new AssertionError("Unexpected font engine");if(Math.abs(DollTooltip.sway(0)-DollTooltip.sway(2.8))>1e-9)throw new AssertionError("cycle discontinuity");if(Math.abs(YuleItemTooltip.raisedAlpha(0)-YuleItemTooltip.raisedAlpha(4.4))>1e-6)throw new AssertionError("pose cycle discontinuity");Files.writeString(Path.of("tooltip-qa-results.txt"),"PASS Chengran + Yule compact/expanded, Shift pin and pagination, fish pose cycle; modernui="+ModernUiCompat.textEngine()+"\n");}catch(Exception x){throw new RuntimeException(x);}mc.stop();}
 }
 private static final class Preview extends net.minecraft.client.gui.screens.Screen {
  private final boolean expanded;Preview(boolean expanded){super(net.minecraft.network.chat.Component.literal("Tooltip acceptance preview"));this.expanded=expanded;}
  @Override public void render(net.minecraft.client.gui.GuiGraphics g,int mx,int my,float partial){
   g.fill(0,0,width,height,0xff202b38);g.drawString(font,"CHENGRAN / "+(expanded?"SHIFT":"COMPACT"),8,8,0xffffffff,false);
   var stack=new net.minecraft.world.item.ItemStack(StarcrownDoll.ITEM.get());
   if(expanded)DollTooltip.draw(g,width/3,height/2,width,height,true,stack.getHoverName());
   else g.renderTooltip(font,stack,width/3,height/2);
  }
 }
 private static final class YulePreview extends net.minecraft.client.gui.screens.Screen {
  private final boolean expanded;YulePreview(boolean expanded){super(net.minecraft.network.chat.Component.literal("Yule tooltip acceptance"));this.expanded=expanded;}
  @Override public void render(net.minecraft.client.gui.GuiGraphics g,int mx,int my,float partial){
   g.fill(0,0,width,height,0xff514353);g.drawString(font,"YULE / "+(expanded?"SHIFT":"COMPACT"),8,8,0xffffffff,false);
   var stack=new net.minecraft.world.item.ItemStack(StarcrownDoll.YULE_ITEM.get());
   if(expanded)YuleItemTooltip.draw(g,width/3,height/2,width,height,true,stack.getHoverName());else g.renderTooltip(font,stack,width/3,height/2);
  }
 }
}
