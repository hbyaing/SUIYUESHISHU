package dev.starcrown.treasury;

import dev.starcrown.StarcrownDoll;
import io.netty.handler.codec.DecoderException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

@Mod.EventBusSubscriber(modid=StarcrownDoll.ID)
public final class TreasuryNetwork {
    static final int CHAIN_TICKS = 80;
    public static final int RELEASE_TICKS = 15;
    private static final String VERSION = "3";
    static final int MAX_GATES = 100;
    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(StarcrownDoll.ID, "treasury"), () -> VERSION, VERSION::equals, VERSION::equals);
    // Only short effects live here. Ongoing target bindings belong to the world's SavedData.
    private static final Map<UUID, TimedVisual> TIMED = new HashMap<>();

    public record Cast(UUID id, UUID caster, ResourceKey<Level> dimension, int mode, long start,
                       Vec3 origin, Vec3 forward, int targetId, Optional<UUID> target,
                       Vec3 end, float width, float height, UUID sessionId, List<TreasuryLayout.Gate> gates) {
        public Cast {
            Objects.requireNonNull(sessionId);
            gates = List.copyOf(gates);
            if (mode < 0 || mode > 2 || gates.size() > MAX_GATES || (mode == 2) != gates.isEmpty()
                    || (mode == 2 && !id.equals(sessionId))) {
                throw new IllegalArgumentException("Invalid treasury cast layout");
            }
            if (mode < 2 && id.equals(sessionId) && gates.size() != (mode == 0 ? 20 : MAX_GATES)) {
                throw new IllegalArgumentException("Invalid initial treasury gate count");
            }
        }

        public Cast(UUID id, UUID caster, ResourceKey<Level> dimension, int mode, long start,
                    Vec3 origin, Vec3 forward, int targetId, Optional<UUID> target,
                    Vec3 end, float width, float height) {
            this(id,caster,dimension,mode,start,origin,forward,targetId,target,end,width,height,id,
                    mode == 2 ? List.of() : TreasuryLayout.gates(mode == 0 ? 20 : MAX_GATES,id.getLeastSignificantBits()));
        }

        boolean chains() { return mode == 2; }
        public boolean refill() { return !id.equals(sessionId); }

        public int launchAge(int index) {
            if (!refill()) return TreasuryVolley.launchAge(index,gates.size());
            return 22 + (int)((seed() >>> (2 * (index & 31))) & 3L);
        }

        int duration() {
            if (chains()) return CHAIN_TICKS;
            int latest = launchAge(gates.size() - 1);
            if (refill()) {
                for (int i = 0; i < gates.size() - 1; i++) latest = Math.max(latest,launchAge(i));
            }
            return latest + TreasuryVolley.CLOSE_END_AFTER_LAUNCH;
        }
        public boolean isBoundChain() { return chains() && target.isPresent(); }
        public boolean hasTimedOut(long gameTime) { return !isBoundChain() && gameTime - start >= duration(); }
        long seed() { return id.getLeastSignificantBits(); }

        private Cast forTarget(LivingEntity living) {
            return new Cast(id,caster,dimension,mode,start,origin,forward,living.getId(),target,
                    living.getBoundingBox().getCenter(),living.getBbWidth(),living.getBbHeight());
        }
    }

    private record TimedVisual(Cast cast, long releaseAt) {
        boolean releasing() { return releaseAt != Long.MIN_VALUE; }
        boolean expired(long now) { return releasing() ? now-releaseAt >= RELEASE_TICKS : cast.hasTimedOut(now); }
    }

    private TreasuryNetwork() {}

    public static void register() {
        var direction = Optional.of(NetworkDirection.PLAY_TO_CLIENT);
        CHANNEL.registerMessage(0, Cast.class, TreasuryNetwork::write, TreasuryNetwork::read,
                TreasuryNetwork::receive, direction);
        CHANNEL.registerMessage(1, End.class, (end, b) -> b.writeUUID(end.id()), b -> new End(b.readUUID()),
                (end, supply) -> {
                    var ctx = supply.get();
                    ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> TreasuryClient.remove(end.id())));
                    ctx.setPacketHandled(true);
                }, direction);
        CHANNEL.registerMessage(2, Release.class, (release,b) -> {
            b.writeUUID(release.id()); b.writeLong(release.start());
        }, b -> new Release(b.readUUID(),b.readLong()), (release,supply) -> {
            var ctx=supply.get();
            ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                    () -> () -> TreasuryClient.release(release.id(),release.start())));
            ctx.setPacketHandled(true);
        }, direction);
    }

    private record End(UUID id) {}
    private record Release(UUID id,long start) {}

    public static Cast cast(ServerPlayer player, int mode, Vec3 origin, Vec3 forward, int targetId, Vec3 end) {
        var level = player.serverLevel();
        var entity = targetId < 0 ? null : level.getEntity(targetId);
        var target = entity instanceof LivingEntity living && living.isAlive() ? living : null;
        var cast = new Cast(UUID.randomUUID(), player.getUUID(), level.dimension(), mode, level.getGameTime(),
                origin, forward, target == null ? -1 : target.getId(), Optional.ofNullable(target).map(LivingEntity::getUUID),
                end, target == null ? .8f : target.getBbWidth(), target == null ? 1.7f : target.getBbHeight());
        if (cast.isBoundChain()) {
            var data = TreasurySavedData.get(level.getServer());
            if (data.byOwner(cast.caster()) != null) return null;
            data.put(cast);
            // Bindings follow target tracking, including when the owner moves away.
            CHANNEL.send(PacketDistributor.TRACKING_ENTITY.with(() -> target),cast);
        } else {
            startTimed(player,cast);
        }
        return cast;
    }

    static Cast refill(ServerPlayer owner, int mode, List<TreasuryLayout.Gate> gates, UUID sessionId) {
        var level = owner.serverLevel();
        Vec3 origin = owner.position();
        var cast = new Cast(UUID.randomUUID(),owner.getUUID(),level.dimension(),mode,level.getGameTime(),
                origin,Vec3.directionFromRotation(0,owner.getYRot()),-1,Optional.empty(),origin,.8f,1.7f,sessionId,gates);
        startTimed(owner,cast);
        return cast;
    }

    private static void startTimed(ServerPlayer player, Cast cast) {
        TIMED.put(cast.id(),new TimedVisual(cast,Long.MIN_VALUE));
        if (!cast.chains()) {
            TreasuryVolley.start(player,cast);
            // Begin carries the portal and its real item models together, including on late tracking.
            if (TreasuryVolleyNetwork.hasTransmission(cast.id())) return;
        }
        var center = cast.chains() ? cast.end() : cast.origin();
        CHANNEL.send(PacketDistributor.NEAR.with(() -> new PacketDistributor.TargetPoint(
                center.x, center.y, center.z, 128, cast.dimension())), cast);
    }

    /** The owner can release their target even from a different dimension or after reconnecting. */
    public static boolean releaseChains(ServerPlayer owner) {
        MinecraftServer server = owner.serverLevel().getServer();
        var data = TreasurySavedData.get(server);
        var binding = data.byOwner(owner.getUUID());
        if (binding == null) return false;
        Cast cast = binding.cast();
        data.remove(cast.id());
        var level = server.getLevel(cast.dimension());
        if (level != null) {
            long now = level.getGameTime();
            TIMED.put(cast.id(),new TimedVisual(cast,now));
            CHANNEL.send(PacketDistributor.DIMENSION.with(cast::dimension),new Release(cast.id(),now));
        }
        return true;
    }

    /** Immediate end, e.g. when the target dies. Never generates a new visual for a missing target. */
    public static void endBinding(MinecraftServer server,UUID castId) {
        var binding=TreasurySavedData.get(server).remove(castId);
        if (binding != null) {
            Cast cast = binding.cast();
            CHANNEL.send(PacketDistributor.DIMENSION.with(cast::dimension),new End(cast.id()));
        }
    }

    @SubscribeEvent(priority=EventPriority.LOWEST)
    public static void died(LivingDeathEvent event) {
        LivingEntity target = event.getEntity();
        if (!(target.level() instanceof ServerLevel level)) return;
        MinecraftServer server=level.getServer();
        var bindings=TreasurySavedData.get(server).bindings();
        for (var binding : bindings) {
            Cast cast=binding.cast();
            if (!cast.caster().equals(target.getUUID()) && cast.target().filter(target.getUUID()::equals).isEmpty()) continue;
            // Wait until every death handler has had a chance to cancel or revive the target.
            server.tell(new TickTask(server.getTickCount(),() -> {
                if (!event.isCanceled() && !target.isAlive()) endBinding(server,cast.id());
            }));
        }
    }

    @SubscribeEvent
    public static void tracking(PlayerEvent.StartTracking event) {
        if (!(event.getEntity() instanceof ServerPlayer viewer)) return;
        var level=viewer.serverLevel();
        var watched=event.getTarget();
        var destination=PacketDistributor.PLAYER.with(() -> viewer);
        for (var visual : TIMED.values()) {
            Cast cast=visual.cast();
            if (!level.dimension().equals(cast.dimension()) || visual.expired(level.getGameTime())) continue;
            if (visual.releasing()) {
                if (watched instanceof LivingEntity living && cast.target().filter(living.getUUID()::equals).isPresent()) {
                    CHANNEL.send(destination,cast.forTarget(living));
                    CHANNEL.send(destination,new Release(cast.id(),visual.releaseAt()));
                }
            } else if (watched.getUUID().equals(cast.caster()) && !TreasuryVolleyNetwork.replicatesTo(cast.id(),viewer)) {
                CHANNEL.send(destination,cast);
            }
        }
        if (watched instanceof LivingEntity living) {
            for (var binding : TreasurySavedData.get(level.getServer()).bindings()) {
                Cast cast=binding.cast();
                if (level.dimension().equals(cast.dimension()) && cast.target().filter(living.getUUID()::equals).isPresent())
                    CHANNEL.send(destination,cast.forTarget(living));
            }
        }
    }

    @SubscribeEvent
    public static void untracking(PlayerEvent.StopTracking event) {
        if (!(event.getEntity() instanceof ServerPlayer viewer) || !(event.getTarget() instanceof LivingEntity living)) return;
        var level=viewer.serverLevel();
        var destination=PacketDistributor.PLAYER.with(() -> viewer);
        for (var binding : TreasurySavedData.get(level.getServer()).bindings()) {
            Cast cast=binding.cast();
            if (level.dimension().equals(cast.dimension()) && cast.target().filter(living.getUUID()::equals).isPresent())
                CHANNEL.send(destination,new End(cast.id()));
        }
        for (var visual : TIMED.values()) {
            Cast cast=visual.cast();
            if (visual.releasing() && level.dimension().equals(cast.dimension()) && cast.target().filter(living.getUUID()::equals).isPresent())
                CHANNEL.send(destination,new End(cast.id()));
        }
    }

    @SubscribeEvent
    public static void tick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || TIMED.isEmpty()) return;
        TIMED.values().removeIf(visual -> {
            Cast cast = visual.cast();
            var level = event.getServer().getLevel(cast.dimension());
            if (level == null || visual.expired(level.getGameTime())) return true;
            if (visual.releasing()) return false;
            var owner = event.getServer().getPlayerList().getPlayer(cast.caster());
            if (owner != null && owner.level() == level && owner.isAlive() && !owner.isSpectator()) return false;
            CHANNEL.send(PacketDistributor.DIMENSION.with(cast::dimension), new End(cast.id()));
            return true;
        });
    }

    @SubscribeEvent public static void stopped(ServerStoppedEvent event) { TIMED.clear(); }

    static void write(Cast c, FriendlyByteBuf b) {
        b.writeUUID(c.id()); b.writeUUID(c.caster()); b.writeResourceLocation(c.dimension().location());
        b.writeByte(c.refill() ? c.mode() + 3 : c.mode()); b.writeVarLong(c.start());
        vector(b, c.origin()); vector(b, c.forward());
        if (c.chains()) {
            b.writeVarInt(c.targetId()); b.writeOptional(c.target(), FriendlyByteBuf::writeUUID);
            vector(b, c.end()); b.writeFloat(c.width()); b.writeFloat(c.height());
        } else if (c.refill()) {
            b.writeUUID(c.sessionId()); b.writeByte(c.gates().size());
            for (var gate : c.gates()) {
                writeLocal(b,gate.x()); writeLocal(b,gate.y()); writeLocal(b,gate.z()); writeLocal(b,gate.radius());
            }
        }
    }

    static Cast read(FriendlyByteBuf b) {
        UUID id = b.readUUID(), owner = b.readUUID();
        var dimension = ResourceKey.create(Registries.DIMENSION,b.readResourceLocation());
        int kind = b.readUnsignedByte();
        if (kind > 4) throw new DecoderException("Invalid treasury cast kind");
        long start = b.readVarLong();
        Vec3 origin = vector(b), forward = vector(b);
        if (kind == 2) {
            int targetId = b.readVarInt();
            var target = b.readOptional(FriendlyByteBuf::readUUID);
            Vec3 end = vector(b);
            float width = b.readFloat(), height = b.readFloat();
            if (!Float.isFinite(width) || !Float.isFinite(height) || width <= 0 || height <= 0)
                throw new DecoderException("Invalid treasury binding dimensions");
            return new Cast(id,owner,dimension,kind,start,origin,forward,targetId,target,end,width,height);
        }
        if (kind < 2)
            return new Cast(id,owner,dimension,kind,start,origin,forward,-1,Optional.empty(),origin,.8f,1.7f);
        UUID sessionId = b.readUUID();
        int count = b.readUnsignedByte();
        if (id.equals(sessionId) || count < 1 || count > MAX_GATES) throw new DecoderException("Invalid treasury refill");
        var gates = new ArrayList<TreasuryLayout.Gate>(count);
        for (int i = 0; i < count; i++) {
            var gate = new TreasuryLayout.Gate(readLocal(b),readLocal(b),readLocal(b),readLocal(b));
            if (gate.radius() <= 0) throw new DecoderException("Invalid treasury gate radius");
            gates.add(gate);
        }
        return new Cast(id,owner,dimension,kind-3,start,origin,forward,-1,Optional.empty(),origin,.8f,1.7f,sessionId,gates);
    }

    // Pack local coordinates in 1/256 block units, retaining full floats for outliers.
    private static void writeLocal(FriendlyByteBuf b, float value) {
        int units = Math.round(value * 256);
        if (units > Short.MIN_VALUE && units <= Short.MAX_VALUE && (units != 0 || value == 0)) b.writeShort(units);
        else { b.writeShort(Short.MIN_VALUE); b.writeFloat(value); }
    }

    private static float readLocal(FriendlyByteBuf b) {
        short units = b.readShort();
        float value = units == Short.MIN_VALUE ? b.readFloat() : units / 256f;
        if (!Float.isFinite(value)) throw new DecoderException("Nonfinite treasury gate coordinate");
        return value;
    }

    private static void vector(FriendlyByteBuf b, Vec3 v) { b.writeDouble(v.x); b.writeDouble(v.y); b.writeDouble(v.z); }
    private static Vec3 vector(FriendlyByteBuf b) {
        var v = new Vec3(b.readDouble(), b.readDouble(), b.readDouble());
        if (!Double.isFinite(v.lengthSqr())) throw new DecoderException("Nonfinite treasury cast vector");
        return v;
    }

    private static void receive(Cast c, Supplier<NetworkEvent.Context> supply) {
        var ctx = supply.get();
        ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> TreasuryClient.add(c)));
        ctx.setPacketHandled(true);
    }
}
