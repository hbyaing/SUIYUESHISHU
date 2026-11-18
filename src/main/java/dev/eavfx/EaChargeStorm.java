package dev.eavfx;

import com.mojang.blaze3d.vertex.*;
import com.mojang.logging.LogUtils;
import java.io.IOException;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RegisterShadersEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;

/** The weapon carries the ignition. The much wider wind field follows the interpolated caster. */
public final class EaChargeStorm extends RenderType {
    private static final double TAU=Math.PI*2;
    private static final float WIND=0, ORBIT=.45f, LIGHTNING=.75f, FLARE=1;
    private static ShaderInstance shader;
    private static final MultiBufferSource.BufferSource WORLD=MultiBufferSource.immediate(new BufferBuilder(131072));
    private static final RenderType TYPE=create("ea_charge_storm",DefaultVertexFormat.POSITION_COLOR_TEX,
            VertexFormat.Mode.QUADS,131072,false,true,CompositeState.builder()
                    .setShaderState(new ShaderStateShard(()->shader))
                    .setTransparencyState(TRANSLUCENT_TRANSPARENCY).setDepthTestState(LEQUAL_DEPTH_TEST)
                    .setCullState(NO_CULL).setWriteMaskState(COLOR_WRITE).createCompositeState(false));

    private EaChargeStorm(){
        super("unused",DefaultVertexFormat.POSITION_COLOR_TEX,VertexFormat.Mode.QUADS,256,false,false,()->{},()->{});
    }

    static void register(RegisterShadersEvent event){
        shader=null;
        try{
            event.registerShader(new ShaderInstance(event.getResourceProvider(),
                    new ResourceLocation("starcrown_ea","ea_charge_storm"),DefaultVertexFormat.POSITION_COLOR_TEX),
                    loaded->shader=loaded);
        }catch(IOException|RuntimeException error){LogUtils.getLogger().warn("Ea charge storm shader unavailable",error);}
    }

    static float sequenceAge(float rawAge,int chantTicks){
        if(rawAge<=EaServer.CHARGE_TICKS)return rawAge;
        return EaServer.CHARGE_TICKS+(rawAge-EaServer.CHARGE_TICKS)*EaAudioConfig.DEFAULT_CHANT_TICKS/chantTicks;
    }

    private static float rise(float from,float to,float age){
        float t=Mth.clamp((age-from)/(to-from),0,1);
        return t*t*(3-2*t);
    }

    public static void tip(PoseStack pose,MultiBufferSource buffers,float x,float y,float z,float tick,float age){
        if(shader==null||age<23)return;
        var out=buffers.getBuffer(TYPE);
        float orbit=rise(23,40,age)*(1-rise(64,102,age));
        if(orbit>.005f){
            Vec3 center=new Vec3(x,y,z);
            double size=.14+1.13*rise(27,72,age);
            for(int ring=0;ring<3;ring++){
                double azimuth=ring*2.16+tick*.024;
                double inclination=(ring-1)*.99+.30;
                Vec3 a=new Vec3(Math.cos(azimuth),0,Math.sin(azimuth));
                Vec3 b=new Vec3(-Math.sin(azimuth)*Math.cos(inclination),Math.sin(inclination),
                        Math.cos(azimuth)*Math.cos(inclination));
                double phase=tick*(ring%2==0?.087:-.068)+ring*1.93;
                int steps=36;
                for(int i=0;i<steps;i++){
                    float u=i/(float)steps,v=(i+1)/(float)steps;
                    Vec3 first=a.scale(Math.cos(phase+u*TAU*.94)).add(b.scale(.76*Math.sin(phase+u*TAU*.94)));
                    Vec3 second=a.scale(Math.cos(phase+v*TAU*.94)).add(b.scale(.76*Math.sin(phase+v*TAU*.94)));
                    float thickness=(float)(.012+.045*size);
                    vertex(out,pose,center.add(first.scale(size-thickness)),u,0,0,ring*.3f,ORBIT,orbit);
                    vertex(out,pose,center.add(second.scale(size-thickness)),v,0,0,ring*.3f,ORBIT,orbit);
                    vertex(out,pose,center.add(second.scale(size+thickness)),v,1,0,ring*.3f,ORBIT,orbit);
                    vertex(out,pose,center.add(first.scale(size+thickness)),u,1,0,ring*.3f,ORBIT,orbit);
                }
            }
        }
        // Expand a single quad in view space. Its centre and scale come from this exact held mesh.
        float ignition=rise(29,89,age);
        float size=(.06f+1.73f*ignition+.48f*rise(310,455,age))*(1+.035f*Mth.sin(tick*.21f));
        float displayScale=(float)Math.sqrt(pose.last().pose().m00()*pose.last().pose().m00()
                +pose.last().pose().m01()*pose.last().pose().m01()+pose.last().pose().m02()*pose.last().pose().m02());
        float width=size*displayScale,height=width*.52f;
        Vec3 point=new Vec3(x,y,z);
        float alpha=.50f+.50f*ignition;
        vertex(out,pose,point,-width,-height,0,0,FLARE,alpha);
        vertex(out,pose,point,width,-height,0,0,FLARE,alpha);
        vertex(out,pose,point,width,height,0,0,FLARE,alpha);
        vertex(out,pose,point,-width,height,0,0,FLARE,alpha);
    }

    static void world(RenderLevelStageEvent event,LivingEntity owner,float tick,float age,long seed){
        float strength=rise(55,155,age);
        if(shader==null||strength<=0)return;
        Vec3 eye=event.getCamera().getPosition();
        Vec3 position=owner.getPosition(event.getPartialTick());
        if(eye.distanceToSqr(position)>160*160)return;
        var pose=event.getPoseStack();
        pose.pushPose();
        try{
            Vec3 relative=position.subtract(eye);
            pose.translate(relative.x,relative.y,relative.z);
            Vec3 localEye=eye.subtract(position);
            var out=WORLD.getBuffer(TYPE);
            double yaw=(seed&1023)*TAU/1024;
            float swell=rise(65,260,age),late=rise(270,470,age);
            // Each sweep is a torn crescent. Its upper lip flares outward, leaving an open central cavity.
            for(int band=0;band<9;band++){
                double layer=band/8.;
                double angle=yaw+band*2.39996+tick*(.018+.012*(1-layer));
                double radius=(1.9+layer*4.3)*(.58+.42*swell);
                double height=.72+layer*4.9;
                double width=(.44+layer*.65)*(.65+.35*swell);
                float warmth=(float)(late*(1-layer)*.95+.07*Math.sin(band*3.4)+.13);
                sweep(out,pose,angle,radius,height,width,layer,band,warmth,strength);
            }
            float crossing=rise(91,223,age);
            if(crossing>0){
                for(int wing=0;wing<2;wing++){
                    double angle=yaw+wing*2.7+tick*.024;
                    sweep(out,pose,angle,3.7+wing*.88,3.0+wing*.72,.73+.15*wing,
                            .6,14+wing,.20f+late*.36f,strength*crossing);
                }
            }
            float eyeStrength=rise(126,276,age);
            if(eyeStrength>0){
                for(int inner=0;inner<3;inner++){
                    double angle=yaw+tick*.046+inner*2.15;
                    sweep(out,pose,angle,1.44+inner*.68,3.50+inner*.19,.24+inner*.08,
                            .32,10+inner,.73f+.27f*late,strength*eyeStrength);
                }
            }
            worldLightning(out,pose,localEye,tick,age,seed,strength);
            WORLD.endBatch(TYPE);
        }finally{pose.popPose();}
    }

    private static void sweep(VertexConsumer out,PoseStack pose,double start,double radius,double height,
                              double width,double layer,int index,float warmth,float alpha){
        final int steps=32;
        boolean crossing=index>=14;
        boolean eye=index>=10&&!crossing;
        double span=crossing?3.95:eye?2.53:2.94+1.33*Math.sin(index*4.23);
        Vec3 lastBottom=null,lastTop=null;
        for(int i=0;i<=steps;i++){
            float u=i/(float)steps;
            double angle=start+u*span;
            double sin=Math.sin(angle),cos=Math.cos(angle);
            double r=radius*(1+.13*Math.sin(angle*2+index*.79)+.09*Math.cos(u*3.3+index));
            double lift=crossing?2.25:eye?.22:.37+layer*.82;
            double h=height+lift*Math.sin(angle+.75+(crossing?index*.89:0))+.23*Math.sin(u*5+index);
            double w=width*(.09+.91*Math.pow(Math.sin(Math.PI*u),.66));
            Vec3 bottom=new Vec3(cos*(r-w*.56),h-w*.65,sin*(r-w*.56));
            Vec3 top=new Vec3(cos*(r+w*.94),h+w*.89,sin*(r+w*.94));
            if(i>0){
                float v=(i-1)/(float)steps;
                float shift=(index*.233f)%1;
                float material=eye?.18f:WIND;
                vertex(out,pose,lastBottom,v,0,warmth,shift,material,alpha);
                vertex(out,pose,bottom,u,0,warmth,shift,material,alpha);
                vertex(out,pose,top,u,1,warmth,shift,material,alpha);
                vertex(out,pose,lastTop,v,1,warmth,shift,material,alpha);
            }
            lastTop=top;lastBottom=bottom;
        }
    }

    private static void worldLightning(VertexConsumer out,PoseStack pose,Vec3 eye,float tick,float age,long seed,float strength){
        float gate=rise(96,143,age)*strength;
        if(gate<=0)return;
        // Stable vertices during each flash, with a short, weaker return stroke along the same path.
        for(int bolt=0;bolt<3;bolt++){
            float clock=tick+bolt*19;
            int burst=(int)(clock/47);
            float local=clock-burst*47;
            float pulse=Math.max(1-local/4.8f, .61f*(1-Math.abs(local-7.5f)/1.8f));
            if(pulse<=0)continue;
            long key=seed+burst*127L+bolt*911L;
            double angle=random(key)*TAU;
            Vec3 radial=new Vec3(Math.cos(angle),0,Math.sin(angle));
            Vec3 tangent=new Vec3(-radial.z,0,radial.x);
            Vec3 top=radial.scale(3.2+random(key+1)*1.7).add(0,5.3+random(key+2)*1.45,0);
            Vec3 bottom=radial.scale(2.8+random(key+3)*1.9).add(tangent.scale(random(key+4)*2-1));
            Vec3 previous=top;
            for(int i=1;i<=9;i++){
                double u=i/9.;
                double edge=Math.sin(Math.PI*u);
                Vec3 point=top.lerp(bottom,u).add(tangent.scale((random(key+i*17)-.5)*1.25*edge))
                        .add(radial.scale((random(key+i*29)-.5)*.89*edge));
                boltSegment(out,pose,eye,previous,point,.115f*(1-(float)u*.45f),gate*pulse);
                if(i==4||i==6){
                    Vec3 fork=point.add(tangent.scale(i==4?.52:-.4)).add(0,-.34,.12);
                    boltSegment(out,pose,eye,point,fork,.058f,gate*pulse*.86f);
                    boltSegment(out,pose,eye,fork,fork.add(radial.scale(.21)).add(0,-.58,0),
                            .036f,gate*pulse*.72f);
                }
                previous=point;
            }
        }
    }

    private static double random(long key){
        key=(key^(key>>>30))*0xbf58476d1ce4e5b9L;
        key=(key^(key>>>27))*0x94d049bb133111ebL;
        return ((key^(key>>>31))>>>11)*0x1.0p-53;
    }

    private static void boltSegment(VertexConsumer out,PoseStack pose,Vec3 eye,Vec3 a,Vec3 b,float width,float alpha){
        Vec3 side=b.subtract(a).cross(eye.subtract(a)).normalize().scale(width);
        vertex(out,pose,a.subtract(side),0,0,0,0,LIGHTNING,alpha);
        vertex(out,pose,b.subtract(side),1,0,0,0,LIGHTNING,alpha);
        vertex(out,pose,b.add(side),1,1,0,0,LIGHTNING,alpha);
        vertex(out,pose,a.add(side),0,1,0,0,LIGHTNING,alpha);
    }

    private static void vertex(VertexConsumer out,PoseStack pose,Vec3 p,float u,float v,float warm,float seed,
                               float material,float alpha){
        out.vertex(pose.last().pose(),(float)p.x,(float)p.y,(float)p.z)
                .color(Mth.clamp(warm,0,1),seed,material,alpha).uv(u,v).endVertex();
    }
}
