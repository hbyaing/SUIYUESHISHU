package dev.starcrown.mixin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(net.minecraft.world.level.entity.EntityLookup.class)
public abstract class LookupAnchor {
 @Inject(method={"remove(Lnet/minecraft/world/level/entity/EntityAccess;)V","m_156822_(Lnet/minecraft/world/level/entity/EntityAccess;)V"},at=@At("HEAD"),cancellable=true,remap=false,require=1)
 private void anchorRemove(net.minecraft.world.level.entity.EntityAccess entity,CallbackInfo ci){if(dev.starcrown.DivineAnchor.blockContainer(entity,"EntityLookup.remove"))ci.cancel();}
}
