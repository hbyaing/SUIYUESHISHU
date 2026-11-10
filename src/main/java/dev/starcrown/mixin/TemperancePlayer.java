package dev.starcrown.mixin;
import dev.starcrown.YuleTemperance;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
@Mixin(Player.class)
public abstract class TemperancePlayer {
 @Inject(method={"getProjectile(Lnet/minecraft/world/item/ItemStack;)Lnet/minecraft/world/item/ItemStack;","m_6298_(Lnet/minecraft/world/item/ItemStack;)Lnet/minecraft/world/item/ItemStack;"},at=@At("RETURN"),cancellable=true,remap=false,require=1)
 private void ammo(ItemStack weapon,CallbackInfoReturnable<ItemStack> c){if(YuleTemperance.active((Player)(Object)this)&&weapon.getItem() instanceof BowItem&&c.getReturnValue().isEmpty())c.setReturnValue(new ItemStack(Items.ARROW));}
 @Inject(method={"canEat(Z)Z","m_36391_(Z)Z"},at=@At("HEAD"),cancellable=true,remap=false,require=1)
 private void eatWhileFull(boolean alwaysEat,CallbackInfoReturnable<Boolean> c){if(YuleTemperance.active((Player)(Object)this))c.setReturnValue(true);}
}
