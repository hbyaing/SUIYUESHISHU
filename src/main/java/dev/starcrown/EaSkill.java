package dev.starcrown;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

public enum EaSkill {
    ENUMA("enuma"),
    GATE_NORMAL("gate_normal"),
    GATE_FULL("gate_full"),
    CHAINS("chains");

    private static final String TAG = "EaSkill";
    private static final EaSkill[] CYCLE = values();
    private final String id;

    EaSkill(String id) {
        this.id = id;
    }

    public static EaSkill selected(ItemStack stack) {
        var tag = stack.getTag();
        if (tag != null) {
            String id = tag.getString(TAG);
            for (EaSkill skill : CYCLE) if (skill.id.equals(id)) return skill;
        }
        return ENUMA;
    }

    public Component display() {
        return Component.translatable("skill.starcrowndoll.ea." + id);
    }

    public static void cycle(ServerPlayer player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!stack.is(StarcrownDoll.EA.get()) || !player.isAlive() || player.isSpectator() || player.isUsingItem()) return;
        EaSkill next = CYCLE[(selected(stack).ordinal() + 1) % CYCLE.length];
        if (next == ENUMA) stack.removeTagKey(TAG);
        else stack.getOrCreateTag().putString(TAG, next.id);
        player.getInventory().setChanged();
        player.containerMenu.broadcastChanges();
        player.displayClientMessage(Component.translatable("message.starcrowndoll.ea.skill", next.display())
                .withStyle(ChatFormatting.GOLD), true);
    }
}
