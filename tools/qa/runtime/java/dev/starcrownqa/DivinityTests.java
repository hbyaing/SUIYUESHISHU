package dev.starcrownqa;

import dev.starcrown.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.*;
import net.minecraft.world.inventory.ClickType;
import net.minecraftforge.gametest.*;

@GameTestHolder("starcrownqa") @PrefixGameTestTemplate(false)
public final class DivinityTests {
    static Item[] dolls() { return new Item[]{StarcrownDoll.ITEM.get(),StarcrownDoll.TWILIGHT_ITEM.get(),StarcrownDoll.YULE_ITEM.get(),StarcrownDoll.CALM_ITEM.get(),StarcrownDoll.SELF_ITEM.get()}; }
    static void clear(net.minecraft.server.level.ServerPlayer p) {
        DivineAnchor.unlock(p); DivinityInventory.force(p,ItemStack.EMPTY); p.getInventory().armor.set(3,ItemStack.EMPTY); DivineWill.update(p,false);
    }
    @GameTest(template="empty") public static void everyDollUsesEitherLocation(GameTestHelper test) {
        var p=ChastityTests.player(test);
        try {
            for(var item:dolls()) for(boolean god:new boolean[]{false,true}) {
                clear(p);var stack=new ItemStack(item);stack.getOrCreateTag().putString("proof","unchanged");
                if(god)DivinityInventory.own(p).setStackInSlot(0,stack);else p.setItemSlot(EquipmentSlot.HEAD,stack);
                test.assertTrue(DivinityInventory.equipped(p).is(item),"effective doll in either position: "+item);
                test.assertTrue(DivineWill.wearing(p)==(item==StarcrownDoll.ITEM.get()),"supreme predicate");
                test.assertTrue(TwilightCrown.wearing(p)==(item==StarcrownDoll.TWILIGHT_ITEM.get()),"twilight predicate");
                test.assertTrue(YuleHumility.active(p)==(item==StarcrownDoll.YULE_ITEM.get()),"yule predicate");
                test.assertTrue(CalmYule.active(p)==(item==StarcrownDoll.CALM_ITEM.get()),"calm predicate");
            }
            test.succeed();
        } finally {clear(p);ChastityTests.close(p);}
    }
    @GameTest(template="empty") public static void allPairsAreMutuallyExclusive(GameTestHelper test) {
        var p=ChastityTests.player(test);
        try {
            for(var first:dolls()) for(var second:dolls()) {
                clear(p);p.setItemSlot(EquipmentSlot.HEAD,new ItemStack(first));
                var remainder=DivinityInventory.own(p).insertItem(0,new ItemStack(second),false);
                test.assertTrue(remainder.is(second)&&DivinityInventory.stack(p).isEmpty(),"head blocks any second divinity, no consumption");
                clear(p);DivinityInventory.own(p).setStackInSlot(0,new ItemStack(first));
                test.assertTrue(!p.inventoryMenu.getSlot(5).mayPlace(new ItemStack(second)),"god blocks all dolls in real helmet slot");
                test.assertTrue(p.inventoryMenu.getSlot(5).mayPlace(new ItemStack(Items.DIAMOND_HELMET)),"ordinary helmet is allowed");
                p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,new ItemStack(second));
                var used=second.use(p.level(),p,net.minecraft.world.InteractionHand.MAIN_HAND);
                test.assertTrue(used.getResult()==net.minecraft.world.InteractionResult.FAIL&&p.getMainHandItem().getCount()==1&&p.getItemBySlot(EquipmentSlot.HEAD).isEmpty(),"right click cannot bypass mutual exclusion or consume doll");
            }
            test.succeed();
        } finally {clear(p);ChastityTests.close(p);}
    }
    @GameTest(template="empty") public static void vanillaInventoryClicksAndCraftingRemainIntact(GameTestHelper test) {
        var p=ChastityTests.player(test);
        try {
            var menu=p.inventoryMenu;
            test.assertTrue(menu.slots.size()==49&&menu.getSlot(0) instanceof net.minecraft.world.inventory.ResultSlot,"native inventory and crafting retained, only three slots appended");
            p.getInventory().setItem(9,new ItemStack(StarcrownDoll.SELF_ITEM.get()));
            menu.clicked(9,0,ClickType.QUICK_MOVE,p);
            test.assertTrue(DivinityInventory.stack(p).is(StarcrownDoll.SELF_ITEM.get())&&p.getInventory().getItem(9).isEmpty(),"native shift click equips divinity");
            menu.setCarried(new ItemStack(StarcrownDoll.YULE_ITEM.get()));menu.clicked(5,0,ClickType.PICKUP,p);
            test.assertTrue(menu.getCarried().is(StarcrownDoll.YULE_ITEM.get())&&p.getItemBySlot(EquipmentSlot.HEAD).isEmpty(),"helmet pickup rejects second divinity without loss");
            menu.setCarried(ItemStack.EMPTY);menu.clicked(46,0,ClickType.QUICK_MOVE,p);
            test.assertTrue(DivinityInventory.stack(p).isEmpty()&&p.getInventory().countItem(StarcrownDoll.SELF_ITEM.get())==1,"native shift click unequips");
            p.getInventory().setItem(10,new ItemStack(Items.PAPER,3));menu.clicked(10,0,ClickType.QUICK_MOVE,p);
            test.assertTrue(AsWrittenInventory.get(p).getStackInSlot(0).getCount()==1&&AsWrittenInventory.get(p).getStackInSlot(1).getCount()==1&&p.getInventory().countItem(Items.PAPER)==1,"book slots still work in original inventory");
            p.getInventory().setItem(11,new ItemStack(Items.OAK_PLANKS,2));menu.setCarried(p.getInventory().removeItem(11,2));
            menu.clicked(1,1,ClickType.PICKUP,p);menu.clicked(3,1,ClickType.PICKUP,p);
            test.assertTrue(menu.getSlot(0).getItem().is(Items.STICK),"2x2 crafting still updates the vanilla result");
            test.succeed();
        } finally {clear(p);ChastityTests.close(p);}
    }
    @GameTest(template="empty") public static void supremeAnchorStaysInDivinitySlotAndNormalRemovalReleases(GameTestHelper test) {
        var p=ChastityTests.player(test);
        try {
            p.setItemSlot(EquipmentSlot.HEAD,new ItemStack(Items.DIAMOND_HELMET));
            var stack=new ItemStack(StarcrownDoll.ITEM.get());stack.getOrCreateTag().putString("proof","anchor");
            DivinityInventory.own(p).setStackInSlot(0,stack);DivineAnchor.observe(p);
            DivinityInventory.own(p).rawSet(ItemStack.EMPTY);
            test.assertTrue(DivineProtection.active(p),"raw deletion still protected before next tick");DivineAnchor.repair(p);
            test.assertTrue(ItemStack.isSameItemSameTags(DivinityInventory.stack(p),stack)&&p.getItemBySlot(EquipmentSlot.HEAD).is(Items.DIAMOND_HELMET),"anchor restores correct slot and preserves real helmet");
            p.inventoryMenu.clicked(46,0,ClickType.QUICK_MOVE,p);DivineAnchor.repair(p);
            test.assertTrue(DivinityInventory.stack(p).isEmpty()&&!DivineWill.wearing(p)&&!DivineAnchor.locked(p)&&p.getInventory().countItem(StarcrownDoll.ITEM.get())==1,"normal slot removal releases protection without duplication");
            test.succeed();
        } finally {clear(p);ChastityTests.close(p);}
    }
    @GameTest(template="empty") public static void divinityPersistsClonesAndDropsOnce(GameTestHelper test) {
        var p=ChastityTests.player(test);var next=ChastityTests.player(test);
        var rule=p.level().getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_KEEPINVENTORY);boolean before=rule.get();
        try {
            var stack=new ItemStack(StarcrownDoll.CALM_ITEM.get());stack.getOrCreateTag().putString("proof","stored");DivinityInventory.own(p).setStackInSlot(0,stack);
            var saved=p.serializeNBT().getCompound("ForgeCaps").getCompound(DivinityInventory.ID.toString());
            var restored=new DivinityInventory.Contents(p);restored.deserializeNBT(saved);test.assertTrue(ItemStack.isSameItemSameTags(restored.getStackInSlot(0),stack),"player save includes divinity NBT");
            rule.set(true,p.server);var drops=new java.util.ArrayList<net.minecraft.world.entity.item.ItemEntity>();
            var event=new net.minecraftforge.event.entity.living.LivingDropsEvent(p,p.damageSources().generic(),drops,0,false);DivinityInventory.drops(event);
            p.invalidateCaps();DivinityInventory.clone(new net.minecraftforge.event.entity.player.PlayerEvent.Clone(next,p,true));
            test.assertTrue(drops.isEmpty()&&ItemStack.isSameItemSameTags(DivinityInventory.stack(next),stack),"keepInventory death clone");p.reviveCaps();
            rule.set(false,p.server);DivinityInventory.drops(event);DivinityInventory.drops(event);
            test.assertTrue(drops.size()==1&&DivinityInventory.stack(p).isEmpty()&&ItemStack.isSameItemSameTags(drops.get(0).getItem(),stack),"normal death drops exactly once");test.succeed();
        } finally {rule.set(before,p.server);clear(p);clear(next);ChastityTests.close(p);ChastityTests.close(next);}
    }
}
