package dev.starcrown;

import net.minecraft.client.*;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.*;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid=StarcrownDoll.ID,value=Dist.CLIENT)
public final class EternalReturnClient {
    private static EternalReturnNetwork.State state=EternalReturnNetwork.State.EMPTY;
    private static final KeyMapping ACTIVATE=new KeyMapping("key.starcrowndoll.eternal_return",
            com.mojang.blaze3d.platform.InputConstants.Type.KEYSYM,org.lwjgl.glfw.GLFW.GLFW_KEY_G,"key.categories.starcrowndoll");
    public static void receive(EternalReturnNetwork.State s) {state=s;}
    public static net.minecraft.world.item.ItemStack rewindVisual() {
        var stack=new net.minecraft.world.item.ItemStack(StarcrownDoll.ETERNAL_RETURN.get());
        stack.getOrCreateTag().putBoolean("EternalRewindVisual",true);return stack;
    }
    public static void rewind(int entityId) {
        var mc=Minecraft.getInstance();if(mc.level==null)return;var entity=mc.level.getEntity(entityId);if(entity==null)return;
        mc.particleEngine.createTrackingEmitter(entity,net.minecraft.core.particles.ParticleTypes.TOTEM_OF_UNDYING,30);
        mc.level.playLocalSound(entity.getX(),entity.getY(),entity.getZ(),net.minecraft.sounds.SoundEvents.TOTEM_USE,entity.getSoundSource(),1,1,false);
        if(entity==mc.player)mc.gameRenderer.displayItemActivation(rewindVisual());
    }
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if(event.phase!=TickEvent.Phase.END)return;
        var mc=Minecraft.getInstance();if(mc.player==null) {state=EternalReturnNetwork.State.EMPTY;return;}
        while(ACTIVATE.consumeClick())if(mc.screen==null&&state.worn())SevenNetwork.CHANNEL.sendToServer(new EternalReturnNetwork.Action());
    }
    @SubscribeEvent public static void hud(RenderGuiEvent.Post event) {
        var mc=Minecraft.getInstance();if(!state.worn()||mc.options.hideGui||mc.player==null)return;
        var g=event.getGuiGraphics();int x=8,y=g.guiHeight()-69;
        String status=state.activeTicks()>0?"轮转 "+(state.activeTicks()+19)/20+"s  护盾 "+(int)Math.ceil(state.shield())+"  蓄伤 "+(int)state.stored():
                state.cooldownTicks()>0?"轮转冷却 "+(state.cooldownTicks()+19)/20+"s":"[G] 轮转 / 终结";
        g.drawString(mc.font,"永劫回归  火种 "+state.seeds()+"/12  记忆 "+state.memory()+"/30",x,y,0xFFE6AC);
        g.drawString(mc.font,status,x,y+11,0xFFD584);
        g.drawString(mc.font,"柴薪 "+String.format(java.util.Locale.ROOT,"%,d",state.kindling())+
                "  回归 "+(state.rewindTicks()>0?(state.rewindTicks()+19)/20+"s":"就绪")+
                (state.scars()>0?"  伤痕 "+state.scars():"")+(state.fatigueTicks()>0?"  燃尽 "+(state.fatigueTicks()+19)/20+"s":""),x,y+22,0xD0A478);
    }
    @Mod.EventBusSubscriber(modid=StarcrownDoll.ID,value=Dist.CLIENT,bus=Mod.EventBusSubscriber.Bus.MOD)
    public static final class Setup {
        @SubscribeEvent public static void keys(RegisterKeyMappingsEvent e) {e.register(ACTIVATE);}
        @SubscribeEvent public static void init(net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent e) {
            e.enqueueWork(()->{
                top.theillusivec4.curios.api.client.CuriosRendererRegistry.register(StarcrownDoll.ETERNAL_RETURN.get(),EternalHaloRenderer::new);
                net.minecraft.client.renderer.item.ItemProperties.register(StarcrownDoll.ETERNAL_RETURN.get(),
                        new net.minecraft.resources.ResourceLocation(StarcrownDoll.ID,"rewind"),
                        (stack,level,entity,seed)->stack.hasTag()&&stack.getTag().getBoolean("EternalRewindVisual")?1:0);
            });
        }
        @SubscribeEvent public static void reload(RegisterClientReloadListenersEvent e) {
            e.registerReloadListener((net.minecraft.server.packs.resources.ResourceManagerReloadListener)EternalHaloRenderer::reload);
        }
    }
}
