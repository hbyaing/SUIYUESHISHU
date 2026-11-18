package dev.starcrown;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

@Mod.EventBusSubscriber(modid = StarcrownDoll.ID, value = Dist.CLIENT)
public final class ExcaliburVisuals {
    private static final int BAND = 0, RAY = 1, CRESCENT = 2, STAR = 3, GLOW = 4,
            TWIRL = 5, RING = 6, BURST = 7, SCRATCH = 8, SIGIL = 9, GLINT = 10, SMOKE_RING = 11;
    private static final int GOLD = 0xffb431, IVORY = 0xfff6cb, AMBER = 0xf8851d,
            AIR = 0xc8cdd1, PEARL = 0xf8fafb, MINT = 0xc6ffe0;
    private static final double TAU = Math.PI * 2;
    private static final ResourceLocation ATLAS = new ResourceLocation(StarcrownDoll.ID, "textures/effect/excalibur.png");
    private static final RenderType MATERIAL = RenderType.entityTranslucentEmissive(ATLAS);
    private static final RenderType WORLD_LIGHT = ExcaliburWorldLight.material(ATLAS);
    private static final MultiBufferSource.BufferSource BUFFERS =
            MultiBufferSource.immediate(new BufferBuilder(32768));
    private static final Deque<Active> ACTIVE = new ArrayDeque<>();
    private static ClientLevel world;

    private record Active(ExcaliburEffects.Effect cue, long born) {}

    static void receive(ExcaliburEffects.Effect cue) {
        var level = Minecraft.getInstance().level;
        bindWorld(level);
        if (level == null || !level.dimension().location().equals(cue.dimension())) return;
        if (ACTIVE.size() >= 64) ACTIVE.removeFirst();
        ACTIVE.addLast(new Active(cue, level.getGameTime()));
    }

    @SubscribeEvent public static void unload(LevelEvent.Unload event) {
        if (event.getLevel() == world) bindWorld(null);
    }

    private static void bindWorld(ClientLevel level) {
        if (world != level) {
            ACTIVE.clear();
            ExcaliburGroundSparks.clear();
            world = level;
        }
    }

    @SubscribeEvent public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        var level = Minecraft.getInstance().level;
        bindWorld(level);
        if (level == null) return;
        long tick = level.getGameTime();
        float partial = event.getPartialTick();
        ACTIVE.removeIf(a -> tick - a.born >= a.cue.kind().ticks);
        Frame f = ExcaliburGroundSparks.draw(event, level, partial);
        for (var active : ACTIVE) {
            if (active.cue.kind() == ExcaliburEffects.Kind.BEAM) continue;
            if (f == null) f = new Frame(event, false);
            draw(f, level, active, tick - active.born + partial, partial);
        }
        if (f != null) BUFFERS.endBatch(MATERIAL);
        Frame light = drawSkyCharges(event, level, partial);
        for (var active : ACTIVE) {
            if (active.cue.kind() != ExcaliburEffects.Kind.BEAM) continue;
            if (light == null) light = new Frame(event, true);
            draw(light, level, active, tick - active.born + partial, partial);
        }
        if (light != null) BUFFERS.endBatch(WORLD_LIGHT);
    }

    private static void draw(Frame f, ClientLevel level, Active active, double age, float partial) {
        var e = active.cue;
        double t = Mth.clamp(age / e.kind().ticks, 0, 1);
        switch (e.kind()) {
            case BEAM -> {
                // Keep the guide's preburst, main burst, and later flare on the same clock as the chanting blade.
                double phase = ExcaliburChant.skill03Phase(ExcaliburChant.DURATION + age);
                double afterHit = Mth.clamp((phase - ExcaliburChant.HIT_PHASE) / (1 - ExcaliburChant.HIT_PHASE), 0, 1);
                if (age < 22) beam(f, e.start(), e.end(), age / 22, .08 + .92 * smooth(afterHit / IMPACT_PREBURST));
                if (afterHit >= IMPACT_PREBURST) impact(f, e, afterHit);
            }
            case WIND -> wind(f, e.start(), e.end(), t);
            case VEIL, REVEAL -> {
                boolean veil = e.kind() == ExcaliburEffects.Kind.VEIL;
                double opacity = Math.sin(Math.PI * t);
                f.sprite(TWIRL, e.start(), .4 + t * 1.2, t * (veil ? -3 : 3), veil ? AIR : GOLD, .65 * opacity);
                f.sprite(veil ? SMOKE_RING : GLINT, e.start(), .25 + 1.35 * t, -t, veil ? PEARL : IVORY, .74 * opacity);
                if (!veil) f.sprite(RING, e.start(), .44 + 1.52 * t, 0, GOLD, .34 * opacity);
            }
            case HIT, WIND_HIT -> {
                boolean wind = e.kind() == ExcaliburEffects.Kind.WIND_HIT;
                double angle = e.owner() * 1.13 + (active.born % 8) * .47;
                f.sprite(wind ? CRESCENT : SCRATCH, e.end(), .46 + t * .95, angle + .6 * t,
                        wind ? AIR : GOLD, 1 - t);
                if (wind) {
                    f.sprite(SCRATCH, e.end(), .40 + t * .52, angle - .18, PEARL, .75 * (1 - t));
                } else {
                    f.sprite(STAR, e.end(), .43 * (1 - t), angle, IVORY, .94 * (1 - t));
                }
            }
            case SANCTUARY, HEAL -> {
                var owner = level.getEntity(e.owner());
                if (owner == null || !owner.isAlive()) return;
                var center = owner.getPosition(partial).add(0, owner.getBbHeight() * .52, 0);
                avalon(f, center, t, e.kind() == ExcaliburEffects.Kind.SANCTUARY);
            }
        }
    }

    // Third-person sockets are camera-relative render coordinates from this world's entity pass.
    private static final Map<Integer, Vec3> SKY_TIPS = new HashMap<>();
    private static final int SKY_GOLD = 0xfae034, SKY_PALE = 0xfafda1, SKY_CORE = 0xfffafc;

    @SubscribeEvent public static void clearSkyTips(RenderLevelStageEvent event) {
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_CUTOUT_BLOCKS) SKY_TIPS.clear();
    }

    private static double skyPower(double chant) {
        return smooth((chant - ExcaliburChant.LAST_LINE_TICK) / 1.8)
                * (1 - smooth((chant - ExcaliburChant.NAME_START_TICK) / 5));
    }

    private static Frame drawSkyCharges(RenderLevelStageEvent event, ClientLevel level, float partial) {
        if (!ExcaliburChant.Client.hasActiveCasts()) return null;
        Frame f = null;
        Matrix4f inverseView = null;
        for (var caster : level.players()) {
            int elapsed = ExcaliburChant.elapsed(caster);
            if (elapsed < ExcaliburChant.LAST_LINE_TICK || ExcaliburChargePose.chantHand(caster) == null) continue;
            double chant = elapsed + partial;
            double power = skyPower(chant);
            if (power < .003) continue;
            if (f == null) f = new Frame(event, true);
            double age = chant - ExcaliburChant.LAST_LINE_TICK;
            Vec3 feet = caster.getPosition(partial);
            if (caster.onGround()) skyFootprint(f, feet.add(0, .04, 0), age, power);
            if (caster == event.getCamera().getEntity() && !event.getCamera().isDetached()) continue;
            Vec3 tip = SKY_TIPS.get(caster.getId());
            if (tip == null) {
                // The player mesh can be culled while the upper twenty blocks of the column are on screen.
                float yaw = Mth.rotLerp(partial, caster.yBodyRotO, caster.yBodyRot);
                tip = feet.add(Vec3.directionFromRotation(0, yaw).scale(.40)).add(0, caster.getBbHeight() + .83, 0);
            } else {
                if (inverseView == null) inverseView = new Matrix4f(f.pose).invert();
                Vector3f p = inverseView.transformPosition((float)tip.x, (float)tip.y, (float)tip.z, new Vector3f());
                tip = f.camera.add(p.x, p.y, p.z);
            }
            skyColumn(f, tip, age, power);
        }
        return f;
    }

    private static void skyFootprint(Frame f, Vec3 foot, double age, double power) {
        Vec3 east = new Vec3(1, 0, 0), south = new Vec3(0, 0, 1);
        double shock = smooth(age / 11);
        f.plane(GLOW, foot, east, south, 2.4, SKY_GOLD, .24 * power);
        f.plane(RING, foot.add(0, .012, 0), east, south, .55 + 3.4 * shock,
                SKY_PALE, (1 - shock) * power);
        f.plane(RING, foot.add(0, .018, 0), east, south, 1.45 + .08 * Math.sin(age * .16),
                SKY_GOLD, .42 * power);
    }

    private static final double[] SKY_STATIONS = {0, .036, .105, .22, .56, .84, 1};
    private static final double[] SKY_RADII = {.040, .49, .76, .57, .99, .78, .044};
    private static final double[] HAND_STATIONS = {0, .048, .093, .14, .245, .78, 1};
    private static final double[] HAND_RADII = {.04, .40, .76, .53, 1.03, 1.15, .046};
    private static final double[] SPILL_RADII = {.05, .31, .22, .018};
    private static final double[] SPILL_OFFSET = {.08, .85, 1.58, 1.15};
    private static final double[] WORLD_SPILL = {.015, .12, .38, .74};
    private static final double[] HAND_SPILL = {.012, .070, .145, .29};

    private static void skyColumn(Frame f, Vec3 tip, double age, double power) {
        double surge = 1 - smooth((age - 2) / 11);
        double radius = (1.50 + .56 * surge + .07 * Math.sin(age * .20)) * power;
        solidSkyPlume(f, tip, UP, new Vec3(1, 0, 0), 26 * smooth(age / 2), radius, age, power, false);
    }

    private static void handSkyColumn(Frame f, Vec3 base, Vec3 tip, Vec3 side, double age, double power) {
        Vec3 blade = tip.subtract(base);
        double length = blade.length();
        double surge = 1 - smooth((age - 2) / 11);
        double radius = length * (.235 + .072 * surge) * power;
        // The swelling starts midway up the blade, below the crop even when the raised tip leaves the frame.
        Vec3 feed = base.lerp(tip, .46);
        f.ribbon(base.lerp(tip, .17), feed, side.scale(.009 * length), side.scale(.054 * length),
                SKY_PALE, .84 * power);
        f.sprite(GLOW, feed, .42 * length, 0, SKY_GOLD, .62 * power);
        solidSkyPlume(f, feed, blade.scale(1 / length), side,
                length * 3.9 * smooth(age / 2), radius, age, power, true);
    }

    private record PlumeVertex(Vec3 position, int color, double opacity, float u, float v) {}
    private record PlumeFace(PlumeVertex a, PlumeVertex b, PlumeVertex c, PlumeVertex d, double depth) {}

    private static void solidSkyPlume(Frame f, Vec3 base, Vec3 axis, Vec3 u, double height, double radius,
                                      double age, double power, boolean hand) {
        var faces = new java.util.ArrayList<PlumeFace>(104);
        Vec3 v = axis.cross(u).normalize();
        double[] stations = hand ? HAND_STATIONS : SKY_STATIONS;
        Vec3[] centers = new Vec3[stations.length];
        for (int i = 0; i < centers.length; i++) centers[i] = base.add(axis.scale(height * stations[i]));
        double[] radii = hand ? HAND_RADII : SKY_RADII;
        Vec3 fringe = f.side(centers[0], axis).scale(1.85 * radius);
        for (int i = 1; i < centers.length; i++) {
            f.ribbon(centers[i - 1], centers[i], fringe.scale(radii[i - 1]), fringe.scale(radii[i]),
                    SKY_GOLD, .48 * power);
        }
        plumeHull(faces, f.camera, centers, radii, radius, axis, u, v, 8, age, false);

        // Each stream leaves the swelling, rolls around it, and ends in a closed point.
        double[] spill = hand ? HAND_SPILL : WORLD_SPILL;
        for (int j = 0; j < 2; j++) {
            Vec3[] path = new Vec3[spill.length];
            for (int i = 0; i < path.length; i++) {
                double angle = j * Math.PI + .22 + .23 * Math.sin(age * .085) + .26 * i;
                double reach = SPILL_OFFSET[i] * radius;
                path[i] = base.add(axis.scale(height * spill[i] * (1 - .14 * j)))
                        .add(u.scale(reach * Math.cos(angle))).add(v.scale(reach * Math.sin(angle)));
            }
            plumeHull(faces, f.camera, path, SPILL_RADII, radius, axis, u, v, 6, age + 7 * j, true);
        }
        // The world light pass only writes color. Submit visible walls far-to-near across all three bodies.
        faces.sort(java.util.Comparator.comparingDouble(PlumeFace::depth).reversed());
        int alpha = (int)(255 * smooth(power / .55));
        for (PlumeFace face : faces) f.solidFace(face, alpha);
    }

    private static void plumeHull(java.util.List<PlumeFace> faces, Vec3 eye, Vec3[] centers, double[] radii,
                                  double radius, Vec3 axis, Vec3 u, Vec3 v, int sides, double age, boolean spill) {
        PlumeVertex[][] rings = new PlumeVertex[centers.length][sides + 1];
        double height = Math.max(.0001, axis.dot(centers[centers.length - 1].subtract(centers[0])));
        double scroll = 1 - fraction(age / 22);
        for (int k = 0; k < centers.length; k++) {
            double twist = .07 * k + .09 * Math.sin(age * .09 - k * .74);
            Vec3 toEye = eye.subtract(centers[k]);
            Vec3 eyeAcross = toEye.subtract(axis.scale(toEye.dot(axis))).normalize();
            double y = axis.dot(centers[k].subtract(centers[0])) / height;
            // Four identical tiles across the fourth atlas row allow a whole repeat to scroll without a UV jump.
            float texU = (float)((.5 + 128 * (.16 + 2.55 * y + scroll)) / 512);
            for (int i = 0; i < sides; i++) {
                double angle = TAU * i / sides + twist;
                double swell = 1 + .085 * Math.cos(3 * angle + .6 * k - age * .16);
                Vec3 radial = u.scale(Math.cos(angle)).add(v.scale(Math.sin(angle)));
                Vec3 pos = centers[k].add(radial.scale(radius * radii[k] * swell));
                double facing = Math.max(0, radial.dot(eyeAcross));
                double hot = smooth((facing + .11) / 1.02);
                int green = (int)(217 + 38 * hot);
                int blue = (int)((spill ? 96 : 107) + (spill ? 144 : 147) * hot);
                double opacity = .27 + .73 * smooth((facing + .035) / .76);
                float texV = (float)((384.5 + 127.0 * i / sides) / 512);
                rings[k][i] = new PlumeVertex(pos, 0xff0000 | green << 8 | blue, opacity, texU, texV);
            }
            PlumeVertex seam = rings[k][0];
            rings[k][sides] = new PlumeVertex(seam.position, seam.color, seam.opacity, texU, 511.5f / 512);
        }
        for (int k = 0; k < centers.length - 1; k++) {
            for (int i = 0; i < sides; i++) {
                addPlumeFace(faces, eye, rings[k][i], rings[k][i + 1], rings[k + 1][i + 1], rings[k + 1][i]);
            }
        }
        int top = centers.length - 1;
        PlumeVertex foot = new PlumeVertex(centers[0], SKY_PALE, .8, rings[0][0].u, rings[0][0].v);
        PlumeVertex crown = new PlumeVertex(centers[top], SKY_CORE, 1, rings[top][0].u, rings[top][0].v);
        for (int i = 0; i < sides; i += 2) {
            addPlumeFace(faces, eye, foot, rings[0][i + 2], rings[0][i + 1], rings[0][i]);
            addPlumeFace(faces, eye, crown, rings[top][i], rings[top][i + 1], rings[top][i + 2]);
        }
    }

    private static void addPlumeFace(java.util.List<PlumeFace> faces, Vec3 eye,
                                     PlumeVertex a, PlumeVertex b, PlumeVertex c, PlumeVertex d) {
        Vec3 normal = c.position.subtract(a.position).cross(d.position.subtract(b.position));
        if (normal.dot(eye.subtract(a.position)) <= 0) return;
        Vec3 mid = a.position.lerp(c.position, .5).lerp(b.position.lerp(d.position, .5), .5);
        faces.add(new PlumeFace(a, b, c, d, eye.distanceToSqr(mid)));
    }

    static void chargeAt(MultiBufferSource buffers, double age, Vec3 root, Vec3 tip, boolean firstPerson, int owner) {
        Vec3 blade = tip.subtract(root);
        double length = blade.length();
        if (length < .001) return;
        double chant = age - ExcaliburChant.PREPARE_TICKS;
        double phase = ExcaliburChant.skill03Phase(chant);
        // Start01 lights the face, Weapon_Cam catches the guard, then Start02 opens the fan.
        double ignition = smooth(phase / .072);
        double ready = smooth(age / 12) * (1 - ignition);
        double retreat = smooth((phase - .320093) / (.341122 - .320093));
        double firstFace = ignition * (1 - retreat);
        double guardFlash = smooth((phase - .088785) / .016) * (1 - smooth((phase - .105) / .048));
        double fan = smooth((phase - .144860) / .043) * (1 - retreat);
        // Start04 restores the ambient sparks and blade. Start03_Cam and Start06 mark the final lift.
        double atmosphere = smooth((phase - .448617) / .060);
        double secondFace = smooth((phase - .449083) / .047);
        double lift = smooth((phase - .507009) / (.533195 - .507009));
        double cut = smooth((phase - .533195) / (ExcaliburChant.HIT_PHASE - .533195));
        double pulse = .5 + .5 * Math.sin(age * TAU / 37);
        double face = ready * (.30 + .09 * pulse) + .91 * firstFace
                + .055 * retreat * (1 - secondFace) + .89 * secondFace + .11 * cut;
        if (face < .005) return;

        Frame f = new Frame(buffers);
        Vec3 axis = blade.scale(1 / length);
        Vec3 side = f.side(root.lerp(tip, .5), axis);
        Vec3 across = axis.cross(side);
        // Shift toward the eye along each socket's own view ray to clear the OBJ relief at the same screen position.
        Vec3 guard = root.subtract(root.normalize().scale(.053 * length));
        Vec3 shoulder = root.lerp(tip, .90);
        shoulder = shoulder.subtract(shoulder.normalize().scale(.030 * length));
        Vec3 point = tip.subtract(tip.normalize().scale(.002 * length));
        double sky = skyPower(chant);
        if (sky > .003) {
            if (firstPerson) handSkyColumn(f, guard, point, side, chant - ExcaliburChant.LAST_LINE_TICK, sky);
            else SKY_TIPS.put(owner, tip);
        }
        Vec3 glint = guard.lerp(point, .16);
        double petals = fan + .58 * secondFace * (1 - cut);
        if (petals > .01) chargeFan(f, guard, point, side, length, petals, age);
        f.sprite(GLOW, glint, length * (.23 + .46 * firstFace + .57 * atmosphere),
                0, GOLD, .23 * ready + .48 * firstFace + .56 * atmosphere);

        double width = length * (.058 + .056 * firstFace + .058 * secondFace + .065 * cut);
        for (int layer = 0; layer < 2; layer++) {
            double w = layer == 0 ? width : width * (.36 + .16 * cut);
            int color = layer == 0 ? GOLD : 0xfffbea;
            double opacity = layer == 0 ? face : face * (.62 + .33 * firstFace + .38 * secondFace);
            f.ribbon(guard, shoulder, side.scale(w), side.scale(w * .69), color, opacity);
            f.ribbon(shoulder, point, side.scale(w * .69), Vec3.ZERO, color, opacity);
        }

        // In Start01 a narrow glint runs from the guard to the tip as the sword wakes.
        if (ignition > 0 && ignition < 1) {
            f.sprite(STAR, guard.lerp(point, ignition), length * .27, .50, IVORY, Math.sin(Math.PI * ignition));
        }
        double cross = guardFlash + .56 * lift * (1 - cut);
        if (cross > .01) {
            for (int i = -1; i <= 1; i += 2) {
                Vec3 ray = axis.add(side.scale(i * .96)).normalize().scale(length * (.10 + .40 * cross));
                Vec3 half = f.side(glint, ray.normalize()).scale(length * .024 * cross);
                f.ribbon(glint.subtract(ray), glint, Vec3.ZERO, half, IVORY, cross);
                f.ribbon(glint, glint.add(ray), half, Vec3.ZERO, IVORY, cross);
            }
        }
        f.sprite(STAR, glint, length * (.13 + .57 * cross + .25 * cut), .79, 0xfffeed,
                .37 * ready + .54 * firstFace + cross + .74 * secondFace);

        double sparks = .72 * ready + .82 * firstFace + .18 * retreat + .76 * atmosphere;
        for (int i = 0; i < 7; i++) {
            double flight = fraction(age / 38 + i * .618034);
            double angle = i * 2.39996 + flight * .65;
            double radius = length * (.19 + .13 * (i % 3) + .16 * atmosphere) * (1 - .65 * flight);
            Vec3 mote = guard.lerp(point, .13 + .75 * flight)
                    .add(side.scale(radius * Math.cos(angle))).add(across.scale(radius * Math.sin(angle)));
            f.chargeSpark(mote, length * (.038 + .047 * flight + .024 * atmosphere), angle,
                    i % 3 == 0, Math.sin(Math.PI * flight) * sparks);
        }
    }

    private static void chargeFan(Frame f, Vec3 guard, Vec3 point, Vec3 side,
                                  double length, double openness, double age) {
        // The Saber guide shows pointed gold sheets opening up the sword from one tight base.
        for (int i = 0; i < 6; i++) {
            int pair = i / 2;
            int sign = i % 2 == 0 ? -1 : 1;
            double spread = (.20 + .14 * pair + .016 * Math.sin(age * .11 + i)) * openness;
            double height = (.78 + .34 * openness) * (1.14 - .15 * pair + sign * .045);
            Vec3 foot = guard.lerp(point, .11 + .035 * pair);
            Vec3 bend = guard.lerp(point, height * .65).add(side.scale(sign * length * spread * .36));
            Vec3 end = guard.lerp(point, height).add(side.scale(sign * length * spread));
            Vec3 width = side.scale(length * (.066 - .013 * pair) * openness);
            int color = pair == 0 ? IVORY : pair == 1 ? GOLD : AMBER;
            f.ribbon(foot, bend, Vec3.ZERO, width, color, .90 * openness);
            f.ribbon(bend, end, width, Vec3.ZERO, color, .90 * openness);
        }
    }

    static void windChargeAt(MultiBufferSource buffers, double age, Vec3 focus, Vec3 axis, double scale) {
        Frame f = new Frame(buffers);
        Vec3 u = perpendicular(axis), v = axis.cross(u);
        double power = smooth(age / ExcaliburItem.WIND_CHARGE);
        for (int j = 0; j < 3; j++) {
            Vec3 previous = null;
            for (int i = 0; i <= 9; i++) {
                double s = i / 9.0, width = Math.sin(Math.PI * s);
                double a = age * .24 + j * TAU / 3 - s * (3.2 + 1.25 * power);
                double r = scale * (.07 + .22 * (1 - s)) * (1.32 - .55 * power);
                Vec3 p = focus.add(axis.scale(scale * (-.74 + s * (1.26 - .26 * power) + .38 * power)))
                        .add(u.scale(r * Math.cos(a))).add(v.scale(r * Math.sin(a)));
                if (previous != null) f.stroke(previous, p, scale * (.007 + .032 * width),
                        j == 0 ? PEARL : AIR, (.20 + .57 * power) * (.22 + .78 * width));
                previous = p;
            }
        }
    }

    // Display samples from the native guide, frames 159, 210 and 240.
    private static final int EX_GOLD = 0xfae034, EX_CORE = 0xfffcdd, EX_RIM = 0xfbfd64, EX_EDGE = 0xa8fed9;

    private static void beam(Frame f, Vec3 start, Vec3 end, double t, double reach) {
        Vec3 line = end.subtract(start);
        if (line.lengthSqr() < .0001) return;
        Vec3 dir = line.normalize();
        // The sweep shares the server's ray. Keep the upper edge in the blade plane even for a steep shot.
        Vec3 lateral = perpendicular(dir);
        Vec3 bladeUp = lateral.cross(dir).normalize();
        if (bladeUp.y < 0) bladeUp = bladeUp.reverse();
        double fade = Math.min(1, (1 - t) * 2.3);
        double radius = (.76 + .10 * Math.sin(t * 19)) * (.38 + .62 * fade);
        int segments = 12;
        for (int i = 0; i < segments; i++) {
            double a = reach * i / segments, b = reach * (i + 1) / segments;
            Vec3 pa = start.add(line.scale(a)), pb = start.add(line.scale(b));
            double wa = radius * breadth(a), wb = radius * breadth(b);
            // A raised cutting edge runs ahead of the white core, then flattens into the outgoing stroke.
            double crestA = wa * (1.15 + 2.65 * Math.sin(Math.PI * a) * (1 - smooth(t / .65)));
            double crestB = wb * (1.15 + 2.65 * Math.sin(Math.PI * b) * (1 - smooth(t / .65)));
            f.quad(BAND, pa.subtract(bladeUp.scale(wa * .40)), pb.subtract(bladeUp.scale(wb * .40)),
                    pb.add(bladeUp.scale(crestB)), pa.add(bladeUp.scale(crestA)), 0xf3cf30, .72 * fade);
            f.ribbon(pa.add(bladeUp.scale(crestA * .52)), pb.add(bladeUp.scale(crestB * .52)),
                    bladeUp.scale(crestA * .43), bladeUp.scale(crestB * .43), EX_GOLD, .83 * fade);
            f.ribbon(pa, pb, lateral.scale(wa * 1.24), lateral.scale(wb * 1.24), EX_GOLD, .56 * fade);
            Vec3 side = f.side(pa.lerp(pb, .5), dir);
            f.ribbon(pa, pb, side.scale(wa), side.scale(wb), EX_GOLD, .86 * fade);
        }
        Vec3 headPos = start.add(line.scale(reach));
        Vec3 coreSide = f.side(start.lerp(headPos, .5), dir);
        f.ribbon(start, headPos, coreSide.scale(radius * .10), coreSide.scale(radius * breadth(reach) * .76), EX_CORE, fade);
        // Long splinters of light follow the cut without wrapping into a cylindrical coil.
        for (int j = 0; j < 3; j++) {
            Vec3 last = null;
            for (int i = 0; i <= 10; i++) {
                double s = reach * i / 10;
                double bow = Math.sin(Math.PI * s) * radius;
                double lift = (.58 + j * .52) * bow * (1 - .55 * t);
                Vec3 p = start.add(line.scale(s)).add(bladeUp.scale(lift))
                        .add(lateral.scale((j - 1) * bow * (.60 + .23 * Math.sin(s * 5 - t * 5))));
                int edge = j == 0 && t < .31 ? EX_EDGE : j == 1 ? EX_CORE : EX_GOLD;
                if (last != null) f.stroke(last, p, .026 + .045 * bow, edge, .87 * fade);
                last = p;
            }
        }
        if (reach < 1) {
            Vec3 head = start.add(line.scale(reach));
            f.sprite(GLOW, head, 2.3 + 1.7 * t, 0, GOLD, .86);
            f.sprite(STAR, head, 2.2 + 1.4 * t, .32 + t, IVORY, 1);
        }
    }

    private static final Vec3 UP = new Vec3(0, 1, 0);
    // Skill03 marks the hit at .628505 of its animation. Rescale its following beats to the impact's lifetime.
    private static final double IMPACT_PREBURST = (.635514 - .628505) / (1 - .628505);
    private static final double IMPACT_BURST = (.679907 - .628505) / (1 - .628505);
    private static final double IMPACT_ENVIRONMENT = (.690542 - .628505) / (1 - .628505);
    private static final double IMPACT_FLARE = (.697077 - .628505) / (1 - .628505);

    private static double smooth(double x) {
        x = Mth.clamp(x, 0, 1);
        return x * x * (3 - 2 * x);
    }

    /** Fixed endpoint from the server. All stages use the beam's existing atlas and emissive batch. */
    private static void impact(Frame f, ExcaliburEffects.Effect event, double t) {
        Direction face = event.hitFace();
        Vec3 end = event.end();
        double brightness = .12 + .88 * smooth(f.camera.distanceTo(end) / 6.0);
        double flash = (1 - smooth(t / .27)) * brightness;
        double fade = (1 - smooth((t - .48) / .52)) * brightness;
        Vec3 normal = face == null ? event.start().subtract(end).normalize()
                : new Vec3(face.getStepX(), face.getStepY(), face.getStepZ());
        if (normal.lengthSqr() < .5) normal = UP;
        Vec3 center = end.add(normal.scale(.12));
        f.sprite(GLOW, center, 3.5 + 7.5 * smooth(t / .22), 0, EX_GOLD, .77 * fade);
        f.sprite(STAR, center, 4.1 + 8.0 * smooth(t / .13), .04, EX_CORE, flash);

        if (face == null) {
            // Free space has no footprint. Spread light about the endpoint and let the sparks drift.
            f.sprite(BURST, center, 2.5 + 10.5 * smooth(t / .57), t * .5, EX_GOLD, .88 * fade);
            f.sprite(RING, center, 1.4 + 11.7 * smooth(t / .55), .23, EX_RIM, .70 * fade);
            Vec3 a = perpendicular(normal), b = normal.cross(a);
            for (int i = 0; i < 12; i++) {
                double angle = i * 2.39996, depth = (i + .5) / 6 - 1;
                double plane = Math.sqrt(1 - depth * depth);
                Vec3 outward = a.scale(Math.cos(angle) * plane).add(b.scale(Math.sin(angle) * plane)).add(normal.scale(depth));
                double r = (.62 + 5.4 * smooth(t / .65)) * (.77 + .13 * (i % 3));
                f.stroke(center.add(outward.scale(r * .76)), center.add(outward.scale(r)), .045, IVORY, fade);
                f.sprite(GLINT, center.add(outward.scale(r)), .22 + .10 * (i % 3), angle, GOLD, fade);
            }
            return;
        }

        Vec3 u = perpendicular(normal), v = normal.cross(u);
        double wave = smooth(t / .53), shock = (1 - wave) * brightness;
        double radius = .65 + 8.05 * wave;
        f.plane(RING, center, u, v, radius, EX_RIM, .90 * shock);
        f.plane(SMOKE_RING, center.add(normal.scale(.06)), u, v, radius * .91, IVORY, .67 * shock);
        double slash = Math.sin(Math.PI * smooth((t - IMPACT_PREBURST) / .26)) * brightness;
        if (slash > .01) {
            Vec3 strokeAxis = end.subtract(event.start());
            strokeAxis = strokeAxis.subtract(normal.scale(strokeAxis.dot(normal)));
            if (strokeAxis.lengthSqr() < .001) strokeAxis = u;
            else strokeAxis = strokeAxis.normalize();
            Vec3 rim = normal.cross(strokeAxis).scale(.41 * slash);
            for (int sign = -1; sign <= 1; sign += 2) {
                Vec3 tip = center.add(strokeAxis.scale(sign * (4.1 + 8.9 * slash)));
                f.ribbon(center.add(normal.scale(.09)), tip, rim, Vec3.ZERO, EX_CORE, slash);
            }
        }
        // A low luminous dome outlines the contact for a beat before the taller column opens.
        double dome = Math.sin(Math.PI * smooth((t - IMPACT_PREBURST) / .39)) * brightness;
        if (dome > .007) {
            for (int arc = 0; arc < 2; arc++) {
                Vec3 across = arc == 0 ? u : v;
                Vec3 last = center.add(across.scale(radius));
                for (int k = 1; k <= 12; k++) {
                    double angle = Math.PI * k / 12;
                    Vec3 pos = center.add(across.scale(radius * Math.cos(angle)))
                            .add(normal.scale(radius * .88 * Math.sin(angle)));
                    f.stroke(last, pos, .065 + .07 * dome, EX_RIM, dome);
                    last = pos;
                }
            }
        }
        // Stagger the gilded splashes around the footprint. Each one is a tall, tapered wedge.
        for (int i = 0; i < 9; i++) {
            double flight = smooth((t - IMPACT_ENVIRONMENT - .02 * (i % 4)) / .46);
            double intensity = Math.sin(Math.PI * flight) * brightness;
            if (intensity < .007) continue;
            double angle = i * 2.39996;
            Vec3 radial = u.scale(Math.cos(angle)).add(v.scale(Math.sin(angle)));
            Vec3 foot = center.add(radial.scale(1.0 + 4.7 * flight));
            Vec3 tip = foot.add(radial.scale((1.5 + 1.5 * (i % 3)) * intensity))
                    .add(normal.scale((1.8 + 1.22 * (i % 4)) * flight));
            Vec3 width = normal.cross(radial).scale(.34 + .39 * intensity);
            f.ribbon(foot, tip, width, Vec3.ZERO, i % 3 == 0 ? EX_CORE : EX_GOLD, .85 * intensity);
        }
        // Floor and ceiling hits emerge along their face. Wall hits climb just clear of the wall.
        Vec3 axis = face.getAxis().isVertical() ? normal : UP.add(normal.scale(.65)).normalize();
        double rise = smooth((t - IMPACT_BURST) / .24);
        if (rise < .004) return;
        double height = 19.8 * rise * (1 - .22 * smooth((t - .70) / .30));
        double width = (1.10 + .64 * Math.sin(Math.PI * t)) * rise * (1 - .50 * smooth((t - .63) / .37));
        Vec3 first = center.add(normal.scale(.04)), top = first.add(axis.scale(height));
        Vec3 side = f.side(first.lerp(top, .5), axis);
        double ignition = Math.sin(Math.PI * smooth((t - IMPACT_FLARE) / .22)) * brightness;
        if (ignition > .01) {
            f.sprite(BURST, first.add(axis.scale(.72)), 6.8 + 9.0 * rise, .10, EX_CORE, ignition);
        }
        // The native guide shows a white spine within an amber skirt and a wide orbit above a tighter spiral.
        f.ribbon(first, top, side.scale(width * 1.65), side.scale(width * .35), AMBER, .58 * fade);
        f.ribbon(first, top, side.scale(width), side.scale(width * .22), EX_GOLD, .96 * fade);
        f.ribbon(first, top, side.scale(width * .58), side.scale(width * .095), EX_CORE, .98 * fade);
        Vec3 a = perpendicular(axis), b = axis.cross(a);
        Vec3 last = null;
        for (int j = 0; j <= 16; j++) {
            double y = j / 16.0, r = width * (1.15 + .68 * Math.sin(Math.PI * y));
            double turn = y * TAU * 1.52 - t * 5;
            Vec3 pos = first.add(axis.scale(height * y))
                    .add(a.scale(r * Math.cos(turn))).add(b.scale(r * Math.sin(turn)));
            if (last != null) f.stroke(last, pos, (.19 - .075 * y) * rise, EX_GOLD, .83 * fade);
            last = pos;
        }
        // The upper halo is an almost closed, inclined, flat gold ribbon with two tapered ends.
        double orbit = smooth((t - IMPACT_FLARE) / .22);
        if (orbit > .01) {
            Vec3 previous = null;
            double previousWidth = 0;
            for (int j = 0; j <= 20; j++) {
                double s = j / 20.0, angle = t * 3.6 + s * TAU * .92;
                double r = width * (2.1 + 2.65 * orbit);
                double y = height * (.63 + .13 * Math.sin(angle + .45) + .085 * s);
                Vec3 pos = first.add(axis.scale(y)).add(a.scale(r * Math.cos(angle))).add(b.scale(r * Math.sin(angle)));
                double halfWidth = orbit * (.042 + .38 * Math.sin(Math.PI * s));
                if (previous != null)
                    f.ribbon(previous, pos, axis.scale(previousWidth), axis.scale(halfWidth), EX_RIM, .95 * fade);
                previous = pos;
                previousWidth = halfWidth;
            }
        }
        for (int i = 0; i < 14; i++) {
            double angle = i * 2.39996, r = (.64 + t * 5.63) * (.55 + (i % 5) * .13);
            Vec3 point = center.add(u.scale(Math.cos(angle) * r)).add(v.scale(Math.sin(angle) * r))
                    .add(axis.scale((.20 + t * (2.1 + (i % 4))) * rise));
            f.sprite(i % 3 == 0 ? RAY : GLINT, point, .21 + .12 * (i % 3), angle, i % 2 == 0 ? GOLD : IVORY, fade);
        }
    }

    private static double breadth(double fraction) {
        return .17 + .83 * Math.min(1, Math.sqrt(fraction * 4.6));
    }

    private static void wind(Frame f, Vec3 start, Vec3 end, double t) {
        Vec3 line = end.subtract(start);
        if (line.lengthSqr() < .0001) return;
        Vec3 dir = line.normalize(), u = perpendicular(dir), v = dir.cross(u);
        double head = Math.min(1, .06 + 1.55 * t), tail = Math.min(head, Math.max(0, 1.53 * t - .46));
        double fade = 1 - smooth((t - .59) / .41);
        double peel = smooth((t - .51) / .40);
        double frontRadius = .23 + .86 * head + .68 * peel;
        for (int j = 0; j < 3; j++) {
            Vec3 last = null;
            for (int i = 0; i <= 10; i++) {
                double s = i / 10.0, along = tail + (head - tail) * s;
                double a = -4.3 * s + t * 6 + j * TAU / 3;
                double r = frontRadius * (.12 + .88 * s * s) * (1 + .09 * Math.sin(a + .6));
                Vec3 p = start.add(line.scale(along)).add(u.scale(r * Math.cos(a))).add(v.scale(r * Math.sin(a)));
                if (last != null) f.stroke(last, p, .022 + .070 * Math.sin(Math.PI * s * .92),
                        j == 0 ? PEARL : AIR, (.24 + .67 * s) * fade);
                last = p;
            }
        }
        // The compressed front peels into broken arcs. It clears the muzzle while the wave is in flight.
        Vec3 front = start.add(line.scale(head)).subtract(dir.scale(Math.min(.14, line.length() * .12)));
        for (int j = 0; j < 3; j++) {
            Vec3 last = null;
            for (int i = 0; i <= 7; i++) {
                double s = i / 7.0, a = 6 * t + j * TAU / 3 + s * (1.34 + .18 * j);
                double r = frontRadius * (.84 + .14 * Math.sin(Math.PI * s));
                Vec3 p = front.add(u.scale(r * Math.cos(a))).add(v.scale(r * Math.sin(a)))
                        .subtract(dir.scale((1 - s) * (.25 + .48 * peel)));
                if (last != null) f.stroke(last, p, .025 + .093 * Math.sin(Math.PI * s),
                        j == 2 ? AIR : PEARL, .88 * fade);
                last = p;
            }
        }
    }

    private static void avalon(Frame f, Vec3 center, double t, boolean ward) {
        double fade = Math.min(1, t * (ward ? 13 : 7)) * Math.min(1, (1 - t) * (ward ? 10 : 4));
        if (ward) {
            double r = 1.42 * (.44 + .56 * Math.min(1, 13 * t));
            Vec3 x = new Vec3(1, 0, 0), z = new Vec3(0, 0, 1);
            f.ring(center.add(0, -.8, 0), x, z, r * .80, .054, GOLD, .82 * fade);
            f.ring(center.add(0, .85, 0), x, z, r * .68, .025, IVORY, .55 * fade);
            for (int j = 0; j < 3; j++) {
                double a = t * 2.5 + j * Math.PI / 3;
                Vec3 horizontal = new Vec3(Math.cos(a), 0, Math.sin(a));
                Vec3 tilted = new Vec3(-.30 * Math.sin(a), .954, .30 * Math.cos(a));
                f.ring(center, horizontal, tilted, r, .037, j == 1 ? IVORY : GOLD, .63 * fade);
            }
            f.sprite(SIGIL, center, r * 1.65, .22 + t * .64, GOLD, .18 * fade);
        }
        for (int i = 0; i < (ward ? 15 : 9); i++) {
            double flight = fraction(t * (ward ? 3 : .8) + i * .618034);
            double a = i * 2.39996 + flight * .68, radius = ward ? 1.04 : .46;
            Vec3 p = center.add(Math.cos(a) * radius, (flight - .5) * 2.2, Math.sin(a) * radius);
            f.sprite(GLINT, p, .08 + Math.sin(Math.PI * flight) * .18, a, ward ? IVORY : MINT,
                    Math.sin(Math.PI * flight) * fade);
        }
    }

    private static double fraction(double value) { return value - Math.floor(value); }

    private static Vec3 perpendicular(Vec3 direction) {
        Vec3 axis = Math.abs(direction.y) < .91 ? new Vec3(0, 1, 0) : new Vec3(1, 0, 0);
        return direction.cross(axis).normalize();
    }

    static final class Frame {
        final Vec3 camera, right, up;
        final Matrix4f pose;
        final VertexConsumer out;

        Frame(MultiBufferSource buffers) {
            camera = Vec3.ZERO;
            right = new Vec3(1, 0, 0);
            up = new Vec3(0, 1, 0);
            pose = new Matrix4f();
            out = buffers.getBuffer(MATERIAL);
        }

        Frame(RenderLevelStageEvent event, boolean worldLight) {
            Camera c = event.getCamera();
            camera = c.getPosition();
            Vector3f left = c.getLeftVector(), top = c.getUpVector();
            right = new Vec3(-left.x(), -left.y(), -left.z());
            up = new Vec3(top.x(), top.y(), top.z());
            pose = event.getPoseStack().last().pose();
            out = BUFFERS.getBuffer(worldLight ? WORLD_LIGHT : MATERIAL);
        }

        Vec3 side(Vec3 midpoint, Vec3 direction) {
            Vec3 cross = direction.cross(midpoint.subtract(camera));
            return cross.lengthSqr() < 1.0e-7 ? perpendicular(direction) : cross.normalize();
        }

        void stroke(Vec3 a, Vec3 b, double width, int color, double opacity) {
            Vec3 d = b.subtract(a);
            if (d.lengthSqr() < .0000001) return;
            Vec3 half = side(a.lerp(b, .5), d.normalize()).scale(width);
            ribbon(a, b, half, half, color, opacity);
        }

        void ribbon(Vec3 a, Vec3 b, Vec3 halfA, Vec3 halfB, int color, double opacity) {
            quad(BAND, a.subtract(halfA), b.subtract(halfB), b.add(halfB), a.add(halfA), color, opacity);
        }

        void ring(Vec3 center, Vec3 u, Vec3 v, double radius, double width, int color, double opacity) {
            Vec3 last = u;
            for (int i = 1; i <= 32; i++) {
                double a = (TAU / 32) * i;
                Vec3 radial = u.scale(Math.cos(a)).add(v.scale(Math.sin(a)));
                ribbon(center.add(last.scale(radius)), center.add(radial.scale(radius)),
                        last.scale(width), radial.scale(width), color, opacity);
                last = radial;
            }
        }

        void chargeSpark(Vec3 point, double size, double rotation, boolean pale, double opacity) {
            sprite(GLINT, point, size, rotation, pale ? IVORY : GOLD, opacity);
        }

        void sprite(int tile, Vec3 p, double size, double rotation, int color, double opacity) {
            if (size < .01 || opacity < .01) return;
            double x = .5 * size * Math.cos(rotation), y = .5 * size * Math.sin(rotation);
            Vec3 h = right.scale(x).add(up.scale(y)), v = up.scale(x).subtract(right.scale(y));
            quad(tile, p.subtract(h).subtract(v), p.add(h).subtract(v), p.add(h).add(v), p.subtract(h).add(v),
                    color, opacity);
        }

        void plane(int tile, Vec3 center, Vec3 u, Vec3 v, double radius, int color, double opacity) {
            Vec3 h = u.scale(radius), w = v.scale(radius);
            quad(tile, center.subtract(h).subtract(w), center.add(h).subtract(w),
                    center.add(h).add(w), center.subtract(h).add(w), color, opacity);
        }

        void quad(int tile, Vec3 a, Vec3 b, Vec3 c, Vec3 d, int color, double opacity) {
            if (opacity < .003) return;
            // Half-texel inset prevents filtering from bleeding between the atlas cells.
            float u = (tile % 4 * 128 + .5f) / 512, v = (tile / 4 * 128 + .5f) / 512, span = 127f / 512;
            int alpha = Mth.clamp((int)(opacity * 255), 0, 255);
            vertex(a, u, v + span, color, alpha);
            vertex(b, u + span, v + span, color, alpha);
            vertex(c, u + span, v, color, alpha);
            vertex(d, u, v, color, alpha);
        }

        void solidFace(PlumeFace face, int alpha) {
            plumeVertex(face.a, alpha);
            plumeVertex(face.b, alpha);
            plumeVertex(face.c, alpha);
            plumeVertex(face.d, alpha);
        }

        void plumeVertex(PlumeVertex p, int alpha) {
            vertex(p.position, p.u, p.v, p.color, (int)(alpha * p.opacity));
        }

        void vertex(Vec3 p, float u, float v, int color, int alpha) {
            out.vertex(pose, (float)(p.x - camera.x), (float)(p.y - camera.y), (float)(p.z - camera.z))
                    .color(color >> 16 & 255, color >> 8 & 255, color & 255, alpha)
                    .uv(u, v).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(LightTexture.FULL_BRIGHT)
                    .normal(0, 1, 0).endVertex();
        }
    }
}
