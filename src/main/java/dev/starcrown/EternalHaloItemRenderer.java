package dev.starcrown;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.world.item.*;

/** Only the temporary rewind activation stack selects this model. Normal inventory icons stay pixel art. */
public final class EternalHaloItemRenderer extends BlockEntityWithoutLevelRenderer {
    private static EternalHaloItemRenderer instance;
    public static EternalHaloItemRenderer instance() {
        if(instance==null)instance=new EternalHaloItemRenderer();return instance;
    }
    private EternalHaloItemRenderer() {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(),Minecraft.getInstance().getEntityModels());
    }
    @Override public void renderByItem(ItemStack stack,ItemDisplayContext context,PoseStack pose,MultiBufferSource buffers,int light,int overlay) {
        pose.pushPose();
        try {
            pose.translate(.5,.5,.5);pose.scale(1.65f,-1.65f,-1.65f);
            EternalHaloRenderer.draw(pose,buffers,LightTexture.FULL_BRIGHT,true);
        } finally {pose.popPose();}
    }
}
