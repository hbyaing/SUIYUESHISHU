package dev.starcrown.mixin;

import dev.starcrown.*;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

@Mixin(InventoryMenu.class)
public abstract class InventoryEquipment extends AbstractContainerMenu {
    protected InventoryEquipment(MenuType<?> type, int id) { super(type, id); }
    @Inject(method = "<init>", at = @At("RETURN"), require = 1)
    private void addEquipment(Inventory inventory, boolean active, Player owner, CallbackInfo ci) {
        if (!CuriosEquipment.loaded()) for (int i = 0; i < 3; i++) addSlot(new EquipmentSlots.SideSlot(owner, i));
    }
    @Inject(method = {"quickMoveStack(Lnet/minecraft/world/entity/player/Player;I)Lnet/minecraft/world/item/ItemStack;", "m_7648_(Lnet/minecraft/world/entity/player/Player;I)Lnet/minecraft/world/item/ItemStack;"}, at = @At("HEAD"), cancellable = true, remap = false, require = 1)
    private void equipmentMove(Player player, int index, CallbackInfoReturnable<ItemStack> ci) {
        if (CuriosEquipment.loaded() || index < 0 || index >= slots.size()) return;
        var slot = slots.get(index); var stack = slot.getItem(); if (stack.isEmpty()) return;
        int start, end;
        if (slot instanceof EquipmentSlots.SideSlot) { start = 9; end = 45; }
        else if (index >= 9 && index < 45 && DollAuthority.doll(stack) && slots.get(46).mayPlace(stack)) { start = 46; end = 47; }
        else if (index >= 9 && index < 45 && AsWrittenInventory.accepts(stack)) { start = 47; end = 49; }
        else return;
        var original = stack.copy();
        if (!moveItemStackTo(stack, start, end, false)) { ci.setReturnValue(ItemStack.EMPTY); return; }
        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY); else slot.setChanged();
        slot.onTake(player, stack); ci.setReturnValue(original);
    }
}
