package dev.starcrown;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = StarcrownDoll.ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class MingGuLongClient {
    private MingGuLongClient() {}

    @SubscribeEvent
    public static void registerShaders(net.minecraftforge.client.event.RegisterShadersEvent event) throws java.io.IOException {
        MingGuLongHuangwuVfx.registerShaders(event);
    }

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(StarcrownDoll.MINGGULONG.get(), MingGuLongRenderer::new);
        event.registerEntityRenderer(StarcrownDoll.MINGGULONG_PROJECTILE.get(), MingGuLongEmptyRenderer::new);
        event.registerEntityRenderer(StarcrownDoll.MINGGULONG_VORTEX.get(), MingGuLongEmptyRenderer::new);
    }
}
