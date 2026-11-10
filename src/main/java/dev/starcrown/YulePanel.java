package dev.starcrown;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

final class YulePanel extends Screen {
    private final boolean calm;
    private SevenNetwork.State state;
    private int left, top, panelWidth;

    YulePanel(boolean calm) {
        super(Component.translatable("tooltip.starcrowndoll." + (calm ? "calm" : "yule") + ".divinity"));
        this.calm = calm;
        send(16);
    }

    private void send(int action) {
        SevenNetwork.CHANNEL.sendToServer(new SevenNetwork.Action(action, 0));
    }

    void refresh(SevenNetwork.State next) {
        state = next;
        rebuildWidgets();
    }

    private void control(int row, Component name, int action, boolean ready) {
        var button = Button.builder(name, ignored -> send(action))
                .bounds(left + 14, top + 31 + 25 * row, panelWidth - 28, 21).build();
        button.active = ready;
        addRenderableWidget(button);
    }

    @Override protected void init() {
        panelWidth = Math.min(258, width - 16);
        left = (width - panelWidth) / 2;
        top = (height - 139) / 2;
        boolean synced = state != null;
        control(0, calm ? label("cast") : toggle("peace", synced && state.domain()),
                CalmYule.PEACE_ACTION, synced);
        control(1, toggle(calm ? "frost" : "purify", synced && state.night()),
                calm ? CalmYule.FROST_ACTION : CalmYule.PURIFY_ACTION, synced);
        if (calm) control(2, label("clear_pacts"), CalmYule.CLEAR_PACTS_ACTION, synced);
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), ignored -> onClose())
                .bounds(left + panelWidth - 72, top + 112, 58, 19).build());
    }

    private static Component label(String key) {
        return Component.translatable("gui.starcrowndoll.yule." + key);
    }

    private static Component toggle(String key, boolean on) {
        return label(key).copy().append("  ").append(label(on ? "on" : "off"));
    }

    @Override public boolean isPauseScreen() {
        return false;
    }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        graphics.fill(left - 1, top - 1, left + panelWidth + 1, top + 140, 0xfff0d5a5);
        graphics.fillGradient(left, top, left + panelWidth, top + 139, 0xee483144, 0xee271e35);
        graphics.drawCenteredString(font, getTitle(), width / 2, top + 12, 0xffffdfeb);
        graphics.drawString(font, label("gift"), left + 14, top + 118, 0xffffdfab, false);
        super.render(graphics, mouseX, mouseY, partialTick);
    }
}
