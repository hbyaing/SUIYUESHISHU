package dev.starcrown;

import java.util.*;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f; import org.joml.Matrix3f; import org.joml.Vector3f;

/** One upright item transform supplies both the rendered grip and the analysis emitter. */
public final class FateAnalysisPlacement {
    public static final float SCALE=.65f;
    public static final float TIP=(2.1f+4.536321f)*1.85f/8.3f*.4f;
    public static final float COLUMN_BOTTOM=1.836f;
    private static final Map<UUID,Vec3> tips=new HashMap<>();
    private static final Map<UUID,Vec3> grips=new HashMap<>();
    private static Matrix4f view=new Matrix4f();
    private static Vec3 camera=Vec3.ZERO;
    private static boolean inWorld;
    static void begin(Matrix4f matrix,Vec3 eye){view.set(matrix);camera=eye;tips.clear();grips.clear();inWorld=true;}
    static Matrix4f view(){return new Matrix4f(view);}
    static void end(){inWorld=false;}
    static void clear(){tips.clear();grips.clear();inWorld=false;}
    public static Vec3 tip(UUID owner){return tips.get(owner);}
    static Vec3 grip(UUID owner){return grips.get(owner);}
    public static float rotation(float yaw){return (float)Math.toRadians(180-yaw);}
    static Matrix4f model(Vec3 grip,float yaw){return new Matrix4f(view).translate((float)(grip.x-camera.x),(float)(grip.y-camera.y),(float)(grip.z-camera.z)).rotateY((float)Math.toRadians(90-yaw));}
    static void hand(PoseStack pose,UUID owner,float yaw,float age,boolean left){
        if(!inWorld)return;
        Matrix4f full=new Matrix4f(RenderSystem.getModelViewMatrix()).mul(pose.last().pose());
        Vector3f p=new Matrix4f(view).invert().mul(full).transformPosition(new Vector3f());
        Vec3 grip=camera.add(p.x,p.y,p.z);
        grips.put(owner,grip);
        if(age>=114&&age<143)return;
        Matrix4f item;
        if(age<114){
            item=model(grip,yaw);
            tips.put(owner,grip.add(0,TIP,0));
        }else{
            // The revealed sword is level with the outstretched arm and follows body yaw.
            item=new Matrix4f(view).translate(p).rotateY((float)Math.toRadians(-yaw))
                    .rotateZ((float)Math.toRadians(left?-84:84)).rotateY((float)Math.toRadians(90));
        }
        Matrix4f actual=new Matrix4f(RenderSystem.getModelViewMatrix()).invert().mul(item);
        pose.last().pose().set(actual);pose.last().normal().set(new Matrix3f(actual).invert().transpose());
    }
    static Vec3 localGrip(Player player,float partial,float age){
        boolean left=(player.getMainArm()==net.minecraft.world.entity.HumanoidArm.LEFT)^FateClient.CASTS.get(player.getUUID()).offhand();
        Vec3 forward=Vec3.directionFromRotation(0,player.getViewYRot(partial));
        Vec3 right=new Vec3(-forward.z,0,forward.x);
        float lift=(float)net.minecraft.util.Mth.smoothstep(net.minecraft.util.Mth.clamp(age/20,0,1));
        return player.getEyePosition(partial).add(forward.scale(.65)).add(right.scale(left?-.32:.32)).add(0,-.32+.12*lift,0);
    }
    static void local(Player player,float partial,float age){tips.put(player.getUUID(),localGrip(player,partial,age).add(0,TIP,0));}
    private FateAnalysisPlacement(){}
}

