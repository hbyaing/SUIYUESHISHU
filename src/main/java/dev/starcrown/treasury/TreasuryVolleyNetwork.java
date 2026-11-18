package dev.starcrown.treasury;

import dev.starcrown.StarcrownDoll;
import io.netty.handler.codec.DecoderException;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

/** Visual replication only. Shooting, hit tests and damage are decided by the server. */
@Mod.EventBusSubscriber(modid=StarcrownDoll.ID)
public final class TreasuryVolleyNetwork {
    private static final String VERSION="3";
    private static final int LIMIT=TreasuryNetwork.MAX_GATES;
    private static final double RANGE_SQUARED=128.0*128.0;
    private static final SimpleChannel CHANNEL=NetworkRegistry.newSimpleChannel(
            new ResourceLocation(StarcrownDoll.ID,"treasury_volley"),()->VERSION,VERSION::equals,VERSION::equals);
    private static final Map<UUID,Transmission> ACTIVE=new HashMap<>();

    private TreasuryVolleyNetwork() {}

    private record Begin(TreasuryNetwork.Cast cast,List<ItemStack> dictionary,int[] indices) {}
    private record Frame(ResourceKey<Level> dimension,UUID id,long tick,Vec3 origin,
                         List<TreasuryVolley.Shot> flying,List<Integer> ended) {}
    private record End(ResourceKey<Level> dimension,UUID id) {}
    private record Impact(ResourceKey<Level> dimension,Vec3 point,Vec3 normal,long seed) {}

    private static final class Transmission {
        final Begin begin;
        final Map<Integer,TreasuryVolley.Shot> flying=new HashMap<>();
        final BitSet ended=new BitSet(LIMIT);
        final Set<UUID> viewers=new HashSet<>();
        long lastMotionTick;

        Transmission(Begin begin) {
            this.begin=begin;
            lastMotionTick=begin.cast.start();
        }

        boolean nearby(ServerPlayer player) {
            var eye=player.getCamera().position();
            if (eye.distanceToSqr(begin.cast.origin())<=RANGE_SQUARED) return true;
            for (var shot:flying.values())
                if (eye.distanceToSqr(shot.position())<=RANGE_SQUARED) return true;
            return false;
        }

        void introduce(ServerPlayer viewer) {
            if (!viewers.add(viewer.getUUID())) return;
            var recipient=PacketDistributor.PLAYER.with(()->viewer);
            CHANNEL.send(recipient,begin);
            if (!flying.isEmpty() || !ended.isEmpty()) {
                var cast=begin.cast;
                CHANNEL.send(recipient,new Frame(cast.dimension(),cast.id(),lastMotionTick,cast.origin(),
                        List.copyOf(flying.values()),ended.stream().boxed().toList()));
            }
        }
    }

    public static void register() {
        var direction=Optional.of(NetworkDirection.PLAY_TO_CLIENT);
        CHANNEL.registerMessage(0,Begin.class,TreasuryVolleyNetwork::writeBegin,
                TreasuryVolleyNetwork::readBegin,TreasuryVolleyNetwork::receiveBegin,direction);
        CHANNEL.registerMessage(1,Frame.class,TreasuryVolleyNetwork::writeFrame,
                TreasuryVolleyNetwork::readFrame,TreasuryVolleyNetwork::receiveFrame,direction);
        CHANNEL.registerMessage(2,End.class,(packet,b)->{
            b.writeResourceLocation(packet.dimension.location()); b.writeUUID(packet.id);
        },b->new End(readDimension(b),b.readUUID()),TreasuryVolleyNetwork::receiveEnd,direction);
        CHANNEL.registerMessage(3,Impact.class,(packet,b)->{
            b.writeResourceLocation(packet.dimension.location());
            writeWorld(b,packet.point);
            b.writeShort((int)Math.round(packet.normal.x*32767));
            b.writeShort((int)Math.round(packet.normal.y*32767));
            b.writeShort((int)Math.round(packet.normal.z*32767));
            b.writeLong(packet.seed);
        },b->new Impact(readDimension(b),readWorld(b),
                new Vec3(b.readShort(),b.readShort(),b.readShort()).normalize(),b.readLong()),
                TreasuryVolleyNetwork::receiveImpact,direction);
    }

    static void impact(ServerLevel level,Vec3 point,Vec3 normal,long seed) {
        Vec3 outward=normal.lengthSqr()>1.0e-8 ? normal.normalize() : new Vec3(0,1,0);
        CHANNEL.send(PacketDistributor.NEAR.with(()->new PacketDistributor.TargetPoint(
                point.x,point.y,point.z,96,level.dimension())),
                new Impact(level.dimension(),point,outward,seed));
    }

    static boolean hasTransmission(UUID id) { return ACTIVE.containsKey(id); }

    static boolean replicatesTo(UUID id, ServerPlayer viewer) {
        var state = ACTIVE.get(id);
        return state != null && state.nearby(viewer);
    }

    public static void begin(ServerLevel level,TreasuryNetwork.Cast cast,List<ItemStack> models) {
        if (cast.chains() || ACTIVE.containsKey(cast.id())) return;
        int count=cast.gates().size();
        if (models.size()!=count) throw new IllegalStateException("Wrong number of treasury weapon models");
        List<ItemStack> dictionary=new ArrayList<>();
        int[] indices=new int[count];
        for (int i=0;i<count;i++) {
            var model=models.get(i);
            int entry=0;
            while (entry<dictionary.size() && !ItemStack.matches(model,dictionary.get(entry))) entry++;
            if (entry==dictionary.size()) dictionary.add(model.copy());
            indices[i]=entry;
        }
        var state=new Transmission(new Begin(cast,List.copyOf(dictionary),indices));
        ACTIVE.put(cast.id(),state);
        for (var player:level.players()) if (state.nearby(player)) state.introduce(player);
    }

    /** An ordinary batch contains all current motion samples; an intermediate hit may contain just removals. */
    public static void frame(ServerLevel level,TreasuryNetwork.Cast cast,long tick,
                             List<TreasuryVolley.Shot> flying,List<Integer> ended) {
        var state=ACTIVE.get(cast.id());
        if (state==null) return;
        for (int index:ended) {
            state.ended.set(index);
            state.flying.remove(index);
        }
        if (!flying.isEmpty()) {
            state.lastMotionTick=tick;
            for (var shot:flying) if (!state.ended.get(shot.index())) state.flying.put(shot.index(),shot);
        }
        Frame delta=new Frame(cast.dimension(),cast.id(),tick,cast.origin(),List.copyOf(flying),List.copyOf(ended));
        for (var player:level.players()) {
            if (!state.nearby(player)) {
                if (state.viewers.remove(player.getUUID())) sendEnd(cast,player);
            } else if (!state.viewers.contains(player.getUUID())) {
                state.introduce(player);
            } else if (!flying.isEmpty() || !ended.isEmpty()) {
                CHANNEL.send(PacketDistributor.PLAYER.with(()->player),delta);
            }
        }
    }

    public static void end(ServerLevel level,TreasuryNetwork.Cast cast) {
        var state=ACTIVE.remove(cast.id());
        if (state==null) return;
        for (var player:level.players()) if (state.viewers.contains(player.getUUID())) sendEnd(cast,player);
    }

    private static void sendEnd(TreasuryNetwork.Cast cast,ServerPlayer player) {
        CHANNEL.send(PacketDistributor.PLAYER.with(()->player),new End(cast.dimension(),cast.id()));
    }

    @SubscribeEvent public static void tracking(PlayerEvent.StartTracking event) {
        if (!(event.getEntity() instanceof ServerPlayer viewer) || !(event.getTarget() instanceof ServerPlayer owner)) return;
        for (var state:ACTIVE.values()) {
            var cast=state.begin.cast;
            if (cast.dimension().equals(viewer.level().dimension()) && cast.caster().equals(owner.getUUID()) && state.nearby(viewer))
                state.introduce(viewer);
        }
    }

    private static void forget(UUID viewer) {
        for (var state:ACTIVE.values()) state.viewers.remove(viewer);
    }

    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event) { forget(event.getEntity().getUUID()); }
    @SubscribeEvent public static void changedDimension(PlayerEvent.PlayerChangedDimensionEvent event) { forget(event.getEntity().getUUID()); }
    @SubscribeEvent public static void stopped(ServerStoppedEvent event) { ACTIVE.clear(); }

    private static void writeBegin(Begin packet,FriendlyByteBuf b) {
        TreasuryNetwork.write(packet.cast,b);
        b.writeByte(packet.dictionary.size());
        // Use the same share-tag contract as an inventory sync, including modded visual variants.
        for (var stack:packet.dictionary) b.writeItem(stack);
        for (int index:packet.indices) b.writeByte(index);
    }

    private static Begin readBegin(FriendlyByteBuf b) {
        var cast=TreasuryNetwork.read(b);
        if (cast.chains()) throw new DecoderException("Invalid volley mode");
        int count=cast.gates().size();
        int entries=b.readUnsignedByte();
        if (entries<1 || entries>count) throw new DecoderException("Invalid volley model count");
        List<ItemStack> dictionary=new ArrayList<>(entries);
        for (int i=0;i<entries;i++) dictionary.add(b.readItem());
        int[] indices=new int[count];
        for (int i=0;i<count;i++) {
            indices[i]=b.readUnsignedByte();
            if (indices[i]>=entries) throw new DecoderException("Invalid volley model index");
        }
        return new Begin(cast,List.copyOf(dictionary),indices);
    }

    private static void writeFrame(Frame packet,FriendlyByteBuf b) {
        b.writeResourceLocation(packet.dimension.location()); b.writeUUID(packet.id); b.writeVarLong(packet.tick);
        b.writeByte(packet.flying.size());
        if (!packet.flying.isEmpty()) {
            writeWorld(b,packet.origin);
            for (var shot:packet.flying) {
                b.writeByte(shot.index());
                writeRelative(b,shot.position().subtract(packet.origin),256);
                writeRelative(b,shot.velocity(),1024);
            }
        }
        BitSet removed=new BitSet(LIMIT);
        for (int index:packet.ended) removed.set(index);
        b.writeBitSet(removed);
    }

    private static Frame readFrame(FriendlyByteBuf b) {
        var dim=readDimension(b);
        UUID id=b.readUUID();
        long tick=b.readVarLong();
        int count=b.readUnsignedByte();
        if (count>LIMIT) throw new DecoderException("Invalid volley update count");
        Vec3 origin=count==0?Vec3.ZERO:readWorld(b);
        List<TreasuryVolley.Shot> poses=new ArrayList<>(count);
        for (int i=0;i<count;i++) {
            int index=b.readUnsignedByte();
            if (index>=LIMIT) throw new DecoderException("Invalid volley weapon index");
            var position=origin.add(readRelative(b,256));
            poses.add(new TreasuryVolley.Shot(index,position,readRelative(b,1024)));
        }
        // Wire-compatible with writeBitSet, with a strict two-long bound before allocation.
        int words=b.readVarInt();
        if (words<0 || words>2) throw new DecoderException("Invalid volley removal mask");
        long[] bits=new long[words];
        for (int i=0;i<words;i++) bits[i]=b.readLong();
        var removed=BitSet.valueOf(bits);
        if (removed.length()>LIMIT) throw new DecoderException("Invalid volley removal index");
        return new Frame(dim,id,tick,origin,List.copyOf(poses),removed.stream().boxed().toList());
    }

    private static ResourceKey<Level> readDimension(FriendlyByteBuf b) {
        return ResourceKey.create(Registries.DIMENSION,b.readResourceLocation());
    }
    private static Vec3 readWorld(FriendlyByteBuf b) {
        Vec3 v=new Vec3(b.readDouble(),b.readDouble(),b.readDouble());
        if (!Double.isFinite(v.lengthSqr())) throw new DecoderException("Nonfinite volley vector");
        return v;
    }
    private static void writeWorld(FriendlyByteBuf b,Vec3 v) {
        b.writeDouble(v.x); b.writeDouble(v.y); b.writeDouble(v.z);
    }
    private static void writeRelative(FriendlyByteBuf b,Vec3 v,int scale) {
        writeQuantized(b,v.x,scale); writeQuantized(b,v.y,scale); writeQuantized(b,v.z,scale);
    }
    private static void writeQuantized(FriendlyByteBuf b,double component,int scale) {
        int value=(int)Math.round(component*scale);
        b.writeVarInt((value<<1) ^ (value>>31));
    }
    private static Vec3 readRelative(FriendlyByteBuf b,int scale) {
        return new Vec3(readQuantized(b,scale),readQuantized(b,scale),readQuantized(b,scale));
    }
    private static double readQuantized(FriendlyByteBuf b,int scale) {
        int value=b.readVarInt();
        return ((value>>>1) ^ -(value&1))/(double)scale;
    }

    private static void receiveBegin(Begin packet,Supplier<NetworkEvent.Context> supply) {
        var ctx=supply.get();
        ctx.enqueueWork(()->DistExecutor.unsafeRunWhenOn(Dist.CLIENT,()->()->Client.begin(packet)));
        ctx.setPacketHandled(true);
    }
    private static void receiveFrame(Frame packet,Supplier<NetworkEvent.Context> supply) {
        var ctx=supply.get();
        ctx.enqueueWork(()->DistExecutor.unsafeRunWhenOn(Dist.CLIENT,()->()->Client.frame(packet)));
        ctx.setPacketHandled(true);
    }
    private static void receiveEnd(End packet,Supplier<NetworkEvent.Context> supply) {
        var ctx=supply.get();
        ctx.enqueueWork(()->DistExecutor.unsafeRunWhenOn(Dist.CLIENT,()->()->Client.end(packet)));
        ctx.setPacketHandled(true);
    }
    private static void receiveImpact(Impact packet,Supplier<NetworkEvent.Context> supply) {
        var ctx=supply.get();
        ctx.enqueueWork(()->DistExecutor.unsafeRunWhenOn(Dist.CLIENT,()->()->Client.impact(packet)));
        ctx.setPacketHandled(true);
    }

    // No net.minecraft.client types are resolved when the physical server loads the channel.
    private static final class Client {
        private static boolean in(ResourceKey<Level> dimension) {
            var level=net.minecraft.client.Minecraft.getInstance().level;
            return level!=null && level.dimension().equals(dimension);
        }
        static void begin(Begin packet) {
            if (!in(packet.cast.dimension())) return;
            TreasuryClient.add(packet.cast);
            var stacks=new ArrayList<ItemStack>(packet.indices.length);
            for (int index:packet.indices) stacks.add(packet.dictionary.get(index).copy());
            TreasuryVolleyClient.start(packet.cast,List.copyOf(stacks));
        }
        static void frame(Frame packet) {
            if (in(packet.dimension)) TreasuryVolleyClient.snapshot(packet.id,packet.tick,packet.flying,packet.ended);
        }
        static void end(End packet) {
            if (in(packet.dimension)) TreasuryVolleyClient.end(packet.id);
        }
        static void impact(Impact packet) {
            if (in(packet.dimension)) TreasuryImpactRenderer.add(packet.point,packet.normal,packet.seed);
        }
    }
}
