package dev.starcrown.mixin;
import dev.starcrown.YuleCharity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
@Mixin(ItemEntity.class)
public abstract class CharityItem {
 @Inject(method={"playerTouch(Lnet/minecraft/world/entity/player/Player;)V","m_6123_(Lnet/minecraft/world/entity/player/Player;)V"},at=@At("HEAD"),cancellable=true,remap=false,require=1)
 private void pickup(Player p,CallbackInfo c){if(!p.level().isClientSide)YuleCharity.clearLegacyMark(((ItemEntity)(Object)this).getItem());}
}
