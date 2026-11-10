package dev.starcrown;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.client.event.*;
import net.minecraftforge.event.TickEvent;
import net.minecraft.client.*;
import net.minecraft.client.particle.*;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.core.particles.SimpleParticleType;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
@Mod.EventBusSubscriber(modid=StarcrownDoll.ID,bus=Mod.EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
public class DollClient {
 public static void yuleTooltip(java.util.List<net.minecraft.network.chat.Component> lines,boolean expanded){
  yuleTooltip(lines,expanded,false);
 }
 public static void yuleTooltip(java.util.List<net.minecraft.network.chat.Component> lines,boolean expanded,boolean calm){
  var base="tooltip.starcrowndoll.";
  if(expanded){
   lines.add(net.minecraft.network.chat.Component.translatable(base+"yule.virtues").withStyle(net.minecraft.ChatFormatting.GOLD));
   for(String virtue:new String[]{"humility","tolerance","generosity","chastity","temperance","charity","diligence"}){
    var description=net.minecraft.network.chat.Component.translatable(base+(calm?"calm.":"yule.")+virtue)
      .withStyle(net.minecraft.network.chat.Style.EMPTY.withColor(net.minecraft.ChatFormatting.GRAY).withBold(false));
    lines.add(net.minecraft.network.chat.Component.translatable(base+"yule."+virtue+".name")
      .withStyle(net.minecraft.ChatFormatting.BOLD).append(description));
   }
   for(String key:calm?new String[]{"yule.animation","wear","calm.feast","yule.controls","place"}:
        new String[]{"yule.animation","wear","music","yule.feast","yule.controls","place"}){
    lines.add(net.minecraft.network.chat.Component.translatable(base+key).withStyle(net.minecraft.ChatFormatting.GRAY));
   }
  }else lines.add(net.minecraft.network.chat.Component.translatable(base+"shift").withStyle(net.minecraft.network.chat.Style.EMPTY.withColor(0x999999)));
 }
 @SubscribeEvent public static void clientSetup(net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent e){e.enqueueWork(()->{
  StarcrownDoll.musicListener=DollMusic::toggle;
  StarcrownDoll.selfDollName=SelfDollTooltip::playerName;
 });}
 @SubscribeEvent public static void particles(RegisterParticleProvidersEvent e){e.registerSpriteSet(StarcrownDoll.STAR.get(),Provider::new);}
 @SubscribeEvent public static void layers(EntityRenderersEvent.AddLayers e){for(String skin:e.getSkins()){
  var renderer=e.getSkin(skin);if(renderer instanceof net.minecraft.client.renderer.entity.player.PlayerRenderer pr)pr.addLayer(new HeadLayer(pr));
 }}
 static class HeadLayer extends RenderLayer<AbstractClientPlayer,PlayerModel<AbstractClientPlayer>> {
  HeadLayer(net.minecraft.client.renderer.entity.RenderLayerParent<AbstractClientPlayer,PlayerModel<AbstractClientPlayer>> p){super(p);}
  public void render(PoseStack p,MultiBufferSource b,int light,AbstractClientPlayer player,float a,float c,float partial,float age,float yaw,float pitch){
   var stack=DivinityInventory.equipped(player);
   if(stack==DivinityInventory.stack(player)&&!CuriosEquipment.visible(player))return;
   if(!StarcrownDoll.isDoll(stack)||(player.isInvisible()&&!DivineWill.wearing(player)))return;
   p.pushPose();getParentModel().getHead().translateAndRotate(p);p.translate(0,-.51,0);p.mulPose(Axis.ZP.rotationDegrees(180));p.scale(.52f,.52f,.52f);
   int overlay=net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY;
   p.pushPose();p.translate(-.5,0,-.5);
   if(stack.is(StarcrownDoll.SELF_ITEM.get())){
    SelfDollRenderer.renderForPlayer(player,p,b,light,overlay,DollAnimation.jumpAmount(player,partial));
    p.popPose();p.popPose();
    return;
   }
   DollRenderer.renderBody(StarcrownDoll.dollState(stack),p,b,light,overlay,DollAnimation.jumpAmount(player,partial));p.popPose();
   DollRenderer.renderEffects((player.level().getGameTime()+partial)/20.,p,b,StarcrownDoll.isChengran(stack));p.popPose();
  }
 }
 static class Star extends TextureSheetParticle {
  int grounded;
  Star(ClientLevel l,double x,double y,double z,double dx,double dy,double dz,SpriteSet sprites){super(l,x,y,z,dx,dy,dz);xd=dx;yd=dy;zd=dz;gravity=.012f;friction=.985f;hasPhysics=true;lifetime=200;quadSize=.035f+random.nextFloat()*.025f;rCol=.8f;gCol=.3f;bCol=1;pickSprite(sprites);}
  public ParticleRenderType getRenderType(){return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;}
  protected int getLightColor(float partial){return 15728880;}
  public void tick(){super.tick();if(onGround){grounded++;xd*=.6;zd*=.6;}alpha=Math.min(1,age/6f)*Math.min(1,(lifetime-age)/25f)*(grounded>0?Math.max(0,1-grounded/24f):1);if(grounded>=24)remove();}
 }
 record Provider(SpriteSet sprites) implements ParticleProvider<SimpleParticleType>{public Particle createParticle(SimpleParticleType type,ClientLevel l,double x,double y,double z,double dx,double dy,double dz){return new Star(l,x,y,z,dx,dy,dz,sprites);}}
 @Mod.EventBusSubscriber(modid=StarcrownDoll.ID,value=Dist.CLIENT)
 public static class WorldEvents {
  @SubscribeEvent public static void yuleTooltipWidth(RenderTooltipEvent.GatherComponents event){if(StarcrownDoll.isYule(event.getItemStack()))event.setMaxWidth(Math.min(340,event.getScreenWidth()-24));}
  @SubscribeEvent public static void humilitySizes(TickEvent.ClientTickEvent event){if(event.phase==TickEvent.Phase.END&&Minecraft.getInstance().level!=null)for(var player:Minecraft.getInstance().level.players())YuleHumility.updateSize(player);}
  @SubscribeEvent public static void tooltip(net.minecraftforge.event.entity.player.ItemTooltipEvent e){
   if(!StarcrownDoll.isDoll(e.getItemStack())||e.getItemStack().is(StarcrownDoll.SELF_ITEM.get()))return;
   if(StarcrownDoll.isYule(e.getItemStack())){
    yuleTooltip(e.getToolTip(),net.minecraft.client.gui.screens.Screen.hasShiftDown(),e.getItemStack().is(StarcrownDoll.CALM_ITEM.get()));
    return;
   }
   if(net.minecraft.client.gui.screens.Screen.hasShiftDown()){
    boolean twilight=e.getItemStack().is(StarcrownDoll.TWILIGHT_ITEM.get());
    for(String key:DollTooltip.keys(twilight))e.getToolTip().add(net.minecraft.network.chat.Component.translatable(DollTooltip.translation(key,twilight)).withStyle(net.minecraft.ChatFormatting.GRAY));
   }else e.getToolTip().add(net.minecraft.network.chat.Component.translatable("tooltip.starcrowndoll.shift").withStyle(net.minecraft.network.chat.Style.EMPTY.withColor(0x999999)));
  }

  @SubscribeEvent public static void tick(TickEvent.PlayerTickEvent e){
   var p=e.player;if(e.phase!=TickEvent.Phase.END||!p.level().isClientSide||p.tickCount%4!=0||!StarcrownDoll.isChengran(DivinityInventory.equipped(p)))return;
   // Spawn in world space; particles no longer follow the wearer after emission.
   StarcrownDoll.emit(p.level(),p.getX(),p.getY()+p.getBbHeight()+.64,p.getZ(),.23);
  }
 }
}

