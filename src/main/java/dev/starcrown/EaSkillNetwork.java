package dev.starcrown;

import java.util.Optional;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

public final class EaSkillNetwork {
    private static final String PROTOCOL = "1";
    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(StarcrownDoll.ID, "ea_skill"), () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);

    private EaSkillNetwork() {}

    public static void register() {
        CHANNEL.registerMessage(0, Cycle.class, Cycle::encode, Cycle::decode, Cycle::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));
    }

    @Nullable
    static InteractionHand availableHand(Player player) {
        Item ea = StarcrownDoll.EA.get();
        if (!player.isAlive() || player.isSpectator() || player.isUsingItem() || player.getCooldowns().isOnCooldown(ea)) {
            return null;
        }
        if (player.getMainHandItem().is(ea)) return InteractionHand.MAIN_HAND;
        if (player.getOffhandItem().is(ea)) return InteractionHand.OFF_HAND;
        return null;
    }

    static void request(InteractionHand hand) {
        CHANNEL.sendToServer(new Cycle(hand));
    }

    private record Cycle(InteractionHand hand) {
        private static void encode(Cycle packet, FriendlyByteBuf out) {
            out.writeBoolean(packet.hand == InteractionHand.OFF_HAND);
        }

        private static Cycle decode(FriendlyByteBuf in) {
            return new Cycle(in.readBoolean() ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND);
        }

        private static void handle(Cycle packet, Supplier<NetworkEvent.Context> contextSupplier) {
            NetworkEvent.Context context = contextSupplier.get();
            context.enqueueWork(() -> {
                ServerPlayer player = context.getSender();
                if (player != null && availableHand(player) == packet.hand) {
                    EaSkill.cycle(player, packet.hand);
                }
            });
            context.setPacketHandled(true);
        }
    }
}
