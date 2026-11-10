package dev.starcrown;

import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraftforge.common.capabilities.*;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.items.ItemStackHandler;

/** The mod owns its equipment data, independently of any accessory API. */
@Mod.EventBusSubscriber(modid = StarcrownDoll.ID)
public final class AsWrittenInventory {
    public static final int SIZE = 2;
    public static final ResourceLocation ID = new ResourceLocation(StarcrownDoll.ID, "as_written");
    public static final ResourceLocation ICON = new ResourceLocation(StarcrownDoll.ID, "slot/empty_as_written_slot");
    public static final Capability<Contents> CAPABILITY = CapabilityManager.get(new CapabilityToken<>() {});
    public static final TagKey<Item> ACCESSORIES = ItemTags.create(new ResourceLocation(StarcrownDoll.ID, "as_written_accessories"));
    public static final TagKey<Item> LEGACY_TAG = ItemTags.create(new ResourceLocation("curios", "starcrowndoll_as_written"));
    public static void init(IEventBus bus) {
        bus.addListener((RegisterCapabilitiesEvent event) -> event.register(Contents.class));
    }

    public static Contents get(Player player) {
        return player.getCapability(CAPABILITY).resolve().orElseThrow(() -> new IllegalStateException("Missing As Written inventory"));
    }

    public static boolean accepts(ItemStack stack) {
        return !stack.isEmpty() && (stack.is(ACCESSORIES) || stack.is(LEGACY_TAG));
    }

    public static final class Contents extends ItemStackHandler {
        public Contents() { super(SIZE); }
        @Override public int getSlotLimit(int slot) { return 1; }
        @Override public boolean isItemValid(int slot, ItemStack stack) { return accepts(stack); }
        @Override public void deserializeNBT(CompoundTag tag) {
            var fixed = tag.copy();
            fixed.putInt("Size", SIZE);
            super.deserializeNBT(fixed);
        }
    }

    private static final class Provider implements ICapabilitySerializable<CompoundTag> {
        private final Contents contents = new Contents();
        private LazyOptional<Contents> optional = LazyOptional.of(() -> contents);
        @Override public <T> LazyOptional<T> getCapability(Capability<T> capability, Direction side) {
            return capability == CAPABILITY ? optional.cast() : LazyOptional.empty();
        }
        @Override public CompoundTag serializeNBT() { return contents.serializeNBT(); }
        @Override public void deserializeNBT(CompoundTag tag) { contents.deserializeNBT(tag); }
        private void invalidate() {
            optional.invalidate();
            // The outer player capability gates access until Forge revives the old player for cloning.
            optional = LazyOptional.of(() -> contents);
        }
    }

    @SubscribeEvent
    public static void attach(AttachCapabilitiesEvent<net.minecraft.world.entity.Entity> event) {
        if (event.getObject() instanceof Player) {
            var provider = new Provider();
            event.addCapability(ID, provider);
            event.addListener(provider::invalidate);
        }
    }

    @SubscribeEvent
    public static void clone(PlayerEvent.Clone event) {
        var original = event.getOriginal();
        original.reviveCaps();
        try { get(event.getEntity()).deserializeNBT(get(original).serializeNBT()); }
        finally { original.invalidateCaps(); }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void drops(LivingDropsEvent event) {
        if (CuriosEquipment.loaded() || !(event.getEntity() instanceof ServerPlayer player) || player.isSpectator()
                || player.level().getGameRules().getBoolean(GameRules.RULE_KEEPINVENTORY)) return;
        var contents = get(player);
        for (int slot = 0; slot < SIZE; slot++) {
            var stack = contents.getStackInSlot(slot);
            if (stack.isEmpty()) continue;
            var drop = new ItemEntity(player.level(), player.getX(), player.getY() + .5, player.getZ(), stack.copy());
            drop.setDefaultPickUpDelay();
            event.getDrops().add(drop);
            contents.setStackInSlot(slot, ItemStack.EMPTY);
        }
    }
}
