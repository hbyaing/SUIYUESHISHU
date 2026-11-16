package dev.starcrown;

import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.constant.DataTickets;
import net.minecraft.util.Mth;

public final class MingGuLongModel extends GeoModel<MingGuLongEntity> {
    private static final ResourceLocation MODEL = new ResourceLocation(StarcrownDoll.ID, "geo/minggulong.geo.json");
    private static final ResourceLocation ANIMATION = new ResourceLocation(StarcrownDoll.ID, "animations/minggulong.animation.json");
    private static final ResourceLocation TEXTURE = new ResourceLocation(StarcrownDoll.ID, "textures/entity/minggulong.png");

    @Override public ResourceLocation getModelResource(MingGuLongEntity entity) { return MODEL; }
    @Override public ResourceLocation getTextureResource(MingGuLongEntity entity) { return TEXTURE; }
    @Override public ResourceLocation getAnimationResource(MingGuLongEntity entity) { return ANIMATION; }

    @Override
    public void setCustomAnimations(MingGuLongEntity entity, long instanceId, AnimationState<MingGuLongEntity> state) {
        super.setCustomAnimations(entity, instanceId, state);
        // The source clips contain travel for a standalone preview. Minecraft's
        // server moves the entity; retaining this travel would snap on each loop
        // and on the takeoff-to-glide transition. Local Bip001 motion is retained.
        int animation = entity.getAnimationKey();
        if (animation >= 3 && animation <= 8) {
            getBone("Root").ifPresent(root -> root.updatePosition(0, 0, 0));
        }
        if (animation >= 5 && animation <= 7) {
            var data = state.getData(DataTickets.ENTITY_MODEL_DATA);
            if (data != null) {
                float yaw = Mth.clamp(data.netHeadYaw(), -55, 55) * Mth.DEG_TO_RAD;
                float pitch = Mth.clamp(data.headPitch(), -35, 35) * Mth.DEG_TO_RAD;
                // Distribute the additive look across the existing neck chain.
                // Keep every authored wing/body transform and the original mesh.
                addLook("Bip001_Neck", pitch * .2F, yaw * .2F);
                addLook("Bip001_Neck1", pitch * .3F, yaw * .3F);
                addLook("Bip001_Head", pitch * .5F, yaw * .5F);
            }
        }
    }

    private void addLook(String name, float pitch, float yaw) {
        getBone(name).ifPresent(bone -> {
            bone.setRotX(bone.getRotX() + pitch);
            bone.setRotY(bone.getRotY() + yaw);
        });
    }
}
