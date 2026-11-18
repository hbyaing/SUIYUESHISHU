package dev.starcrown.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.starcrown.ExcaliburChargePose;
import dev.starcrown.ExcaliburGrip;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemInHandLayer.class)
public abstract class ExcaliburSupportItem {
    @Inject(method = {"renderArmWithItem", "m_117184_"}, at = @At("HEAD"), cancellable = true, remap = false, require = 1)
    private void freeChantHand(LivingEntity entity, ItemStack stack, ItemDisplayContext display, HumanoidArm arm,
                               PoseStack pose, MultiBufferSource buffers, int light, CallbackInfo ci) {
        if (!(entity instanceof AbstractClientPlayer player)) return;
        var chant = ExcaliburChargePose.chargingHand(player);
        var hand = arm == player.getMainArm() ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
        if (chant != null && chant != hand || ExcaliburGrip.stillGripping(player, arm)) ci.cancel();
    }

    @Inject(method = {"renderArmWithItem", "m_117184_"}, at = {
            @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;pushPose()V", shift = At.Shift.AFTER),
            @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;m_85836_()V", shift = At.Shift.AFTER)
    }, remap = false, require = 1, allow = 1)
    private void turnCastingWrist(LivingEntity entity, ItemStack stack, ItemDisplayContext display, HumanoidArm arm,
                                  PoseStack pose, MultiBufferSource buffers, int light, CallbackInfo ci) {
        if (entity instanceof AbstractClientPlayer player) ExcaliburGrip.applyWrist(player, arm, pose);
    }
}
