package dev.starcrown;

import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;

/** Projectile and vortex visuals are emitted by the server particle timeline. */
public final class MingGuLongEmptyRenderer<T extends Entity> extends EntityRenderer<T> {
    public MingGuLongEmptyRenderer(EntityRendererProvider.Context context) { super(context); }
    @Override public ResourceLocation getTextureLocation(T entity) { return new ResourceLocation(StarcrownDoll.ID, "textures/entity/minggulong.png"); }
}
