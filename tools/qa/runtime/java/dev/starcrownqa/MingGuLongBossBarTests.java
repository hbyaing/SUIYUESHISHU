package dev.starcrownqa;

import dev.starcrown.MingGuLongEntity;
import dev.starcrown.StarcrownDoll;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("starcrownbossqa")
@PrefixGameTestTemplate(false)
public final class MingGuLongBossBarTests {
    static ServerBossEvent bar(MingGuLongEntity dragon) {
        try {
            var f = MingGuLongEntity.class.getDeclaredField("bossEvent");
            f.setAccessible(true);
            return (ServerBossEvent)f.get(dragon);
        } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
    }

    @GameTest(template="empty", templateNamespace="starcrownbossqa", timeoutTicks=40)
    public static void healthTrackingAndLifecycle(GameTestHelper test) {
        var player = ChastityTests.playerAt(test, test.absolutePos(new BlockPos(1,4,1)));
        var dragon = StarcrownDoll.MINGGULONG.get().create(test.getLevel());
        var second = StarcrownDoll.MINGGULONG.get().create(test.getLevel());
        try {
            ((Mob)dragon).setNoAi(true);
            ((Entity)dragon).setNoGravity(true);
            ((Entity)dragon).moveTo(player.getX()+4,player.getY(),player.getZ());
            test.getLevel().addFreshEntity(dragon);
            var bar=bar(dragon);
            ((Entity)dragon).tick();
            test.assertTrue(bar.getProgress()==1 && bar.isVisible(), "full health shown even with NoAI");
            ((LivingEntity)dragon).setHealth(333);
            ((Entity)dragon).tick();
            test.assertTrue(bar.getProgress()==.5F, "health loss updates to half");
            ((LivingEntity)dragon).heal(166.5F);
            ((Entity)dragon).tick();
            test.assertTrue(bar.getProgress()==.75F, "healing updates to three quarters");
            ((LivingEntity)dragon).getAttribute(Attributes.MAX_HEALTH).setBaseValue(999);
            ((Entity)dragon).tick();
            test.assertTrue(bar.getProgress()==.5F, "maximum health changes update ratio");
            ((Entity)dragon).setCustomName(Component.literal("Named Dragon"));
            dragon.startSeenByPlayer(player);
            dragon.startSeenByPlayer(player);
            test.assertTrue(bar.getPlayers().size()==1, "repeated tracking does not duplicate bars");
            var name=(net.minecraft.network.chat.contents.TranslatableContents)bar.getName().getContents();
            test.assertTrue(((Component)name.getArgs()[0]).getString().equals("Named Dragon"), "custom name preserved");
            dragon.stopSeenByPlayer(player);
            test.assertTrue(bar.getPlayers().isEmpty(), "leaving tracking removes player");
            dragon.startSeenByPlayer(player);
            test.assertTrue(bar.getPlayers().contains(player), "re-entering restores player");
            test.assertTrue(!bar.getId().equals(bar(second).getId()), "multiple dragons have distinct boss IDs");
            ((LivingEntity)dragon).setHealth(0);
            ((Entity)dragon).tick();
            test.assertTrue(bar.getProgress()==0 && !bar.isVisible(), "death hides bar");
            ((Entity)dragon).discard();
            test.assertTrue(bar.getPlayers().isEmpty(), "removal clears all subscribers");
            test.succeed();
        } finally {
            ((Entity)dragon).discard();
            ((Entity)second).discard();
            ChastityTests.close(player);
        }
    }
}
