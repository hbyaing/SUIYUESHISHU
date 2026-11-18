package dev.starcrown.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.starcrown.ExcaliburGrip;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntityRenderer.class)
public abstract class ExcaliburPlayerGrip {
    // Native first-person hands call PlayerModel.setupAnim directly. Only the full entity pass gets the second grip.
    @Inject(method = {
            "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            "m_7392_(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V"
    }, at = {
            @At(value = "INVOKE", target = "Lnet/minecraft/client/model/EntityModel;setupAnim(Lnet/minecraft/world/entity/Entity;FFFFF)V", shift = At.Shift.AFTER),
            @At(value = "INVOKE", target = "Lnet/minecraft/client/model/EntityModel;m_6973_(Lnet/minecraft/world/entity/Entity;FFFFF)V", shift = At.Shift.AFTER)
    }, remap = false, require = 1, allow = 1)
    private void alignExcalibur(LivingEntity entity, float yaw, float partial, PoseStack pose, MultiBufferSource buffers,
                                int light, CallbackInfo ci) {
        if (entity instanceof AbstractClientPlayer player &&
                ((LivingEntityRenderer<?, ?>)(Object)this).getModel() instanceof PlayerModel<?> model) {
            ExcaliburGrip.settle(model, player, partial);
        }
    }
}
