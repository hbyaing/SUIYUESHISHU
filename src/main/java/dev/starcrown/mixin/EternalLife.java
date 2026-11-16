package dev.starcrown.mixin;

import dev.starcrown.EternalReturn;
import net.minecraft.world.entity.*;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.damagesource.DamageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class EternalLife {
    @Inject(method={"checkTotemDeathProtection(Lnet/minecraft/world/damagesource/DamageSource;)Z","m_21262_(Lnet/minecraft/world/damagesource/DamageSource;)Z"},
            at=@At("HEAD"),cancellable=true,remap=false,require=1)
    private void rewindBeforeTotem(DamageSource source,CallbackInfoReturnable<Boolean> result) {
        var entity=(LivingEntity)(Object)this;
        if(EternalReturn.rewind(entity,source))result.setReturnValue(true);
        else if(EternalReturn.causalLocked(entity))result.setReturnValue(false);
    }
    @ModifyVariable(method={"addEffect(Lnet/minecraft/world/effect/MobEffectInstance;Lnet/minecraft/world/entity/Entity;)Z",
            "m_147207_(Lnet/minecraft/world/effect/MobEffectInstance;Lnet/minecraft/world/entity/Entity;)Z",
            "forceAddEffect(Lnet/minecraft/world/effect/MobEffectInstance;Lnet/minecraft/world/entity/Entity;)V",
            "m_147215_(Lnet/minecraft/world/effect/MobEffectInstance;Lnet/minecraft/world/entity/Entity;)V"},
            at=@At("HEAD"),argsOnly=true,remap=false,require=2)
    private MobEffectInstance memoryScars(MobEffectInstance effect) {
        return EternalReturn.extendNegative((LivingEntity)(Object)this,effect);
    }
}
