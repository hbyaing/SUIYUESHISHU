package dev.starcrown.mixin;
import dev.starcrown.AsWrittenClient;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.*;
@Mixin(targets="net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen$SlotWrapper")
public abstract class CreativeEquipmentSlot implements AsWrittenClient.Wrapped {
    @Unique private static java.lang.reflect.Field starcrown$wrappedTarget;
    public Slot starcrown$target() {
        if (starcrown$wrappedTarget == null)
            starcrown$wrappedTarget = net.minecraftforge.fml.util.ObfuscationReflectionHelper.findField(getClass(), "f_98655_");
        try { return (Slot) starcrown$wrappedTarget.get(this); }
        catch (IllegalAccessException error) { throw new IllegalStateException(error); }
    }
}
