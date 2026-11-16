package dev.starcrown;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.type.capability.ICurioItem;

/** Inventory icon and Curios head ornament share a single persistent fuel store. */
public final class EternalReturnItem extends Item implements ICurioItem {
    public EternalReturnItem() { super(new Properties().stacksTo(1).fireResistant().rarity(Rarity.EPIC)); }
    @Override public void initializeClient(java.util.function.Consumer<net.minecraftforge.client.extensions.common.IClientItemExtensions> consumer) {
        consumer.accept(new net.minecraftforge.client.extensions.common.IClientItemExtensions() {
            @Override public net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer getCustomRenderer() {
                return EternalHaloItemRenderer.instance();
            }
        });
    }
    @Override public boolean canEquip(SlotContext context, ItemStack stack) {
        return context.identifier().equals("head") && context.entity() instanceof Player p
                && (EternalReturn.equipped(p).isEmpty() || EternalReturn.equipped(p) == stack);
    }
    @Override public boolean canEquipFromUse(SlotContext context, ItemStack stack) { return true; }
    @Override public boolean overrideOtherStackedOnMe(ItemStack halo, ItemStack carried, Slot slot,
            ClickAction action, Player player, SlotAccess access) {
        if (action != ClickAction.SECONDARY || carried.isEmpty() || !slot.allowModification(player)) return false;
        if (!player.level().isClientSide) { EternalReturn.burn(player, halo, carried); slot.setChanged(); }
        return true;
    }
    @Override public boolean overrideStackedOnOther(ItemStack halo, Slot slot, ClickAction action, Player player) {
        if (action != ClickAction.SECONDARY || !slot.allowModification(player) || slot.getItem().isEmpty()) return false;
        if (!player.level().isClientSide) { EternalReturn.burn(player, halo, slot.getItem()); slot.setChanged(); }
        return true;
    }
    @Override public void appendHoverText(ItemStack stack, Level level, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.literal("Curios 头饰 · 高清立体神环").withStyle(ChatFormatting.GOLD));
        lines.add(Component.literal("柴薪：" + String.format(java.util.Locale.ROOT,"%,d",EternalReturn.kindling(stack))
                + " / 33,550,336").withStyle(ChatFormatting.YELLOW));
        lines.add(Component.literal(EternalReturn.awakened(stack) ? "永劫已觉醒" : "柴薪首次蓄满后永久觉醒").withStyle(ChatFormatting.LIGHT_PURPLE));
        lines.add(Component.literal("生命 +40% · 最终伤害 +20% · 减伤 20%").withStyle(ChatFormatting.GRAY));
        lines.add(Component.literal("每百万柴薪：最终伤害 +1%；高柴薪会侵蚀自身。").withStyle(ChatFormatting.GRAY));
        lines.add(Component.literal("将物品拖到神环上，右键焚烧整组（含容器内容）。").withStyle(ChatFormatting.RED));
        lines.add(Component.literal("12 枚火种后按 G 开启；再次按 G 释放终结技。").withStyle(ChatFormatting.GRAY));
        lines.add(Component.literal("致命伤自动回归 6 秒前；冷却 180 秒。").withStyle(ChatFormatting.GRAY));
    }
}
