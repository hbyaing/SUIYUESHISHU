package dev.starcrown.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.starcrown.ExcaliburAirRefraction;
import dev.starcrown.ExcaliburChargePose;
import dev.starcrown.ExcaliburChantHands;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.InteractionHand;
import dev.starcrown.ExcaliburItem;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemInHandRenderer.class)
public abstract class ExcaliburAirHand {
    // Keep the support slot out of vanilla's held-item pass even if a hand-event listener uncancels it.
    @Inject(method = {"renderArmWithItem", "m_109371_"}, at = @At("HEAD"), cancellable = true, remap = false, require = 1)
    private void hideChargingSupportItem(AbstractClientPlayer player, float partial, float pitch, InteractionHand hand,
                                         float swing, ItemStack stack, float equip, PoseStack pose,
                                         MultiBufferSource buffers, int light, CallbackInfo info) {
        var swordHand = ExcaliburChargePose.chargingHand(player);
        if (swordHand != null && swordHand != hand) info.cancel();
    }

    @Inject(method = {"renderItem", "m_269530_"}, at = @At("HEAD"), remap = false, require = 1)
    private void refractInvisibleAir(LivingEntity bearer, ItemStack stack, ItemDisplayContext context, boolean left,
                                     PoseStack pose, MultiBufferSource buffers, int light, CallbackInfo info) {
        if (context.firstPerson() && stack.getItem() instanceof ExcaliburItem && ExcaliburItem.isConcealed(stack))
            ExcaliburAirRefraction.draw(bearer, stack, context, left, pose);
    }

    @Inject(method = {"renderItem", "m_269530_"}, at = @At("RETURN"), remap = false, require = 1)
    private void renderSwordHandAndLight(LivingEntity bearer, ItemStack stack, ItemDisplayContext context, boolean left,
                                    PoseStack pose, MultiBufferSource buffers, int light, CallbackInfo info) {
        if (context.firstPerson() && bearer instanceof LocalPlayer player && stack.getItem() instanceof ExcaliburItem) {
            ExcaliburChantHands.ordinaryGrip(pose, buffers, light, player, stack, context, left);
            ExcaliburChargePose.afterFirstPersonSword(pose, buffers, player, stack, context, left);
        }
    }
}
