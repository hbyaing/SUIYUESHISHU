package dev.starcrown;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemDisplayContext;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Keep both rigid Minecraft arms on the actual sword handle after vanilla swing and crouch animation. */
public final class ExcaliburGrip {
    private static final float PALM_Y = 8.25F;
    private static final Map<AbstractClientPlayer, Wrist> WRISTS = new WeakHashMap<>();
    private record Wrist(HumanoidArm arm, Vector3f pivot, Vector3f target, Quaternionf turn, boolean twoHands) {}

    public static void settle(PlayerModel<?> model, AbstractClientPlayer player, float partial) {
        WRISTS.remove(player);
        var stroke = ExcaliburChant.Client.swordStroke(player, partial);
        var hand = stroke == null ? ExcaliburChargePose.chargingHand(player) : stroke.hand();
        boolean charging = hand != null;
        if (!charging) {
            if (player.getMainHandItem().getItem() instanceof ExcaliburItem && player.getOffhandItem().isEmpty())
                hand = InteractionHand.MAIN_HAND;
            else if (player.getOffhandItem().getItem() instanceof ExcaliburItem && player.getMainHandItem().isEmpty())
                hand = InteractionHand.OFF_HAND;
            else return;
        }
        var mc = Minecraft.getInstance();
        var stack = player.getItemInHand(hand);
        boolean right = (hand == InteractionHand.MAIN_HAND) == (player.getMainArm() == HumanoidArm.RIGHT);
        var lead = right ? model.rightArm : model.leftArm;
        var support = right ? model.leftArm : model.rightArm;
        float strokeWeight = stroke == null ? 0F : ExcaliburSwordStroke.weight(stroke.tick());
        boolean recovering = stroke != null && stroke.tick() > ExcaliburChant.DURATION;
        var otherHand = hand == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
        boolean returnToVanilla = recovering && !player.getItemInHand(otherHand).isEmpty();
        var vanillaLead = returnToVanilla ? rotation(lead) : null;
        var vanillaSupport = returnToVanilla ? rotation(support) : null;
        float swing = Mth.sin(Mth.sqrt(model.attackTime) * Mth.PI);
        float raise = recovering ? strokeWeight
                : charging ? Mth.clamp(ExcaliburChargePose.chargeAge(player, partial) / 10F, 0, 1) : 0;
        lead.xRot = -1.38F + .32F * Mth.clamp(model.head.xRot, -.6F, .6F) - .36F * raise - .65F * swing;
        boolean slim = "slim".equals(player.getModelName());
        PoseStack bind = new PoseStack();
        if (slim) bind.translate((right ? .5F : -.5F) / 16F, 0, 0);
        bind.mulPose(Axis.XP.rotationDegrees(-90));
        bind.mulPose(Axis.YP.rotationDegrees(180));
        bind.translate((right ? 1 : -1) / 16F, .125F, -.625F);
        var display = right ? ItemDisplayContext.THIRD_PERSON_RIGHT_HAND : ItemDisplayContext.THIRD_PERSON_LEFT_HAND;
        mc.getItemRenderer().getModel(stack, player.level(), player, player.getId())
                .applyTransform(display, bind, !right);
        // The support hand grips the lower blue winding at OBJ y=.410; ItemRenderer centers the OBJ at .500.
        var lower = bind.last().pose().transformPosition(0, -.090F, 0, new Vector3f()).mul(16);
        float palmX = (right ? 1F : -1F) * (slim ? .5F : 1F);
        alignHands(lead, support, new Vector3f(lower), palmX, right);
        if (strokeWeight > 0F) {
            var upper = bind.last().pose().transformPosition(0, .075F, 0, new Vector3f()).mul(16);
            // PlayerModel applies the slim-hand translation to its pivot, before the arm rotates.
            float slimX = slim ? (right ? .5F : -.5F) : 0F;
            var shoulder = new Vector3f(lead.x, lead.y, lead.z);
            var otherShoulder = new Vector3f(support.x, support.y, support.z);
            var leadRot = rotation(lead);
            var mount = new Vector3f(shoulder).add(slimX, 0, 0);
            leadRot.transform(upper.sub(slimX, 0, 0)).add(mount);
            leadRot.transform(lower.sub(slimX, 0, 0)).add(mount);
            var pose = ExcaliburSwordStroke.solve(stroke.tick(), strokeWeight, model.head.xRot, model.head.yRot,
                    shoulder, otherShoulder, upper, lower);
            var delta = pose.shoulders();
            lead.setPos(lead.x + delta.x, lead.y + delta.y, lead.z + delta.z);
            support.setPos(support.x + delta.x, support.y + delta.y, support.z + delta.z);
            setRotation(lead, new Quaternionf(pose.leadArm()).mul(leadRot));
            setRotation(support, new Quaternionf(pose.supportArm()).mul(rotation(support)));

            var pivot = pose.leadArm().transform(upper.sub(mount, new Vector3f())).add(mount).add(delta);
            var turn = new Quaternionf(pose.blade()).mul(new Quaternionf(pose.leadArm()).conjugate());
            WRISTS.put(player, new Wrist(right ? HumanoidArm.RIGHT : HumanoidArm.LEFT, pivot, pose.upper(), turn,
                    !returnToVanilla || strokeWeight >= .5F));
        }
        if (returnToVanilla) {
            setRotation(lead, rotation(lead).slerp(vanillaLead, 1F - strokeWeight));
            setRotation(support, rotation(support).slerp(vanillaSupport, 1F - strokeWeight));
        }
        model.rightSleeve.copyFrom(model.rightArm);
        model.leftSleeve.copyFrom(model.leftArm);
    }

    /** Both third-person render layers apply this in model space before vanilla translates to the hand. */
    public static void applyWrist(AbstractClientPlayer player, HumanoidArm arm, PoseStack pose) {
        var wrist = WRISTS.get(player);
        if (wrist == null || wrist.arm != arm) return;
        pose.translate(wrist.target.x / 16F, wrist.target.y / 16F, wrist.target.z / 16F);
        pose.mulPose(wrist.turn);
        pose.translate(-wrist.pivot.x / 16F, -wrist.pivot.y / 16F, -wrist.pivot.z / 16F);
    }

    public static boolean stillGripping(AbstractClientPlayer player, HumanoidArm arm) {
        var wrist = WRISTS.get(player);
        return wrist != null && wrist.twoHands && wrist.arm != arm;
    }

    private static Quaternionf rotation(ModelPart arm) {
        return new Quaternionf().rotationZYX(arm.zRot, arm.yRot, arm.xRot);
    }

    private static void setRotation(ModelPart arm, Quaternionf rotation) {
        var angles = ExcaliburSwordStroke.modelAngles(rotation);
        arm.setRotation(angles.x, angles.y, angles.z);
    }

    private static void alignHands(ModelPart lead, ModelPart support, Vector3f grip, float palmX, boolean right) {
        lead.xRot = Mth.clamp(lead.xRot, -2.55F, -1.18F);
        lead.zRot = 0;
        grip.rotateX(lead.xRot);
        float dx = lead.x - support.x, dy = lead.y - support.y, dz = lead.z - support.z;
        float shoulderSpan = Mth.sqrt(dx * dx + dz * dz);
        if (shoulderSpan < .01F) return;

        // Intersect the lead hand's horizontal arc with the support arm's reach sphere.
        float arcRadius = Mth.sqrt(grip.x * grip.x + grip.z * grip.z);
        float palmLengthSq = PALM_Y * PALM_Y + palmX * palmX;
        float along = (palmLengthSq - shoulderSpan * shoulderSpan - grip.lengthSquared() - dy * dy
                - 2 * dy * grip.y) / (2 * shoulderSpan);
        along = Mth.clamp(along, -arcRadius, arcRadius);
        float across = Mth.sqrt(Math.max(0, arcRadius * arcRadius - along * along)) * (right ? 1F : -1F);
        float gx = (along * dx - across * dz) / shoulderSpan;
        float gz = (along * dz + across * dx) / shoulderSpan;
        lead.yRot = (float)(Math.atan2(grip.z, grip.x) - Math.atan2(gz, gx));

        // Rotate the second cuboid from its own shoulder so its palm center lands on that exact point.
        float tx = dx + gx, ty = dy + grip.y, tz = dz + gz;
        support.xRot = (float)Math.asin(Mth.clamp(tz / PALM_Y, -1F, 1F));
        support.yRot = 0;
        support.zRot = (float)(Math.atan2(ty, tx) - Math.atan2(PALM_Y * Mth.cos(support.xRot), palmX));
    }
}
