package dev.starcrown.mixin;
import dev.starcrown.AsWrittenClient;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin({InventoryScreen.class,CreativeModeInventoryScreen.class})
public abstract class InventorySidebar {
    @Inject(method={"hasClickedOutside(DDIII)Z","m_7467_(DDIII)Z"},at=@At("HEAD"),cancellable=true,remap=false,require=1)
    private void sidebarInside(double x,double y,int left,int top,int button,CallbackInfoReturnable<Boolean> ci) {
        if(AsWrittenClient.contains((net.minecraft.client.gui.screens.Screen)(Object)this,x,y))ci.setReturnValue(false);
    }
}
