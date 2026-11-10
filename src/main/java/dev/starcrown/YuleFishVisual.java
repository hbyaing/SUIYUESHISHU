package dev.starcrown;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

final class YuleFishVisual {
    static final ResourceLocation TEXTURE = new ResourceLocation(StarcrownDoll.ID, "textures/item/fishing_god_fish.png");
    static final int WIDTH = 1265;
    static final int HEIGHT = 1244;

    static void render(PoseStack pose, MultiBufferSource buffers, int light, int overlay, boolean inventory) {
        pose.translate(.5, 7.35 / 16, 3.9 / 16);
        pose.mulPose(Axis.YP.rotationDegrees(15));
        pose.mulPose(Axis.ZP.rotationDegrees(73));
        pose.mulPose(Axis.YP.rotationDegrees(180));
        pose.scale(.72f, .72f, .72f);
        // The GUI's unshaded textured layer preserves the original pixel colors.
        var vertices = buffers.getBuffer(inventory ? RenderType.text(TEXTURE) : RenderType.entityCutoutNoCull(TEXTURE));
        float halfHeight = .5f * HEIGHT / WIDTH;
        vertex(vertices, pose.last(), -.5f, halfHeight, 0, 0, light, overlay, inventory);
        vertex(vertices, pose.last(), -.5f, -halfHeight, 0, 1, light, overlay, inventory);
        vertex(vertices, pose.last(), .5f, -halfHeight, 1, 1, light, overlay, inventory);
        vertex(vertices, pose.last(), .5f, halfHeight, 1, 0, light, overlay, inventory);
    }

    private static void vertex(VertexConsumer out, PoseStack.Pose pose, float x, float y,
                               float u, float v, int light, int overlay, boolean inventory) {
        out.vertex(pose.pose(), x, y, 0).color(255, 255, 255, 255).uv(u, v);
        if (!inventory) out.overlayCoords(overlay);
        out.uv2(light);
        if (!inventory) out.normal(pose.normal(), 0, 0, 1);
        out.endVertex();
    }
}
