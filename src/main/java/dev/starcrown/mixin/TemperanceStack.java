package dev.starcrown.mixin;
import dev.starcrown.YuleTemperance;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
@Mixin(ItemStack.class)
public abstract class TemperanceStack {
 @Inject(method={"hurtAndBreak(ILnet/minecraft/world/entity/LivingEntity;Ljava/util/function/Consumer;)V","m_41622_(ILnet/minecraft/world/entity/LivingEntity;Ljava/util/function/Consumer;)V"},at=@At("HEAD"),cancellable=true,remap=false,require=1)
 private void unbroken(int n,LivingEntity e,java.util.function.Consumer<LivingEntity> action,CallbackInfo c){if(YuleTemperance.equipped((ItemStack)(Object)this,e))c.cancel();}
 @Inject(method={"hurt(ILnet/minecraft/util/RandomSource;Lnet/minecraft/server/level/ServerPlayer;)Z","m_220157_(ILnet/minecraft/util/RandomSource;Lnet/minecraft/server/level/ServerPlayer;)Z"},at=@At("HEAD"),cancellable=true,remap=false,require=1)
 private void hurt(int n,RandomSource r,ServerPlayer p,CallbackInfoReturnable<Boolean> c){if(YuleTemperance.equipped((ItemStack)(Object)this,p)||YuleTemperance.durable((ItemStack)(Object)this)||n>0&&dev.starcrown.CalmYule.saveDurability(p,(ItemStack)(Object)this,r))c.setReturnValue(false);}
 @ModifyVariable(method={"setDamageValue(I)V","m_41721_(I)V"},at=@At("HEAD"),argsOnly=true,remap=false,require=1)
 private int damage(int n){var s=(ItemStack)(Object)this;return n>s.getDamageValue()&&YuleTemperance.durable(s)?s.getDamageValue():n;}
}
