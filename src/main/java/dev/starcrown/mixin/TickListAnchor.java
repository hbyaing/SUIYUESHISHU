package dev.starcrown.mixin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(net.minecraft.world.level.entity.EntityTickList.class)
public abstract class TickListAnchor {
 @Inject(method={"remove(Lnet/minecraft/world/entity/Entity;)V","m_156912_(Lnet/minecraft/world/entity/Entity;)V"},at=@At("HEAD"),cancellable=true,remap=false,require=1)
 private void anchorRemove(net.minecraft.world.entity.Entity entity,CallbackInfo ci){if(dev.starcrown.DivineAnchor.blockContainer(entity,"EntityTickList.remove"))ci.cancel();}
}
