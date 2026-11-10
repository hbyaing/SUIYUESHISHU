package dev.starcrown.mixin;

import dev.starcrown.CrownStrike;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Group;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class SupremeStrike {
    @Inject(method = {"getDamageAfterArmorAbsorb(Lnet/minecraft/world/damagesource/DamageSource;F)F",
            "m_21161_(Lnet/minecraft/world/damagesource/DamageSource;F)F"}, at = @At("HEAD"),
            remap = false, cancellable = true, require = 1)
    private void bypassArmor(DamageSource source, float amount, CallbackInfoReturnable<Float> cir) {
        if (CrownStrike.supremeStrike((LivingEntity) (Object) this, source)) cir.setReturnValue(amount);
    }

    @Group(name = "starcrown_bypass_resistance", min = 1, max = 1)
    @Redirect(method = "getDamageAfterMagicAbsorb(Lnet/minecraft/world/damagesource/DamageSource;F)F",
            at = @At(value = "INVOKE", ordinal = 1,
                    target = "Lnet/minecraft/world/damagesource/DamageSource;is(Lnet/minecraft/tags/TagKey;)Z"),
            remap = false, require = 0)
    private boolean bypassResistanceNamed(DamageSource source, TagKey<DamageType> tag) {
        return source.is(tag) || CrownStrike.supremeStrike((LivingEntity) (Object) this, source);
    }

    @Group(name = "starcrown_bypass_resistance", min = 1, max = 1)
    @Redirect(method = "m_6515_(Lnet/minecraft/world/damagesource/DamageSource;F)F",
            at = @At(value = "INVOKE", ordinal = 1,
                    target = "Lnet/minecraft/world/damagesource/DamageSource;m_269533_(Lnet/minecraft/tags/TagKey;)Z"),
            remap = false, require = 0)
    private boolean bypassResistanceSrg(DamageSource source, TagKey<DamageType> tag) {
        return source.is(tag) || CrownStrike.supremeStrike((LivingEntity) (Object) this, source);
    }
}
