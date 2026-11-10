package dev.starcrown.mixin;

import dev.starcrown.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.damagesource.DamageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

/** Shared Forge entrypoints, not any particular item's implementation class. */
@Mixin(value=net.minecraftforge.common.ForgeHooks.class,remap=false)
public abstract class ForgeDefenseGuard {
 @Inject(method="onLivingHurt(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/damagesource/DamageSource;F)F",at=@At("RETURN"),cancellable=true,remap=false,require=1)
 private static void supremeHit(LivingEntity victim,DamageSource source,float amount,CallbackInfoReturnable<Float> ci){
  float resolved=ci.getReturnValueF(),empowered=CrownStrike.empowerSupreme(victim,source,resolved);
  if(empowered!=resolved)ci.setReturnValue(empowered);
 }
 @Inject(method="onLivingDeath(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/damagesource/DamageSource;)Z",at=@At("HEAD"),cancellable=true,remap=false,require=1)
 private static void genericDeath(LivingEntity victim,DamageSource source,CallbackInfoReturnable<Boolean> ci){if(DivineProtection.active(victim)&&DivineProtection.blockDeath(victim,"ForgeHooks.onLivingDeath"))ci.setReturnValue(true);}
 @Inject(method="onPlayerAttackTarget(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/entity/Entity;)Z",at=@At("HEAD"),cancellable=true,remap=false,require=1)
 private static void genericItemAttack(Player source,Entity target,CallbackInfoReturnable<Boolean> ci){if(DivineProtection.active(target)){DollAuthority.onAttack(source,target);DivineProtection.prevent(target,"ForgeHooks.onPlayerAttackTarget",0);ci.setReturnValue(false);}}
}
