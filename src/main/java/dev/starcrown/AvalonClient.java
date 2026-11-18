package dev.starcrown;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import org.lwjgl.glfw.GLFW;

@Mod.EventBusSubscriber(modid = StarcrownDoll.ID, value = Dist.CLIENT)
public final class AvalonClient {
    private static final KeyMapping SANCTUARY = new KeyMapping("key.starcrowndoll.avalon", GLFW.GLFW_KEY_V, "key.categories.starcrowndoll");

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        var mc = Minecraft.getInstance();
        while (SANCTUARY.consumeClick()) {
            if (mc.player != null && mc.screen == null && !mc.player.isSpectator() && AvalonItem.carries(mc.player)) {
                SevenNetwork.CHANNEL.sendToServer(new AvalonItem.WakeSanctuary());
            }
        }
    }

    @Mod.EventBusSubscriber(modid = StarcrownDoll.ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class Setup {
        @SubscribeEvent
        public static void keys(RegisterKeyMappingsEvent event) { event.register(SANCTUARY); }

        @SubscribeEvent
        public static void models(FMLClientSetupEvent event) {
            event.enqueueWork(() -> {
                ItemProperties.register(StarcrownDoll.EXCALIBUR.get(),
                    new ResourceLocation(StarcrownDoll.ID, "concealed"),
                    (stack, level, entity, seed) -> ExcaliburItem.isConcealed(stack) ? 1 : 0);
                ItemProperties.register(StarcrownDoll.EXCALIBUR.get(), new ResourceLocation(StarcrownDoll.ID, "wind_swing"),
                    (stack, level, entity, seed) -> {
                        if (entity == null || !entity.swinging || entity.swingingArm == null
                                || entity.getItemInHand(entity.swingingArm) != stack) return 0;
                        float progress = entity.getAttackAnim(Minecraft.getInstance().getFrameTime());
                        return progress > .16f && progress < .76f ? 1 : 0;
                    });
                ItemProperties.register(StarcrownDoll.AVALON.get(), new ResourceLocation(StarcrownDoll.ID, "sheathed"),
                    (stack, level, entity, seed) -> AvalonItem.isSheathed(stack) ? 1 : 0);
            });
        }
    }
}
