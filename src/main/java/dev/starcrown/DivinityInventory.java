package dev.starcrown;

import java.util.Optional;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraftforge.common.capabilities.*;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.network.*;

/** One source of effective divinity, backed by Curios when present. */
@Mod.EventBusSubscriber(modid = StarcrownDoll.ID)
public final class DivinityInventory {
    public static final int ANCHOR_SLOT = -100;
    public static final ResourceLocation ID = new ResourceLocation(StarcrownDoll.ID, "divinity");
    public static final ResourceLocation ICON = new ResourceLocation(StarcrownDoll.ID, "slot/empty_divinity_slot");
    public static final Capability<Contents> CAPABILITY = CapabilityManager.get(new CapabilityToken<>() {});
    public static void init(IEventBus bus) {
        bus.addListener((RegisterCapabilitiesEvent event) -> event.register(Contents.class));
        bus.addListener((net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent event) -> event.enqueueWork(CuriosEquipment::init));
    }
    public static Contents own(Player player) { return player.getCapability(CAPABILITY).orElseThrow(IllegalStateException::new); }
    public static ItemStack stack(Player player) {
        if (CuriosEquipment.loaded()) return CuriosEquipment.stack(player);
        return player.getCapability(CAPABILITY).map(c -> c.getStackInSlot(0)).orElse(ItemStack.EMPTY);
    }
    public static ItemStack equipped(Player player) {
        var head = player.getItemBySlot(EquipmentSlot.HEAD);
        var god = stack(player);
        // Corrupt/external dual writes never activate two divinities, even before reconciliation.
        if (head.is(StarcrownDoll.ITEM.get())) return head;
        return DollAuthority.doll(god) ? god : head;
    }
    public static boolean canEquipHead(Player player, ItemStack next) { return !DollAuthority.doll(next) || !DollAuthority.doll(stack(player)); }
    public static boolean canEquipDivinity(Player player, ItemStack next) {
        return DollAuthority.doll(next) && !DollAuthority.doll(player.getItemBySlot(EquipmentSlot.HEAD));
    }
    public static void force(Player player, ItemStack stack) {
        if (CuriosEquipment.loaded()) CuriosEquipment.force(player, stack);
        else own(player).rawSet(stack);
    }
    public static final class Contents extends ItemStackHandler {
        private final Player owner;
        public boolean curiosBackup;
        public boolean usedCurios;
        public Contents(Player owner) { super(1); this.owner = owner; }
        @Override public int getSlotLimit(int slot) { return 1; }
        @Override public boolean isItemValid(int slot, ItemStack stack) { return canEquipDivinity(owner, stack); }
        @Override public void setStackInSlot(int slot, ItemStack stack) {
            if (!owner.level().isClientSide && !stack.isEmpty() && !isItemValid(slot, stack)) return;
            if (!owner.level().isClientSide && DivineAnchor.blockSlot(owner, ANCHOR_SLOT, stack)) return;
            super.setStackInSlot(slot, stack);
        }
        @Override public ItemStack extractItem(int slot, int amount, boolean simulate) {
            if (!simulate && !owner.level().isClientSide && DivineAnchor.blockSlot(owner, ANCHOR_SLOT, ItemStack.EMPTY)) return ItemStack.EMPTY;
            return super.extractItem(slot, amount, simulate);
        }
        public void rawSet(ItemStack stack) { super.setStackInSlot(0, stack); }
        @Override public CompoundTag serializeNBT() { var tag = super.serializeNBT(); tag.putBoolean("CuriosBackup", curiosBackup); tag.putBoolean("UsedCurios",usedCurios); return tag; }
        @Override public void deserializeNBT(CompoundTag tag) {
            var fixed = tag.copy(); fixed.putInt("Size", 1); super.deserializeNBT(fixed); curiosBackup = tag.getBoolean("CuriosBackup"); usedCurios = tag.getBoolean("UsedCurios");
        }
    }
    private static final class Provider implements ICapabilitySerializable<CompoundTag> {
        private final Contents contents;
        private LazyOptional<Contents> optional;
        Provider(Player owner) { contents = new Contents(owner); optional = LazyOptional.of(() -> contents); }
        @Override public <T> LazyOptional<T> getCapability(Capability<T> capability, Direction side) { return capability == CAPABILITY ? optional.cast() : LazyOptional.empty(); }
        @Override public CompoundTag serializeNBT() { return contents.serializeNBT(); }
        @Override public void deserializeNBT(CompoundTag tag) { contents.deserializeNBT(tag); }
        void invalidate() { optional.invalidate(); optional = LazyOptional.of(() -> contents); }
    }
    @SubscribeEvent public static void attach(AttachCapabilitiesEvent<Entity> event) {
        if (event.getObject() instanceof Player player) { var provider = new Provider(player); event.addCapability(ID, provider); event.addListener(provider::invalidate); }
    }
    @SubscribeEvent public static void clone(PlayerEvent.Clone event) {
        var original = event.getOriginal(); original.reviveCaps();
        try { own(event.getEntity()).deserializeNBT(own(original).serializeNBT()); }
        finally { original.invalidateCaps(); }
    }
    @SubscribeEvent(priority = EventPriority.LOWEST) public static void drops(LivingDropsEvent event) {
        if (CuriosEquipment.loaded() || !(event.getEntity() instanceof ServerPlayer player) || player.isSpectator()
                || player.level().getGameRules().getBoolean(GameRules.RULE_KEEPINVENTORY)) return;
        var contents = own(player); var stack = contents.getStackInSlot(0);
        if (!stack.isEmpty()) { var drop = new ItemEntity(player.level(), player.getX(), player.getY() + .5, player.getZ(), stack.copy()); drop.setDefaultPickUpDelay(); event.getDrops().add(drop); contents.rawSet(ItemStack.EMPTY); }
    }
    @SubscribeEvent(priority = EventPriority.HIGHEST) public static void tick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.START || !(event.player instanceof ServerPlayer player)) return;
        if (CuriosEquipment.loaded()) CuriosEquipment.migrate(player);
        else own(player).curiosBackup = false;
        var head = player.getItemBySlot(EquipmentSlot.HEAD); var god = stack(player);
        if (DollAuthority.doll(head) && DollAuthority.doll(god)) {
            ItemStack displaced;
            if (head.is(StarcrownDoll.ITEM.get())) { displaced = god.copy(); force(player, ItemStack.EMPTY); }
            else { displaced = head.copy(); player.getInventory().armor.set(3, ItemStack.EMPTY); }
            if (!player.getInventory().add(displaced)) player.drop(displaced, false);
            player.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.starcrowndoll.divinity_conflict"), true);
        }
    }
    private static final java.util.Map<ServerPlayer, ItemStack> SENT = new java.util.WeakHashMap<>();
    @SubscribeEvent public static void sync(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)) return;
        if (CuriosEquipment.loaded()) { CuriosEquipment.backup(player); return; }
        var stack = stack(player); var previous = SENT.get(player);
        if (previous == null || !ItemStack.matches(previous, stack)) {
            SENT.put(player, stack.copy()); SevenNetwork.CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> player), new State(player.getId(), stack.copy()));
        }
    }
    @SubscribeEvent public static void tracking(PlayerEvent.StartTracking event) {
        if (!CuriosEquipment.loaded() && event.getEntity() instanceof ServerPlayer viewer && event.getTarget() instanceof Player target)
            SevenNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> viewer), new State(target.getId(), stack(target).copy()));
    }
    @SubscribeEvent public static void changedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (!CuriosEquipment.loaded() && event.getEntity() instanceof ServerPlayer player) SENT.remove(player);
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) { if(CuriosEquipment.loaded())CuriosEquipment.backup(player); SENT.remove(player); }
    }
    public record State(int entity, ItemStack stack) {
        public static void register() {
            SevenNetwork.CHANNEL.registerMessage(5, State.class, (s,b) -> { b.writeVarInt(s.entity); b.writeItem(s.stack); }, b -> new State(b.readVarInt(), b.readItem()),
                (s,supplier) -> { var context = supplier.get(); context.enqueueWork(() -> net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT, () -> () -> AsWrittenClient.receive(s))); context.setPacketHandled(true); }, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        }
    }
}
