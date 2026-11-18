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
    private static final Map<UUID,Reveal> REVEALS=new HashMap<>();
    private record DrawKey(FateNetwork.State cast,net.minecraft.world.InteractionHand hand,int slot,ItemStack key){
        boolean valid(LocalPlayer p,float partial){
            return p!=null&&p.isAlive()&&p.getUUID().equals(cast.owner())&&p.level().dimension().location().equals(cast.dimension())
                    &&(cast.offhand()||slot==p.getInventory().selected)&&p.getItemInHand(hand)==key&&age(cast,partial)<=166;
        }
    }
    private static DrawKey localKey;
    static boolean showingDrawnBlade(LocalPlayer p,boolean left,ItemStack rendered,float partial){
        return localKey!=null&&rendered==localKey.key()&&localKey.valid(p,partial)
                &&left==((p.getMainArm()==HumanoidArm.LEFT)^localKey.cast().offhand())&&age(localKey.cast(),partial)>=143;
    }
    private static final class Reveal {
        final FateNetwork.State cast;
        final int slot;
        ItemStack sword;
        Reveal(FateNetwork.State cast,net.minecraft.world.entity.player.Player player){
            this.cast=cast;
            slot=player.getInventory().selected;
        }
        boolean valid(net.minecraft.world.entity.player.Player player,float elapsed){
            if(!player.isAlive()||elapsed>194||(!cast.offhand()&&slot!=player.getInventory().selected))return false;
            ItemStack held=player.getItemInHand(cast.offhand()?net.minecraft.world.InteractionHand.OFF_HAND:net.minecraft.world.InteractionHand.MAIN_HAND);
            if(sword!=null)return held==sword&&dev.eavfx.EaClient.ageFor(player)<0;
            if(held.is(StarcrownDoll.EA.get())){sword=held;return true;}
            // Inventory packets may follow the completion cue.
            return elapsed<=166&&held.is(StarcrownDoll.BAB_ILU.get());
        }
    }
    static float runeProgress(net.minecraft.world.entity.player.Player owner,ItemStack stack,boolean left,float partial){
        if(owner==null||!stack.is(StarcrownDoll.EA.get()))return 1;
        var reveal=REVEALS.get(owner.getUUID());
        if(reveal==null||left!=((owner.getMainArm()==HumanoidArm.LEFT)^reveal.cast.offhand()))return 1;
        float elapsed=age(reveal.cast,partial);
        if(!reveal.valid(owner,elapsed)){REVEALS.remove(owner.getUUID());return 1;}
        return MthClamp((elapsed-164)/28);
    }
    static ItemStack resolveFirstPersonStack(LocalPlayer player,boolean left,ItemStack rendered,float partial){
        if(player==null||!rendered.is(StarcrownDoll.BAB_ILU.get()))return rendered;
        var reveal=REVEALS.get(player.getUUID());
        if(reveal==null||left!=((player.getMainArm()==HumanoidArm.LEFT)^reveal.cast.offhand()))return rendered;
        var hand=reveal.cast.offhand()?net.minecraft.world.InteractionHand.OFF_HAND:net.minecraft.world.InteractionHand.MAIN_HAND;
        ItemStack held=player.getItemInHand(hand);
        // The equip animation may submit the previous key for a few frames after the inventory swap.
        return held.is(StarcrownDoll.EA.get())&&reveal.valid(player,age(reveal.cast,partial))?held:rendered;
    }
    static UUID renderedPlayer;
    private static final net.minecraft.client.renderer.MultiBufferSource.BufferSource DRAW_BUFFER=
            net.minecraft.client.renderer.MultiBufferSource.immediate(new com.mojang.blaze3d.vertex.BufferBuilder(4096));
    static float eaRaise(float age){
        float t=MthClamp(age/10f);
        return t*t*(3-2*t);
    }
    static final net.minecraft.client.model.HumanoidModel.ArmPose EA_CHARGE_POSE=net.minecraft.client.model.HumanoidModel.ArmPose.create("STARCROWN_EA_CHARGE",false,(model,entity,arm)->{
        float age=dev.eavfx.EaClient.ageFor(entity);
        if(age<0)return;
        float raise=eaRaise(age);
        var part=arm==HumanoidArm.RIGHT?model.rightArm:model.leftArm;
        part.xRot=net.minecraft.util.Mth.lerp(raise,-.31f,-3.05f);
        part.yRot=0;
        part.zRot=(arm==HumanoidArm.RIGHT?-.10f:.10f)*raise;
    });
    static final net.minecraft.client.model.HumanoidModel.ArmPose RETRIEVAL_POSE=net.minecraft.client.model.HumanoidModel.ArmPose.create("STARCROWN_FATE_RETRIEVAL",false,(model,entity,arm)->{
        var cast=CASTS.get(entity.getUUID());if(cast==null)return;
        float time=age(cast,Minecraft.getInstance().getFrameTime());
        var part=arm==HumanoidArm.RIGHT?model.rightArm:model.leftArm;
        float reach=step(time,78,99),lateral=step(time,134,146);
        float side=arm==HumanoidArm.RIGHT?1:-1;
        part.xRot=-2.9f+1.35f*reach;
        part.yRot=side*(.08f*reach-1.16f*lateral);
        part.zRot=side*.05f*lateral;
    });
    private static float MthClamp(float x){return net.minecraft.util.Mth.clamp(x,0,1);}
    static float step(float age,float start,float end){float t=MthClamp((age-start)/(end-start));return t*t*(3-2*t);}
    @SubscribeEvent public static void playerPre(RenderPlayerEvent.Pre e){renderedPlayer=e.getEntity().getUUID();}
    @SubscribeEvent public static void playerPost(RenderPlayerEvent.Post e){renderedPlayer=null;}
    public static void receive(FateNetwork.State s){
        var mc=Minecraft.getInstance();if(mc.level==null||!mc.level.dimension().location().equals(s.dimension()))return;
        if(s.mode()==2){
            if(mc.level.getEntity(s.entity()) instanceof net.minecraft.world.entity.player.Player owner)
                REVEALS.put(s.owner(),new Reveal(s,owner));
            return;
        }
        REVEALS.remove(s.owner());
        if(mc.player!=null&&mc.player.getUUID().equals(s.owner())){
            var hand=s.offhand()?net.minecraft.world.InteractionHand.OFF_HAND:net.minecraft.world.InteractionHand.MAIN_HAND;
            ItemStack key=mc.player.getItemInHand(hand);
            localKey=s.mode()==0&&key.is(StarcrownDoll.BAB_ILU.get())?new DrawKey(s,hand,mc.player.getInventory().selected,key):null;
        }
        if(s.mode()==1){CASTS.remove(s.owner());return;}
        if(s.mode()==0)CASTS.put(s.owner(),s);
        // Completion acknowledges the sword swap; it must not reset or restart the visual clock.
    }
    static float age(FateNetwork.State s,float partial){
        var level=Minecraft.getInstance().level;return level==null?-1:level.getGameTime()-s.start()+partial;
    }
    static float localAge(float partial){var p=Minecraft.getInstance().player;if(p==null)return -1;var s=CASTS.get(p.getUUID());return s==null?-1:age(s,partial);}
    static boolean controlsHand(LocalPlayer player,net.minecraft.world.InteractionHand hand,float partial){
        var cast=CASTS.get(player.getUUID());
        return cast!=null&&cast.offhand()==(hand==net.minecraft.world.InteractionHand.OFF_HAND)&&age(cast,partial)<FateItems.DURATION;
    }
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event){
        if(event.phase!=TickEvent.Phase.END)return;
        var mc=Minecraft.getInstance();if(localKey!=null&&!localKey.valid(mc.player,0))localKey=null;
        if(mc.level==null){CASTS.clear();REVEALS.clear();FateAnalysisPlacement.clear();FateOrbBloom.release();return;}
        REVEALS.values().removeIf(r->!mc.level.dimension().location().equals(r.cast.dimension())||
                !(mc.level.getEntity(r.cast.entity()) instanceof net.minecraft.world.entity.player.Player p)||
                !p.getUUID().equals(r.cast.owner())||!r.valid(p,age(r.cast,0)));
        CASTS.values().removeIf(s->{var e=mc.level.getEntity(s.entity());return !mc.level.dimension().location().equals(s.dimension())
                ||age(s,0)>FateItems.DURATION+1||e==null||!e.isAlive()||!e.getUUID().equals(s.owner());});
    }
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut e){localKey=null;CASTS.clear();REVEALS.clear();FateAnalysisPlacement.clear();FateOrbBloom.release();}
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
        var pose=event.getPoseStack();
        float reach=step(age,80,102),clench=step(age,101,113),pull=step(age,141,157);
        float x=side*(.50f-.60f*reach+.50f*pull),y=-.21f+.21f*reach-.16f*pull,z=-.86f-.16f*reach-.10f*pull;
        var screen=new org.joml.Matrix4f(com.mojang.blaze3d.systems.RenderSystem.getProjectionMatrix())
                .mul(com.mojang.blaze3d.systems.RenderSystem.getModelViewMatrix()).mul(pose.last().pose());
        var fist=screen.transformProject(x,y,z,new org.joml.Vector3f());
        var aperture=screen.transformProject(-side*.10f,0,-1.03f,new org.joml.Vector3f());
        FateVfx.handSurface(age,side,aperture.x,aperture.y,fist.x,fist.y,false);
        pose.pushPose();try{
            pose.translate(x,y,z);
            pose.mulPose(Axis.ZP.rotationDegrees(side*(46+38*reach-24*pull)));
            pose.mulPose(Axis.XP.rotationDegrees(-32-10*clench-19*pull));
            pose.mulPose(Axis.YP.rotationDegrees(side*(11-21*clench)));
            // Keep the fist at the aperture and continue the shoulder beyond the active screen edge.
            // Solve in homogeneous coordinates so both hands track bob, FOV and window aspect.
            var armClip=new org.joml.Matrix4f(com.mojang.blaze3d.systems.RenderSystem.getProjectionMatrix())
                    .mul(com.mojang.blaze3d.systems.RenderSystem.getModelViewMatrix()).mul(pose.last().pose());
            var gripClip=armClip.getColumn(3,new org.joml.Vector4f());
            var shoulderDelta=armClip.getColumn(1,new org.joml.Vector4f()).mul(-.68f);
            float margin=1.24f;
            float outward=side*shoulderDelta.x-margin*shoulderDelta.w;
            if(outward>.001f){
                float length=(margin*gripClip.w-side*gripClip.x)/outward;
                pose.scale(1,net.minecraft.util.Mth.clamp(length,1,4),1);
            }
            pose.translate(side*.3125,-.68,0);
            var renderer=(net.minecraft.client.renderer.entity.player.PlayerRenderer)mc.getEntityRenderDispatcher().getRenderer(p);
            FateDrawHand.draw(renderer,p,arm==HumanoidArm.LEFT,pose,DRAW_BUFFER,event.getPackedLight(),age);
        }finally{pose.popPose();}
        if(age>=143){
            pose.pushPose();try{
                pose.translate(x,y,z);
                pose.mulPose(Axis.ZP.rotationDegrees(side*(82-52*step(age,149,160))));
                pose.mulPose(Axis.YP.rotationDegrees(90));pose.scale(.8f,.8f,.8f);
                FateItemRenderer.drawHeldEa(pose,DRAW_BUFFER,event.getPackedLight(),-1,0);
            }finally{pose.popPose();}
        }
        DRAW_BUFFER.endBatch();
        FateVfx.handSurface(age,side,aperture.x,aperture.y,fist.x,fist.y,true);
    }
    public static final class Setup {
        public static void register(net.minecraftforge.eventbus.api.IEventBus bus){bus.addListener(Setup::reload);}
        public static void reload(RegisterClientReloadListenersEvent e){
            com.mojang.logging.LogUtils.getLogger().info("Attaching Fate resource reload listener");
            e.registerReloadListener((net.minecraft.server.packs.resources.ResourceManagerReloadListener)resources->{FateItemRenderer.reload(resources);FateVfx.reload(resources);});
        }
    }
}
