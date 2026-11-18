package dev.starcrown.treasury;

import dev.starcrown.EaSkill;
import dev.starcrown.StarcrownDoll;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.event.entity.EntityTravelToDimensionEvent;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
import net.minecraftforge.event.entity.living.MobSpawnEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid=StarcrownDoll.ID)
public final class TreasuryItems {
    private static final String FULL_POWER = "TreasuryFullPower";
    static final int LOCK_START = 25;
    private static final int RELEASE_COOLDOWN = 15;
    private static final double REACH = 32.0;
    private static final double AIR_REACH = 12.0;
    private static final Map<UUID, GateHold> HELD_GATES = new HashMap<>();

    private record GateHold(ServerLevel level, InteractionHand hand, int slot, ItemStack stack,
                            boolean full, TreasuryNetwork.Cast initial, Map<TreasuryLayout.Gate, TreasuryLayout.Gate> neighbors,
                            List<TreasuryLayout.Gate> refills) {
        GateHold(ServerPlayer owner, InteractionHand hand, ItemStack stack, boolean full, TreasuryNetwork.Cast initial) {
            this(owner.serverLevel(), hand, owner.getInventory().selected, stack, full, initial, new HashMap<>(), new ArrayList<>());
        }

        boolean matches(ServerPlayer owner) {
            if (!owner.isAlive() || owner.isSpectator() || owner.level() != level || !owner.isUsingItem()
                    || owner.getUsedItemHand() != hand || owner.getUseItem() != stack
                    || owner.getItemInHand(hand) != stack || owner.getInventory().selected != slot) return false;
            if (stack.getItem() instanceof GateOfBabylon) return GateOfBabylon.full(stack) == full;
            return stack.is(StarcrownDoll.EA.get()) && EaSkill.selected(stack) == (full ? EaSkill.GATE_FULL : EaSkill.GATE_NORMAL);
        }

        TreasuryLayout.Gate neighbor(TreasuryLayout.Gate fired) {
            var known = neighbors.get(fired);
            if (known != null) return known;
            // Each lane alternates between two stable seats. Compute the second seat only on its first refill.
            var next = findNeighbor(fired);
            neighbors.put(fired, next);
            neighbors.put(next, fired);
            return next;
        }

        private TreasuryLayout.Gate findNeighbor(TreasuryLayout.Gate gate) {
            double angle = new SplittableRandom(initial.seed() ^ gate.hashCode()).nextDouble(2 * Math.PI);
            float depth = gate.z() + (angle < Math.PI ? .24f : -.24f);
            for (double shift = .84; shift >= .1; shift *= .5) {
                for (int i = 0; i < 12; i++) {
                    double theta = angle + i * Math.PI / 6;
                    float x = (float) (gate.x() + shift * Math.cos(theta));
                    float y = (float) (gate.y() + shift * Math.sin(theta));
                    float floor = Math.abs(x) < gate.radius() + .55f ? 2.3f + gate.radius() : 2.35f;
                    if (y < floor) continue;
                    var candidate = new TreasuryLayout.Gate(x, y, depth, gate.radius());
                    if (hasRoom(candidate, gate)) return candidate;
                }
            }
            return gate;
        }

        private boolean hasRoom(TreasuryLayout.Gate candidate, TreasuryLayout.Gate home) {
            for (var other : initial.gates()) {
                if (other.equals(home)) continue;
                if (!clearOf(candidate, other)) return false;
                var alternate = neighbors.get(other);
                if (alternate != null && !clearOf(candidate, alternate)) return false;
            }
            return true;
        }

        private boolean clearOf(TreasuryLayout.Gate a, TreasuryLayout.Gate b) {
            double dx = a.x() - b.x(), dy = a.y() - b.y();
            double gap = a.radius() + b.radius() + .04;
            if (dx * dx + dy * dy < gap * gap) return false;
            // Check the same frontal projection as the original array for every possible pair of seats.
            double eyeZ = full ? 21 : 12;
            double eyeY = 2.35 + (full ? 16.5 : 7.6) * .4;
            double sa = (eyeZ + 5.35) / (eyeZ - a.z()), sb = (eyeZ + 5.35) / (eyeZ - b.z());
            dx = a.x() * sa - b.x() * sb;
            dy = (a.y() - eyeY) * sa - (b.y() - eyeY) * sb;
            gap = a.radius() * sa + b.radius() * sb + .04;
            return dx * dx + dy * dy >= gap * gap;
        }
    }

    private TreasuryItems() {}

    private abstract static class TreasuryItem extends Item {
        TreasuryItem() {
            super(new Properties().stacksTo(1).fireResistant().rarity(Rarity.EPIC));
        }

        @Override
        public InteractionResult useOn(UseOnContext context) {
            Player player = context.getPlayer();
            if (player == null) return InteractionResult.PASS;
            return use(context.getLevel(), player, context.getHand()).getResult();
        }
    }

    public static final class GateOfBabylon extends TreasuryItem {
        @Override public int getUseDuration(ItemStack stack) { return Integer.MAX_VALUE; }

        @Override public InteractionResult onItemUseFirst(ItemStack stack, UseOnContext context) {
            return context.getPlayer() != null && !context.getPlayer().isShiftKeyDown() ? useOn(context) : InteractionResult.PASS;
        }

        private static boolean full(ItemStack stack) {
            return stack.hasTag() && stack.getTag().getBoolean(FULL_POWER);
        }

        private static Component mode(ItemStack stack) {
            return Component.translatable(full(stack) ? "message.starcrowndoll.gate_mode.full"
                    : "message.starcrowndoll.gate_mode.normal").withStyle(ChatFormatting.GOLD);
        }

        @Override
        public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
            ItemStack stack = player.getItemInHand(hand);
            if (!player.isShiftKeyDown()) return useGate(level, player, hand, full(stack));
            if (!canUse(player, this)) return InteractionResultHolder.fail(stack);
            if (player instanceof ServerPlayer owner) {
                stack.getOrCreateTag().putBoolean(FULL_POWER, !full(stack));
                owner.displayClientMessage(mode(stack), true);
                owner.getCooldowns().addCooldown(this, 6);
            }
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }

        @Override
        public void appendHoverText(ItemStack stack, Level level, List<Component> lines, TooltipFlag flag) {
            lines.add(mode(stack));
            lines.add(Component.translatable("tooltip.starcrowndoll.gate_of_babylon"));
        }
    }

    public static final class ChainsOfHeaven extends TreasuryItem {
        @Override
        public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
            return useChains(level, player, hand);
        }

        @Override public int getUseDuration(ItemStack stack) { return Integer.MAX_VALUE; }
        @Override public UseAnim getUseAnimation(ItemStack stack) { return UseAnim.NONE; }

        @Override
        public InteractionResult onItemUseFirst(ItemStack stack, UseOnContext context) {
            return useOn(context);
        }

        @Override
        public void appendHoverText(ItemStack stack, Level level, List<Component> lines, TooltipFlag flag) {
            lines.add(Component.translatable("tooltip.starcrowndoll.chains_of_heaven").withStyle(ChatFormatting.GOLD));
        }
    }

    private static boolean canUse(Player player, Item ability) {
        return player.isAlive() && !player.isSpectator() && !player.getCooldowns().isOnCooldown(ability);
    }

    public static InteractionResultHolder<ItemStack> useGate(Level level, Player player, InteractionHand hand, boolean full) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.isUsingItem()) {
            return player.getUsedItemHand() == hand && player.getUseItem() == stack
                    ? InteractionResultHolder.consume(stack) : InteractionResultHolder.fail(stack);
        }
        if (!canUse(player, StarcrownDoll.GATE_OF_BABYLON.get())) return InteractionResultHolder.fail(stack);
        player.startUsingItem(hand);
        if (!player.isUsingItem() || player.getUseItem() != stack) return InteractionResultHolder.fail(stack);
        if (player instanceof ServerPlayer owner) {
            Vec3 origin = owner.position();
            Vec3 forward = Vec3.directionFromRotation(0, owner.getYRot());
            var cast = TreasuryNetwork.cast(owner, full ? 1 : 0, origin, forward, -1, origin);
            HELD_GATES.put(owner.getUUID(), new GateHold(owner, hand, stack, full, cast));
            owner.getCooldowns().addCooldown(StarcrownDoll.GATE_OF_BABYLON.get(), TreasuryVolley.cycleTicks(full ? 100 : 20));
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    static void gateFired(ServerPlayer owner, TreasuryNetwork.Cast cast, TreasuryLayout.Gate gate) {
        var hold = HELD_GATES.get(owner.getUUID());
        if (hold != null && hold.initial().sessionId().equals(cast.sessionId()) && hold.matches(owner)) {
            hold.refills().add(hold.neighbor(gate));
        }
    }

    // Called after all volleys have advanced. Only actual launches in this tick can create replacements.
    static void finishGateTick(MinecraftServer server) {
        var entries = HELD_GATES.entrySet().iterator();
        while (entries.hasNext()) {
            var entry = entries.next();
            GateHold hold = entry.getValue();
            ServerPlayer owner = server.getPlayerList().getPlayer(entry.getKey());
            if (owner == null || !hold.matches(owner)) {
                entries.remove();
                if (owner != null && owner.getUseItem() == hold.stack()) owner.stopUsingItem();
            } else if (!hold.refills().isEmpty()) {
                TreasuryNetwork.refill(owner, hold.initial().mode(), List.copyOf(hold.refills()), hold.initial().sessionId());
                hold.refills().clear();
            }
        }
    }

    private static void stopHeldGate(Player player) {
        if (player instanceof ServerPlayer owner) {
            GateHold hold = HELD_GATES.remove(owner.getUUID());
            if (hold != null && owner.getUseItem() == hold.stack()) owner.stopUsingItem();
        }
    }

    @SubscribeEvent(receiveCanceled = true)
    public static void released(LivingEntityUseItemEvent.Stop event) {
        if (event.getEntity() instanceof ServerPlayer owner) {
            var hold = HELD_GATES.get(owner.getUUID());
            if (hold != null && event.getItem() == hold.stack()) HELD_GATES.remove(owner.getUUID());
        }
    }

    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event) { stopHeldGate(event.getEntity()); }
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent event) { stopHeldGate(event.getEntity()); }
    @SubscribeEvent public static void stopped(ServerStoppedEvent event) { HELD_GATES.clear(); }

    public static InteractionResultHolder<ItemStack> useChains(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        // Releasing the use key arms the next press, for both Ea and standalone chains.
        if (player.isUsingItem() && player.getUseItem() == stack) return InteractionResultHolder.consume(stack);
        Item ability = StarcrownDoll.CHAINS_OF_HEAVEN.get();
        if (!canUse(player, ability)) return InteractionResultHolder.fail(stack);
        if (player instanceof ServerPlayer owner) {
            int cooldown = TreasuryNetwork.releaseChains(owner) ? RELEASE_COOLDOWN : castChains(owner);
            owner.getCooldowns().addCooldown(ability, cooldown);
        }
        player.startUsingItem(hand);
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    private static int castChains(ServerPlayer owner) {
        Vec3 eye = owner.getEyePosition();
        Vec3 look = owner.getLookAngle();
        Vec3 lineEnd = eye.add(look.scale(REACH));
        Vec3 wall = owner.level().clip(new ClipContext(eye, lineEnd, ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, owner)).getLocation();
        AABB bounds = new AABB(eye, wall).inflate(1.0);
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(owner, eye, wall, bounds,
                entity -> entity instanceof LivingEntity && entity.isPickable() && entity.isAlive()
                        && !entity.isSpectator(), eye.distanceToSqr(wall));
        // Only the first visible entity may be selected; a bystander in front blocks target selection.
        LivingEntity target = hit != null && hit.getEntity() instanceof LivingEntity living && canBind(owner, living)
                ? living : null;
        Vec3 end = target == null ? eye.add(look.scale(Math.min(AIR_REACH, eye.distanceTo(wall))))
                : target.getBoundingBox().getCenter();
        TreasuryNetwork.cast(owner, 2, owner.position(), Vec3.directionFromRotation(0, owner.getYRot()),
                target == null ? -1 : target.getId(), end);
        return target == null ? TreasuryNetwork.CHAIN_TICKS : 4;
    }

    // At close range vanilla sends an entity interaction before attempting Item.use.
    @SubscribeEvent
    public static void chainInteraction(PlayerInteractEvent.EntityInteractSpecific event) {
        ItemStack stack = event.getItemStack();
        boolean eaPower = stack.is(StarcrownDoll.EA.get()) && !event.getEntity().isShiftKeyDown()
                && EaSkill.selected(stack) != EaSkill.ENUMA;
        boolean standaloneGate = stack.getItem() instanceof GateOfBabylon && !event.getEntity().isShiftKeyDown();
        if (!(stack.getItem() instanceof ChainsOfHeaven) && !eaPower && !standaloneGate) return;
        var result = stack.getItem().use(event.getLevel(), event.getEntity(), event.getHand());
        event.setCancellationResult(result.getResult());
        event.setCanceled(true);
    }

    static boolean canBind(ServerPlayer owner, LivingEntity target) {
        return target instanceof Mob mob && canMaintainBind(owner, mob)
                && (mob instanceof Enemy || mob.getType().getCategory() == MobCategory.MONSTER || mob.getTarget() == owner);
    }

    static boolean canMaintainBind(ServerPlayer owner, LivingEntity target) {
        return target instanceof Mob && target.isAlive() && !target.isSpectator() && !target.isInvulnerable()
                && !target.isPassenger() && !target.isAlliedTo(owner) && !owner.isAlliedTo(target);
    }

    private static Vec3 activeAnchor(TreasurySavedData data, LivingEntity entity) {
        return data.activeAnchor(entity.level().dimension(), entity.getUUID());
    }

    public static boolean isBound(LivingEntity entity) {
        return entity.level() instanceof ServerLevel level && activeAnchor(TreasurySavedData.get(level.getServer()), entity) != null;
    }

    @SubscribeEvent
    public static void tick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        MinecraftServer server = event.getServer();
        var data = TreasurySavedData.get(server);
        for (var binding : data.bindings()) maintain(server, data, binding);
    }

    private static void maintain(MinecraftServer server, TreasurySavedData data, TreasurySavedData.Binding binding) {
        var cast = binding.cast();
        ServerLevel level = server.getLevel(cast.dimension());
        if (level == null) return;
        ServerPlayer owner = server.getPlayerList().getPlayer(cast.caster());
        if (owner != null && owner.isDeadOrDying()) {
            TreasuryNetwork.endBinding(server, cast.id());
            return;
        }
        // A missing entity may simply be saved in an unloaded chunk. Looking it up by UUID does not load that chunk.
        Entity loaded = cast.target().map(level::getEntity).orElse(null);
        if (loaded == null) return;
        if (!(loaded instanceof Mob target) || !target.isAlive()) {
            TreasuryNetwork.endBinding(server, cast.id());
            return;
        }
        long age = level.getGameTime() - cast.start();
        if (binding.anchor() == null && owner != null && !canBind(owner, target)) {
            TreasuryNetwork.endBinding(server, cast.id());
            return;
        }
        if (age < LOCK_START) return;
        Vec3 anchor = binding.anchor();
        if (anchor == null) {
            Vec3 shared = activeAnchor(data, target);
            anchor = shared == null ? target.position() : shared;
            data.setAnchor(cast.id(), anchor);
        }
        // The target keeps its original AI and gravity flags. Only pin it while its chunk is active.
        if (target.position().distanceToSqr(anchor) > 1.0e-8 && level.hasChunkAt(BlockPos.containing(anchor))) {
            target.setPos(anchor);
        }
        if (target.getDeltaMovement().lengthSqr() > 1.0e-8) {
            target.setDeltaMovement(Vec3.ZERO);
            target.hurtMarked = true;
        }
        target.fallDistance = 0;
    }

    @SubscribeEvent
    public static void preventBoundDespawn(MobSpawnEvent.AllowDespawn event) {
        if (isBound(event.getEntity())) event.setResult(Event.Result.DENY);
    }

    @SubscribeEvent
    public static void preventBoundTravel(EntityTravelToDimensionEvent event) {
        if (event.getEntity() instanceof Mob target && isBound(target)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void removed(EntityLeaveLevelEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !(event.getEntity() instanceof LivingEntity entity)) return;
        Entity.RemovalReason reason = entity.getRemovalReason();
        if (reason != Entity.RemovalReason.KILLED && reason != Entity.RemovalReason.CHANGED_DIMENSION
                && !(reason == Entity.RemovalReason.DISCARDED && !(entity instanceof Player))) return;
        UUID removed = entity.getUUID();
        for (var binding : TreasurySavedData.get(level.getServer()).bindings()) {
            var cast = binding.cast();
            boolean ownerDied = reason == Entity.RemovalReason.KILLED && cast.caster().equals(removed);
            boolean targetGone = cast.dimension().equals(level.dimension()) && cast.target().filter(removed::equals).isPresent();
            if (ownerDied || targetGone) {
                TreasuryNetwork.endBinding(level.getServer(), cast.id());
            }
        }
    }
}
