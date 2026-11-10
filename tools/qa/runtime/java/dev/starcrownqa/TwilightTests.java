package dev.starcrownqa;

import dev.starcrown.DivineAnchor;
import dev.starcrown.DivineEternity;
import dev.starcrown.DivineProtection;
import dev.starcrown.DivineSeven;
import dev.starcrown.DivineWill;
import dev.starcrown.StarcrownDoll;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;

@GameTestHolder("starcrownqa")
@PrefixGameTestTemplate(false)
public final class TwilightTests {
    @GameTest(template = "empty")
    public static void supremeStrike(GameTestHelper test) {
        var player = ChastityTests.player(test);
        var target = ChastityTests.spawn(EntityType.COW, player, 3, 0, 0);
        try {
            player.setItemSlot(EquipmentSlot.HEAD, new ItemStack(StarcrownDoll.ITEM.get()));
            target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100);
            target.getAttribute(Attributes.ARMOR).setBaseValue(20);
            target.getAttribute(Attributes.ARMOR_TOUGHNESS).setBaseValue(12);
            target.setHealth(100);
            target.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 200, 4));
            target.hurt(player.damageSources().playerAttack(player), 2);
            test.assertTrue(target.getHealth() == 80, "Supreme hit is 10x despite armor and Resistance V");
            test.succeed();
        } finally {
            DivineAnchor.unlock(player);
            ChastityTests.close(player);
            target.discard();
        }
    }

    @GameTest(template = "empty")
    public static void supremeSecondaryDamage(GameTestHelper test) {
        var player = ChastityTests.player(test);
        var target = ChastityTests.spawn(EntityType.COW, player, 3, 0, 0);
        var first = new net.minecraft.world.entity.projectile.Arrow(test.getLevel(), player);
        var followup = new net.minecraft.world.entity.projectile.Arrow(test.getLevel(), player);
        followup.setOwner(first);
        var type = player.damageSources().magic().typeHolder();
        var indirect = new net.minecraft.world.damagesource.DamageSource(type, followup, null);
        class Bonus {
            @net.minecraftforge.eventbus.api.SubscribeEvent(priority = net.minecraftforge.eventbus.api.EventPriority.LOWEST)
            public void apply(net.minecraftforge.event.entity.living.LivingHurtEvent event) {
                if (event.getEntity() == target && event.getSource() == indirect) event.setAmount(event.getAmount() + 3);
            }
        }
        var bonus = new Bonus();
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(bonus);
        try {
            player.setItemSlot(EquipmentSlot.HEAD, new ItemStack(StarcrownDoll.ITEM.get()));
            target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100);
            target.setHealth(100);
            target.hurt(indirect, 2);
            test.assertTrue(target.getHealth() == 50, "owned secondary hit and late hurt-event bonus receive exactly one multiplier");
            target.invulnerableTime = 0;
            target.hurt(player.damageSources().magic(), 2);
            test.assertTrue(target.getHealth() == 48, "unattributed damage remains ordinary");
            test.succeed();
        } finally {
            net.minecraftforge.common.MinecraftForge.EVENT_BUS.unregister(bonus);
            DivineAnchor.unlock(player);
            ChastityTests.close(player);
            target.discard();
        }
    }

    @GameTest(template = "empty")
    public static void twilightWearAndRescue(GameTestHelper test) {
        var player = ChastityTests.player(test);
        var cow = ChastityTests.spawn(EntityType.COW, player, 3, 0, 0);
        var zombie = ChastityTests.spawn(EntityType.ZOMBIE, player, 8, 0, 0);
        var wither = ChastityTests.spawn(EntityType.WITHER, player, 12, 0, 0);
        try {
            player.setItemSlot(EquipmentSlot.HEAD, new ItemStack(StarcrownDoll.TWILIGHT_ITEM.get()));
            var tick = new TickEvent.PlayerTickEvent(TickEvent.Phase.END, player);
            DivineWill.tick(tick);
            ObfuscationReflectionHelper.setPrivateValue(ServerPlayer.class, player, 0, "f_8921_");
            test.assertTrue(player.getAbilities().mayfly && player.hasEffect(MobEffects.NIGHT_VISION), "flight and night vision");
            test.assertTrue(!DivineProtection.active(player), "no Supreme protection");
            DivineSeven.action(player, 0, 0);
            test.assertTrue(DivineEternity.sealed(zombie) && !DivineEternity.sealed(wither), "domain seals ordinary mobs only");
            cow.hurt(player.damageSources().playerAttack(player), 2);
            test.assertTrue(cow.getHealth() == 7, "Twilight hit is 1.5x");
            player.setHealth(20);
            player.hurt(player.damageSources().generic(), 100);
            test.assertTrue(player.getHealth() == 13, "hit capped to 35 percent max health, remaining " + player.getHealth());
            player.setHealth(2);
            player.invulnerableTime = 0;
            player.hurt(player.damageSources().generic(), 100);
            test.assertTrue(player.getHealth() == 8 && player.isAlive(), "free rescue at 40 percent max health");
            player.invulnerableTime = 0;
            player.hurt(player.damageSources().generic(), 100);
            test.assertTrue(player.getHealth() == 8, "rescue grace prevents repeated damage");
            player.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
            DivineWill.tick(tick);
            test.assertTrue(!player.getAbilities().mayfly && !player.hasEffect(MobEffects.NIGHT_VISION)
                    && !DivineEternity.sealed(zombie), "unequipping releases flight, vision and domain");
            test.succeed();
        } finally {
            ChastityTests.close(player);
            cow.discard();
            zombie.discard();
            wither.discard();
        }
    }

    @GameTest(template = "empty")
    public static void twilightAnchorAndMining(GameTestHelper test) {
        var player = ChastityTests.player(test);
        var level = player.serverLevel();
        try {
            player.setGameMode(GameType.SURVIVAL);
            player.setItemSlot(EquipmentSlot.HEAD, new ItemStack(StarcrownDoll.TWILIGHT_ITEM.get()));
            var start = player.position();
            level.setBlockAndUpdate(player.blockPosition(), Blocks.AIR.defaultBlockState());
            level.setBlockAndUpdate(player.blockPosition().above(), Blocks.AIR.defaultBlockState());
            level.setBlockAndUpdate(player.blockPosition().below(), Blocks.STONE.defaultBlockState());
            DivineSeven.action(player, 5, 0);
            player.moveTo(start.add(3, 0, 0));
            player.giveExperiencePoints(999);
            DivineSeven.action(player, 6, 0);
            test.assertTrue(player.position().distanceToSqr(start) > 1 && player.totalExperience == 999, "insufficient XP leaves player in place");
            player.giveExperiencePoints(251);
            DivineSeven.action(player, 6, 2);
            test.assertTrue(player.totalExperience == 1250, "missing anchor leaves XP untouched");
            DivineSeven.action(player, 6, 0);
            test.assertTrue(player.position().distanceToSqr(start) < .01 && player.totalExperience == 250
                    && player.experienceLevel == 13, "successful return deducts exactly 1000 points, got "
                    + player.totalExperience + " XP at level " + player.experienceLevel + ", position " + player.position() + " expected " + start);
            var center = player.blockPosition().offset(2, 0, 0);
            var neighbor = center.above();
            var gated = center.below();
            level.setBlockAndUpdate(center, Blocks.STONE.defaultBlockState());
            level.setBlockAndUpdate(neighbor, Blocks.STONE.defaultBlockState());
            level.setBlockAndUpdate(gated, Blocks.DIAMOND_ORE.defaultBlockState());
            var pick = new ItemStack(Items.STONE_PICKAXE);
            player.setItemSlot(EquipmentSlot.MAINHAND, pick);
            DivineSeven.action(player, 3, 0);
            test.assertTrue(player.gameMode.destroyBlock(center), "central block broken by native route");
            test.assertTrue(level.getBlockState(neighbor).isAir() && pick.getDamageValue() >= 2
                    && level.getBlockState(gated).is(Blocks.DIAMOND_ORE), "area mining uses durability and respects tool level");
            test.succeed();
        } finally {
            ChastityTests.close(player);
        }
    }
}
