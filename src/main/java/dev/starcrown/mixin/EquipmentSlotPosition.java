package dev.starcrown.mixin;
import dev.starcrown.AsWrittenClient;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.*;
@Mixin(Slot.class)
public abstract class EquipmentSlotPosition implements AsWrittenClient.Position {
    public void starcrown$position(int x, int y) {
        net.minecraftforge.fml.util.ObfuscationReflectionHelper.setPrivateValue(Slot.class,(Slot)(Object)this,x,"f_40220_");
        net.minecraftforge.fml.util.ObfuscationReflectionHelper.setPrivateValue(Slot.class,(Slot)(Object)this,y,"f_40221_");
    }
}
