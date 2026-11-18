package dev.eavfx;

import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.renderer.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RegisterShadersEvent;
import java.io.IOException;

/** Rounded launch cap blended into a solid, shader-lit cylinder. */
public final class EaBeam extends RenderType {
    private static final ResourceLocation FLOW = new ResourceLocation("starcrown_ea", "textures/effect/beam_flow.png");
    private static ShaderInstance core, glow;
    private static final MultiBufferSource.BufferSource BUFFER = MultiBufferSource.immediate(new BufferBuilder(262144));
    private static final RenderType CORE = type(false), GLOW = type(true);
    private static final int SIDES=48,CAP_RINGS=12;

    private EaBeam() { super("ea_beam_unused", DefaultVertexFormat.POSITION_COLOR_TEX, VertexFormat.Mode.QUADS, 256, false, false, ()->{}, ()->{}); }

    private static RenderType type(boolean emission) {
        return create(emission ? "ea_beam_glow" : "ea_beam_core", DefaultVertexFormat.POSITION_COLOR_TEX,
                VertexFormat.Mode.QUADS, 262144, false, !emission,
                CompositeState.builder().setShaderState(new ShaderStateShard(() -> emission ? glow : core))
                        .setTextureState(new TextureStateShard(FLOW, true, false))
                        .setTransparencyState(emission ? ADDITIVE_TRANSPARENCY : TRANSLUCENT_TRANSPARENCY)
                        .setCullState(CULL).setDepthTestState(LEQUAL_DEPTH_TEST)
                        .setWriteMaskState(emission?COLOR_WRITE:COLOR_DEPTH_WRITE)
                        .createCompositeState(false));
    }

    public static void registerShader(RegisterShadersEvent event) throws IOException {
        event.registerShader(new ShaderInstance(event.getResourceProvider(), new ResourceLocation("starcrown_ea", "beam"), DefaultVertexFormat.POSITION_COLOR_TEX), s -> core=s);
        event.registerShader(new ShaderInstance(event.getResourceProvider(), new ResourceLocation("starcrown_ea", "beam_glow"), DefaultVertexFormat.POSITION_COLOR_TEX), s -> glow=s);
    }

    public static void draw(PoseStack pose, Vec3 origin, Vec3 forward, Vec3 side, Vec3 up, EaFrame frame) {
        if (core == null || glow == null || frame.length() <= .01 || frame.fade() <= 0) return;
        float time = EaClient.visualTick()/20f;
        for (int pass=0; pass<2; pass++) {
            ShaderInstance shader = pass==0 ? core : glow;
            if (shader.getUniform("Time") != null) shader.getUniform("Time").set(time);
            RenderType type = pass==0 ? CORE : GLOW;
            VertexConsumer out = BUFFER.getBuffer(type);
            double length = frame.length();
            double growth=Math.min(1,length/8.0);
            double radius=EaServer.BEAM_RADIUS*growth*(pass==0?1:1.009);
            double cap=Math.min(EaServer.BEAM_RADIUS,length*.5);
            for(int ring=0;ring<CAP_RINGS;ring++){
                double start=ring*Math.PI*.5/CAP_RINGS,end=(ring+1)*Math.PI*.5/CAP_RINGS;
                band(out,pose,origin,forward,side,up,cap*(1-Math.cos(start)),cap*(1-Math.cos(end)),
                        radius*Math.sin(start),radius*Math.sin(end),-Math.cos(start),-Math.cos(end),radius,frame.fade());
            }
            band(out,pose,origin,forward,side,up,cap,length-cap,radius,radius,0,0,radius,frame.fade());
            for(int ring=CAP_RINGS;ring>0;ring--){
                double start=ring*Math.PI*.5/CAP_RINGS,end=(ring-1)*Math.PI*.5/CAP_RINGS;
                band(out,pose,origin,forward,side,up,length-cap*(1-Math.cos(start)),
                        length-cap*(1-Math.cos(end)),radius*Math.sin(start),radius*Math.sin(end),
                        Math.cos(start),Math.cos(end),radius,frame.fade());
            }
            BUFFER.endBatch(type);
        }
    }

    private static void band(VertexConsumer out,PoseStack pose,Vec3 origin,Vec3 axis,Vec3 side,Vec3 up,
                             double start,double end,double radius0,double radius1,
                             double axialNormal0,double axialNormal1,double radius,float alpha){
        if(end-start<1e-7)return;
        Vec3 center0=origin.add(axis.scale(start)),center1=origin.add(axis.scale(end));
        for(int facet=0;facet<SIDES;facet++){
            float u0=facet/(float)SIDES,u1=(facet+1)/(float)SIDES;
            Vec3 radial0=radial(side,up,u0),radial1=radial(side,up,u1);
            Vec3 along0=axis.scale(axialNormal0),along1=axis.scale(axialNormal1);
            put(out,pose,center0.add(radial0.scale(radius0)),along0.add(radial0.scale(radius0/radius)),start,u0,alpha);
            put(out,pose,center1.add(radial0.scale(radius1)),along1.add(radial0.scale(radius1/radius)),end,u0,alpha);
            put(out,pose,center1.add(radial1.scale(radius1)),along1.add(radial1.scale(radius1/radius)),end,u1,alpha);
            put(out,pose,center0.add(radial1.scale(radius0)),along0.add(radial1.scale(radius0/radius)),start,u1,alpha);
        }
    }
    private static Vec3 radial(Vec3 side,Vec3 up,float fraction){
        double angle=fraction*2*Math.PI;
        return side.scale(Math.cos(angle)).add(up.scale(Math.sin(angle)));
    }
    private static void put(VertexConsumer out,PoseStack pose,Vec3 p,Vec3 normal,double distance,float across,float alpha) {
        var n=pose.last().normal().transform(new org.joml.Vector3f((float)normal.x,(float)normal.y,(float)normal.z)).normalize();
        out.vertex(pose.last().pose(),(float)p.x,(float)p.y,(float)p.z)
                .color(n.x*.5f+.5f,n.y*.5f+.5f,n.z*.5f+.5f,alpha)
                .uv((float)distance,across).endVertex();
    }
}
