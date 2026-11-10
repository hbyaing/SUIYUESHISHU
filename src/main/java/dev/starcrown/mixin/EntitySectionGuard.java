package dev.starcrown.mixin;

import net.minecraft.world.level.entity.EntityAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(net.minecraft.world.level.entity.EntitySection.class)
public abstract class EntitySectionGuard {
 @Inject(method={"remove(Lnet/minecraft/world/level/entity/EntityAccess;)Z","m_188355_(Lnet/minecraft/world/level/entity/EntityAccess;)Z"},at=@At("HEAD"),cancellable=true,remap=false,require=1)
 private void genericSectionRemoval(EntityAccess entity,CallbackInfoReturnable<Boolean> ci){if(dev.starcrown.DivineAnchor.blockContainer(entity,"EntitySection.remove"))ci.setReturnValue(false);}
}
