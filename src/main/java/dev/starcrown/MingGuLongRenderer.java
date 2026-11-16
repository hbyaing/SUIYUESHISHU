package dev.starcrown;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public final class MingGuLongRenderer extends GeoEntityRenderer<MingGuLongEntity> {
    public MingGuLongRenderer(EntityRendererProvider.Context context) {
        super(context, new MingGuLongModel());
        this.shadowRadius = 2.2F;
    }
}
