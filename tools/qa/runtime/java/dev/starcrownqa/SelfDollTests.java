package dev.starcrownqa;

import com.mojang.authlib.properties.Property;
import dev.starcrown.StarcrownDoll;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("starcrownqa")
@PrefixGameTestTemplate(false)
public final class SelfDollTests {
    @GameTest(template = "empty")
    public static void placedSelfDollKeepsSkinOwner(GameTestHelper test) {
        var player = ChastityTests.player(test);
        try {
            var profile = player.getGameProfile();
            profile.getProperties().put("textures", new Property("textures", "qa-texture", "qa-signature"));
            profile.getProperties().put("other", new Property("other", "qa-extra"));
            var level = test.getLevel();
            var pos = test.absolutePos(new BlockPos(1, 1, 1));
            var block = StarcrownDoll.SELF_DOLL.get();
            var state = block.defaultBlockState();
            var stack = new ItemStack(StarcrownDoll.SELF_ITEM.get());
            level.setBlockAndUpdate(pos, state);
            block.setPlacedBy(level, pos, state, player, stack);

            var placed = (StarcrownDoll.DollEntity) level.getBlockEntity(pos);
            var reloaded = new StarcrownDoll.DollEntity(pos, state);
            reloaded.load(placed.saveWithoutMetadata());
            var synchronizedCopy = new StarcrownDoll.DollEntity(pos, state);
            synchronizedCopy.handleUpdateTag(placed.getUpdateTag());
            for (var copy : new StarcrownDoll.DollEntity[]{reloaded, synchronizedCopy}) {
                var owner = copy.skinOwner();
                test.assertTrue(owner != null && owner.equals(profile), "owner identity survives save and sync");
                var textures = owner.getProperties().get("textures");
                test.assertTrue(textures.size() == 1 && "qa-signature".equals(textures.iterator().next().getSignature()),
                        "signed skin data survives save and sync");
                test.assertTrue(!owner.getProperties().containsKey("other") && !copy.isPlaying(),
                        "only skin data is copied and the doll stays silent");
            }
            test.succeed();
        } finally {
            ChastityTests.close(player);
        }
    }
}
