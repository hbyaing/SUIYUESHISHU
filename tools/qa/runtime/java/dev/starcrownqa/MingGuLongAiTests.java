package dev.starcrownqa;

import dev.starcrown.MingGuLongEntity;
import dev.starcrown.StarcrownDoll;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.core.BlockPos;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("starcrownqa")
@PrefixGameTestTemplate(false)
public final class MingGuLongAiTests {
    private MingGuLongAiTests() {}

    @GameTest(template = "empty", templateNamespace = "starcrownqa", timeoutTicks = 240)
    public static void pursuesAndAttacksSurvivalPlayer(GameTestHelper test) {
            ServerPlayer player = ChastityTests.playerAt(test, new BlockPos(1024, 200, 1024));
        var level = player.serverLevel();
        var dragon = StarcrownDoll.MINGGULONG.get().create(level);
        try {
            player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
            player.getAbilities().invulnerable = false;
            player.setInvulnerable(false);

            BlockPos floor = player.blockPosition().below();
            for (int x = -12; x <= 28; x++) {
                for (int z = -10; z <= 10; z++) {
                    BlockPos platform = floor.offset(x, 0, z);
                    level.getChunkAt(platform);
                    level.setBlockAndUpdate(platform, Blocks.STONE.defaultBlockState());
                }
            }

            dragon.moveTo(player.getX() + 10.0D, player.getY(), player.getZ(), 0.0F, 0.0F);
            test.assertTrue(level.addFreshEntity(dragon), "spawn egg entity accepted by the server");
            test.assertTrue(!player.isCreative() && !player.isSpectator() && !player.isInvulnerable(),
                "test target is a vulnerable survival player");
            test.assertTrue(level.getEntitiesOfClass(net.minecraft.world.entity.player.Player.class,
                dragon.getBoundingBox().inflate(96.0D)).contains(player), "server entity scan sees the player");
            double initialDistance = dragon.distanceTo(player);
            float initialHealth = player.getHealth();
            String[] samples = new String[3];

            test.runAfterDelay(45, () -> samples[0] = sample(level, dragon, player));
            test.runAfterDelay(60, () -> samples[1] = sample(level, dragon, player));
            test.runAfterDelay(100, () -> samples[2] = sample(level, dragon, player));
            test.runAfterDelay(230, () -> {
                String diagnostics = "initial=" + initialDistance + ", current=" + dragon.distanceTo(player)
                    + ", playerHealth=" + player.getHealth() + ", samples=" + java.util.Arrays.toString(samples);
                test.assertTrue(dragon.distanceTo(player) < initialDistance - 2.0D, "dragon closes distance: " + diagnostics);
                test.assertTrue(player.getHealth() < initialHealth, "claw contact frame deals melee damage: " + diagnostics);
                dragon.discard();
                ChastityTests.close(player);
                test.succeed();
            });
        } catch (Throwable error) {
            dragon.discard();
            ChastityTests.close(player);
            throw error;
        }
    }

    private static String sample(net.minecraft.server.level.ServerLevel level, MingGuLongEntity dragon, ServerPlayer player) {
        return "time=" + level.getGameTime() + ", dragonTick=" + dragon.tickCount + ", playerTick=" + player.tickCount
            + ", target=" + (dragon.getTarget() == player) + ", alive=" + player.isAlive() + ", removed=" + player.isRemoved()
            + ", health=" + player.getHealth() + ", animation=" + dragon.getAnimationName()
            + ", dragon=" + dragon.position() + ", player=" + player.position()
            + ", sight=" + dragon.hasLineOfSight(player) + ", onGround=" + dragon.onGround();
    }
}
