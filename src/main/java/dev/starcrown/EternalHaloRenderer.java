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
    private static final ResourceLocation TEXTURE=new ResourceLocation(StarcrownDoll.ID,"textures/accessory/eternal_halo.png");
    private static volatile float[] vertices=new float[0];
    public static void reload(ResourceManager resources) {
        try(var input=new DataInputStream(new BufferedInputStream(resources.open(MESH)))) {
            if(input.readInt()!=0x45524831)throw new IOException("Invalid eternal halo mesh header");
            int count=input.readInt();if(count<=0||count>100_000||count%3!=0)throw new IOException("Invalid vertex count "+count);
            float[] next=new float[count*8];
            for(int i=0;i<next.length;i++){next[i]=input.readFloat();if(!Float.isFinite(next[i]))throw new IOException("Non-finite mesh coordinate");}
            if(input.read()!=-1)throw new IOException("Trailing mesh bytes");vertices=next;
        } catch(IOException e) {throw new IllegalStateException("Cannot load original eternal halo mesh",e);}
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
            headed.getHead().translateAndRotate(pose);
            // Preserve the existing height and size; head-local +Z moves the mesh behind the head.
            // Its nearest surface is now beyond the outer hair layer (Z=0.28125), with an air gap.
            pose.translate(0,-.56,.55);pose.scale(.72f,.72f,.72f);
            draw(pose,buffers,light);
        } finally {pose.popPose();}
    }
    public static void draw(PoseStack pose,MultiBufferSource buffers,int light) {
        draw(pose,buffers,light,false);
    }
    public static void draw(PoseStack pose,MultiBufferSource buffers,int light,boolean innerOnly) {
        float[] mesh=vertices;var buffer=buffers.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));var transform=pose.last();
        int end=innerOnly?Math.min(mesh.length,920*24):mesh.length;
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
