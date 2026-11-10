package dev.starcrown.mixin;
import dev.starcrown.DivineProtection;
import net.minecraft.world.entity.*;
import net.minecraft.world.damagesource.DamageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
@Mixin(net.minecraft.world.entity.player.Player.class)
public abstract class PlayerGuard {
 @Inject(method={"dropEquipment()V","m_5907_()V"},at=@At("HEAD"),cancellable=true,remap=false,require=1)
 private void nativeDeathEquipment(CallbackInfo ci){if(DivineProtection.active((Entity)(Object)this)){DivineProtection.prevent((Entity)(Object)this,"Player.dropEquipment",0);ci.cancel();}}
 @Inject(method={"hurt(Lnet/minecraft/world/damagesource/DamageSource;F)Z","m_6469_(Lnet/minecraft/world/damagesource/DamageSource;F)Z"},at=@At("HEAD"),cancellable=true,remap=false,require=1)
 private void nativePlayerDamage(DamageSource source,float amount,CallbackInfoReturnable<Boolean> ci){if(DivineProtection.blockDamage((LivingEntity)(Object)this,source,amount,"Player.hurt"))ci.setReturnValue(false);}
 @Inject(method={"actuallyHurt(Lnet/minecraft/world/damagesource/DamageSource;F)V","m_6475_(Lnet/minecraft/world/damagesource/DamageSource;F)V"},at=@At("HEAD"),cancellable=true,remap=false,require=1)
 private void nativePlayerHealthLoss(DamageSource source,float amount,CallbackInfo ci){if(DivineProtection.blockDamage((LivingEntity)(Object)this,source,amount,"Player.actuallyHurt"))ci.cancel();}
 @Inject(method={"canBeSeenAsEnemy()Z","m_142066_()Z"},at=@At("HEAD"),cancellable=true,remap=false,require=1)
 private void humilitySeen(CallbackInfoReturnable<Boolean> ci){if(dev.starcrown.YuleHumility.active((Entity)(Object)this))ci.setReturnValue(false);}
 @Inject(method={"attack(Lnet/minecraft/world/entity/Entity;)V","m_5706_(Lnet/minecraft/world/entity/Entity;)V"},at=@At("HEAD"),cancellable=true,remap=false,require=1)
 private void divineAttack(Entity target,CallbackInfo ci){if(dev.starcrown.YuleTolerance.active((Entity)(Object)this)){ci.cancel();return;}dev.starcrown.DollAuthority.onAttack((Entity)(Object)this,target);if(dev.starcrown.DivineCombat.reject(target,"Player.attack"))ci.cancel();}

 @Inject(method={"setItemSlot(Lnet/minecraft/world/entity/EquipmentSlot;Lnet/minecraft/world/item/ItemStack;)V","m_8061_(Lnet/minecraft/world/entity/EquipmentSlot;Lnet/minecraft/world/item/ItemStack;)V"},at=@At("HEAD"),cancellable=true,remap=false,require=1)
 private void divineHead(EquipmentSlot slot,net.minecraft.world.item.ItemStack stack,CallbackInfo ci){if(slot==EquipmentSlot.HEAD&&dev.starcrown.DivineAnchor.blockSlot((net.minecraft.world.entity.player.Player)(Object)this,39,stack))ci.cancel();}

 @Inject(method={"die(Lnet/minecraft/world/damagesource/DamageSource;)V","m_6667_(Lnet/minecraft/world/damagesource/DamageSource;)V"},at=@At("HEAD"),cancellable=true,remap=false,require=1)
 private void divineDeath(DamageSource source,CallbackInfo ci){if(DivineProtection.blockDeath((Entity)(Object)this,"PlayerGuard.die"))ci.cancel();}
}
