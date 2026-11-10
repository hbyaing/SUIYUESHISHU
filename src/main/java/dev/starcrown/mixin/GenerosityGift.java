package dev.starcrown.mixin;
import dev.starcrown.YuleGenerosity;
import net.minecraft.world.entity.item.ItemEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
@Mixin(ItemEntity.class)
public abstract class GenerosityGift {
 @Inject(method={"isMergable()Z","m_32070_()Z"},at=@At("HEAD"),cancellable=true,remap=false,require=1)
 private void uniqueGift(CallbackInfoReturnable<Boolean> ci){var data=((ItemEntity)(Object)this).getPersistentData();if(data.hasUUID(YuleGenerosity.DONOR)||data.hasUUID(YuleGenerosity.CALM_DONOR))ci.setReturnValue(false);}
}
