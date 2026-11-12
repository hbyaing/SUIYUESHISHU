package dev.minggulong.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import dev.minggulong.MingGuLongMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.List;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;

@Mod.EventBusSubscriber(modid=MingGuLongMod.ID,value=Dist.CLIENT)
public final class HuangWuClient {
    private static Vec3 center;
    private static double startTick, fixed=-1;
    private static boolean loop;
    private static Object sourceLevel;
    private static double tick(){var l=Minecraft.getInstance().level;return l==null?0:l.getGameTime();}
    private static int play(boolean repeat){
        var mc=Minecraft.getInstance();if(mc.player==null||mc.level==null)return 0;
        HitResult hit=mc.player.pick(16,0,false);
        center=hit.getType()!=HitResult.Type.MISS?hit.getLocation().add(0,1.6,0):mc.player.getEyePosition().add(mc.player.getLookAngle().scale(9));
        startTick=tick();fixed=-1;loop=repeat;sourceLevel=mc.level;
        mc.player.displayClientMessage(Component.literal("荒芜之噬 · 特效预览"),true);return 1;
    }
    @SubscribeEvent public static void commands(RegisterClientCommandsEvent e){
        e.getDispatcher().register(Commands.literal("huangwu").executes(c->play(false))
            .then(Commands.literal("loop").executes(c->play(true)))
            .then(Commands.literal("off").executes(c->{center=null;return 1;}))
            .then(Commands.literal("time").then(Commands.argument("seconds",DoubleArgumentType.doubleArg(0,4.32)).executes(c->{if(center==null)play(false);fixed=DoubleArgumentType.getDouble(c,"seconds");return 1;}))));
    }
    @SubscribeEvent public static void render(RenderLevelStageEvent e){
        if(e.getStage()!=RenderLevelStageEvent.Stage.AFTER_PARTICLES||center==null)return;
        if(Minecraft.getInstance().level==null||Minecraft.getInstance().level!=sourceLevel){center=null;return;}
        double t=fixed>=0?fixed:(tick()+e.getPartialTick()-startTick)/20.;
        if(loop&&fixed<0)t%=HuangWuGeometry.DURATION;
        if(t>=HuangWuGeometry.DURATION){center=null;return;}
        List<HuangWuGeometry.Tri> mesh=HuangWuGeometry.sample(t);
        PoseStack pose=e.getPoseStack();Vec3 cam=e.getCamera().getPosition();pose.pushPose();
        pose.translate(center.x-cam.x,center.y-cam.y,center.z-cam.z);
        boolean depth=GL11.glIsEnabled(GL11.GL_DEPTH_TEST),blend=GL11.glIsEnabled(GL11.GL_BLEND),cull=GL11.glIsEnabled(GL11.GL_CULL_FACE),mask=GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        int src=GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB),dst=GL11.glGetInteger(GL14.GL_BLEND_DST_RGB),srcA=GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA),dstA=GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA);
        var oldShader=RenderSystem.getShader();float[] oldColor=RenderSystem.getShaderColor().clone();
        try {
            RenderSystem.enableDepthTest();RenderSystem.disableCull();
            RenderSystem.setShader(GameRenderer::getPositionColorShader);
            RenderSystem.setShaderColor(1,1,1,1);
            RenderSystem.disableBlend();RenderSystem.depthMask(true);draw(mesh,pose.last().pose(),true);
            RenderSystem.enableBlend();RenderSystem.blendFunc(com.mojang.blaze3d.platform.GlStateManager.SourceFactor.SRC_ALPHA,com.mojang.blaze3d.platform.GlStateManager.DestFactor.ONE);
            RenderSystem.depthMask(false);draw(mesh,pose.last().pose(),false);
        } finally {
            RenderSystem.depthMask(mask);if(depth)RenderSystem.enableDepthTest();else RenderSystem.disableDepthTest();
            if(cull)RenderSystem.enableCull();else RenderSystem.disableCull();
            RenderSystem.blendFuncSeparate(src,dst,srcA,dstA);if(blend)RenderSystem.enableBlend();else RenderSystem.disableBlend();
            RenderSystem.setShader(()->oldShader);RenderSystem.setShaderColor(oldColor[0],oldColor[1],oldColor[2],oldColor[3]);pose.popPose();
        }
    }
    private static void draw(List<HuangWuGeometry.Tri> mesh,Matrix4f matrix,boolean solid){
        BufferBuilder b=Tesselator.getInstance().getBuilder();b.begin(VertexFormat.Mode.TRIANGLES,DefaultVertexFormat.POSITION_COLOR);
        for(var tri:mesh)if(tri.solid()==solid){vertex(b,matrix,tri.a(),tri.color());vertex(b,matrix,tri.b(),tri.colorB());vertex(b,matrix,tri.c(),tri.colorC());}
        BufferUploader.drawWithShader(b.end());
    }
    private static void vertex(BufferBuilder b,Matrix4f m,HuangWuGeometry.V p,HuangWuGeometry.C c){b.vertex(m,(float)p.x(),(float)p.y(),(float)p.z()).color(c.r(),c.g(),c.b(),c.a()).endVertex();}
}
