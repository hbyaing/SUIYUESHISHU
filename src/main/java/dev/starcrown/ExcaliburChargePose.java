package dev.starcrown;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Vector3f;

@Mod.EventBusSubscriber(modid = StarcrownDoll.ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class ExcaliburChargePose implements IClientItemExtensions {
    /** The visible hand during preparation and throughout a committed, server-timed invocation. */
    public static InteractionHand chantHand(Player player) {
        if (!player.isAlive()) return null;
        var hand = ExcaliburChant.activeHand(player);
        if (hand == null) {
            if (!player.isUsingItem()) return null;
            hand = player.getUsedItemHand();
            // After automatic release vanilla can keep the use key down until the user lifts it.
            if (player.getCooldowns().isOnCooldown(player.getItemInHand(hand).getItem())
                    || player.getTicksUsingItem() > ExcaliburChant.PREPARE_TICKS + 10) return null;
        }
        var stack = player.getItemInHand(hand);
        return stack.getItem() instanceof ExcaliburItem && !ExcaliburItem.isConcealed(stack) ? hand : null;
    }

    /** The other held item yields to either an accepted EX or an actively charging Invisible Air. */
    public static InteractionHand chargingHand(Player player) {
        var hand = chantHand(player);
        if (hand != null) return hand;
        if (player.isAlive() && player.isUsingItem() && player.getUseItem().getItem() instanceof ExcaliburItem
                && ExcaliburItem.isConcealed(player.getUseItem()))
            return player.getUsedItemHand();
        return null;
    }

    static float chargeAge(Player player, float partial) {
        int elapsed = ExcaliburChant.elapsed(player);
        return (elapsed >= 0 ? ExcaliburChant.PREPARE_TICKS + elapsed : player.getTicksUsingItem()) + partial;
    }

    @Override
    public HumanoidModel.ArmPose getArmPose(LivingEntity entity, InteractionHand hand, ItemStack stack) {
        var other = hand == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
        if (entity.getItemInHand(other).isEmpty()) return HumanoidModel.ArmPose.ITEM;
        return entity.isUsingItem() && entity.getUsedItemHand() == hand && ExcaliburItem.isConcealed(stack)
                ? HumanoidModel.ArmPose.BLOCK : null;
    }

    @Override
    public boolean applyForgeHandTransform(PoseStack pose, LocalPlayer player, HumanoidArm arm, ItemStack stack,
                                           float partial, float equip, float swing) {
        var hand = arm == player.getMainArm() ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
        var held = player.getItemInHand(hand);
        if (!(held.getItem() instanceof ExcaliburItem)) return false;
        boolean gatheringWind = ExcaliburItem.isConcealed(held) && player.isUsingItem() && player.getUsedItemHand() == hand;
        if (!gatheringWind && chantHand(player) != hand) return false;
        placeChargingSword(pose, player, arm, partial, equip);
        if (gatheringWind) {
            boolean left = arm == HumanoidArm.LEFT;
            var context = left ? ItemDisplayContext.FIRST_PERSON_LEFT_HAND : ItemDisplayContext.FIRST_PERSON_RIGHT_HAND;
            gatherOnBlade(pose, Minecraft.getInstance().renderBuffers().bufferSource(), player, held, context, left, partial);
        }
        return true;
    }

    /** Submit the luminous blade after the opaque first-person OBJ has been drawn. */
    public static void afterFirstPersonSword(PoseStack pose, MultiBufferSource buffers, LocalPlayer player,
                                             ItemStack stack, ItemDisplayContext context, boolean left) {
        var arm = left ? HumanoidArm.LEFT : HumanoidArm.RIGHT;
        var hand = arm == player.getMainArm() ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
        if (!ExcaliburItem.isConcealed(stack) && chantHand(player) == hand)
            gatherOnBlade(pose, buffers, player, stack, context, left, Minecraft.getInstance().getFrameTime());
    }

    static void placeChargingSword(PoseStack pose, LocalPlayer player, HumanoidArm arm, float partial, float equip) {
        int side = arm == HumanoidArm.LEFT ? -1 : 1;
        float raise = ease(chargeAge(player, partial) / 9F);
        var hand = arm == player.getMainArm() ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
        var sword = player.getItemInHand(hand);
        if (ExcaliburItem.isConcealed(sword)) {
            pose.translate(side * .56F * (1F - raise), -.52F + .05F * raise - .6F * equip, -.72F - .115F * raise);
            return;
        }
        pose.translate(side * .56F * (1F - raise), -.52F + .124F * raise - .6F * equip, -.72F - .214F * raise);
        // Turn the broad face toward the camera about the baked hilt center as the sword rises.
        boolean left = arm == HumanoidArm.LEFT;
        var context = left ? ItemDisplayContext.FIRST_PERSON_LEFT_HAND : ItemDisplayContext.FIRST_PERSON_RIGHT_HAND;
        var display = Minecraft.getInstance().getItemRenderer().getModel(sword, player.level(), player, player.getId())
                .getTransforms().getTransform(context);
        pose.rotateAround(Axis.YP.rotationDegrees(-side * display.rotation.y * raise),
                side * display.translation.x, display.translation.y, display.translation.z);
    }

    static float ease(float value) {
        float t = Mth.clamp(value, 0F, 1F);
        return t * t * (3F - 2F * t);
    }

    @SubscribeEvent
    public static void layers(EntityRenderersEvent.AddLayers event) {
        for (String skin : event.getSkins()) {
            if (event.getSkin(skin) instanceof PlayerRenderer renderer) renderer.addLayer(new ChargeLayer(renderer));
        }
    }

    private static final class ChargeLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
        ChargeLayer(PlayerRenderer renderer) { super(renderer); }

        @Override
        public void render(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player,
                           float limbSwing, float limbAmount, float partial, float age, float yaw, float pitch) {
            var hand = chantHand(player);
            if (hand == null) {
                if (!player.isUsingItem()) return;
                hand = player.getUsedItemHand();
                if (!ExcaliburItem.isConcealed(player.getItemInHand(hand))) return;
            }
            var stack = player.getItemInHand(hand);
            if (!(stack.getItem() instanceof ExcaliburItem)) return;
            var arm = hand == InteractionHand.MAIN_HAND ? player.getMainArm() : player.getMainArm().getOpposite();
            boolean left = arm == HumanoidArm.LEFT;
            var context = left ? ItemDisplayContext.THIRD_PERSON_LEFT_HAND : ItemDisplayContext.THIRD_PERSON_RIGHT_HAND;
            pose.pushPose();
            try {
                // Mirror ItemInHandLayer's grip, including the animated arm of the selected player model.
                ExcaliburGrip.applyWrist(player, arm, pose);
                getParentModel().translateToHand(arm, pose);
                pose.mulPose(Axis.XP.rotationDegrees(-90));
                pose.mulPose(Axis.YP.rotationDegrees(180));
                pose.translate((left ? -1F : 1F) / 16F, .125F, -.625F);
                gatherOnBlade(pose, buffers, player, stack, context, left, partial);
            } finally {
                pose.popPose();
            }
        }
    }

    static void gatherOnBlade(PoseStack pose, MultiBufferSource buffers, Player player, ItemStack stack,
                              ItemDisplayContext context, boolean left, float partial) {
        var model = Minecraft.getInstance().getItemRenderer().getModel(stack, player.level(), player, player.getId());
        pose.pushPose();
        try {
            model.applyTransform(context, pose, left);
            float age = chargeAge(player, partial);
            if (ExcaliburItem.isConcealed(stack)) {
                Vec3 focus = bladePoint(pose, .70F);
                double scale = context.firstPerson() ? .43 : .84;
                var axis = pose.last().pose().transformDirection(0F, 1F, 0F, new Vector3f()).normalize();
                ExcaliburVisuals.windChargeAt(buffers, age, focus, new Vec3(axis.x, axis.y, axis.z), scale);
            } else {
                // part_1 runs from OBJ y=.667382 to 1.710300 on x=z=.5. The item renderer subtracts .5.
                Vec3 root = bladePoint(pose, .167382F), tip = bladePoint(pose, 1.210300F);
                ExcaliburVisuals.chargeAt(buffers, age, root, tip, context.firstPerson(), player.getId());
            }
        } finally {
            pose.popPose();
        }
    }

    private static Vec3 bladePoint(PoseStack pose, float centeredY) {
        var point = pose.last().pose().transformPosition(0F, centeredY, 0F, new Vector3f());
        return new Vec3(point.x, point.y, point.z);
    }
}
