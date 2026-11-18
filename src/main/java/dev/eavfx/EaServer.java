package dev.eavfx;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import dev.starcrown.StarcrownDoll;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid=StarcrownDoll.ID)
public final class EaServer {
    public static final int BEAM_LENGTH=1000;
    public static final double BEAM_RADIUS=3.5;
    public static final int CHARGE_TICKS = 40;
    public static final int RELEASE_TICKS = EaAudioConfig.RELEASE_SOUND_TICKS;
    public static final int CANCEL_TICKS = 8;
    private static final Map<UUID, Cast> CASTS = new HashMap<>();
    private static long nextCastId = 1;

    private EaServer() {}

    private static final class Cast {
        final long id;
        final long seed;
        final ServerLevel level;
        final InteractionHand hand;
        final ItemStack stack;
        final int entityId;
        final int chantTicks;
        int phase;
        long startTick;
        Vec3 origin;
        Vec3 direction;
        float maxLength = BEAM_LENGTH;
        EaTerrainCut terrain;

        Cast(ServerPlayer player, InteractionHand hand) {
            id = nextCastId++;
            seed = player.getRandom().nextLong();
            level = player.serverLevel();
            this.hand = hand;
            stack = player.getItemInHand(hand);
            entityId = player.getId();
            chantTicks = EaAudioConfig.chantTicks();
            startTick = level.getGameTime();
            direction = player.getLookAngle().normalize();
            origin = source(player, hand, direction);
        }

        EaNetwork.CastPacket snapshot() {
            return new EaNetwork.CastPacket(entityId, id, phase, startTick, origin, direction, maxLength, seed,
                    level.dimension().location(), chantTicks);
        }
    }

    public static boolean begin(ServerPlayer player, InteractionHand hand) {
        if (!player.isAlive() || player.isSpectator() || !player.getItemInHand(hand).is(StarcrownDoll.EA.get())) return false;
        Cast previous = CASTS.get(player.getUUID());
        if (previous != null && previous.level != player.serverLevel()) {
            cleanup(player.getUUID(), previous);
            previous = null;
        }
        if (previous != null && previous.level == player.serverLevel()
                && player.level().getGameTime() - previous.startTick < (previous.phase == 2 ? CANCEL_TICKS : RELEASE_TICKS)) {
            return false;
        }
        // A still-charging cast is never replaced while the same use input remains active.
        if (previous != null && (previous.phase == 0 || previous.terrain != null)) return false;
        if(CASTS.size()>=4&&previous==null)return false;
        Cast cast = new Cast(player, hand);
        CASTS.put(player.getUUID(), cast);
        EaNetwork.sendToTrackingAndSelf(player, cast.snapshot());
        return true;
    }

    private static void fire(ServerPlayer player, Cast cast) {
        long now = cast.level.getGameTime();
        cast.phase = 1;
        cast.startTick = now;
        cast.direction = player.getLookAngle().normalize();
        cast.origin = source(player, cast.hand, cast.direction);
        cast.terrain=new EaTerrainCut(cast.id,cast.level,cast.origin,cast.direction);
        EaNetwork.sendToTrackingAndSelf(player, cast.snapshot());
        player.stopUsingItem();
        damageBeam(player, cast, 0);
    }

    private static void damageBeam(ServerPlayer owner, Cast cast, long age) {
        float length = EaFrame.lengthAt(age, cast.maxLength);
        EaBeamDamage.apply(owner, cast.origin, cast.direction, length);
    }

    public static void interrupt(ServerPlayer player) {
        Cast cast = CASTS.get(player.getUUID());
        if (cast != null && cast.phase == 0) cancel(player);
    }

    public static void cancel(ServerPlayer player) {
        Cast cast = CASTS.get(player.getUUID());
        if (cast == null || cast.phase == 2) return;
        if (cast.phase == 0 && player.serverLevel() == cast.level) {
            cast.direction = player.getLookAngle().normalize();
            cast.origin = source(player, cast.hand, cast.direction);
        }
        cast.phase = 2;
        cast.startTick = cast.level.getGameTime();
        EaNetwork.sendToTrackingAndSelf(player, cast.snapshot());
    }

    private static boolean validOwner(ServerPlayer player, Cast cast) {
        return player.isAlive() && !player.isSpectator() && player.serverLevel() == cast.level
                && player.getItemInHand(cast.hand) == cast.stack && cast.stack.is(StarcrownDoll.EA.get());
    }

    @SubscribeEvent
    public static void tick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        for (UUID owner : new ArrayList<>(CASTS.keySet())) {
            Cast cast = CASTS.get(owner);
            if (cast == null) continue;
            ServerPlayer player = event.getServer().getPlayerList().getPlayer(owner);
            long age = cast.level.getGameTime() - cast.startTick;
            if (player == null || player.serverLevel() != cast.level) {
                cleanup(owner, cast);
                continue;
            }
            if (cast.phase == 2) {
                if (age >= CANCEL_TICKS) CASTS.remove(owner);
                continue;
            }
            if (!player.isAlive() || player.isSpectator()) {
                cleanup(owner, cast);
                continue;
            }
            // The released beam is an immutable world-space cast, independent of the held stack.
            if (cast.phase == 1) {
                if (age > 0 && age < RELEASE_TICKS) {
                    damageBeam(player, cast, age);
                    if (CASTS.get(owner) != cast || cast.phase != 1) continue;
                }
                if(cast.terrain!=null&&cast.terrain.advance(player))cast.terrain=null;
                if (age >= RELEASE_TICKS&&cast.terrain==null) CASTS.remove(owner);
                continue;
            }
            if (!validOwner(player, cast)) {
                cancel(player);
                player.stopUsingItem();
                continue;
            }
            if (!player.isUsingItem() || player.getUsedItemHand() != cast.hand
                    || player.getUseItem() != cast.stack) {
                cancel(player);
                player.stopUsingItem();
            } else if (age >= CHARGE_TICKS + cast.chantTicks) fire(player, cast);
        }
    }

    @SubscribeEvent
    public static void startTracking(PlayerEvent.StartTracking event) {
        if (!(event.getEntity() instanceof ServerPlayer viewer) || !(event.getTarget() instanceof ServerPlayer owner)) return;
        Cast cast = CASTS.get(owner.getUUID());
        if (cast == null || cast.level != viewer.serverLevel()) return;
        long age = cast.level.getGameTime() - cast.startTick;
        if (cast.phase == 2 || age >= (cast.phase == 0 ? CHARGE_TICKS + cast.chantTicks : RELEASE_TICKS)) return;
        if (cast.phase == 0) {
            cast.direction = owner.getLookAngle().normalize();
            cast.origin = source(owner, cast.hand, cast.direction);
        }
        EaNetwork.sendToPlayer(viewer, cast.snapshot());
    }

    @SubscribeEvent
    public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        cleanup(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void dimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        cleanup(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void died(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) cleanup(player.getUUID());
    }

    @SubscribeEvent
    public static void stopped(ServerStoppedEvent event) {
        for(Cast cast:CASTS.values())if(cast.terrain!=null)cast.terrain.close();
        CASTS.clear();
        nextCastId = 1;
    }

    private static void cleanup(UUID owner) {
        Cast cast = CASTS.get(owner);
        if (cast != null) cleanup(owner, cast);
    }

    private static void cleanup(UUID owner, Cast cast) {
        if(cast.terrain!=null)cast.terrain.close();
        cast.phase = 2;
        cast.startTick = cast.level.getGameTime();
        // The entity may already be untracked, dead, or in a new dimension.
        EaNetwork.sendToDimension(cast.level.dimension(), cast.snapshot());
        CASTS.remove(owner);
    }

    private static Vec3 source(ServerPlayer player, InteractionHand hand, Vec3 direction) {
        Vec3 eyes = player.getEyePosition();
        Vec3 right=Vec3.directionFromRotation(0,player.getYRot()).cross(new Vec3(0,1,0));
        boolean left=(player.getMainArm()==net.minecraft.world.entity.HumanoidArm.LEFT)^(hand==InteractionHand.OFF_HAND);
        Vec3 proposed=eyes.add(direction.scale(1.68)).add(right.scale(left?-.27:.27)).add(0,-.28,0);
        Vec3 offset = proposed.subtract(eyes);
        proposed = eyes.add(offset.normalize().scale(loadedRange(player.serverLevel(), eyes,
                offset.normalize(), offset.length())));
        BlockHitResult hit = player.level().clip(new ClipContext(eyes, proposed,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        if (hit.getType() == HitResult.Type.MISS) return proposed;
        double distance = Math.max(0, eyes.distanceTo(hit.getLocation()) - 0.03);
        return eyes.add(proposed.subtract(eyes).normalize().scale(distance));
    }

    /** Do not load new chunks just to find a visual beam's endpoint. */
    private static double loadedRange(ServerLevel level, Vec3 origin, Vec3 direction, double requested) {
        BlockPos start = BlockPos.containing(origin);
        int chunkX = start.getX() >> 4;
        int chunkZ = start.getZ() >> 4;
        double distance = 0;
        while (distance <= requested) {
            // Visit every crossed chunk, including arbitrarily short diagonal corner crossings.
            if (!level.hasChunkAt(new BlockPos(chunkX << 4, start.getY(), chunkZ << 4)))
                return Math.max(0, distance - 1.0e-4);
            double nextX = chunkBoundary(origin.x, direction.x, chunkX);
            double nextZ = chunkBoundary(origin.z, direction.z, chunkZ);
            double next = Math.min(nextX, nextZ);
            if (next > requested) return requested;
            if (nextX <= nextZ) chunkX += direction.x > 0 ? 1 : -1;
            if (nextZ <= nextX) chunkZ += direction.z > 0 ? 1 : -1;
            distance = next;
        }
        return requested;
    }

    private static double chunkBoundary(double origin, double direction, int chunk) {
        if (Math.abs(direction) < 1.0e-12) return Double.POSITIVE_INFINITY;
        return ((direction > 0 ? chunk + 1 : chunk) * 16.0 - origin) / direction;
    }
}
