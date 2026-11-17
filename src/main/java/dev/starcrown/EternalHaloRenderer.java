package dev.starcrown;

import java.io.*;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.model.*;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.client.ICurioRenderer;

/** Original textured triangle mesh, not a sprite plane or cube approximation. */
public final class EternalHaloRenderer implements ICurioRenderer {
    private static final ResourceLocation MESH=new ResourceLocation(StarcrownDoll.ID,"models/accessory/eternal_halo.mesh");
    private static final ResourceLocation WINGS=new ResourceLocation(StarcrownDoll.ID,"models/accessory/eternal_wings.mesh");
    private static final ResourceLocation TEXTURE=new ResourceLocation(StarcrownDoll.ID,"textures/accessory/eternal_halo.png");
    private static volatile float[] vertices=new float[0];
    private static volatile float[] wings=new float[0];
    // Match the source avatar's roughly 1.85 m body height to the vanilla model's 2 m height.
    public static final float WORN_SCALE=1.083f;
    public static final float WINGS_SCALE=WORN_SCALE;
    public static final float HEIGHT_OFFSET=1.501f-1.8155191f*WORN_SCALE;
    public static final float BACK_OFFSET=.31046635f*WORN_SCALE;
    public static void reload(ResourceManager resources) {
        float[] halo=loadMesh(resources,MESH),nextWings=loadMesh(resources,WINGS);
        vertices=halo;wings=nextWings;
    }
    private static float[] loadMesh(ResourceManager resources,ResourceLocation path) {
        try(var input=new DataInputStream(new BufferedInputStream(resources.open(path)))) {
            if(input.readInt()!=0x45524831)throw new IOException("Invalid eternal halo mesh header");
            int count=input.readInt();if(count<=0||count>100_000||count%3!=0)throw new IOException("Invalid vertex count "+count);
            float[] next=new float[count*8];
            for(int i=0;i<next.length;i++){next[i]=input.readFloat();if(!Float.isFinite(next[i]))throw new IOException("Non-finite mesh coordinate");}
            if(input.read()!=-1)throw new IOException("Trailing mesh bytes");return next;
        } catch(IOException e) {throw new IllegalStateException("Cannot load original accessory mesh "+path,e);}
    }
    @Override public <T extends LivingEntity,M extends EntityModel<T>> void render(ItemStack stack,SlotContext context,
            PoseStack pose,RenderLayerParent<T,M> parent,MultiBufferSource buffers,int light,float limbSwing,
            float limbSwingAmount,float partialTick,float ageInTicks,float netHeadYaw,float headPitch) {
        var entity=context.entity();
        if(!context.visible()||entity.isInvisible()||entity instanceof net.minecraft.world.entity.player.Player player
                && DollCameraVisibility.isFirstPersonBodyPass(player))return;
        if(!(parent.getModel() instanceof HeadedModel headed)||!headed.getHead().visible)return;
        pose.pushPose();
        try {
            wornPose(parent.getModel(),pose,ageInTicks);
            draw(pose,buffers,LightTexture.FULL_BRIGHT);
            EternalHaloBloom.capture(pose);
        } finally {pose.popPose();}
        pose.pushPose();
        try {
            wingsPose(parent.getModel(),pose,ageInTicks);
            drawTriangles(wings,wings.length,pose,buffers,LightTexture.FULL_BRIGHT);
        } finally {pose.popPose();}
    }
    /** Root pose already follows body yaw. Head pitch/yaw must never enter the halo transform. */
    public static void wornPose(EntityModel<?> model,PoseStack pose,float ageInTicks) {
        attachmentPose(model,pose,ageInTicks);pose.scale(WORN_SCALE,WORN_SCALE,WORN_SCALE);
    }
    public static void wingsPose(EntityModel<?> model,PoseStack pose,float ageInTicks) {
        attachmentPose(model,pose,ageInTicks);pose.scale(WINGS_SCALE,WINGS_SCALE,WINGS_SCALE);
    }
    private static void attachmentPose(EntityModel<?> model,PoseStack pose,float ageInTicks) {
        if(model instanceof HumanoidModel<?> human)human.body.translateAndRotate(pose);
        float hover=(float)Math.sin(ageInTicks*.065f)*.018f;
        pose.translate(0,HEIGHT_OFFSET+hover,BACK_OFFSET);
    }
    public static int wingTriangles(){return wings.length/24;}
    static float[] mesh() {return vertices;}
    static ResourceLocation texture() {return TEXTURE;}
    public static void draw(PoseStack pose,MultiBufferSource buffers,int light) {
        draw(pose,buffers,light,false);
    }
    public static void draw(PoseStack pose,MultiBufferSource buffers,int light,boolean innerOnly) {
        float[] mesh=vertices;
        int end=innerOnly?Math.min(mesh.length,920*24):mesh.length;
        drawTriangles(mesh,end,pose,buffers,light);
    }
    private static void drawTriangles(float[] mesh,int end,PoseStack pose,MultiBufferSource buffers,int light) {
        var buffer=buffers.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));var transform=pose.last();
        for(int i=0;i<end;i+=24) {
            vertex(buffer,transform,mesh,i,light);vertex(buffer,transform,mesh,i+8,light);
            vertex(buffer,transform,mesh,i+16,light);vertex(buffer,transform,mesh,i+16,light);
        }
    }
    private static void vertex(VertexConsumer buffer,PoseStack.Pose transform,float[] data,int i,int light) {
        buffer.vertex(transform.pose(),data[i],data[i+1],data[i+2]).color(255,255,255,255).uv(data[i+6],data[i+7])
                .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(transform.normal(),data[i+3],data[i+4],data[i+5]).endVertex();
    }
}
