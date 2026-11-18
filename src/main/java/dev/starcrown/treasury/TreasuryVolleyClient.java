package dev.starcrown.treasury;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.starcrown.StarcrownDoll;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

/** Real item displays and interpolated server-owned weapon tips; all damage and impacts are authoritative. */
@Mod.EventBusSubscriber(modid=StarcrownDoll.ID,value=Dist.CLIENT)
public final class TreasuryVolleyClient {
    private static final Map<UUID,Volley> ACTIVE=new LinkedHashMap<>();
    private static final Vec3 UP=new Vec3(0,1,0);
    private static final int LIGHT=LightTexture.pack(13,15);
    // Forge subscribes this class before Sheets can resolve registry-backed materials.
    // Only the render thread creates or uses these batches, starting with the first visible weapon.
    private static MultiBufferSource.BufferSource buffers;
    private static ClientLevel world;

    private static MultiBufferSource.BufferSource makeBuffers(){
        Map<RenderType,BufferBuilder> batches=new LinkedHashMap<>();
        for(RenderType type:List.of(Sheets.solidBlockSheet(),Sheets.cutoutBlockSheet(),Sheets.translucentCullBlockSheet(),
                Sheets.translucentItemSheet(),RenderType.glint(),RenderType.glintDirect(),RenderType.entityGlint(),
                RenderType.entityGlintDirect(),RenderType.lightning())){
            batches.computeIfAbsent(type,key->new BufferBuilder(16384));
        }
        return MultiBufferSource.immediateWithBuffers(batches,new BufferBuilder(65536));
    }

    private record Sample(long tick,Vec3 position,Vec3 velocity) {}
    private record Motion(Vec3 position,Vec3 velocity) {}

    private static final class Display {
        final ItemStack stack;
        BakedModel lastModel;
        TreasuryWeaponPose pose;
        Display(ItemStack stack){this.stack=stack;}
        TreasuryWeaponPose profile(BakedModel current){
            if(current!=lastModel){
                pose=TreasuryWeaponPose.inspect(stack,current);
                lastModel=current;
            }
            return pose;
        }
    }

    private static final class Volley {
        final TreasuryNetwork.Cast cast;
        final ShotView[] shots;
        Volley(TreasuryNetwork.Cast cast,List<ItemStack> models){
            this.cast=cast;
            var gates=cast.gates();
            shots=new ShotView[models.size()];
            List<Display> displays=new ArrayList<>();
            for(int i=0;i<shots.length;i++){
                ItemStack stack=models.get(i);
                Display display=null;
                for(Display d:displays)if(ItemStack.isSameItemSameTags(d.stack,stack)){display=d;break;}
                if(display==null){display=new Display(stack.copy());displays.add(display);}
                shots[i]=new ShotView(cast,gates.get(i),i,display);
            }
        }
    }

    private static final class ShotView {
        final Display display;
        final ItemStack stack;
        final Vec3 gate,launch,forward,presentedDirection;
        final int index,styleIndex,launchAge,openingDelay;
        final long launchTick;
        final double length,roll;
        final ArrayDeque<Sample> history=new ArrayDeque<>();
        boolean ended;

        ShotView(TreasuryNetwork.Cast cast,TreasuryLayout.Gate layout,int index,Display display){
            this.display=display;
            this.stack=display.stack;
            this.index=index;
            // Most refill packets contain only one or two gates. Keep their rolls and wake density varied.
            styleIndex=cast.refill()?(int)Math.floorMod(cast.seed()+index,100L):index;
            gate=TreasuryVolley.gateCenter(cast,layout);
            launch=TreasuryVolley.launchPoint(cast,layout);
            forward=cast.forward().normalize();
            launchAge=cast.launchAge(index);
            launchTick=cast.start()+launchAge;
            openingDelay=GoldenGateRenderer.openingDelay(cast,index);
            double side=Mth.clamp(layout.x()/(cast.mode()==0?7.5:16),-1,1);
            presentedDirection=forward.add(forward.cross(UP).scale(.57*side)).add(0,.54+.09*(styleIndex%3),0).normalize();
            length=Mth.clamp(1.56*layout.radius()+.32,1.32,2.45);
            roll=(styleIndex*.61803398875%1.0-.5)*1.45;
        }

        void append(long tick,TreasuryVolley.Shot shot){
            if(ended || (!history.isEmpty() && tick<history.getLast().tick()))return;
            if(history.isEmpty() && tick>=launchTick && tick<launchTick+8)
                history.add(new Sample(launchTick-1,launch,forward.scale(1.25)));
            if(!history.isEmpty() && history.getLast().tick()==tick)history.removeLast();
            history.add(new Sample(tick,shot.position(),shot.velocity()));
            while(history.size()>6)history.removeFirst();
        }

        Motion flight(double tick){
            if(history.isEmpty()) {
                Vec3 velocity=forward.scale(1.25);
                return new Motion(launch.add(velocity.scale(Mth.clamp(tick-launchTick+1,0,4))),velocity);
            }
            Sample a=history.getFirst();
            if(tick<=a.tick())return new Motion(a.position(),a.velocity());
            for(Sample b:history){
                if(b.tick()==a.tick())continue;
                if(tick<=b.tick()){
                    double span=b.tick()-a.tick(),u=(tick-a.tick())/span;
                    // Cubic Hermite preserves both transmitted endpoints and tangents through homing turns.
                    Vec3 pos=a.position().lerp(b.position(),u*u*(3-2*u))
                            .add(a.velocity().scale(span*u*(1-u)*(1-u)))
                            .subtract(b.velocity().scale(span*u*u*(1-u)));
                    Vec3 velocity=b.position().subtract(a.position()).scale(6*u*(1-u)/span)
                            .add(a.velocity().scale((1-u)*(1-3*u))).add(b.velocity().scale(u*(3*u-2)));
                    return new Motion(pos,velocity.lengthSqr()>1e-8?velocity:b.velocity());
                }
                a=b;
            }
            return new Motion(a.position().add(a.velocity().scale(Math.min(3,tick-a.tick()))),a.velocity());
        }
    }

    public static void start(TreasuryNetwork.Cast cast,List<ItemStack> perGate){
        ClientLevel level=Minecraft.getInstance().level;
        if(level==null || !level.dimension().equals(cast.dimension()) || cast.chains()
                || perGate.size()!=cast.gates().size())return;
        useLevel(level);
        ACTIVE.computeIfAbsent(cast.id(),id->new Volley(cast,perGate));
    }

    public static void snapshot(UUID id,long serverTick,List<TreasuryVolley.Shot> flying,List<Integer> removed){
        Volley volley=ACTIVE.get(id);
        if(volley==null)return;
        for(var shot:flying)if(shot.index()>=0 && shot.index()<volley.shots.length)
            volley.shots[shot.index()].append(serverTick,shot);
        for(int i:removed)if(i>=0 && i<volley.shots.length){
            volley.shots[i].ended=true;
            volley.shots[i].history.clear();
        }
    }

    public static void end(UUID id){ACTIVE.remove(id);}

    private static void useLevel(ClientLevel level){
        if(world!=level){ACTIVE.clear();world=level;}
    }

    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event){
        if(event.phase!=TickEvent.Phase.END)return;
        useLevel(Minecraft.getInstance().level);
        if(world!=null)ACTIVE.values().removeIf(v->world.getGameTime()-v.cast.start()>160);
    }
    @SubscribeEvent public static void leave(ClientPlayerNetworkEvent.LoggingOut event){useLevel(null);}

    @SubscribeEvent(priority=EventPriority.LOWEST)
    public static void render(RenderLevelStageEvent event){
        if(event.getStage()!=RenderLevelStageEvent.Stage.AFTER_BLOCK_ENTITIES || ACTIVE.isEmpty()
                || world==null || world!=Minecraft.getInstance().level)return;
        Vec3 eye=event.getCamera().getPosition();
        double now=(double)world.getGameTime()+event.getPartialTick(), flightTime=now-2;
        PoseStack matrices=event.getPoseStack();
        int wakeBudget=48, flareBudget=12;
        for(Volley volley:ACTIVE.values()){
            double age=now-volley.cast.start();
            boolean refill=volley.cast.refill();
            if(age<0 || (!refill && age<=8))continue;
            boolean dense=volley.cast.mode()==1;
            // A fresh replacement has a pin of liquid at birth. Let its tip fit that same growing mouth.
            double mouthWidth=refill?.035+.965*smooth((age+2)/13):1;
            for(ShotView shot:volley.shots){
                if(shot.ended || shot.stack.isEmpty())continue;
                boolean fired=age>=shot.launchAge;
                double emerging=refill?smooth((age+2)/15):smooth((age-8-shot.openingDelay)/10);
                if(emerging<=0)continue;
                Motion motion;
                if(fired)motion=shot.flight(Math.max(flightTime,shot.launchTick-1));
                else {
                    double aiming=smooth((age-shot.launchAge+8)/8);
                    double tilt=(1-aiming)*(refill?emerging:1);
                    Vec3 axis=shot.forward.lerp(shot.presentedDirection,tilt).normalize();
                    double hover=.045*Math.sin(age*.20+shot.styleIndex)*emerging*(1-aiming);
                    Vec3 tip=shot.gate.add(axis.scale(shot.gate.distanceTo(shot.launch)*emerging)).add(0,hover,0);
                    motion=new Motion(tip,axis);
                }
                Vec3 tip=motion.position();
                if(!event.getFrustum().isVisible(new AABB(tip,tip).inflate(fired?6:3)))continue;
                Vec3 axis=motion.velocity().normalize();
                double exposedLength=shot.length;
                double hiltClearance=Double.POSITIVE_INFINITY;
                if(now<shot.launchTick+6){
                    double alignment=Mth.clamp(axis.dot(shot.forward),-1,1);
                    double tipDepth=tip.subtract(shot.gate).dot(shot.forward);
                    // Allow for the width of angled guards as the full-width model crosses the portal.
                    double guardMargin=.28*shot.length*Math.sqrt(1-alignment*alignment)+.015;
                    exposedLength=Mth.clamp((tipDepth-guardMargin)/Math.max(.01,alignment),0,shot.length);
                    if(exposedLength<.025)continue;
                    hiltClearance=tipDepth-shot.length*alignment-.025;
                }
                var renderer=Minecraft.getInstance().getItemRenderer();
                BakedModel model=renderer.getModel(shot.stack,world,null,shot.index);
                TreasuryWeaponPose shape=shot.display.profile(model);
                double scale=shot.length/shape.length();
                Vec3 local=tip.subtract(eye);
                matrices.pushPose();
                try{
                    matrices.translate(local.x,local.y,local.z);
                    matrices.mulPose(Axis.YP.rotation((float)Math.atan2(axis.x,axis.z)));
                    matrices.mulPose(Axis.XP.rotation((float)-Math.asin(Mth.clamp(axis.y,-1,1))));
                    matrices.mulPose(Axis.ZP.rotation((float)shot.roll));
                    float widthScale=(float)(scale*mouthWidth);
                    matrices.scale(widthScale,widthScale,(float)(exposedLength/shape.length()));
                    matrices.mulPose(shape.toForward());
                    matrices.translate(-shape.tip().x,-shape.tip().y,-shape.tip().z);
                    // NONE retains the real item model and permits BEWLR rendering without invoking a held-use pose.
                    if(buffers==null)buffers=makeBuffers();
                    renderer.render(shot.stack,ItemDisplayContext.NONE,false,matrices,buffers,LIGHT,
                            OverlayTexture.NO_OVERLAY,model);
                }finally{matrices.popPose();}
                if(fired){
                    double launchFade=1-smooth((flightTime-shot.launchTick+1)/4.5);
                    boolean keepsWake=!dense || shot.styleIndex%3==0;
                    if(wakeBudget>0 && hiltClearance>.03 && (keepsWake || launchFade>.02)){
                        wake(matrices.last().pose(),eye,shot,motion,flightTime,launchFade,
                                keepsWake?1:launchFade,dense,hiltClearance);
                        wakeBudget--;
                    }
                    if(flareBudget>0 && hiltClearance>.65 && launchFade>.02 && local.lengthSqr()<64*64){
                        launchGlints(matrices.last().pose(),local,axis,shot,launchFade,dense);
                        flareBudget--;
                    }
                }
            }
        }
        if(buffers!=null)buffers.endBatch();
    }

    private static double smooth(double x){x=Mth.clamp(x,0,1);return x*x*(3-2*x);}

    /** Sample the actual hilt trajectory, clipping by distance even during a sharp homing turn. */
    private static void wake(Matrix4f matrix,Vec3 eye,ShotView shot,Motion motion,double tick,
                             double launchFade,double visibility,boolean dense,double hiltClearance){
        double speed=motion.velocity().length();
        if(speed<.05 || tick<=shot.launchTick-1)return;
        double acceleration=smooth((speed-1.25)/1.95);
        double length=Math.min(hiltClearance,Math.min(1.62,shot.length*(.34+.30*acceleration))*(dense?.76:1));
        double halfWidth=.021-.005*acceleration+.017*launchFade;
        double opacity=visibility*(dense?.72:1);
        int segments=dense?2:3;
        VertexConsumer out=buffers.getBuffer(RenderType.lightning());
        Vec3 a=motion.position().subtract(motion.velocity().normalize().scale(shot.length)).subtract(eye);
        double walked=0;
        for(int i=1;i<=segments;i++){
            double sampleTick=Math.max(shot.launchTick-1,tick-length*i/(speed*segments));
            Motion sample=shot.flight(sampleTick);
            Vec3 b=sample.position().subtract(sample.velocity().normalize().scale(shot.length)).subtract(eye);
            double step=a.distanceTo(b);
            if(step<.001)break;
            if(step>length-walked){
                b=a.lerp(b,(length-walked)/step);
                step=length-walked;
            }
            double front=1-walked/length;
            walked+=step;
            double back=1-walked/length;
            if(i==segments || sampleTick<=shot.launchTick-1)back=0;
            Vec3 side=a.subtract(b).cross(a).normalize();
            if(side.lengthSqr()>.5){
                stroke(out,matrix,a,b,side,halfWidth*front,halfWidth*back,255,170,55,
                        (int)(100*opacity*front),(int)(90*opacity*back));
                if(!dense || shot.styleIndex%6==0){
                    stroke(out,matrix,a,b,side,halfWidth*.32*front,halfWidth*.32*back,255,239,171,
                            (int)((135+40*launchFade)*opacity*front),(int)(110*opacity*back));
                }
            }
            a=b;
            if(walked>=length-.001 || sampleTick<=shot.launchTick-1)break;
        }
    }

    /** Short gold filings peel away from the departing hilt during the launch pulse. */
    private static void launchGlints(Matrix4f matrix,Vec3 tip,Vec3 axis,ShotView shot,double fade,boolean dense){
        VertexConsumer out=buffers.getBuffer(RenderType.lightning());
        Vec3 hilt=tip.subtract(axis.scale(shot.length));
        Vec3 side=(Math.abs(axis.y)<.95?UP:new Vec3(1,0,0)).cross(axis).normalize();
        Vec3 up=axis.cross(side);
        int count=dense?2:3;
        for(int i=0;i<count;i++){
            double angle=shot.roll+i*2*Math.PI/count;
            Vec3 radial=side.scale(Math.cos(angle)).add(up.scale(Math.sin(angle)));
            Vec3 a=hilt.add(radial.scale(.04+.07*(1-fade)));
            Vec3 b=hilt.subtract(axis.scale(.24+.33*(1-fade))).add(radial.scale(.12+.18*(1-fade)));
            Vec3 width=a.subtract(b).cross(a).normalize();
            stroke(out,matrix,a,b,width,.023*fade,0,255,226,138,(int)(200*fade),0);
        }
    }

    private static void stroke(VertexConsumer out,Matrix4f matrix,Vec3 a,Vec3 b,Vec3 side,
                               double widthA,double widthB,int red,int green,int blue,int alphaA,int alphaB){
        Vec3 wa=side.scale(widthA),wb=side.scale(widthB);
        ribbonVertex(out,matrix,a.add(wa),red,green,blue,alphaA);
        ribbonVertex(out,matrix,b.add(wb),red,green,blue,alphaB);
        ribbonVertex(out,matrix,b.subtract(wb),red,green,blue,alphaB);
        ribbonVertex(out,matrix,a.subtract(wa),red,green,blue,alphaA);
    }

    private static void ribbonVertex(VertexConsumer out,Matrix4f matrix,Vec3 point,int r,int g,int b,int alpha){
        out.vertex(matrix,(float)point.x,(float)point.y,(float)point.z)
                .color(r,g,b,alpha).endVertex();
    }
    private TreasuryVolleyClient(){}
}
