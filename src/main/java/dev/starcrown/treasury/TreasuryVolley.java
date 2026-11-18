package dev.starcrown.treasury;

import dev.starcrown.StarcrownDoll;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Server-owned weapon flight. A gate's index identifies the same weapon on every client. */
@Mod.EventBusSubscriber(modid = StarcrownDoll.ID)
public final class TreasuryVolley {
    private static final Map<UUID, Volley> ACTIVE = new HashMap<>();
    private static final Vec3 UP = new Vec3(0, 1, 0);
    private static final double AIM_REACH = 72;
    private static final int FLIGHT_TICKS = 60;
    private static final int SYNC_INTERVAL = 3;
    public static final int CLOSE_START_AFTER_LAUNCH = 1;
    public static final int CLOSE_END_AFTER_LAUNCH = 8;

    /** The authoritative world-space weapon tip and its movement per tick. */
    public record Shot(int index, Vec3 position, Vec3 velocity) {}

    public static int launchAge(int index, int count) {
        return 22 + index * (count == 20 ? 34 : 54) / (count - 1);
    }

    public static int closeStartAge(int index, int count) {
        return launchAge(index, count) + CLOSE_START_AFTER_LAUNCH;
    }

    public static int closeEndAge(int index, int count) {
        return launchAge(index, count) + CLOSE_END_AFTER_LAUNCH;
    }

    public static int cycleTicks(int count) {
        return closeEndAge(count - 1, count) + 5;
    }

    public static Vec3 gateCenter(TreasuryNetwork.Cast cast, TreasuryLayout.Gate gate) {
        Vec3 forward = cast.forward();
        return cast.origin().add(forward.cross(UP).scale(gate.x())).add(0, gate.y(), 0).add(forward.scale(gate.z()));
    }

    public static Vec3 launchPoint(TreasuryNetwork.Cast cast, TreasuryLayout.Gate gate) {
        return gateCenter(cast, gate).add(cast.forward().scale(1.55));
    }

    private static final class Blade {
        final TreasuryLayout.Gate layout;
        final int launchAge;
        final Vec3 gate;
        final Vec3 tip;
        Vec3 position;
        Vec3 velocity;
        int state; // 0 in the gate, 1 in flight, 2 finished

        Blade(TreasuryNetwork.Cast cast, TreasuryLayout.Gate layout, int launchAge) {
            this.layout = layout;
            this.launchAge = launchAge;
            gate = gateCenter(cast, layout);
            tip = launchPoint(cast, layout);
            position = tip;
            velocity = cast.forward();
        }
    }

    private static final class Volley {
        final ServerPlayer caster;
        final ServerLevel level;
        final TreasuryNetwork.Cast cast;
        final Blade[] blades;
        final int firstLaunchAge;
        int remaining;

        Volley(ServerPlayer owner, TreasuryNetwork.Cast cast, List<ItemStack> models) {
            this.caster = owner;
            this.level = owner.serverLevel();
            this.cast = cast;
            List<TreasuryLayout.Gate> gates = cast.gates();
            blades = new Blade[gates.size()];
            int first = Integer.MAX_VALUE;
            for (int i = 0; i < blades.length; i++) {
                blades[i] = new Blade(cast, gates.get(i), cast.launchAge(i));
                if (models.get(i).isEmpty()) blades[i].state = 2;
                else {
                    remaining++;
                    first = Math.min(first, blades[i].launchAge);
                }
            }
            firstLaunchAge = first;
        }
    }

    private record Aim(Vec3 point, boolean canImpact, Direction face) {}
    private record Contact(Vec3 point, LivingEntity target, boolean impact, Direction face) {}

    private TreasuryVolley() {}

    public static void start(ServerPlayer owner, TreasuryNetwork.Cast cast) {
        if (cast.chains() || ACTIVE.containsKey(cast.id())) return;
        List<ItemStack> models = TreasuryWeaponPool.choose(cast.seed(), cast.gates().size());
        if (models.stream().allMatch(ItemStack::isEmpty)) return;
        var volley = new Volley(owner, cast, models);
        ACTIVE.put(cast.id(), volley);
        TreasuryVolleyNetwork.begin(volley.level, cast, models);
    }

    @SubscribeEvent
    public static void tick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Map<ServerPlayer, Aim> aims = new HashMap<>();
        for (var volley : List.copyOf(ACTIVE.values())) {
            ServerPlayer owner = event.getServer().getPlayerList().getPlayer(volley.cast.caster());
            if (owner == volley.caster && owner.isAlive() && !owner.isSpectator() && owner.level() == volley.level) {
                advance(volley, owner, aims);
                if (volley.remaining > 0) continue;
            }
            ACTIVE.remove(volley.cast.id());
            TreasuryVolleyNetwork.end(volley.level, volley.cast);
        }
        TreasuryItems.finishGateTick(event.getServer());
    }

    @SubscribeEvent public static void stopped(ServerStoppedEvent event) { ACTIVE.clear(); }

    private static void advance(Volley volley, ServerPlayer owner, Map<ServerPlayer, Aim> aims) {
        long now = volley.level.getGameTime();
        long age = now - volley.cast.start();
        if (age < volley.firstLaunchAge) return;
        Aim aim = aims.computeIfAbsent(owner, TreasuryVolley::aim);
        boolean sync = age % SYNC_INTERVAL == 0;
        List<Shot> flying = sync ? new ArrayList<>(volley.remaining) : List.of();
        List<Integer> ended = new ArrayList<>();
        for (int i = 0; i < volley.blades.length; i++) {
            Blade blade = volley.blades[i];
            long flightAge = age - blade.launchAge;
            if (blade.state == 2 || flightAge < 0) continue;
            Contact contact = null;
            if (blade.state == 0) {
                blade.state = 1;
                TreasuryItems.gateFired(owner, volley.cast, blade.layout);
                contact = sweep(owner, blade.gate, blade.tip);
            }
            if (contact == null && flightAge < FLIGHT_TICKS) contact = steer(owner, blade, aim, flightAge);
            if (contact != null || flightAge >= FLIGHT_TICKS) {
                blade.state = 2;
                volley.remaining--;
                ended.add(i);
                if (contact != null && contact.impact()) {
                    long seed = volley.cast.seed() ^ (0x9E3779B97F4A7C15L * (i + 1));
                    Vec3 incoming = contact.face() == null ? blade.velocity
                            : Vec3.atLowerCornerOf(contact.face().getOpposite().getNormal());
                    TreasuryImpacts.hit(owner, contact.point(), contact.target(), seed, incoming);
                    if (!owner.isAlive() || owner.level() != volley.level) break;
                }
            } else if (sync) {
                flying.add(new Shot(i, blade.position, blade.velocity));
            }
        }
        if (!flying.isEmpty() || !ended.isEmpty()) TreasuryVolleyNetwork.frame(volley.level, volley.cast, now, flying, ended);
    }

    /** Shared by all of the owner's flying weapons on this server tick. */
    private static Aim aim(ServerPlayer owner) {
        Vec3 eye = owner.getEyePosition();
        Vec3 direction = owner.getLookAngle();
        double range = loadedRange(owner.serverLevel(), eye, direction, AIM_REACH);
        Vec3 limit = eye.add(direction.scale(range));
        BlockHitResult wall = blockHit(owner, eye, limit);
        Vec3 end = wall.getLocation();
        EntityHitResult entity = entityHit(owner, eye, end, 0.03f);
        if (entity != null) return new Aim(entity.getLocation(), true, null);
        if (wall.getType() == HitResult.Type.BLOCK) return new Aim(end, true, wall.getDirection());
        return new Aim(end, range >= AIM_REACH, null);
    }

    private static Contact steer(ServerPlayer owner, Blade blade, Aim aim, long flightAge) {
        Vec3 delta = aim.point().subtract(blade.position);
        double distance = delta.length();
        double speed = Math.min(3.2, 1.25 + 0.18 * flightAge);
        if (distance < 1.0e-6) return new Contact(aim.point(), null, aim.canImpact(), aim.face());
        Vec3 desired = delta.scale(1.0 / distance);
        Vec3 heading = blade.velocity.normalize();
        if (distance <= speed * 2.5) {
            heading = desired;
        } else {
            double cosine = Mth.clamp(heading.dot(desired), -1.0, 1.0);
            double angle = Math.acos(cosine);
            double turn = Math.max(.23, 2.8 * speed / distance);
            if (angle <= turn) {
                heading = desired;
            } else {
                Vec3 tangent = desired.subtract(heading.scale(cosine));
                if (tangent.lengthSqr() < 1e-10) {
                    tangent = heading.cross(Math.abs(heading.y) > .98 ? new Vec3(1, 0, 0) : UP);
                }
                heading = heading.scale(Math.cos(turn)).add(tangent.normalize().scale(Math.sin(turn)));
            }
        }
        Vec3 previous = blade.position;
        blade.velocity = heading.scale(Math.min(speed, distance));
        blade.position = previous.add(blade.velocity);
        Contact hit = sweep(owner, previous, blade.position);
        if (hit != null) return hit;
        return distance <= speed ? new Contact(aim.point(), null, aim.canImpact(), aim.face()) : null;
    }

    private static Contact sweep(ServerPlayer owner, Vec3 start, Vec3 end) {
        Vec3 movement = end.subtract(start);
        double length = movement.length();
        if (length < 1.0e-7) return null;
        Vec3 direction = movement.scale(1.0 / length);
        double range = loadedRange(owner.serverLevel(), start, direction, length);
        if (range <= 0) return new Contact(start, null, false, null);
        Vec3 limit = range >= length ? end : start.add(direction.scale(range));
        BlockHitResult block = blockHit(owner, start, limit);
        EntityHitResult entity = entityHit(owner, start, block.getLocation(), .1f);
        if (entity != null) return new Contact(entity.getLocation(), (LivingEntity) entity.getEntity(), true, null);
        if (block.getType() != HitResult.Type.MISS) return new Contact(block.getLocation(), null, true, block.getDirection());
        return range < length ? new Contact(limit, null, false, null) : null;
    }

    private static BlockHitResult blockHit(ServerPlayer owner, Vec3 start, Vec3 end) {
        return owner.level().clip(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, owner));
    }

    private static EntityHitResult entityHit(ServerPlayer owner, Vec3 start, Vec3 end, float radius) {
        EntityHitResult nearest = null;
        double distance = Double.POSITIVE_INFINITY;
        var area = new AABB(start, end).inflate(radius + 1.0);
        for (LivingEntity target : owner.serverLevel().getEntitiesOfClass(LivingEntity.class, area,
                mob -> mob != owner && mob.isAlive() && mob.isPickable() && !mob.isSpectator())) {
            AABB hitBox = target.getBoundingBox().inflate(radius);
            Vec3 point = hitBox.contains(start) ? start : hitBox.clip(start, end).orElse(null);
            if (point != null && start.distanceToSqr(point) < distance) {
                distance = start.distanceToSqr(point);
                nearest = new EntityHitResult(target, point);
            }
        }
        return nearest;
    }

    /** Clip the sweep to already-loaded chunks, including short diagonal corner crossings. */
    private static double loadedRange(ServerLevel level, Vec3 start, Vec3 direction, double maximum) {
        int cx = Mth.floor(start.x) >> 4;
        int cz = Mth.floor(start.z) >> 4;
        int stepX = (int) Math.signum(direction.x);
        int stepZ = (int) Math.signum(direction.z);
        double nextX = stepX == 0 ? Double.POSITIVE_INFINITY : ((cx + (stepX > 0 ? 1 : 0)) * 16.0 - start.x) / direction.x;
        double nextZ = stepZ == 0 ? Double.POSITIVE_INFINITY : ((cz + (stepZ > 0 ? 1 : 0)) * 16.0 - start.z) / direction.z;
        double strideX = stepX == 0 ? Double.POSITIVE_INFINITY : 16.0 / Math.abs(direction.x);
        double strideZ = stepZ == 0 ? Double.POSITIVE_INFINITY : 16.0 / Math.abs(direction.z);
        double entered = 0;
        while (level.getChunkSource().getChunkNow(cx, cz) != null) {
            double next = Math.min(nextX, nextZ);
            if (next > maximum) return maximum;
            if (nextX <= next) { cx += stepX; nextX += strideX; }
            if (nextZ <= next) { cz += stepZ; nextZ += strideZ; }
            entered = next;
        }
        return Math.max(0, entered - .001);
    }
}
