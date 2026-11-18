package dev.eavfx;

import java.util.Optional;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

public final class EaNetwork {
    private static final String PROTOCOL = "5";
    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation("starcrowndoll", "ea_cast"), () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);

    private EaNetwork() {}

    public static void register() {
        CHANNEL.registerMessage(0, CastPacket.class, CastPacket::encode, CastPacket::decode,
                CastPacket::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
    }

    public static void sendToTrackingAndSelf(ServerPlayer player, CastPacket packet) {
        CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> player), packet);
    }

    public static void sendToPlayer(ServerPlayer player, CastPacket packet) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);
    }

    /** Lifecycle cleanup can outlive the owner's tracking entry or dimension. */
    public static void sendToDimension(ResourceKey<Level> dimension, CastPacket packet) {
        CHANNEL.send(PacketDistributor.DIMENSION.with(() -> dimension), packet);
    }

    /** Phase 0: charging; 1: release/compression; 2: cancel with an 8 tick fade. */
    public record CastPacket(int entityId, long castId, int phase, long startTick,
                             Vec3 origin, Vec3 direction, float maxLength, long seed,
                             ResourceLocation dimension, int chantTicks) {
        static void encode(CastPacket packet, FriendlyByteBuf out) {
            out.writeVarInt(packet.entityId);
            out.writeLong(packet.castId);
            out.writeByte(packet.phase);
            out.writeLong(packet.startTick);
            writeVector(out, packet.origin);
            writeVector(out, packet.direction);
            out.writeFloat(packet.maxLength);
            out.writeLong(packet.seed);
            out.writeResourceLocation(packet.dimension);
            out.writeVarInt(packet.chantTicks);
        }

        static CastPacket decode(FriendlyByteBuf in) {
            return new CastPacket(in.readVarInt(), in.readLong(), in.readUnsignedByte(), in.readLong(),
                    readVector(in), readVector(in), in.readFloat(), in.readLong(), in.readResourceLocation(), in.readVarInt());
        }

        static void handle(CastPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
            NetworkEvent.Context context = contextSupplier.get();
            if (packet.phase >= 0 && packet.phase <= 2 && packet.castId > 0
                    && packet.chantTicks >= 20 && packet.chantTicks <= EaAudioConfig.MAX_CHANT_TICKS
                    && finite(packet.origin) && finite(packet.direction)
                    && packet.direction.lengthSqr() > 0.5 && packet.direction.lengthSqr() < 1.5
                    && Float.isFinite(packet.maxLength) && packet.maxLength >= 0 && packet.maxLength <= EaServer.BEAM_LENGTH) {
                context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                        () -> () -> EaClient.accept(packet)));
            }
            context.setPacketHandled(true);
        }

        private static void writeVector(FriendlyByteBuf out, Vec3 vector) {
            out.writeDouble(vector.x); out.writeDouble(vector.y); out.writeDouble(vector.z);
        }

        private static Vec3 readVector(FriendlyByteBuf in) {
            return new Vec3(in.readDouble(), in.readDouble(), in.readDouble());
        }

        private static boolean finite(Vec3 vector) {
            return Double.isFinite(vector.x) && Double.isFinite(vector.y) && Double.isFinite(vector.z);
        }
    }
}
