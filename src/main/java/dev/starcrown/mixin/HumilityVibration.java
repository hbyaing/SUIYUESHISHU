package dev.starcrown.mixin;

import dev.starcrown.YuleHumility;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.gameevent.GameEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = {
        "net.minecraft.world.level.block.entity.SculkSensorBlockEntity$VibrationUser",
        "net.minecraft.world.level.block.entity.SculkShriekerBlockEntity$VibrationUser",
        "net.minecraft.world.entity.monster.warden.Warden$VibrationUser"
})
public abstract class HumilityVibration {
    @Inject(method = {
            "canReceiveVibration(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/gameevent/GameEvent;Lnet/minecraft/world/level/gameevent/GameEvent$Context;)Z",
            "m_280080_(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/gameevent/GameEvent;Lnet/minecraft/world/level/gameevent/GameEvent$Context;)Z"
    }, at = @At("HEAD"), cancellable = true, remap = false, require = 1)
    private void hideVibration(ServerLevel level, BlockPos pos, GameEvent event, GameEvent.Context context,
                               CallbackInfoReturnable<Boolean> result) {
        if (YuleHumility.quietSource(context.sourceEntity())) result.setReturnValue(false);
    }
}
