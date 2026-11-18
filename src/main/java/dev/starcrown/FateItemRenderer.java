package dev.starcrown;

import java.io.*;
import java.util.*;
import java.util.zip.GZIPInputStream;
import com.mojang.blaze3d.vertex.*;
import com.mojang.math.Axis;
import dev.eavfx.EaChargeShader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.item.*;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Original triangle meshes and UVs. The pivot is the middle of each source grip. */
public final class FateItemRenderer extends BlockEntityWithoutLevelRenderer {
    private static FateItemRenderer INSTANCE;
    private record Part(ResourceLocation texture,ResourceLocation glow,float[] data){}
    private record BladeSpin(Vector3f pivot,Vector3f axis,float degreesPerSecond,float bottom,float top,
                             EaChargeShader.Rotor arcs){}
    private static final ResourceLocation BLADE_TEXTURE=id("textures/fate/ea_lame.png");
    private static List<Part> key=List.of(),ea=List.of();
    private static Map<Part,BladeSpin> eaSpins=Map.of();
    private static Vector3f eaTip;
    private static float runeBase, runeTip;
    public static FateItemRenderer instance(){if(INSTANCE==null)INSTANCE=new FateItemRenderer();return INSTANCE;}
    private FateItemRenderer(){super(Minecraft.getInstance().getBlockEntityRenderDispatcher(),Minecraft.getInstance().getEntityModels());}
    static ResourceLocation id(String path){return new ResourceLocation(StarcrownDoll.ID,path);}
    public static void reload(ResourceManager resources){
        key=load(resources,"bab_ilu");
        ea=load(resources,"ea");
        eaSpins=bladeSpins(ea);
        runeBase=(float)eaSpins.values().stream().mapToDouble(BladeSpin::bottom).min().orElse(0);
        runeTip=(float)eaSpins.values().stream().mapToDouble(BladeSpin::top).max().orElse(0);
        eaTip=ea.stream().filter(part->part.texture().equals(id("textures/fate/ea_bout.png")))
                .findFirst().map(part->{
                    Vector3f tip=center(part.data());
                    for(int i=1;i<part.data().length;i+=8)tip.y=Math.max(tip.y,part.data()[i]);
                    return tip;
                }).orElse(null);
    }
    private static Map<Part,BladeSpin> bladeSpins(List<Part> parts){
        var sections=parts.stream().filter(part->part.texture().equals(BLADE_TEXTURE))
                .map(part->Map.entry(part,center(part.data())))
                .sorted(Comparator.comparingDouble(entry->entry.getValue().y)).toList();
        if(sections.size()!=3)return Map.of();
        Vector3f lower=sections.get(0).getValue(),upper=sections.get(2).getValue();
        Vector3f axis=new Vector3f(upper).sub(lower);
        if(axis.y<1e-4f)return Map.of();
        axis.normalize();
        float[] speeds={24,-36,30};
        Map<Part,BladeSpin> spins=new HashMap<>();
        for(int index=0;index<sections.size();index++){
            var section=sections.get(index);
            float height=section.getValue().y;
            Vector3f pivot=new Vector3f(lower).fma((height-lower.y)/axis.y,axis);
            float bottom=Float.POSITIVE_INFINITY,top=Float.NEGATIVE_INFINITY;
            float[] vertices=section.getKey().data();
            for(int vertex=0;vertex<vertices.length;vertex+=8){
                float y=vertices[vertex+1];
                bottom=Math.min(bottom,y);
                top=Math.max(top,y);
            }
            var arcs=new EaChargeShader.Rotor(index,pivot,axis,bottom,top,vertices);
            spins.put(section.getKey(),new BladeSpin(pivot,axis,speeds[index],bottom,top,arcs));
        }
        return Map.copyOf(spins);
    }
    private static Vector3f center(float[] data){
        Vector3f min=new Vector3f(Float.POSITIVE_INFINITY),max=new Vector3f(Float.NEGATIVE_INFINITY);
        for(int index=0;index<data.length;index+=8){
            float x=data[index],y=data[index+1],z=data[index+2];
            min.set(Math.min(min.x,x),Math.min(min.y,y),Math.min(min.z,z));
            max.set(Math.max(max.x,x),Math.max(max.y,y),Math.max(max.z,z));
        }
        return min.add(max).mul(.5f);
    }
    private static List<Part> load(ResourceManager resources,String name){
        List<Part> parts=new ArrayList<>();
        try(var stream=new DataInputStream(new GZIPInputStream(resources.open(id("models/fate/"+name+".mesh.gz"))))){
            if(stream.readInt()!=0x46415431)throw new IOException("Invalid Fate mesh header");
            int n=stream.readInt();if(n<1||n>32)throw new IOException("Invalid part count");
            for(int p=0;p<n;p++){
                ResourceLocation texture=id(stream.readUTF());String glow=stream.readUTF();
                int count=stream.readInt();if(count<=0||count>600_000||count%3!=0)throw new IOException("Invalid triangle count");
                float[] data=new float[count*8];for(int i=0;i<data.length;i++){data[i]=stream.readFloat();if(!Float.isFinite(data[i]))throw new IOException("Non-finite mesh");}
                parts.add(new Part(texture,glow.isEmpty()?null:id(glow),data));
            }
            if(stream.read()!=-1)throw new IOException("Trailing mesh bytes");
        }catch(IOException error){com.mojang.logging.LogUtils.getLogger().error("Fate mesh unavailable: {}",name,error);return List.of();}
        return List.copyOf(parts);
    }
    @Override public void renderByItem(ItemStack stack,ItemDisplayContext context,PoseStack pose,MultiBufferSource buffers,int light,int overlay){
        boolean sword=stack.is(StarcrownDoll.EA.get());
        var mc=Minecraft.getInstance();
        var localCast=mc.player==null?null:FateClient.CASTS.get(mc.player.getUUID());
        boolean activeFirst=localCast!=null&&((context==ItemDisplayContext.FIRST_PERSON_LEFT_HAND)==((mc.player.getMainArm()==net.minecraft.world.entity.HumanoidArm.LEFT)^localCast.offhand()));
        if(!sword&&context.firstPerson()&&mc.player!=null&&activeFirst){
            float age=FateClient.localAge(mc.getFrameTime());
            if(age>=86&&age<143)return;
            if(age>=143&&age<=FateItems.DURATION)sword=true;
        }
        var cast=!sword&&FateClient.renderedPlayer!=null&&(context==ItemDisplayContext.THIRD_PERSON_LEFT_HAND||context==ItemDisplayContext.THIRD_PERSON_RIGHT_HAND)?FateClient.CASTS.get(FateClient.renderedPlayer):null;
        if(cast!=null){
            var owner=mc.level.getEntity(cast.entity());
            boolean left=owner instanceof net.minecraft.world.entity.player.Player p&&(p.getMainArm()==net.minecraft.world.entity.HumanoidArm.LEFT)^cast.offhand();
            if((context==ItemDisplayContext.THIRD_PERSON_LEFT_HAND)!=left)cast=null;
        }
        pose.pushPose();try{
            pose.translate(.5,.5,.5);
            if(cast!=null){
                float age=FateClient.age(cast,mc.getFrameTime());
                var owner=mc.level.getEntity(cast.entity());
                FateAnalysisPlacement.hand(pose,cast.owner(),owner==null?cast.yaw():owner.getViewYRot(mc.getFrameTime()),age,context==ItemDisplayContext.THIRD_PERSON_LEFT_HAND);
                if(age>=86&&age<143)return;
                if(age>=143&&age<=160)sword=true;
            }
            if(context==ItemDisplayContext.GUI){
                pose.mulPose(Axis.ZP.rotationDegrees(-32));pose.mulPose(Axis.YP.rotationDegrees(75));
                float scale=sword?.47f:1.175f;pose.scale(scale,scale,scale);pose.translate(0,sword?-.65:-.26,0);
                light=LightTexture.FULL_BRIGHT;
            }else if(context==ItemDisplayContext.GROUND){pose.scale(.55f,.55f,.55f);pose.translate(0,.1,0);}
            else if(context==ItemDisplayContext.FIXED){pose.scale(.5f,.5f,.5f);pose.translate(0,-.5,0);pose.mulPose(Axis.YP.rotationDegrees(90));}
            if(sword&&(context.firstPerson()||context==ItemDisplayContext.THIRD_PERSON_LEFT_HAND
                    ||context==ItemDisplayContext.THIRD_PERSON_RIGHT_HAND)){
                var owner=context.firstPerson()?mc.player:mc.level!=null&&FateClient.renderedPlayer!=null
                        ?mc.level.getPlayerByUUID(FateClient.renderedPlayer):null;
                boolean left=context==ItemDisplayContext.FIRST_PERSON_LEFT_HAND||context==ItemDisplayContext.THIRD_PERSON_LEFT_HAND;
                boolean active=owner!=null&&owner.isUsingItem()&&left==((owner.getMainArm()==net.minecraft.world.entity.HumanoidArm.LEFT)
                        ^(owner.getUsedItemHand()==net.minecraft.world.InteractionHand.OFF_HAND));
                float castAge=active&&stack.is(StarcrownDoll.EA.get())&&EaSkill.selected(stack)==EaSkill.ENUMA
                        ?dev.eavfx.EaClient.ageFor(owner):-1;
                if(!context.firstPerson()&&castAge>=0){
                    // The raised arm carries the grip; turn the blade upward in that hand.
                    pose.mulPose(Axis.XP.rotationDegrees(-90*FateClient.eaRaise(castAge)));
                }
                float ignition=cast!=null?0:FateClient.runeProgress(owner,stack,left,mc.getFrameTime());
                drawHeldEa(pose,buffers,light,castAge,ignition);
                if(castAge>=0 && eaTip!=null){
                    pose.pushPose();
                    try{
                        pose.mulPose(Axis.YP.rotationDegrees(180));
                        dev.eavfx.EaChargeStorm.tip(pose,buffers,eaTip.x,eaTip.y+.11f,eaTip.z,
                                castAge,dev.eavfx.EaClient.effectAgeFor(owner));
                    }finally{pose.popPose();}
                }
            }
            else draw(sword,pose,buffers,light);
        }finally{pose.popPose();}
    }
    static void drawHeldEa(PoseStack pose,MultiBufferSource buffers,int light,float castAge,float ignition){
        pose.pushPose();
        try{
            pose.mulPose(Axis.YP.rotationDegrees(180));
            draw(true,pose,buffers,light,castAge,ignition);
        }finally{pose.popPose();}
    }
    static void draw(boolean sword,PoseStack pose,MultiBufferSource buffers,int light){
        draw(sword,pose,buffers,light,-1,1);
    }
    private static void draw(boolean sword,PoseStack pose,MultiBufferSource buffers,int light,float castAge,float ignition){
        pose.pushPose();try{
        if(!sword)pose.scale(.4f,.4f,.4f);
        var mc=Minecraft.getInstance();
        double seconds=sword&&mc.level!=null?(mc.level.getGameTime()+((double)mc.getFrameTime()))/20.0:0;
        double elapsed=Math.max(0,castAge)/20.0;
        double ramp=Math.min(elapsed,.5);
        double acceleration=ramp*ramp+Math.max(0,elapsed-.5);
        boolean charged=castAge>=0&&EaChargeShader.ready();
        for(Part part:sword?ea:key){
            BladeSpin spin=sword?eaSpins.get(part):null;
            if(spin==null){drawPart(part,pose,buffers,light,ignition);continue;}
            pose.pushPose();
            try{
                Vector3f pivot=spin.pivot();
                float rate=spin.degreesPerSecond();
                // At full charge: 6.67 / -10 / 8.33 revolutions per second.
                float angle=(float)Math.toRadians((seconds*rate+acceleration*rate*99)%360);
                pose.translate(pivot.x,pivot.y,pivot.z);
                pose.mulPose(new Quaternionf().rotationAxis(angle,spin.axis()));
                pose.translate(-pivot.x,-pivot.y,-pivot.z);
                if(charged&&part.glow()!=null){
                    emit(part.data(),pose.last(),buffers.getBuffer(RenderType.entityCutoutNoCull(part.texture())),light);
                    emit(part.data(),pose.last(),EaChargeShader.runes(buffers,part.glow()),EaChargeShader.runeCharge(castAge));
                }else drawPart(part,pose,buffers,light,ignition);
            }finally{pose.popPose();}
        }
        // The conducting sections turn beneath each short-lived channel. Submit after the opaque mesh
        // so the reverse side of each arc is hidden by the blade, guard and other sections.
        if(charged&&castAge>=10)for(Part part:ea){
            BladeSpin spin=eaSpins.get(part);
            if(spin!=null&&part.glow()!=null)spin.arcs().draw(pose,buffers,castAge);
        }
        }finally{pose.popPose();}
    }
    private static void drawPart(Part part,PoseStack pose,MultiBufferSource buffers,int light,float ignition){
        emit(part.data(),pose.last(),buffers.getBuffer(RenderType.entityCutoutNoCull(part.texture())),light);
        if(part.glow()==null||ignition<=0)return;
        var out=buffers.getBuffer(RenderType.eyes(part.glow()));
        if(ignition>=1)emit(part.data(),pose.last(),out,LightTexture.FULL_BRIGHT);
        else emitBelow(part.data(),pose.last(),out,runeBase+(runeTip-runeBase)*ignition);
    }
    /** Clip only the emissive triangles. Interpolating their UVs keeps the source glyphs intact at the advancing edge. */
    private static void emitBelow(float[] data,PoseStack.Pose pose,VertexConsumer out,float height){
        float[] clipped=new float[32];
        for(int tri=0;tri<data.length;tri+=24){
            int count=0;
            for(int edge=0;edge<3;edge++){
                int a=tri+8*edge,b=tri+8*((edge+1)%3);
                boolean inside=data[a+1]<=height;
                if(inside){System.arraycopy(data,a,clipped,count,8);count+=8;}
                if(inside!=(data[b+1]<=height)){
                    float t=(height-data[a+1])/(data[b+1]-data[a+1]);
                    for(int k=0;k<8;k++)clipped[count++]=net.minecraft.util.Mth.lerp(t,data[a+k],data[b+k]);
                }
            }
            if(count<24)continue;
            for(int i=0;i<count;i+=8)vertex(clipped,i,pose,out,LightTexture.FULL_BRIGHT);
            if(count==24)vertex(clipped,16,pose,out,LightTexture.FULL_BRIGHT);
        }
    }
    private static void emit(float[] data,PoseStack.Pose pose,VertexConsumer out,int light){
        for(int i=0;i<data.length;i+=24){vertex(data,i,pose,out,light);vertex(data,i+8,pose,out,light);vertex(data,i+16,pose,out,light);vertex(data,i+16,pose,out,light);}
    }
    private static void vertex(float[] d,int i,PoseStack.Pose p,VertexConsumer out,int light){
        out.vertex(p.pose(),d[i],d[i+1],d[i+2]).color(255,255,255,255).uv(d[i+6],d[i+7]).overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(light).normal(p.normal(),d[i+3],d[i+4],d[i+5]).endVertex();
    }
}
