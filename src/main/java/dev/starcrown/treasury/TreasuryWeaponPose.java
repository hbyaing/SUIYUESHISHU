package dev.starcrown.treasury;

import dev.starcrown.StarcrownDoll;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

/** Tip and longitudinal axis in the posed item renderer's NONE coordinates. */
record TreasuryWeaponPose(Vec3 tip, Quaternionf toForward, double length) {
    private static final Vec3 UP = new Vec3(0, 1, 0);
    private static TreasuryWeaponPose pose(Vec3 tip, Vec3 axis, double length) {
        return new TreasuryWeaponPose(tip, new Quaternionf().rotationTo((float)axis.x, (float)axis.y,
                (float)axis.z, 0, 0, 1), length);
    }

    static TreasuryWeaponPose inspect(ItemStack stack, BakedModel model) {
        if (stack.is(StarcrownDoll.EA.get())) return pose(new Vec3(.025, 1.849, 0), UP, 1.95);
        if (stack.is(Items.TRIDENT)) return pose(new Vec3(-.5, -.25, -.5), UP, 1.9375);
        // A custom renderer need not expose its geometry. Keep its normal upright display convention.
        if (model.isCustomRenderer()) return pose(new Vec3(0, 1, 0), UP, 1.65);
        List<Vec3> points = new ArrayList<>();
        if (!model.isGui3d()) {
            SpriteContents sprite = model.getParticleIcon().contents();
            int step = Math.max(1, Math.max(sprite.width(), sprite.height()) / 48);
            for (int y = 0; y < sprite.height(); y += step)
                for (int x = 0; x < sprite.width(); x += step)
                    if (!sprite.isTransparent(0, x, y))
                        points.add(new Vec3((x + .5) / sprite.width() - .5, .5 - (y + .5) / sprite.height(), 0));
        }
        if (points.size() < 3) {
            points.clear();
            collect(model.getQuads(null, null, RandomSource.create(42)), points);
            for (Direction face : Direction.values()) {
                collect(model.getQuads(null, face, RandomSource.create(42)), points);
            }
        }
        if (points.size() < 3) return pose(new Vec3(.46, .46, 0), new Vec3(1, 1, 0).normalize(), 1.3);

        Vec3 mean = Vec3.ZERO;
        for (Vec3 point : points) mean = mean.add(point);
        mean = mean.scale(1.0 / points.size());
        double xx=0, yy=0, zz=0, xy=0, xz=0, yz=0;
        for (Vec3 point : points) {
            double x=point.x-mean.x, y=point.y-mean.y, z=point.z-mean.z;
            xx+=x*x; yy+=y*y; zz+=z*z; xy+=x*y; xz+=x*z; yz+=y*z;
        }
        Vec3 axis = xx>yy && xx>zz ? new Vec3(1,0,0) : zz>yy ? new Vec3(0,0,1) : UP;
        for (int i=0; i<12; i++) {
            Vec3 step = new Vec3(xx*axis.x+xy*axis.y+xz*axis.z, xy*axis.x+yy*axis.y+yz*axis.z,
                    xz*axis.x+yz*axis.y+zz*axis.z);
            if (step.lengthSqr() < 1e-12) break;
            axis = step.normalize();
        }
        double positive = Math.abs(axis.y)>.12 ? axis.y : Math.abs(axis.x)>.12 ? axis.x : axis.z;
        if (positive < 0) axis=axis.scale(-1);
        double low=Double.POSITIVE_INFINITY, high=Double.NEGATIVE_INFINITY;
        for (Vec3 point : points) {
            double along=point.dot(axis);
            low=Math.min(low,along); high=Math.max(high,along);
        }
        Vec3 end=Vec3.ZERO;
        int ends=0;
        for (Vec3 point : points) if (high-point.dot(axis) <= Math.max(.005,(high-low)*.025)) {
            end=end.add(point);
            ends++;
        }
        return pose(end.scale(1.0/ends),axis,Math.max(.12,high-low));
    }

    private static void collect(List<BakedQuad> quads, List<Vec3> points) {
        int step = Math.max(1, quads.size()/256);
        for (int q=0; q<quads.size(); q+=step) {
            int[] vertices=quads.get(q).getVertices();
            int stride=vertices.length/4;
            for (int j=0; j<4; j++) {
                int at=j*stride;
                points.add(new Vec3(Float.intBitsToFloat(vertices[at])-.5,
                        Float.intBitsToFloat(vertices[at+1])-.5,Float.intBitsToFloat(vertices[at+2])-.5));
            }
        }
    }
}
