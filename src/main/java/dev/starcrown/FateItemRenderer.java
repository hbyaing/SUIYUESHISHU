package dev.starcrown;

import java.io.*;
import java.util.*;
import java.util.zip.GZIPInputStream;
import com.mojang.blaze3d.vertex.*;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.item.*;

/** Original triangle meshes and UVs. The pivot is the middle of each source grip. */
public final class FateItemRenderer extends BlockEntityWithoutLevelRenderer {
    private static FateItemRenderer INSTANCE;
    private record Part(ResourceLocation texture,ResourceLocation glow,float[] data){}
    private static List<Part> key=List.of(),ea=List.of();
    public static FateItemRenderer instance(){if(INSTANCE==null)INSTANCE=new FateItemRenderer();return INSTANCE;}
    private FateItemRenderer(){super(Minecraft.getInstance().getBlockEntityRenderDispatcher(),Minecraft.getInstance().getEntityModels());}
    static ResourceLocation id(String path){return new ResourceLocation(StarcrownDoll.ID,path);}
    public static void reload(ResourceManager resources){key=load(resources,"bab_ilu");ea=load(resources,"ea");}
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
                if(age<114)FateAnalysisPlacement.upright(pose,cast.owner(),owner==null?cast.yaw():owner.getViewYRot(mc.getFrameTime()));
                if(age>=86&&age<143)return;
                if(age>=143&&age<=160)sword=true;
            }
            if(context==ItemDisplayContext.GUI){
                pose.mulPose(Axis.ZP.rotationDegrees(-32));pose.mulPose(Axis.YP.rotationDegrees(75));
                float scale=sword?.47f:1.175f;pose.scale(scale,scale,scale);pose.translate(0,sword?-.65:-.26,0);
                light=LightTexture.FULL_BRIGHT;
            }else if(context==ItemDisplayContext.GROUND){pose.scale(.55f,.55f,.55f);pose.translate(0,.1,0);}
            else if(context==ItemDisplayContext.FIXED){pose.scale(.5f,.5f,.5f);pose.translate(0,-.5,0);pose.mulPose(Axis.YP.rotationDegrees(90));}
            draw(sword,pose,buffers,light);
        }finally{pose.popPose();}
    }
    static void draw(boolean sword,PoseStack pose,MultiBufferSource buffers,int light){
        pose.pushPose();try{
        if(!sword)pose.scale(.4f,.4f,.4f);
        for(Part part:sword?ea:key){
            emit(part.data(),pose.last(),buffers.getBuffer(RenderType.entityCutoutNoCull(part.texture())),light);
            if(part.glow()!=null)emit(part.data(),pose.last(),buffers.getBuffer(RenderType.eyes(part.glow())),LightTexture.FULL_BRIGHT);
        }
        }finally{pose.popPose();}
    }
    private static void emit(float[] data,PoseStack.Pose pose,VertexConsumer out,int light){
        for(int i=0;i<data.length;i+=24){vertex(data,i,pose,out,light);vertex(data,i+8,pose,out,light);vertex(data,i+16,pose,out,light);vertex(data,i+16,pose,out,light);}
    }
    private static void vertex(float[] d,int i,PoseStack.Pose p,VertexConsumer out,int light){
        out.vertex(p.pose(),d[i],d[i+1],d[i+2]).color(255,255,255,255).uv(d[i+6],d[i+7]).overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(light).normal(p.normal(),d[i+3],d[i+4],d[i+5]).endVertex();
    }
}
