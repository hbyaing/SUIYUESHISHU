package dev.starcrown;

import com.mojang.datafixers.util.Pair;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandlerModifiable;
import net.minecraftforge.items.SlotItemHandler;

public final class EquipmentSlots {
    public static final int START = 46, END = 49;
    public record CreativeClick(int slot, int button, net.minecraft.world.inventory.ClickType type, ItemStack carried) {
        public static void register() {
            SevenNetwork.CHANNEL.registerMessage(6, CreativeClick.class,
                (s,b) -> { b.writeVarInt(s.slot); b.writeVarInt(s.button); b.writeEnum(s.type); b.writeItem(s.carried); },
                b -> new CreativeClick(b.readVarInt(),b.readVarInt(),b.readEnum(net.minecraft.world.inventory.ClickType.class),b.readItem()),
                (s,supplier) -> { var context=supplier.get(); context.enqueueWork(() -> {
                    var player=context.getSender();
                    if(CuriosEquipment.loaded()||player==null||!player.isCreative()||player.containerMenu!=player.inventoryMenu||s.slot<5||s.slot>=49||s.button<0||s.button>8)return;
                    player.inventoryMenu.setCarried(s.carried.copy()); player.inventoryMenu.clicked(s.slot,s.button,s.type,player); player.inventoryMenu.broadcastChanges();
                    // Vanilla ignores individual carried-stack updates in the creative screen.
                    player.inventoryMenu.sendAllDataToRemote();
                });context.setPacketHandled(true); }, java.util.Optional.of(net.minecraftforge.network.NetworkDirection.PLAY_TO_SERVER));
        }
    }
    public static final class SideSlot extends SlotItemHandler {
        public boolean visible = true;
        public final int kind;
        public SideSlot(Player player, int kind) { super(new Deferred(player, kind), kind == 0 ? 0 : kind - 1, -28, 28 + kind * 22); this.kind = kind; }
        @Override public boolean isActive() { return visible; }
        @Override public Pair<ResourceLocation, ResourceLocation> getNoItemIcon() { return Pair.of(InventoryMenu.BLOCK_ATLAS, kind == 0 ? DivinityInventory.ICON : AsWrittenInventory.ICON); }
    }
    private record Deferred(Player owner, int kind) implements IItemHandlerModifiable {
        private IItemHandlerModifiable target() {
            return kind == 0 ? owner.getCapability(DivinityInventory.CAPABILITY).orElse(null) : owner.getCapability(AsWrittenInventory.CAPABILITY).orElse(null);
        }
        @Override public int getSlots() { return kind == 0 ? 1 : 2; }
        @Override public ItemStack getStackInSlot(int slot) { var t = target(); return t == null ? ItemStack.EMPTY : t.getStackInSlot(slot); }
        @Override public void setStackInSlot(int slot, ItemStack stack) { var t = target(); if (t != null) t.setStackInSlot(slot, stack); }
        @Override public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) { var t = target(); return t == null ? stack : t.insertItem(slot, stack, simulate); }
        @Override public ItemStack extractItem(int slot, int amount, boolean simulate) { var t = target(); return t == null ? ItemStack.EMPTY : t.extractItem(slot, amount, simulate); }
        @Override public int getSlotLimit(int slot) { return 1; }
        @Override public boolean isItemValid(int slot, ItemStack stack) { var t = target(); return t != null && t.isItemValid(slot, stack); }
    }
}
