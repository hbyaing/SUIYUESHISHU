package dev.starcrown;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderTooltipEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

@Mod.EventBusSubscriber(modid = StarcrownDoll.ID, value = Dist.CLIENT)
public final class SelfDollTooltip {
    private static final long EPOCH = System.nanoTime();
    private static final String TITLE = "tooltip.starcrowndoll.self.divinity";
    private static final String LORE = "tooltip.starcrowndoll.self.lore";
    private static final int[] PALETTES = {0, 2, 1};
    private static final Vector3f FRONT_LIGHT = new Vector3f(.25f, -.7f, 1).normalize();
    private static final Vector3f FILL_LIGHT = new Vector3f(-.35f, -.35f, .8f).normalize();

    private record Line(FormattedCharSequence text, int palette) {}

    static Component playerName() {
        var mc = Minecraft.getInstance();
        if (!mc.isSameThread()) return Component.translatable("block.starcrowndoll.self_doll");
        String name = mc.player == null ? mc.getUser().getName() : mc.player.getGameProfile().getName();
        return Component.translatable("item.starcrowndoll.self_doll.named", name);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void render(RenderTooltipEvent.Pre event) {
        if (!event.getItemStack().is(StarcrownDoll.SELF_ITEM.get())) return;
        event.setCanceled(true);
        if (TooltipReading.pinned()) return;

        var mc = Minecraft.getInstance();
        var font = mc.font;
        var name = event.getItemStack().getHoverName();
        int screenWidth = event.getScreenWidth(), screenHeight = event.getScreenHeight();
        int maxWidth = Math.max(1, Math.min(230, screenWidth - 28));
        var layout = TooltipLayouts.get("self", maxWidth, false, name, () -> {
            var lines = new ArrayList<Line>();
            var rows = List.of(name, Component.translatable(TITLE), Component.translatable(LORE));
            for (int i = 0; i < rows.size(); i++) {
                for (var line : font.split(rows.get(i), maxWidth)) lines.add(new Line(line, PALETTES[i]));
            }
            return new TooltipLayouts.Layout<>(List.copyOf(lines), lines.size());
        });
        int width = layout.lines().stream().mapToInt(line -> font.width(line.text())).max().orElse(1) + 14;
        int height = layout.lines().size() * TooltipReading.ROW_HEIGHT + 12;
        int mouseX = event.getX();
        int x = mouseX + 12 + width > screenWidth - 6 ? mouseX - 12 - width : mouseX + 12;
        x = Math.max(6, Math.min(screenWidth - width - 6, x));
        int y = Math.max(6, Math.min(screenHeight - height - 6, event.getY() - 10));
        double seconds = (System.nanoTime() - EPOCH) / 1e9;
        var graphics = event.getGraphics();

        float size = Math.min(screenHeight * .8f, Math.max(116, width * 1.25f));
        float center = Math.max(size * .4f, Math.min(screenWidth - size * .4f, x + width * .5f));
        float bottom = Math.max(size + 3, Math.min(screenHeight - 4, y + size * .65f));
        portrait(graphics, center, bottom, size, seconds);

        var pose = graphics.pose();
        pose.pushPose();
        try {
            pose.translate(x, y, 400);
            graphics.fillGradient(0, 0, width, height, 0xc21a1530, 0xb80f1129);
            DollTooltip.frame(graphics, width, height, seconds);
            pose.translate(0, 0, 2);
            int row = 6;
            for (Line line : layout.lines()) {
                DollTooltip.gradientLine(graphics, line.text(), 7, row, line.palette(), seconds, 1, x, y);
                row += TooltipReading.ROW_HEIGHT;
            }
            graphics.flush();
        } finally {
            pose.popPose();
        }
    }

    private static void portrait(GuiGraphics graphics, float x, float y, float size, double seconds) {
        graphics.flush();
        var pose = graphics.pose();
        pose.pushPose();
        RenderSystem.setShaderLights(FRONT_LIGHT, FILL_LIGHT);
        try {
            pose.translate(x, y, 245);
            pose.mulPose(Axis.ZP.rotation((float) DollTooltip.sway(seconds) * .075f));
            pose.scale(size, -size, size);
            pose.mulPose(Axis.YP.rotationDegrees(195));
            pose.translate(-.5, 0, -.5);
            SelfDollRenderer.renderForPlayer(Minecraft.getInstance().player, pose, graphics.bufferSource(),
                    LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, 0);
            graphics.bufferSource().endBatch();
        } finally {
            Lighting.setupFor3DItems();
            pose.popPose();
        }
    }
}
