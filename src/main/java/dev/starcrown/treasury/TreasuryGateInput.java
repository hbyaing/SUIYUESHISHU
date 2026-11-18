package dev.starcrown.treasury;

import dev.starcrown.EaSkill;
import dev.starcrown.StarcrownDoll;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

@Mod.EventBusSubscriber(modid = StarcrownDoll.ID, value = Dist.CLIENT)
public final class TreasuryGateInput {
    private record Hold(LocalPlayer player, ClientLevel level, InteractionHand hand, int slot, ItemStack stack) {
        boolean stillHeld(Minecraft mc) {
            return mc.player == player && mc.level == level && player.isUsingItem()
                    && player.getUsedItemHand() == hand && player.getInventory().selected == slot
                    && player.getItemInHand(hand) == stack && player.getUseItem() == stack && isGate(stack);
        }
    }

    private static Hold hold;
    private static boolean interrupted;

    private TreasuryGateInput() {}

    private static boolean isGate(ItemStack stack) {
        if (stack.is(StarcrownDoll.GATE_OF_BABYLON.get())) return true;
        if (!stack.is(StarcrownDoll.EA.get())) return false;
        EaSkill skill = EaSkill.selected(stack);
        return skill == EaSkill.GATE_NORMAL || skill == EaSkill.GATE_FULL;
    }

    private static void checkHold(Minecraft mc) {
        if (hold != null && !hold.stillHeld(mc)) {
            hold = null;
            interrupted = true;
        }
    }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        checkHold(mc);
        LocalPlayer player = mc.player;
        if (hold != null || interrupted || player == null || !mc.options.keyUse.isDown() || !player.isUsingItem()) return;
        ItemStack stack = player.getUseItem();
        if (isGate(stack)) hold = new Hold(player, mc.level, player.getUsedItemHand(), player.getInventory().selected, stack);
    }

    @SubscribeEvent
    public static void use(InputEvent.InteractionKeyMappingTriggered event) {
        if (!event.isUseItem()) return;
        Minecraft mc = Minecraft.getInstance();
        checkHold(mc);
        if (interrupted && mc.player != null && !mc.player.isShiftKeyDown() && isGate(mc.player.getItemInHand(event.getHand()))) {
            event.setSwingHand(false);
            event.setCanceled(true);
        }
    }

    // World changes may reset KeyMapping.isDown while the button is physically held.
    // A fresh press also clears a release missed while the game window lacked focus.
    private static void keyEdge(int action) {
        if (action == GLFW.GLFW_PRESS || action == GLFW.GLFW_RELEASE) {
            hold = null;
            interrupted = false;
            if (action == GLFW.GLFW_RELEASE) {
                Minecraft mc = Minecraft.getInstance();
                LocalPlayer player = mc.player;
                // A release and re-press between client ticks would otherwise keep the old gate session alive.
                if (mc.gameMode != null && player != null && player.isUsingItem() && isGate(player.getUseItem())) {
                    mc.gameMode.releaseUsingItem(player);
                }
            }
        }
    }

    @SubscribeEvent
    public static void mouse(InputEvent.MouseButton.Pre event) {
        if (Minecraft.getInstance().options.keyUse.matchesMouse(event.getButton())) keyEdge(event.getAction());
    }

    @SubscribeEvent
    public static void keyboard(InputEvent.Key event) {
        if (Minecraft.getInstance().options.keyUse.matches(event.getKey(), event.getScanCode())) keyEdge(event.getAction());
    }
}
