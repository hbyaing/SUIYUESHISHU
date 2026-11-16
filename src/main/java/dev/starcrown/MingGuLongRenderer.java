package dev.starcrown;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public final class MingGuLongRenderer extends GeoEntityRenderer<MingGuLongEntity> {
    public MingGuLongRenderer(EntityRendererProvider.Context context) {
        super(context, new MingGuLongModel());
        this.shadowRadius = 2.2F;
    }

    @Override protected float getDeathMaxRotation(MingGuLongEntity entity) {
        // Fight_Die already contains the authored collapse; do not add vanilla roll.
        return 0.0F;
    }
}
