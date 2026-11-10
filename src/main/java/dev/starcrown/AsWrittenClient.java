package dev.starcrown;

import net.minecraft.client.*;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.*;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.*;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Standalone slots are part of the vanilla inventory, never a second screen. */
@Mod.EventBusSubscriber(modid = StarcrownDoll.ID, value = Dist.CLIENT)
public final class AsWrittenClient {
    public static final KeyMapping OPEN = new KeyMapping("key.starcrowndoll.as_written", org.lwjgl.glfw.GLFW.GLFW_KEY_J, "key.categories.starcrowndoll");
    public static boolean expanded = true;
    public interface Position { void starcrown$position(int x, int y); }
    public interface Wrapped { Slot starcrown$target(); }
    public static Slot unwrap(Slot slot) { return slot instanceof Wrapped wrapped ? wrapped.starcrown$target() : slot; }
    private static boolean inventory(Screen screen) { return screen instanceof InventoryScreen || screen instanceof CreativeModeInventoryScreen; }
    private static boolean usable(Screen screen) { return screen instanceof InventoryScreen || screen instanceof CreativeModeInventoryScreen creative && creative.isInventoryOpen(); }
    private static int offset(AbstractContainerScreen<?> screen) {
        return screen instanceof InventoryScreen inventory && inventory.getRecipeBookComponent().isVisible() ? screen.getXSize() + 12 : -28;
    }
    public static boolean contains(Screen value, double mouseX, double mouseY) {
        if (CuriosEquipment.loaded() || !expanded || !usable(value)) return false;
        var screen = (AbstractContainerScreen<?>) value; int x = screen.getGuiLeft() + offset(screen), y = screen.getGuiTop();
        return mouseX >= x - 8 && mouseX < x + 24 && mouseY >= y + 20 && mouseY < y + 94;
    }
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if (CuriosEquipment.loaded() || event.phase != TickEvent.Phase.END) return;
        var mc = Minecraft.getInstance();
        while (OPEN.consumeClick()) if (mc.player != null && mc.screen == null) mc.setScreen(new InventoryScreen(mc.player));
    }
    @SubscribeEvent public static void init(ScreenEvent.Init.Post event) {
        if (!CuriosEquipment.loaded() && inventory(event.getScreen())) event.addListener(new ToggleButton());
    }
    public static final class ToggleButton extends Button {
        ToggleButton() { super(0, 0, 12, 12, Component.literal("‹"), b -> { expanded = !expanded; }, DEFAULT_NARRATION); }
    }
    @SubscribeEvent public static void background(ContainerScreenEvent.Render.Background event) {
        if (CuriosEquipment.loaded() || !inventory(event.getContainerScreen())) return;
        var screen = event.getContainerScreen(); boolean shown = usable(screen); int offset = offset(screen);
        for (var child : screen.children()) if (child instanceof ToggleButton button) {
            button.visible = shown; button.setX(screen.getGuiLeft() + offset + 14); button.setY(screen.getGuiTop() + 5);
            button.setMessage(Component.literal(expanded ? "‹" : "›"));
            button.setTooltip(Tooltip.create(Component.translatable(expanded ? "ui.starcrowndoll.hide_slots" : "ui.starcrowndoll.show_slots")));
        }
        var mc = Minecraft.getInstance();
        for (var slot : mc.player.inventoryMenu.slots) if (slot instanceof EquipmentSlots.SideSlot side) side.visible = shown && expanded;
        if (!shown) return;
        for (var slot : screen.getMenu().slots) if (unwrap(slot) instanceof EquipmentSlots.SideSlot side && (slot.x != offset || slot.y != 28 + side.kind * 22))
            ((Position) slot).starcrown$position(offset, 28 + side.kind * 22);
        if (!expanded) return;
        var graphics = event.getGuiGraphics(); int x = screen.getGuiLeft() + offset - 8, y = screen.getGuiTop() + 20;
        graphics.fill(x, y, x + 32, y + 74, 0xff373737);
        graphics.fill(x + 1, y + 1, x + 31, y + 73, 0xffeeeeee);
        graphics.fill(x + 3, y + 3, x + 29, y + 71, 0xffc6c6c6);
        graphics.fill(x + 30, y + 2, x + 31, y + 73, 0xff555555);
        graphics.fill(x + 2, y + 72, x + 31, y + 73, 0xff555555);
        for (int i = 0; i < 3; i++) {
            int sx = x + 8, sy = y + 8 + i * 22;
            graphics.fill(sx - 1, sy - 1, sx + 17, sy + 17, 0xffffffff);
            graphics.fill(sx - 1, sy - 1, sx + 16, sy + 16, 0xff373737);
            graphics.fill(sx, sy, sx + 16, sy + 16, 0xff8b8b8b);
        }
    }
    @SubscribeEvent public static void tooltip(ScreenEvent.Render.Post event) {
        if (CuriosEquipment.loaded() || !usable(event.getScreen()) || !expanded) return;
        var screen = (AbstractContainerScreen<?>) event.getScreen(); var slot = screen.getSlotUnderMouse();
        if (slot != null && !slot.hasItem() && unwrap(slot) instanceof EquipmentSlots.SideSlot side)
            event.getGuiGraphics().renderTooltip(Minecraft.getInstance().font, Component.translatable(side.kind == 0 ? "curios.identifier.starcrowndoll_divinity" : "container.starcrowndoll.as_written.slot", side.kind), event.getMouseX(), event.getMouseY());
    }
    public static void receive(DivinityInventory.State state) {
        var mc = Minecraft.getInstance();
        if (!CuriosEquipment.loaded() && mc.level != null && mc.level.getEntity(state.entity()) instanceof net.minecraft.world.entity.player.Player player)
            player.getCapability(DivinityInventory.CAPABILITY).ifPresent(c -> c.rawSet(state.stack()));
    }
    @Mod.EventBusSubscriber(modid = StarcrownDoll.ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class Setup {
        @SubscribeEvent public static void keys(RegisterKeyMappingsEvent event) { if (!CuriosEquipment.loaded()) event.register(OPEN); }
    }
}
