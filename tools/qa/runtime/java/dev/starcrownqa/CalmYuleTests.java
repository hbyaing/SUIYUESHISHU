package dev.starcrownqa;

import dev.starcrown.CalmYule;
import dev.starcrown.DivineSeven;
import dev.starcrown.DollDance;
import dev.starcrown.StarcrownDoll;
import dev.starcrown.YuleCharity;
import dev.starcrown.YuleDiligence;
import dev.starcrown.YuleGenerosity;
import dev.starcrown.YuleHumility;
import dev.starcrown.YuleTemperance;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;

@GameTestHolder("starcrownqa")
@PrefixGameTestTemplate(false)
public final class CalmYuleTests {
    private static void equip(ServerPlayer player) {
        player.setItemSlot(EquipmentSlot.HEAD, new ItemStack(StarcrownDoll.CALM_ITEM.get()));
    }

    @GameTest(template = "empty")
    public static void calmSurvivalDefense(GameTestHelper test) {
        var player = ChastityTests.player(test);
        var zombie = ChastityTests.spawn(EntityType.ZOMBIE, player, 8, 0, 0);
        var wither = ChastityTests.spawn(EntityType.WITHER, player, -8, 0, 0);
        var pet = ChastityTests.spawn(EntityType.WOLF, player, 2, 0, 0);
        try {
            equip(player);
            ObfuscationReflectionHelper.setPrivateValue(ServerPlayer.class, player, 0, "f_8921_");
            test.assertTrue(!YuleHumility.active(player), "separate from original one-point cap");
            player.hurt(player.damageSources().generic(), 100);
            test.assertTrue(player.getHealth() == 12, "40 percent max-health cap");
            player.invulnerableTime = 0;
            player.hurt(player.damageSources().generic(), 4);
            test.assertTrue(Math.abs(player.getHealth() - 9.4) < .01, "35 percent damage reduction");
            player.addEffect(new MobEffectInstance(MobEffects.POISON, 100));
            test.assertTrue(player.getEffect(MobEffects.POISON).getDuration() == 40, "poison shortened by 60 percent");
            player.forceAddEffect(new MobEffectInstance(MobEffects.WEAKNESS, 100), null);
            test.assertTrue(player.getEffect(MobEffects.WEAKNESS).getDuration() == 40, "forced effects use the same duration");
            YuleTemperance.bind(player);
            player.getFoodData().addExhaustion(4);
            test.assertTrue(player.getFoodData().getExhaustionLevel() < 2, "exhaustion reduced by 75 percent");

            DivineSeven.action(player, 19, 0);
            zombie.setTarget(player);
            wither.setTarget(player);
            test.assertTrue(zombie.getTarget() == null && wither.getTarget() == player, "field excludes bosses");
            float before = zombie.getHealth();
            zombie.hurt(player.damageSources().playerAttack(player), 4);
            test.assertTrue(zombie.getHealth() < before, "attacking in the field works");
            pet.tame(player);pet.setHealth(2);
            YuleCharity.use(player, pet, InteractionHand.MAIN_HAND);
            test.assertTrue(pet.getHealth() == 10 && pet.getEffect(MobEffects.DAMAGE_RESISTANCE).getAmplifier() == 1,
                    "pet gets 40 percent healing and Resistance II");
            pet.setHealth(2);YuleCharity.use(player, pet, InteractionHand.MAIN_HAND);
            test.assertTrue(pet.getHealth() == 2, "healing cooldown is shared");
            player.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
            zombie.setTarget(player);
            test.assertTrue(zombie.getTarget() == player, "unequip releases the field");
            test.succeed();
        } finally {
            zombie.discard();wither.discard();pet.discard();ChastityTests.close(player);
        }
    }

    @GameTest(template = "empty")
    public static void calmGiftKeepsExactStack(GameTestHelper test) {
        var donor = ChastityTests.player(test);
        var receiver = ChastityTests.player(test);
        try {
            equip(donor);
            var original = new ItemStack(Items.COBBLESTONE, 48);
            original.getOrCreateTag().putString("qa", "exact");
            donor.setItemInHand(InteractionHand.MAIN_HAND, original.copy());
            DivineSeven.action(donor, 22, 0);
            var level = donor.serverLevel();
            var gift = level.getEntitiesOfClass(ItemEntity.class, donor.getBoundingBox().inflate(3),
                    item -> item.getPersistentData().hasUUID(YuleGenerosity.CALM_DONOR)).stream().findFirst().orElseThrow();
            test.assertTrue(donor.getMainHandItem().isEmpty() && ItemStack.matches(gift.getItem(), original), "gift preserves original item, quantity and NBT");
            donor.setItemInHand(InteractionHand.MAIN_HAND, original.copy());
            DivineSeven.action(donor, 22, 0);
            test.assertTrue(ItemStack.matches(donor.getMainHandItem(), original), "cooldown leaves the next stack untouched");
            gift.setNoPickUpDelay();gift.playerTouch(receiver);
            test.assertTrue(gift.isRemoved() && ItemStack.matches(receiver.getInventory().getItem(0), original), "native pickup delivers the exact stack");
            for (var player : new ServerPlayer[]{donor,receiver}) {
                var boon = player.getEffect(MobEffects.HERO_OF_THE_VILLAGE);
                test.assertTrue(boon != null && boon.getAmplifier() == 1 && boon.getDuration() == 1200,
                        "both players receive temporary Hero II");
                test.assertTrue(!player.getPersistentData().getBoolean(YuleGenerosity.BLESSED), "no permanent blessing");
            }
            test.succeed();
        } finally {
            ChastityTests.close(donor);ChastityTests.close(receiver);
        }
    }

    @GameTest(template = "empty")
    public static void calmGrowthAndMusic(GameTestHelper test) {
        var player = ChastityTests.player(test);
        var level = player.serverLevel();
        var block = player.blockPosition().offset(2, 0, 0);
        var dollPos = player.blockPosition().offset(-2, 0, 0);
        var animal = ChastityTests.spawn(EntityType.COW, player, 1, 0, 2);
        try {
            equip(player);
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_PICKAXE));
            for (int i = 0; i < 10; i++) {
                level.setBlockAndUpdate(block, Blocks.STONE.defaultBlockState());
                test.assertTrue(player.gameMode.destroyBlock(block), "native mining succeeds");
            }
            test.assertTrue(player.totalExperience == 20 && YuleDiligence.units(player) == 0, "ten natural blocks give 20 XP and separate progress");
            level.setBlockAndUpdate(block.below(), Blocks.STONE.defaultBlockState());
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIRT, 1));
            var hit = new BlockHitResult(Vec3.atCenterOf(block.below()).add(0,.5,0), net.minecraft.core.Direction.UP, block.below(), false);
            player.getMainHandItem().useOn(new net.minecraft.world.item.context.UseOnContext(player, InteractionHand.MAIN_HAND, hit));
            test.assertTrue(level.getBlockState(block).is(Blocks.DIRT), "placed fixture");
            player.gameMode.destroyBlock(block);
            test.assertTrue(player.getPersistentData().getCompound("starcrown_yule_options").getLong("calm_work") == 10,
                    "player-placed block gives no work point");
            level.setBlockAndUpdate(dollPos, StarcrownDoll.CALM_DOLL.get().defaultBlockState());
            ((StarcrownDoll.DollEntity) level.getBlockEntity(dollPos)).toggleMusic();
            test.assertTrue(!DollDance.controlled(animal), "Calm music never freezes mob AI");
            test.succeed();
        } finally {
            level.setBlockAndUpdate(block, Blocks.AIR.defaultBlockState());
            level.setBlockAndUpdate(block.below(), Blocks.AIR.defaultBlockState());
            level.setBlockAndUpdate(dollPos, Blocks.AIR.defaultBlockState());
            animal.discard();ChastityTests.close(player);
        }
    }
}
