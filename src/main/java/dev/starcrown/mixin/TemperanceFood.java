package dev.starcrown.mixin;
import dev.starcrown.YuleTemperance;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
@Mixin(FoodData.class)
public abstract class TemperanceFood {
 @Inject(method={"tick(Lnet/minecraft/world/entity/player/Player;)V","m_38710_(Lnet/minecraft/world/entity/player/Player;)V"},at={@At("HEAD"),@At("RETURN")},remap=false,require=1)
 private void tick(Player p,CallbackInfo c){YuleTemperance.fill(p);}
 @ModifyVariable(method={"setFoodLevel(I)V","m_38705_(I)V"},at=@At("HEAD"),argsOnly=true,remap=false,require=1)
 private int food(int n){return YuleTemperance.locked((FoodData)(Object)this)?20:n;}
 @ModifyVariable(method={"setSaturation(F)V","m_38717_(F)V"},at=@At("HEAD"),argsOnly=true,remap=false,require=1)
 private float saturation(float n){return YuleTemperance.locked((FoodData)(Object)this)?20:n;}
 @ModifyVariable(method={"setExhaustion(F)V","m_150378_(F)V"},at=@At("HEAD"),argsOnly=true,remap=false,require=1)
 private float exhaustion(float n){return YuleTemperance.locked((FoodData)(Object)this)?0:n;}
 @ModifyVariable(method={"addExhaustion(F)V","m_38703_(F)V"},at=@At("HEAD"),argsOnly=true,remap=false,require=1)
 private float calmExhaustion(float n){return YuleTemperance.exhaustion((FoodData)(Object)this,n);}
 @Inject(method={"addExhaustion(F)V","m_38703_(F)V"},at=@At("HEAD"),cancellable=true,remap=false,require=1)
 private void exhaust(float n,CallbackInfo c){if(YuleTemperance.locked((FoodData)(Object)this))c.cancel();}
}
