package dev.starcrownqa;

import dev.starcrown.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.*;
import net.minecraft.world.item.*;
import net.minecraftforge.gametest.*;

@GameTestHolder("fateqa") @PrefixGameTestTemplate(false)
public final class FateTests {
    private static net.minecraft.server.level.ServerPlayer player(GameTestHelper h){
        var p=ChastityTests.player(h);p.getAbilities().invulnerable=true;p.setNoGravity(true);
        // Synthetic GameTest players have no connection-list tick; drive the actual vanilla use timer.
        for(int i=1;i<=165;i++)h.runAtTickTime(i,p::doTick);
        return p;
    }
    private static int count(net.minecraft.server.level.ServerPlayer p,Item item){
        int total=0;for(int i=0;i<p.getInventory().getContainerSize();i++)if(p.getInventory().getItem(i).is(item))total+=p.getInventory().getItem(i).getCount();return total;
    }
    @GameTest(template="empty",timeoutTicks=200)
    public static void completeAndConsumeKey(GameTestHelper h){
        var p=player(h);var key=new ItemStack(StarcrownDoll.BAB_ILU.get());p.setItemInHand(InteractionHand.MAIN_HAND,key);
        key.use(p.level(),p,InteractionHand.MAIN_HAND);
        h.runAtTickTime(170,()->{try{
            h.assertTrue(p.getMainHandItem().is(StarcrownDoll.EA.get()),"completed retrieval replaces the key in the active hand");
            h.assertTrue(count(p,StarcrownDoll.EA.get())==1&&count(p,StarcrownDoll.BAB_ILU.get())==0,"one sword and no retained key");
            h.assertTrue(p.getCooldowns().isOnCooldown(StarcrownDoll.BAB_ILU.get()),"completion applies key cooldown");
            p.setItemInHand(InteractionHand.OFF_HAND,new ItemStack(StarcrownDoll.BAB_ILU.get()));
            var result=StarcrownDoll.BAB_ILU.get().use(p.level(),p,InteractionHand.OFF_HAND);
            h.assertTrue(result.getResult()==InteractionResult.FAIL&&!p.isUsingItem(),"existing Ea prevents repeated retrieval");h.succeed();
        }finally{ChastityTests.close(p);}});
    }
    @GameTest(template="empty",timeoutTicks=200)
    public static void cancellationDoesNotGrantSword(GameTestHelper h){
        var p=player(h);var key=new ItemStack(StarcrownDoll.BAB_ILU.get());p.setItemInHand(InteractionHand.OFF_HAND,key);
        key.use(p.level(),p,InteractionHand.OFF_HAND);
        h.runAtTickTime(75,p::releaseUsingItem);
        h.runAtTickTime(170,()->{try{
            h.assertTrue(p.getOffhandItem()==key&&count(p,StarcrownDoll.EA.get())==0,"release cancels with no sword and no consumed key");
            h.assertTrue(!p.getCooldowns().isOnCooldown(StarcrownDoll.BAB_ILU.get()),"cancellation does not spend completion cooldown");h.succeed();
        }finally{ChastityTests.close(p);}});
    }
    @GameTest(template="empty",timeoutTicks=200)
    public static void fullInventoryConsumesKeyWithoutDrop(GameTestHelper h){
        var p=player(h);for(int i=0;i<36;i++)p.getInventory().setItem(i,new ItemStack(Items.STONE,64));
        var key=new ItemStack(StarcrownDoll.BAB_ILU.get());p.setItemInHand(InteractionHand.MAIN_HAND,key);key.use(p.level(),p,InteractionHand.MAIN_HAND);
        h.runAtTickTime(170,()->{try{
            h.assertTrue(p.getMainHandItem().is(StarcrownDoll.EA.get()),"full inventory still receives sword in used hand");
            var drops=p.level().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,p.getBoundingBox().inflate(6),e->e.getItem().is(StarcrownDoll.BAB_ILU.get()));
            h.assertTrue(drops.isEmpty()&&count(p,StarcrownDoll.BAB_ILU.get())==0,"full inventory consumes the key without dropping another");
            drops.forEach(net.minecraft.world.entity.Entity::discard);h.succeed();
        }finally{ChastityTests.close(p);}});
    }
    @GameTest(template="empty",timeoutTicks=20)
    public static void earlyCompletionCannotMintSword(GameTestHelper h){
        var p=player(h);try{
            var key=new ItemStack(StarcrownDoll.BAB_ILU.get());p.setItemInHand(InteractionHand.MAIN_HAND,key);key.use(p.level(),p,InteractionHand.MAIN_HAND);
            var result=key.finishUsingItem(p.level(),p);
            h.assertTrue(result==key&&count(p,StarcrownDoll.EA.get())==0,"finish without elapsed duration cannot grant a sword");
            p.releaseUsingItem();h.succeed();
        }finally{ChastityTests.close(p);}
    }
    @GameTest(template="empty",timeoutTicks=200)
    public static void offhandCompletionPreservesMainHand(GameTestHelper h){
        var p=player(h);var main=new ItemStack(Items.STICK);var key=new ItemStack(StarcrownDoll.BAB_ILU.get());
        p.setItemInHand(InteractionHand.MAIN_HAND,main);p.setItemInHand(InteractionHand.OFF_HAND,key);key.use(p.level(),p,InteractionHand.OFF_HAND);
        h.runAtTickTime(170,()->{try{
            h.assertTrue(p.getOffhandItem().is(StarcrownDoll.EA.get())&&p.getMainHandItem()==main,"offhand retrieval replaces only the used hand");
            h.assertTrue(count(p,StarcrownDoll.BAB_ILU.get())==0&&count(p,StarcrownDoll.EA.get())==1,"offhand completion consumes exactly the used key");h.succeed();
        }finally{ChastityTests.close(p);}});
    }
    @GameTest(template="empty",timeoutTicks=200)
    public static void replacementKeyCannotInheritCast(GameTestHelper h){
        var p=player(h);var key=new ItemStack(StarcrownDoll.BAB_ILU.get());p.setItemInHand(InteractionHand.MAIN_HAND,key);key.use(p.level(),p,InteractionHand.MAIN_HAND);
        h.runAtTickTime(50,()->p.setItemInHand(InteractionHand.MAIN_HAND,key.copy()));
        h.runAtTickTime(170,()->{try{
            h.assertTrue(count(p,StarcrownDoll.EA.get())==0&&count(p,StarcrownDoll.BAB_ILU.get())==1,"a replacement stack cannot complete the old cast");h.succeed();
        }finally{ChastityTests.close(p);}});
    }
}
