package dev.starcrownqa;

import dev.starcrown.AsWrittenInventory;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("starcrownqa")
@PrefixGameTestTemplate(false)
public final class AsWrittenInventoryTests {
    private static ItemStack fixture(int count) {
        var stack = new ItemStack(Items.PAPER, count);
        stack.setHoverName(Component.literal("As Written QA"));
        stack.getOrCreateTag().putString("proof", "preserve-me");
        return stack;
    }

    @GameTest(template = "empty")
    public static void independentStorageAndPlayerSave(GameTestHelper test) {
        var player = ChastityTests.player(test);
        try {
            var contents = AsWrittenInventory.get(player);
            test.assertTrue(contents.getSlots() == 2 && contents.getStackInSlot(0).isEmpty() && contents.getStackInSlot(1).isEmpty(), "two empty slots without Curios");
            test.assertTrue(!contents.isItemValid(0, new ItemStack(Items.STONE)), "ordinary items rejected");
            test.assertTrue(contents.isItemValid(0, fixture(1)) && contents.getSlotLimit(0) == 1, "tagged fixture accepted; one per slot");
            var remainder = contents.insertItem(0, fixture(4), false);
            test.assertTrue(remainder.getCount() == 3 && contents.getStackInSlot(0).getCount() == 1, "insertion conserves stack count");
            var saved = player.serializeNBT().getCompound("ForgeCaps").getCompound(AsWrittenInventory.ID.toString());
            var restored = new AsWrittenInventory.Contents();
            restored.deserializeNBT(saved);
            test.assertTrue(ItemStack.isSameItemSameTags(restored.getStackInSlot(0), fixture(1)), "capability participates in player save, including item NBT");
            saved.putInt("Size", 99);
            restored.deserializeNBT(saved);
            test.assertTrue(restored.getSlots() == 2, "external NBT cannot change the fixed two-slot capacity");
            test.succeed();
        } finally { ChastityTests.close(player); }
    }

    @GameTest(template = "empty")
    public static void menuTransfersConserveItems(GameTestHelper test) {
        var player = ChastityTests.player(test);
        try {
            var contents = AsWrittenInventory.get(player);
            player.getInventory().setItem(9, fixture(3));
            var menu = player.inventoryMenu;
            test.assertTrue(menu.slots.size() == 49 && menu.stillValid(player), "two accessory slots plus real player inventory");
            menu.quickMoveStack(player, 9);
            menu.quickMoveStack(player, 9);
            menu.quickMoveStack(player, 9);
            test.assertTrue(contents.getStackInSlot(0).getCount() == 1 && contents.getStackInSlot(1).getCount() == 1
                    && player.getInventory().countItem(Items.PAPER) == 1, "shift click fills each slot once and never duplicates");
            menu.quickMoveStack(player, 47);
            menu.quickMoveStack(player, 48);
            test.assertTrue(contents.getStackInSlot(0).isEmpty() && contents.getStackInSlot(1).isEmpty()
                    && player.getInventory().countItem(Items.PAPER) == 3, "shift click returns every item");
            test.assertTrue(player.getInventory().items.stream().filter(s -> s.is(Items.PAPER)).allMatch(s -> ItemStack.isSameItemSameTags(s, fixture(1))), "custom item NBT survives moves");
            test.assertTrue(!menu.getSlot(47).mayPlace(new ItemStack(Items.STONE)) && !menu.getSlot(48).mayPlace(new ItemStack(Items.STONE)), "server menu enforces both slot validators");
            test.succeed();
        } finally { ChastityTests.close(player); }
    }

    @GameTest(template = "empty")
    public static void cloneRevivesInvalidatedCapabilities(GameTestHelper test) {
        var original = ChastityTests.player(test);
        var replacement = ChastityTests.player(test);
        try {
            AsWrittenInventory.get(original).setStackInSlot(1, fixture(1));
            original.invalidateCaps();
            AsWrittenInventory.clone(new PlayerEvent.Clone(replacement, original, false));
            test.assertTrue(ItemStack.isSameItemSameTags(AsWrittenInventory.get(replacement).getStackInSlot(1), fixture(1)), "dimension/End respawn copies data after Forge invalidation");
            AsWrittenInventory.get(replacement).getStackInSlot(1).getOrCreateTag().putString("proof", "new-owner");
            original.reviveCaps();
            test.assertTrue("preserve-me".equals(AsWrittenInventory.get(original).getStackInSlot(1).getTag().getString("proof")), "copy does not share mutable NBT with old player");
            test.succeed();
        } finally { ChastityTests.close(original); ChastityTests.close(replacement); }
    }

    @GameTest(template = "empty")
    public static void deathDropsOnlyOnce(GameTestHelper test) {
        var player = ChastityTests.player(test);
        var replacement = ChastityTests.player(test);
        var rule = player.level().getGameRules().getRule(GameRules.RULE_KEEPINVENTORY);
        boolean previous = rule.get();
        try {
            rule.set(false, player.server);
            var contents = AsWrittenInventory.get(player);
            contents.setStackInSlot(0, fixture(1));
            contents.setStackInSlot(1, fixture(1));
            var drops = new java.util.ArrayList<ItemEntity>();
            var event = new LivingDropsEvent(player, player.damageSources().generic(), drops, 0, false);
            AsWrittenInventory.drops(event);
            AsWrittenInventory.drops(event);
            test.assertTrue(drops.size() == 2 && drops.stream().allMatch(e -> ItemStack.isSameItemSameTags(e.getItem(), fixture(1))), "normal death drops each stack once with NBT");
            player.invalidateCaps();
            AsWrittenInventory.clone(new PlayerEvent.Clone(replacement, player, true));
            test.assertTrue(AsWrittenInventory.get(replacement).getStackInSlot(0).isEmpty() && AsWrittenInventory.get(replacement).getStackInSlot(1).isEmpty(), "dropped equipment is not duplicated on respawn");
            test.succeed();
        } finally { rule.set(previous, player.server); ChastityTests.close(player); ChastityTests.close(replacement); }
    }

    @GameTest(template = "empty")
    public static void keepInventoryRetainsEquipment(GameTestHelper test) {
        var player = ChastityTests.player(test);
        var replacement = ChastityTests.player(test);
        var rule = player.level().getGameRules().getRule(GameRules.RULE_KEEPINVENTORY);
        boolean previous = rule.get();
        try {
            rule.set(true, player.server);
            AsWrittenInventory.get(player).setStackInSlot(0, fixture(1));
            var drops = new java.util.ArrayList<ItemEntity>();
            AsWrittenInventory.drops(new LivingDropsEvent(player, player.damageSources().generic(), drops, 0, false));
            player.invalidateCaps();
            AsWrittenInventory.clone(new PlayerEvent.Clone(replacement, player, true));
            test.assertTrue(drops.isEmpty() && ItemStack.isSameItemSameTags(AsWrittenInventory.get(replacement).getStackInSlot(0), fixture(1)), "keepInventory preserves accessory on real death clone");
            test.succeed();
        } finally { rule.set(previous, player.server); ChastityTests.close(player); ChastityTests.close(replacement); }
    }
}
