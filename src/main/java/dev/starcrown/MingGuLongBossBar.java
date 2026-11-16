package dev.starcrown;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.CustomizeGuiOverlayEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Replaces only Eternal Dragon bars; vanilla handles networking and tracking. */
@Mod.EventBusSubscriber(modid = StarcrownDoll.ID, value = Dist.CLIENT)
public final class MingGuLongBossBar {
    private static final ResourceLocation FRAME = texture("frame");
    private static final ResourceLocation FILL = texture("fill");
    private static final ResourceLocation DECORATION = texture("decoration");
    private static final int TEXTURE_WIDTH = 960, TEXTURE_HEIGHT = 240;
    private static final int FILL_X = 92, FILL_Y = 124, FILL_WIDTH = 776, FILL_HEIGHT = 20;

    private MingGuLongBossBar() {}

    private static ResourceLocation texture(String layer) {
        return new ResourceLocation(StarcrownDoll.ID, "textures/gui/eternal_dragon/" + layer + ".png");
    }

    @SubscribeEvent
    public static void render(CustomizeGuiOverlayEvent.BossEventProgress event) {
        if (!(event.getBossEvent().getName().getContents() instanceof TranslatableContents name)
                || !MingGuLongEntity.BOSS_BAR_NAME.equals(name.getKey())) return;

        event.setCanceled(true);
        GuiGraphics graphics = event.getGuiGraphics();
        float scale = Math.min(320.0F, graphics.guiWidth() - 8.0F) / TEXTURE_WIDTH;
        if (scale <= 0.0F) return;
        float left = (graphics.guiWidth() - TEXTURE_WIDTH * scale) / 2.0F;
        int top = event.getY() - 10;
        // Reserve the whole ornament height, including the head below the frame.
        event.setIncrement(Mth.ceil(220.0F * scale));
        float progress = event.getBossEvent().getProgress();
        if (!Float.isFinite(progress)) progress = 0;
        int filled = Mth.ceil(FILL_WIDTH * Mth.clamp(progress, 0.0F, 1.0F));

        graphics.pose().pushPose();
        try {
            graphics.pose().translate(left, top, 0);
            graphics.pose().scale(scale, scale, 1);
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            graphics.setColor(1, 1, 1, 1);
            graphics.blit(FRAME, 0, 0, 0, 0, TEXTURE_WIDTH, TEXTURE_HEIGHT,
                TEXTURE_WIDTH, TEXTURE_HEIGHT);
            if (filled > 0) graphics.blit(FILL, FILL_X, FILL_Y, FILL_X, FILL_Y,
                filled, FILL_HEIGHT, TEXTURE_WIDTH, TEXTURE_HEIGHT);
            graphics.blit(DECORATION, 0, 0, 0, 0, TEXTURE_WIDTH, TEXTURE_HEIGHT,
                TEXTURE_WIDTH, TEXTURE_HEIGHT);
        } finally {
            graphics.pose().popPose();
            RenderSystem.disableBlend();
        }
        var font = Minecraft.getInstance().font;
        graphics.drawCenteredString(font, event.getBossEvent().getName(),
            graphics.guiWidth() / 2, top + Math.round(46 * scale), 0xFFF1D6);
    }
}
