package dev.eavfx;

import java.io.IOException;
import java.util.List;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RegisterShadersEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;

public final class EaWorldEffects {
    private EaWorldEffects(){}
    public static void registerShader(RegisterShadersEvent event)throws IOException{EaBeam.registerShader(event);}

    public static void render(RenderLevelStageEvent event,List<EaFrame> frames){
        Vec3 camera=event.getCamera().getPosition();
        var pose=event.getPoseStack();
        for(int index=Math.min(12,frames.size())-1;index>=0;index--){
            EaFrame frame=frames.get(index);
            if(frame.length()<=.03||frame.fade()<=0)continue;
            Vec3 forward=frame.direction();
            Vec3 helper=Math.abs(forward.y)>.92?new Vec3(1,0,0):new Vec3(0,1,0);
            Vec3 side=forward.cross(helper).normalize(),up=side.cross(forward).normalize();
            Vec3 origin=frame.origin().subtract(camera);
            EaBeam.draw(pose,origin,forward,side,up,frame);
            float age=frame.blastAge();
            double phase=(frame.seed()&1023)*.013-age*.21;
            for(int filament=0;filament<5;filament++){
                Vec3 previous=null;
                for(int segment=0;segment<=48;segment++){
                    double fraction=segment/48.0;
                    double distance=fraction*frame.length();
                    double angle=phase+filament*1.256637+distance*(.32+filament*.042);
                    double envelope=Math.sin(Math.PI*Math.pow(fraction,.62));
                    double radius=(1.1+2.0*envelope)*Math.pow(1-fraction,.23)
                            *(.85+.15*Math.sin(distance*2.9-age*.92+filament));
                    Vec3 point=origin.add(forward.scale(distance)).add(side.scale(Math.cos(angle)*radius))
                            .add(up.scale(Math.sin(angle)*radius));
                    if(previous!=null){
                        double gate=Math.sin(distance*2.4-age*.94+filament*1.7);
                        float alpha=(float)(frame.fade()*envelope*Math.max(0,gate)*.62);
                        if(alpha>.015f)EaRibbon.draw(pose,previous,point,.023f,
                                1f,.20f,.045f,alpha);
                    }
                    previous=point;
                }
            }
            if(age<12){
                double spread=.14+age*.061;
                Vec3 center=origin.add(forward.scale(Math.min(frame.length()*.12,.14+age*.045)));
                for(int ray=0;ray<9;ray++){
                    double angle=phase+ray*2.399963;
                    Vec3 radial=side.scale(Math.cos(angle)).add(up.scale(Math.sin(angle)));
                    Vec3 begin=center.add(radial.scale(spread*.32));
                    Vec3 end=center.add(radial.scale(spread*(.72+ray*.07))).add(forward.scale(.12+ray*.023));
                    EaRibbon.draw(pose,begin,end,.038f,1,.38f,.12f,frame.fade()*(1-age/12)*.71f);
                }
            }
        }
        EaRibbon.flush();
    }
}
