package dev.starcrown;

import java.util.*;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.*;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid=StarcrownDoll.ID,value=Dist.CLIENT)
public final class FateClient {
    static final Map<UUID,FateNetwork.State> CASTS=new HashMap<>();
    static UUID renderedPlayer;
    static final net.minecraft.client.model.HumanoidModel.ArmPose RETRIEVAL_POSE=net.minecraft.client.model.HumanoidModel.ArmPose.create("STARCROWN_FATE_RETRIEVAL",false,(model,entity,arm)->{
        var cast=CASTS.get(entity.getUUID());if(cast==null)return;
        float time=age(cast,Minecraft.getInstance().getFrameTime());
        var part=arm==HumanoidArm.RIGHT?model.rightArm:model.leftArm;
        float reach=MthClamp((time-78)/18);part.xRot=-2.9f+1.35f*reach;part.yRot=(arm==HumanoidArm.RIGHT?-.18f:.18f)*reach;part.zRot=0;
    });
    private static float MthClamp(float x){return net.minecraft.util.Mth.clamp(x,0,1);}
    @SubscribeEvent public static void playerPre(RenderPlayerEvent.Pre e){renderedPlayer=e.getEntity().getUUID();}
    @SubscribeEvent public static void playerPost(RenderPlayerEvent.Post e){renderedPlayer=null;}
    public static void receive(FateNetwork.State s){
        var mc=Minecraft.getInstance();if(mc.level==null||!mc.level.dimension().location().equals(s.dimension()))return;
        if(s.mode()==1){CASTS.remove(s.owner());return;}
        if(s.mode()==0)CASTS.put(s.owner(),s);
        // Completion acknowledges the sword swap; it must not reset or restart the visual clock.
    }
    static float age(FateNetwork.State s,float partial){
        var level=Minecraft.getInstance().level;return level==null?-1:level.getGameTime()-s.start()+partial;
    }
    static float localAge(float partial){var p=Minecraft.getInstance().player;if(p==null)return -1;var s=CASTS.get(p.getUUID());return s==null?-1:age(s,partial);}
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event){
        if(event.phase!=TickEvent.Phase.END)return;
        var mc=Minecraft.getInstance();if(mc.level==null){CASTS.clear();FateAnalysisPlacement.clear();FateOrbBloom.release();return;}
        CASTS.values().removeIf(s->{var e=mc.level.getEntity(s.entity());return !mc.level.dimension().location().equals(s.dimension())
                ||age(s,0)>FateItems.DURATION+1||e==null||!e.isAlive()||!e.getUUID().equals(s.owner());});
    }
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut e){CASTS.clear();FateAnalysisPlacement.clear();FateOrbBloom.release();}
    public static boolean handPose(PoseStack pose,LocalPlayer player,HumanoidArm arm,ItemStack stack,float partial){
        if(!stack.is(StarcrownDoll.BAB_ILU.get())||!player.isUsingItem()||player.getUseItem()!=stack)return false;
        float t=localAge(partial);if(t<0)t=player.getTicksUsingItem()+partial;
        float side=arm==HumanoidArm.RIGHT?1:-1;
        float reach=net.minecraft.util.Mth.clamp((t-80)/18,0,1);
        pose.translate(side*(.5-.15*reach),-.45+.25*reach,-.7-.2*reach);
        pose.mulPose(Axis.XP.rotationDegrees(-12-78*reach));
        pose.mulPose(Axis.YP.rotationDegrees(side*12));
        return true;
    }
    /** Keep the visible player hand in front of the black world mask, as in the reference. */
    @SubscribeEvent public static void hand(RenderHandEvent event){
        var mc=Minecraft.getInstance();var p=mc.player;if(p==null)return;
        var cast=CASTS.get(p.getUUID());if(cast==null)return;
        if((event.getHand()==net.minecraft.world.InteractionHand.OFF_HAND)!=cast.offhand())return;
        float age=age(cast,event.getPartialTick());if(age>=160)return;
        if(age<80){event.setCanceled(true);return;}
        event.setCanceled(true);
        HumanoidArm arm=cast.offhand()?p.getMainArm().getOpposite():p.getMainArm();float side=arm==HumanoidArm.RIGHT?1:-1;
        var pose=event.getPoseStack();var buffers=event.getMultiBufferSource();
        pose.pushPose();try{
            float reach=net.minecraft.util.Mth.clamp((age-80)/17,0,1);
            pose.translate(side*(.46-.14*reach),-.31+.21*reach,-.72-.18*reach);
            pose.mulPose(Axis.XP.rotationDegrees(-75));
            pose.translate(side*.3125,-.68,0);
            var renderer=(net.minecraft.client.renderer.entity.player.PlayerRenderer)mc.getEntityRenderDispatcher().getRenderer(p);
            int handLight=net.minecraft.client.renderer.LightTexture.FULL_BRIGHT;
            if(arm==HumanoidArm.RIGHT)renderer.renderRightHand(pose,buffers,handLight,p);else renderer.renderLeftHand(pose,buffers,handLight,p);
        }finally{pose.popPose();}
        if(age>=143){
            pose.pushPose();try{
                pose.translate(side*.32,-.1,-.9);pose.mulPose(Axis.ZP.rotationDegrees(side*65));pose.mulPose(Axis.YP.rotationDegrees(90));
                FateItemRenderer.draw(true,pose,buffers,event.getPackedLight());
            }finally{pose.popPose();}
        }
        if(age>=134&&age<143){
            if(buffers instanceof net.minecraft.client.renderer.MultiBufferSource.BufferSource source)source.endBatch();
            FateVfx.handLight(age,side);
        }
    }
    public static final class Setup {
        public static void register(net.minecraftforge.eventbus.api.IEventBus bus){bus.addListener(Setup::reload);}
        public static void reload(RegisterClientReloadListenersEvent e){
            com.mojang.logging.LogUtils.getLogger().info("Attaching Fate resource reload listener");
            e.registerReloadListener((net.minecraft.server.packs.resources.ResourceManagerReloadListener)resources->{FateItemRenderer.reload(resources);FateVfx.reload(resources);});
        }
    }
}
