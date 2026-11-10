package dev.starcrown;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.tags.TagKey;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
/** Shared classification for current and future dolls; confiscation preserves the exact stack. */
public final class DollAuthority {
 public static final TagKey<net.minecraft.world.item.Item> DOLLS=TagKey.create(Registries.ITEM,new ResourceLocation(StarcrownDoll.ID,"dolls"));
 public static boolean doll(ItemStack stack){return stack.getItem() instanceof StarcrownDoll.WearableDoll||stack.is(DOLLS);}
 public static void onAttack(Entity attacker,Entity victim){if(attacker==victim||!(attacker instanceof ServerPlayer source)||!(victim instanceof ServerPlayer target)||!DivineProtection.active(target))return;var head=DivinityInventory.equipped(source);if(!doll(head))return;
  var exact=head.copy();DivineAnchor.unlock(source);if(DivinityInventory.stack(source)==head)DivinityInventory.force(source,ItemStack.EMPTY);else source.getInventory().armor.set(3,ItemStack.EMPTY);DivineSeven.release(source);DivineWill.update(source,false);YuleHumility.updateSize(source);source.getInventory().setChanged();source.inventoryMenu.broadcastChanges();
  var escrow=target.getPersistentData().getList("starcrown_confiscated_dolls",net.minecraft.nbt.Tag.TAG_COMPOUND);var entry=new net.minecraft.nbt.CompoundTag();entry.putUUID("attacker",source.getUUID());entry.put("item",exact.save(new net.minecraft.nbt.CompoundTag()));escrow.add(entry);target.getPersistentData().put("starcrown_confiscated_dolls",escrow);
  source.displayClientMessage(net.minecraft.network.chat.Component.literal("你攻击了成然玩偶佩戴者，玩偶权柄已被没收。"),false);
 }
}
