package dev.starcrown.mixin;
import dev.starcrown.DivineProtection;
import net.minecraft.world.entity.*;
import net.minecraft.world.damagesource.DamageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
@Mixin(net.minecraft.server.level.ServerLevel.class)
public abstract class ServerLevelGuard {
 @Inject(method={"removePlayerImmediately(Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/world/entity/Entity$RemovalReason;)V","m_143261_(Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/world/entity/Entity$RemovalReason;)V"},at=@At("HEAD"),cancellable=true,remap=false,require=1)
 private void divineRemove(net.minecraft.server.level.ServerPlayer player,Entity.RemovalReason reason,CallbackInfo ci){if(DivineProtection.blockRemoval(player,reason,"ServerLevel.removePlayerImmediately"))ci.cancel();}
}
