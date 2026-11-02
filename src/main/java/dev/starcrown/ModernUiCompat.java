package dev.starcrown;

import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.starcrown.TooltipLayouts;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

@Mod.EventBusSubscriber(modid="starcrowndoll", value={Dist.CLIENT})
public final class ModernUiCompat {
    private static Boolean enabled;
    private static final Map<FormattedCharSequence, FormattedCharSequence> ROWS;
    private static final Map<String, FormattedCharSequence> STRINGS;
    private static int ticks;
    private static int lastWidth;
    private static int lastScale;
    private static float lastProbe;

    public static boolean textEngine() {
        if (enabled == null) {
            enabled = false;
            if (ModList.get().isLoaded("modernui")) {
                try {
                    enabled = (Boolean)Class.forName("icyllis.modernui.mc.ModernUIMod").getMethod("isTextEngineEnabled", new Class[0]).invoke(null, new Object[0]);
                }
                catch (LinkageError | ReflectiveOperationException throwable) {
                    // empty catch block
                }
            }
        }
        return enabled;
    }

    public static void clear() {
        ROWS.clear();
        STRINGS.clear();
    }

    public static FormattedCharSequence row(String text) {
        if (STRINGS.size() > 128) {
            STRINGS.clear();
        }
        return STRINGS.computeIfAbsent(text, s -> FormattedCharSequence.forward((String)s, (Style)Style.EMPTY));
    }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !ModernUiCompat.textEngine() || ++ticks % 20 != 0) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        int width = mc.getWindow().getGuiScaledWidth();
        int scale = (int)(mc.getWindow().getGuiScale() * 100.0);
        float probe = mc.font.width("Aa\u6c49\u5b570123456789 fi AV");
        if (width != lastWidth || scale != lastScale || probe != lastProbe) {
            lastWidth = width;
            lastScale = scale;
            lastProbe = probe;
            TooltipLayouts.clear();
        }
    }

    public static boolean draw(GuiGraphics g, FormattedCharSequence text, int x, int y, Tint tint) {
        if (!ModernUiCompat.textEngine()) {
            return false;
        }
        if (text == FormattedCharSequence.EMPTY) {
            return true;
        }
        FormattedCharSequence plain = ROWS.computeIfAbsent(text, row -> {
            ArrayList<Code> codes = new ArrayList<>();
            row.accept((i, s, c) -> {
                codes.add(new Code(i, s.withColor(0xFFFFFF), c));
                return true;
            });
            return sink -> {
                for (Code code : codes) {
                    if (sink.accept(code.index, code.style, code.code)) continue;
                    return false;
                }
                return true;
            };
        });
        Matrix4f matrix = g.pose().last().pose();
        float originX = matrix.m00() * (float)x + matrix.m10() * (float)y + matrix.m30();
        float originY = matrix.m01() * (float)x + matrix.m11() * (float)y + matrix.m31();
        float scale = (float)Math.sqrt(matrix.m00() * matrix.m00() + matrix.m01() * matrix.m01());
        g.flush();
        MultiBufferSource source = type -> new Colored(g.bufferSource().getBuffer(type), originX, originY, Math.max(0.001f, scale), tint);
        Minecraft.getInstance().font.drawInBatch(plain, (float)x, (float)y, -1, false, matrix, source, Font.DisplayMode.NORMAL, 0, 0xF000F0);
        g.flush();
        return true;
    }

    static {
        ROWS = new WeakHashMap<FormattedCharSequence, FormattedCharSequence>();
        STRINGS = new LinkedHashMap<String, FormattedCharSequence>();
        lastWidth = -1;
        lastScale = -1;
        lastProbe = -1.0f;
    }

    @FunctionalInterface
    public static interface Tint {
        public int color(float var1, float var2);
    }

    private static final class Colored
    implements VertexConsumer {
        final VertexConsumer delegate;
        final float originX;
        final float originY;
        final float scale;
        final Tint tint;
        float x;
        float y;

        Colored(VertexConsumer d, float ox, float oy, float s, Tint t) {
            this.delegate = d;
            this.originX = ox;
            this.originY = oy;
            this.scale = s;
            this.tint = t;
        }

        public VertexConsumer vertex(double x, double y, double z) {
            this.x = ((float)x - this.originX) / this.scale;
            this.y = ((float)y - this.originY) / this.scale;
            this.delegate.vertex(x, y, z);
            return this;
        }

        public VertexConsumer color(int r, int g, int b, int a) {
            int c = this.tint.color(this.x, this.y);
            this.delegate.color(r * (c >> 16 & 0xFF) / 255, g * (c >> 8 & 0xFF) / 255, b * (c & 0xFF) / 255, a);
            return this;
        }

        public VertexConsumer uv(float u, float v) {
            this.delegate.uv(u, v);
            return this;
        }

        public VertexConsumer overlayCoords(int u, int v) {
            this.delegate.overlayCoords(u, v);
            return this;
        }

        public VertexConsumer uv2(int u, int v) {
            this.delegate.uv2(u, v);
            return this;
        }

        public VertexConsumer normal(float x, float y, float z) {
            this.delegate.normal(x, y, z);
            return this;
        }

        public void endVertex() {
            this.delegate.endVertex();
        }

        public void defaultColor(int r, int g, int b, int a) {
            this.delegate.defaultColor(r, g, b, a);
        }

        public void unsetDefaultColor() {
            this.delegate.unsetDefaultColor();
        }
    }

    private record Code(int index, Style style, int code) {
    }
}
