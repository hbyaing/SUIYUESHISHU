package dev.starcrown.mixin;
import dev.starcrown.YuleTolerance;
import net.minecraft.world.entity.*;
import net.minecraft.world.effect.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
@Mixin(LivingEntity.class)
public abstract class ToleranceEffects {
 @Inject(method={"canBeSeenAsEnemy()Z","m_142066_()Z"},at=@At("HEAD"),cancellable=true,remap=false,require=1)
 private void friendlyNotEnemy(CallbackInfoReturnable<Boolean> ci){if(YuleTolerance.friendly((Entity)(Object)this))ci.setReturnValue(false);}
}
