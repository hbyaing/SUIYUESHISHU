package dev.starcrown;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.PlayerModelPart;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix3f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** First-person sword grips using the local player's vanilla arm and sleeve meshes. */
@Mod.EventBusSubscriber(modid = StarcrownDoll.ID, value = Dist.CLIENT)
public final class ExcaliburChantHands {
    private static InteractionHand localChargingHand(Minecraft mc) {
        if (mc.player == null || mc.getCameraEntity() != mc.player || !mc.options.getCameraType().isFirstPerson()) return null;
        return ExcaliburChargePose.chargingHand(mc.player);
    }

    static boolean hidesSupport(RenderHandEvent event) {
        var hand = localChargingHand(Minecraft.getInstance());
        return hand != null && event.getHand() != hand;
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void hideSupport(RenderHandEvent event) {
        var mc = Minecraft.getInstance();
        var chantHand = localChargingHand(mc);
        if (chantHand == null || event.getHand() == chantHand) return;
        event.setCanceled(true);
        // Vanilla renders only the weapon hand while using a bow, or only the main hand with a loaded crossbow.
        // When that is the support hand, this event is the only opportunity to draw the chanting sword.
        var player = mc.player;
        if (ExcaliburItem.isConcealed(player.getItemInHand(chantHand))) return;
        boolean swordHandSkipped = player.isUsingItem()
                ? player.getUsedItemHand() == event.getHand() && (player.getUseItem().is(Items.BOW) || player.getUseItem().is(Items.CROSSBOW))
                : chantHand == InteractionHand.OFF_HAND && player.getMainHandItem().is(Items.CROSSBOW)
                        && CrossbowItem.isCharged(player.getMainHandItem());
        if (swordHandSkipped) grip(event, player, chantHand, player.getItemInHand(chantHand), true);
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void hands(RenderHandEvent event) {
        var mc = Minecraft.getInstance();
        var stack = event.getItemStack();
        if (event.getHand() != localChargingHand(mc)
                || !(stack.getItem() instanceof ExcaliburItem) || ExcaliburItem.isConcealed(stack)
                || mc.player.isInvisible()) return;
        grip(event, mc.player, event.getHand(), stack, false);
    }

    private static void grip(RenderHandEvent event, LocalPlayer player, InteractionHand hand, ItemStack stack, boolean drawSword) {
        var mc = Minecraft.getInstance();
        HumanoidArm arm = hand == InteractionHand.MAIN_HAND ? player.getMainArm() : player.getMainArm().getOpposite();
        boolean left = arm == HumanoidArm.LEFT;
        var context = left ? ItemDisplayContext.FIRST_PERSON_LEFT_HAND : ItemDisplayContext.FIRST_PERSON_RIGHT_HAND;
        // A fallback call receives the other hand's equip progress, so its already-held sword stays level.
        var weapon = new PoseStack();
        ExcaliburChargePose.placeChargingSword(weapon, player, arm, event.getPartialTick(), drawSword ? 0F : event.getEquipProgress());
        if (drawSword) {
            var pose = event.getPoseStack();
            pose.pushPose();
            try {
                pose.mulPoseMatrix(weapon.last().pose());
                pose.last().normal().mul(weapon.last().normal());
                mc.getItemRenderer().renderStatic(player, stack, context, left, pose, event.getMultiBufferSource(),
                        player.level(), event.getPackedLight(), OverlayTexture.NO_OVERLAY, player.getId());
                ExcaliburChargePose.gatherOnBlade(pose, event.getMultiBufferSource(), player, stack, context, left, event.getPartialTick());
            } finally {
                pose.popPose();
            }
        }
        if (player.isInvisible() || !(mc.getEntityRenderDispatcher().getRenderer(player) instanceof PlayerRenderer renderer)) return;
        mc.getItemRenderer().getModel(stack, player.level(), player, player.getId()).applyTransform(context, weapon, left);
        // The centered OBJ's blue winding spans y=-.116 to +.115, with a radius of .016 to .026.
        float age = ExcaliburChargePose.chargeAge(player, event.getPartialTick());
        chargingArm(event, player, renderer, arm, weapon, true, ExcaliburChargePose.ease(age / 7F));
        chargingArm(event, player, renderer, arm.getOpposite(), weapon, false, ExcaliburChargePose.ease((age - 2F) / 8F));
    }

    /** The incoming matrix already contains vanilla's hand-specific swing, use and equip transforms. */
    public static void ordinaryGrip(PoseStack pose, MultiBufferSource buffers, int light, LocalPlayer player,
                                    ItemStack stack, ItemDisplayContext context, boolean left) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getCameraEntity() != player || player.isInvisible()) return;
        var charging = ExcaliburChargePose.chargingHand(player);
        // The revealed chant draws its own pair. Invisible Air keeps a single arm on its hilt.
        if (charging != null && !ExcaliburItem.isConcealed(stack)) return;
        if (!(mc.getEntityRenderDispatcher().getRenderer(player) instanceof PlayerRenderer renderer)) return;
        var weapon = new PoseStack();
        var model = mc.getItemRenderer().getModel(stack, player.level(), player, player.getId() + context.ordinal());
        model.applyTransform(context, weapon, left);
        int side = left ? -1 : 1;
        // Both the mesh hilt and the Invisible Air envelope are centered on this axis.
        var wrist = weapon.last().pose().transformPosition(side * .012F, .013F, .010F, new Vector3f());
        var forward = new Vector3f(-side * .47F, .44F, -.53F).normalize();
        drawArm(pose, buffers, light, player, renderer, left ? HumanoidArm.LEFT : HumanoidArm.RIGHT, wrist, forward, 0F, .76F);
    }

    private static void chargingArm(RenderHandEvent event, LocalPlayer player, PlayerRenderer renderer, HumanoidArm arm,
                                    PoseStack weapon, boolean upper, float reach) {
        int side = arm == HumanoidArm.RIGHT ? 1 : -1;
        // Roll each native wrist around its own long axis to seat opposite palm corners on the winding.
        float wristOffset = (upper ? .047F : .077F) - ("slim".equals(player.getModelName()) ? .022F : 0F);
        float gripHeight = upper ? .044F : -.013F;
        float depth = upper ? -.102F : -.016F;
        var socket = weapon.last().pose().transformPosition(side * wristOffset, gripHeight, depth, new Vector3f());
        var wrist = new Vector3f(side * .46F, -.94F, -.64F).lerp(socket, reach);
        var shoulder = weapon.last().pose().transformPosition(0F, 0F, 0F, new Vector3f())
                .add(side * 1.141F, -1.052F, .860F);
        var forward = new Vector3f(wrist).sub(shoulder).normalize();
        drawArm(event.getPoseStack(), event.getMultiBufferSource(), event.getPackedLight(),
                player, renderer, arm, wrist, forward, side * (upper ? .539F : .940F), .69F);
    }

    private static void drawArm(PoseStack pose, MultiBufferSource buffers, int light, LocalPlayer player,
                                PlayerRenderer renderer, HumanoidArm arm, Vector3f wrist, Vector3f forward,
                                float roll, float scale) {
        boolean right = arm == HumanoidArm.RIGHT;
        int side = right ? 1 : -1;
        boolean slim = "slim".equals(player.getModelName());
        Vector3f across = new Vector3f(-forward.z, 0, forward.x).normalize();
        Vector3f normal = across.cross(forward, new Vector3f());
        var rotation = new Quaternionf().setFromNormalized(new Matrix3f(across, forward, normal)).rotateY(-roll);
        var model = renderer.getModel();
        var skin = player.getSkinTextureLocation();
        boolean hasSleeve = player.isModelPartShown(right ? PlayerModelPart.RIGHT_SLEEVE : PlayerModelPart.LEFT_SLEEVE);
        pose.pushPose();
        try {
            pose.translate(wrist.x, wrist.y, wrist.z);
            pose.mulPose(rotation);
            pose.scale(scale, scale, scale);
            // Vanilla arm boxes end at y=10, with different center offsets for Steve and Alex.
            pose.translate(side * (slim ? .5F : 1F) / 16F, -10F / 16F, 0F);
            drawPart(right ? model.rightArm : model.leftArm, pose, buffers.getBuffer(RenderType.entitySolid(skin)), light);
            if (hasSleeve) drawPart(right ? model.rightSleeve : model.leftSleeve, pose,
                    buffers.getBuffer(RenderType.entityTranslucent(skin)), light);
        } finally {
            pose.popPose();
        }
    }

    private static void drawPart(ModelPart part, PoseStack pose, VertexConsumer vertices, int light) {
        var previous = part.storePose();
        boolean visible = part.visible;
        try {
            part.setPos(0, 0, 0);
            part.setRotation(0, 0, 0);
            part.visible = true;
            part.render(pose, vertices, light, OverlayTexture.NO_OVERLAY);
        } finally {
            part.loadPose(previous);
            part.visible = visible;
        }
    }
}
