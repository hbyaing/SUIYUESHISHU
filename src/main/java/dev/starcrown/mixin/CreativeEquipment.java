package dev.starcrown.mixin;
import dev.starcrown.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.ClickType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(CreativeModeInventoryScreen.class)
public abstract class CreativeEquipment {
    @Inject(method={"slotClicked(Lnet/minecraft/world/inventory/Slot;IILnet/minecraft/world/inventory/ClickType;)V","m_6597_(Lnet/minecraft/world/inventory/Slot;IILnet/minecraft/world/inventory/ClickType;)V"},at=@At("HEAD"),cancellable=true,remap=false,require=1)
    private void equipmentClick(Slot slot,int index,int button,ClickType type,CallbackInfo ci) {
        if(CuriosEquipment.loaded()||slot==null||!(((Object)this) instanceof CreativeModeInventoryScreen screen)||!screen.isInventoryOpen())return;
        var target=AsWrittenClient.unwrap(slot); var mc=Minecraft.getInstance();
        if(target instanceof EquipmentSlots.SideSlot || type==ClickType.QUICK_MOVE&&(DollAuthority.doll(target.getItem())||AsWrittenInventory.accepts(target.getItem()))) {
            SevenNetwork.CHANNEL.sendToServer(new EquipmentSlots.CreativeClick(target.index,button,type,mc.player.inventoryMenu.getCarried().copy()));ci.cancel();
        }
    }
}
