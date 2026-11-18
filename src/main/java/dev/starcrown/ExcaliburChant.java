package dev.starcrown;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.PacketDistributor;

/** The accepted cast belongs to this bearer, hand and physical stack until the last syllable. */
@Mod.EventBusSubscriber(modid = StarcrownDoll.ID)
public final class ExcaliburChant {
    public static final int PREPARE_TICKS = 10;
    public static final int LAST_LINE_TICK = 103;
    // The selection has 282853 samples. The added 83867 samples separate the spoken lines by 2 seconds.
    public static final double NAME_START_TICK = (282853.0 + 83867) * 20 / 48000;
    // The last syllable ends at about 11.55 s. Release on the next server tick.
    public static final int DURATION = 232;
    static final double HIT_PHASE = .628505;
    private static final double SKILL_TICKS = (DURATION - NAME_START_TICK) / HIT_PHASE;
    static final int AFTER_HIT_TICKS = (int)Math.ceil((1 - HIT_PHASE) * SKILL_TICKS);

    /** Skill03 progress anchored to the beginning of the call and the authoritative release tick. */
    public static double skill03Phase(double chantTicks) {
        return (chantTicks - NAME_START_TICK) / SKILL_TICKS;
    }
    private static final Map<UUID, Cast> ACTIVE = new HashMap<>();

    private record Cast(ServerPlayer player, ItemStack sword, Sync sync) {}

    private record Sync(UUID owner, ResourceLocation dimension, InteractionHand hand, long start, boolean active, boolean completed) {
        Sync stopped(boolean success) { return new Sync(owner, dimension, hand, start, false, success); }

        void encode(FriendlyByteBuf buf) {
            buf.writeUUID(owner);
            buf.writeResourceLocation(dimension);
            buf.writeEnum(hand);
            buf.writeLong(start);
            buf.writeBoolean(active);
            buf.writeBoolean(completed);
        }

        static Sync decode(FriendlyByteBuf buf) {
            return new Sync(buf.readUUID(), buf.readResourceLocation(), buf.readEnum(InteractionHand.class),
                    buf.readLong(), buf.readBoolean(), buf.readBoolean());
        }
    }

    public static void registerNetwork() {
        SevenNetwork.CHANNEL.registerMessage(13, Sync.class, Sync::encode, Sync::decode, (state, supplier) -> {
            var ctx = supplier.get();
            ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> Client.receive(state)));
            ctx.setPacketHandled(true);
        }, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
    }

    @Nullable
    private static Sync state(Player player) {
        if (player.level().isClientSide) return Client.state(player);
        Cast cast = ACTIVE.get(player.getUUID());
        return cast != null && cast.player == player ? cast.sync : null;
    }

    @Nullable
    public static InteractionHand activeHand(Player player) {
        Sync state = state(player);
        return state == null ? null : state.hand;
    }

    public static int elapsed(Player player) {
        Sync state = state(player);
        return state == null ? -1 : (int)Math.max(0, Math.min(DURATION, player.level().getGameTime() - state.start));
    }

    static void begin(ServerPlayer player, ItemStack sword, InteractionHand hand) {
        if (!player.isAlive() || player.isSpectator() || AvalonItem.isSheltered(player)
                || player.getItemInHand(hand) != sword || player.getCooldowns().isOnCooldown(sword.getItem())) return;
        var sync = new Sync(player.getUUID(), player.level().dimension().location(), hand, player.level().getGameTime(), true, false);
        if (ACTIVE.putIfAbsent(player.getUUID(), new Cast(player, sword, sync)) != null) return;
        send(player, sync);
        player.displayClientMessage(Component.translatable("message.starcrowndoll.excalibur.chant"), true);
    }

    static void cancel(ServerPlayer player) {
        Cast cast = ACTIVE.get(player.getUUID());
        if (cast != null && cast.player == player) end(cast, false);
    }

    private static void end(Cast cast, boolean completed) {
        ACTIVE.remove(cast.player.getUUID(), cast);
        send(cast.player, cast.sync.stopped(completed));
    }

    private static void send(ServerPlayer owner, Sync sync) {
        SevenNetwork.CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> owner), sync);
    }

    @SubscribeEvent
    public static void serverTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || ACTIVE.isEmpty()) return;
        for (Cast cast : List.copyOf(ACTIVE.values())) {
            var player = cast.player;
            if (ACTIVE.get(player.getUUID()) != cast) continue;
            if (player.isRemoved() || !player.isAlive() || player.isSpectator() || AvalonItem.isSheltered(player)
                    || !player.level().dimension().location().equals(cast.sync.dimension)
                    || player.getItemInHand(cast.sync.hand) != cast.sword || ExcaliburItem.isConcealed(cast.sword)
                    || player.isUsingItem() && player.getUsedItemHand() != cast.sync.hand
                    || player.getCooldowns().isOnCooldown(cast.sword.getItem())) {
                end(cast, false);
            } else if (player.level().getGameTime() - cast.sync.start >= DURATION) {
                end(cast, true);
                if (player.getUseItem() == cast.sword) player.stopUsingItem();
                ExcaliburItem.fire(player, cast.sword, false);
            }
        }
    }

    @SubscribeEvent
    public static void tracking(PlayerEvent.StartTracking event) {
        if (!(event.getEntity() instanceof ServerPlayer viewer) || !(event.getTarget() instanceof ServerPlayer owner)) return;
        Cast cast = ACTIVE.get(owner.getUUID());
        if (cast != null) SevenNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> viewer), cast.sync);
    }

    @SubscribeEvent
    public static void stopTracking(PlayerEvent.StopTracking event) {
        if (!(event.getEntity() instanceof ServerPlayer viewer) || !(event.getTarget() instanceof ServerPlayer owner)) return;
        Cast cast = ACTIVE.get(owner.getUUID());
        if (cast != null) SevenNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> viewer), cast.sync.stopped(false));
    }

    @SubscribeEvent
    public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) cancel(player);
    }

    @SubscribeEvent
    public static void dimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) cancel(player);
    }

    @SubscribeEvent
    public static void shutdown(ServerStoppedEvent event) { ACTIVE.clear(); }

    @Mod.EventBusSubscriber(modid = StarcrownDoll.ID, value = Dist.CLIENT)
    public static final class Client {
        private static final Map<UUID, Sync> VIEW = new HashMap<>();
        private static final Map<UUID, FollowThrough> FINISHING = new HashMap<>();
        static final int RECOVERY_TICKS = 18;
        record SwordStroke(InteractionHand hand, float tick) {}
        private record FollowThrough(Player bearer, ItemStack sword, InteractionHand hand, long hit) {
            boolean showing(Player player) {
                return player == bearer && player.isAlive() && !player.isRemoved()
                        && player.getItemInHand(hand) == sword && !ExcaliburItem.isConcealed(sword)
                        && player.level().getGameTime() - hit < RECOVERY_TICKS;
            }
        }
        private static InteractionHand waitForKeyUp;

        static boolean hasActiveCasts() { return !VIEW.isEmpty(); }

        static void receive(Sync sync) {
            var mc = net.minecraft.client.Minecraft.getInstance();
            var level = mc.level;
            if (level == null || !level.dimension().location().equals(sync.dimension)) return;
            Sync current = VIEW.get(sync.owner);
            if (current != null && current.start > sync.start) return;
            if (sync.active) {
                if (sync.equals(current) || level.getGameTime() - sync.start > DURATION) return;
                FINISHING.remove(sync.owner);
                VIEW.put(sync.owner, sync);
            } else {
                if (current == null || current.start != sync.start) return;
                VIEW.remove(sync.owner);
                var bearer = level.getPlayerByUUID(sync.owner);
                if (sync.completed && bearer != null && bearer.getItemInHand(sync.hand).getItem() instanceof ExcaliburItem) {
                    FINISHING.put(sync.owner, new FollowThrough(bearer, bearer.getItemInHand(sync.hand), sync.hand,
                            Math.min(level.getGameTime(), sync.start + DURATION)));
                }
                if (sync.completed && mc.player != null && sync.owner.equals(mc.player.getUUID())
                        && mc.options.keyUse.isDown() && mc.player.getItemInHand(sync.hand).getItem() instanceof ExcaliburItem) {
                    waitForKeyUp = sync.hand;
                    if (mc.player.isUsingItem() && mc.player.getUsedItemHand() == sync.hand) mc.player.stopUsingItem();
                }
            }
            ExcaliburChantAudio.sync(sync.owner, sync.dimension, sync.start, sync.active, sync.completed);
        }

        private static Sync state(Player player) {
            Sync sync = VIEW.get(player.getUUID());
            if (sync == null || !sync.dimension.equals(player.level().dimension().location())) return null;
            return player.level().getGameTime() - sync.start <= DURATION ? sync : null;
        }

        @Nullable
        static SwordStroke swordStroke(Player player, float partial) {
            var sync = VIEW.get(player.getUUID());
            if (sync != null && sync.dimension.equals(player.level().dimension().location()) && player.isAlive()) {
                var held = player.getItemInHand(sync.hand);
                if (!(held.getItem() instanceof ExcaliburItem) || ExcaliburItem.isConcealed(held)) return null;
                float age = player.level().getGameTime() - sync.start + partial;
                // Hold the contact pose if the completion packet is a frame late.
                return age >= LAST_LINE_TICK ? new SwordStroke(sync.hand, Math.min(DURATION, age)) : null;
            }
            var tail = FINISHING.get(player.getUUID());
            return tail != null && tail.showing(player)
                    ? new SwordStroke(tail.hand, DURATION + player.level().getGameTime() - tail.hit + partial) : null;
        }

        @SubscribeEvent
        public static void input(net.minecraftforge.client.event.InputEvent.InteractionKeyMappingTriggered event) {
            if (event.isUseItem() && waitForKeyUp != null) {
                event.setSwingHand(false);
                event.setCanceled(true);
            }
        }

        @SubscribeEvent
        public static void tick(TickEvent.ClientTickEvent event) {
            if (event.phase != TickEvent.Phase.START) return;
            var mc = net.minecraft.client.Minecraft.getInstance();
            FINISHING.values().removeIf(tail -> mc.level != tail.bearer.level() || !tail.showing(tail.bearer));
            if (waitForKeyUp == null) return;
            if (mc.player == null || !mc.options.keyUse.isDown()
                    || !(mc.player.getItemInHand(waitForKeyUp).getItem() instanceof ExcaliburItem)) waitForKeyUp = null;
        }

        @SubscribeEvent
        public static void unload(LevelEvent.Unload event) {
            if (event.getLevel().isClientSide()) {
                VIEW.clear();
                FINISHING.clear();
                waitForKeyUp = null;
            }
        }
    }
}
