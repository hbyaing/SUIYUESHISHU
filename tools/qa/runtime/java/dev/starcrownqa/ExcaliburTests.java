package dev.starcrownqa;

import dev.starcrown.AvalonItem;
import dev.starcrown.DivineSeven;
import dev.starcrown.ExcaliburItem;
import dev.starcrown.ExcaliburChant;
import dev.starcrown.StarcrownDoll;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundCustomPayloadPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.ProjectileImpactEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("excaliburqa")
@PrefixGameTestTemplate(false)
public final class ExcaliburTests {
    private static final InteractionHand MAIN = InteractionHand.MAIN_HAND;
    private static final InteractionHand OFF = InteractionHand.OFF_HAND;
    private static final int PREPARE_TICKS = 10;
    // The server releases at 11.60 seconds, just after the spoken name.
    private static final int SPOKEN_NAME_END_TICK = 232;
    private static final int CHANT_TEST_TIMEOUT = SPOKEN_NAME_END_TICK + 20;

    // The fixtures live at different heights because a single beam can leave the empty GameTest structure.
    private static class Arena implements AutoCloseable {
        final ServerPlayer player;
        final ItemStack sword = new ItemStack(StarcrownDoll.EXCALIBUR.get());
        final List<Entity> entities = new ArrayList<>();
        final List<BlockPos> blocks = new ArrayList<>();
        Consumer<LivingAttackEvent> attackListener;
        int held;
        String firstBlock;

        Arena(GameTestHelper h, int height) {
            BlockPos anchor = h.absolutePos(new BlockPos(6, 0, 6));
            player = ChastityTests.playerAt(h, new BlockPos(anchor.getX(), 210 + height / 3, anchor.getZ()));
            net.minecraftforge.fml.util.ObfuscationReflectionHelper.setPrivateValue(ServerPlayer.class, player, 0, "f_8921_");
            player.setNoGravity(true);
            player.setItemInHand(MAIN, sword);
            player.setYRot(0);
            player.setXRot(0);
        }

        void ticks(int count) {
            // Detached test players have no connection-list tick. Drive the vanilla use/cooldown clocks.
            for (int i = 0; i < count; i++) player.doTick();
        }

        void charge(int ticks) {
            sword.use(player.level(), player, MAIN);
            ticks(ticks);
            held = player.getTicksUsingItem();
            var eye = player.getEyePosition();
            var hit = player.level().clip(new ClipContext(eye, eye.add(player.getLookAngle().scale(64)),
                    ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
            firstBlock = hit.getType() + " " + hit.getBlockPos() + " " + player.level().getBlockState(hit.getBlockPos());
            player.releaseUsingItem();
        }

        void toggle() {
            player.setShiftKeyDown(true);
            sword.use(player.level(), player, MAIN);
            player.setShiftKeyDown(false);
            ticks(9);
        }

        Cow target(double x, double z) {
            Cow cow = ChastityTests.spawn(EntityType.COW, player, x, 0, z);
            cow.setNoAi(true);
            cow.setNoGravity(true);
            cow.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100);
            cow.setHealth(100);
            entities.add(cow);
            return cow;
        }

        void stone(BlockPos pos) {
            player.serverLevel().setBlockAndUpdate(pos, Blocks.STONE.defaultBlockState());
            blocks.add(pos);
        }

        List<Long> recordHitsOn(Cow target) {
            List<Long> times = new ArrayList<>();
            attackListener = event -> {
                if (event.getEntity() == target && event.getSource().getEntity() == player) {
                    times.add(player.level().getGameTime());
                }
            };
            MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, LivingAttackEvent.class, attackListener);
            return times;
        }

        ItemStack sheath() {
            ItemStack sheath = new ItemStack(StarcrownDoll.AVALON.get());
            player.setItemInHand(OFF, sheath);
            return sheath;
        }

        @Override
        public void close() {
            if (attackListener != null) MinecraftForge.EVENT_BUS.unregister(attackListener);
            for (BlockPos pos : blocks) player.serverLevel().setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
            entities.forEach(Entity::discard);
            ChastityTests.close(player);
        }
    }

    @GameTest(template = "empty")
    public static void netherStarUnlocksBothWorkbenchRecipes(GameTestHelper h) {
        ServerPlayer player = ChastityTests.player(h);
        try {
            var sword = new ResourceLocation(StarcrownDoll.ID, "excalibur");
            var sheath = new ResourceLocation(StarcrownDoll.ID, "avalon");
            var book = player.getRecipeBook();
            h.assertTrue(!book.contains(sword) && !book.contains(sheath), "fresh recipe book");
            ItemStack star = new ItemStack(Items.NETHER_STAR);
            player.getInventory().setItem(0, star);
            CriteriaTriggers.INVENTORY_CHANGED.trigger(player, player.getInventory(), star);
            h.assertTrue(book.contains(sword) && book.contains(sheath), "one nether star unlocks both recipes");

            assertWorkbenchResult(h, player, StarcrownDoll.EXCALIBUR.get(), new Item[] {
                    Items.AIR, Items.AIR, Items.NETHER_STAR,
                    Items.AIR, Items.DIAMOND_SWORD, Items.GOLD_INGOT,
                    Items.BLAZE_ROD, Items.GOLD_INGOT, Items.AIR
            });
            assertWorkbenchResult(h, player, StarcrownDoll.AVALON.get(), new Item[] {
                    Items.GOLD_INGOT, Items.LAPIS_BLOCK, Items.GOLD_INGOT,
                    Items.GOLD_INGOT, Items.NETHER_STAR, Items.GOLD_INGOT,
                    Items.GOLD_INGOT, Items.AMETHYST_SHARD, Items.GOLD_INGOT
            });
            h.succeed();
        } finally {
            ChastityTests.close(player);
        }
    }

    private static void assertWorkbenchResult(GameTestHelper h, ServerPlayer player, Item expected, Item[] ingredients) {
        var grid = new TransientCraftingContainer(player.inventoryMenu, 3, 3);
        for (int i = 0; i < ingredients.length; i++) {
            if (ingredients[i] != Items.AIR) grid.setItem(i, new ItemStack(ingredients[i]));
        }
        var level = player.serverLevel();
        var match = level.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, grid, level);
        h.assertTrue(match.isPresent(), "workbench matches " + expected);
        ItemStack result = match.orElseThrow().assemble(grid, level.registryAccess());
        h.assertTrue(result.is(expected) && result.getCount() == 1, "workbench crafts one " + expected);
    }

    @GameTest(template = "empty")
    public static void swordAndInterruptedCharge(GameTestHelper h) {
        try (Arena a = new Arena(h, 60)) {
            Cow target = a.target(0, 2);
            h.assertTrue(ExcaliburItem.isConcealed(a.sword), "new blade is concealed");
            a.ticks(25);
            a.player.attack(target);
            h.assertTrue(target.getHealth() < 100 && !a.sword.isDamaged(), "ordinary sword swing deals damage without wear");
            float before = target.getHealth();
            a.charge(ExcaliburItem.WIND_CHARGE - 1);
            h.assertTrue(before == target.getHealth() && ExcaliburItem.isConcealed(a.sword)
                    && !a.player.getCooldowns().isOnCooldown(a.sword.getItem()), "early release consumes no charge or cooldown");
            a.toggle();
            h.assertTrue(!ExcaliburItem.isConcealed(ItemStack.of(a.sword.save(new CompoundTag()))), "revealed mode survives save/load");
            a.charge(PREPARE_TICKS - 1);
            h.assertTrue(before == target.getHealth() && ExcaliburChant.activeHand(a.player) == null
                    && !a.player.getCooldowns().isOnCooldown(a.sword.getItem()),
                    "an early light release cannot fire");
            h.succeed();
        }
    }

    @GameTest(template = "empty")
    public static void strikeAirReleasesWind(GameTestHelper h) {
        try (Arena a = new Arena(h, 80)) {
            Cow near = a.target(0, 7);
            Cow far = a.target(0, 22);
            Cow outside = a.target(0, 26);
            a.charge(ExcaliburItem.WIND_CHARGE);
            h.assertTrue(near.getHealth() == 88 && far.getHealth() == 88,
                    "wind hits both targets for 12, actual " + near.getHealth() + " / " + far.getHealth()
                    + ", held=" + a.held + ", dir=" + a.player.getLookAngle() + ", eye=" + a.player.getEyePosition() + ", first=" + near.position()
                    + ", obstruction=" + a.firstBlock + ", fired=" + !ExcaliburItem.isConcealed(a.sword));
            h.assertTrue(near.getDeltaMovement().z > .5 && outside.getHealth() == 100, "wind pushes forward within 24 blocks");
            h.assertTrue(!ExcaliburItem.isConcealed(a.sword) && a.player.getCooldowns().isOnCooldown(a.sword.getItem()),
                    "wind reveals the blade and starts cooldown");
            h.assertTrue(a.sword.use(a.player.level(), a.player, MAIN).getResult() == InteractionResult.FAIL,
                    "switching mode cannot immediately bypass the wind cooldown");
            a.ticks(60);
            h.assertTrue(a.sword.use(a.player.level(), a.player, MAIN).getResult().consumesAction()
                    && a.player.isUsingItem(), "casting becomes available after three seconds");
            h.succeed();
        }
    }

    @GameTest(template = "empty", timeoutTicks = CHANT_TEST_TIMEOUT)
    public static void lightBeamChecksCoverAndAllies(GameTestHelper h) {
        Arena a = new Arena(h, 100);
        try {
            a.toggle();
            Cow near = a.target(0, 4);
            Cow far = a.target(0, 54);
            Cow protectedFriend = a.target(0, 9);
            CompoundTag friends = new CompoundTag();
            friends.putBoolean(protectedFriend.getUUID().toString(), true);
            DivineSeven.settings(a.player).put("friends", friends);
            Cow behindWall = a.target(0, 60);
            Cow offAxis = a.target(4, 12);
            // Keep the cow outside the wall's collision box while its near flank remains inside the beam radius.
            Cow sideCover = a.target(2.05, 14);
            // The side wall shadows one otherwise eligible target while the central beam passes it.
            for (int z = 2; z <= 14; z++) {
                for (int y = 0; y <= 2; y++) a.stone(a.player.blockPosition().offset(1, y, z));
            }
            for (int x = -2; x <= 2; x++) {
                for (int y = 0; y <= 2; y++) a.stone(a.player.blockPosition().offset(x, y, 57));
            }
            var hitTimes = a.recordHitsOn(near);
            long started = a.player.level().getGameTime();
            a.charge(PREPARE_TICKS);
            h.assertTrue(ExcaliburChant.activeHand(a.player) == MAIN && !a.player.isUsingItem() && near.getHealth() == 100,
                    "releasing the button after the opening word keeps the chant and deals no early damage");
            h.runAfterDelay(SPOKEN_NAME_END_TICK + 3, () -> {
                try (a) {
                    h.assertTrue(hitTimes.equals(List.of(started + SPOKEN_NAME_END_TICK)),
                            "one automatic hit on the final spoken syllable; observed ticks since chant start: "
                                    + hitTimes.stream().map(t -> t - started).toList());
                    h.assertTrue(near.getHealth() == 60 && far.getHealth() == 60, "chant fires automatically and pierces two targets");
                    h.assertTrue(behindWall.getHealth() == 100 && sideCover.getHealth() == 100 && offAxis.getHealth() == 100,
                            "solid walls, side cover and beam width are respected: "
                                    + behindWall.getHealth() + " / " + sideCover.getHealth() + " / " + offAxis.getHealth());
                    h.assertTrue(protectedFriend.getHealth() == 100 && a.player.getHealth() == a.player.getMaxHealth(), "skips allies and caster");
                    h.assertTrue(a.player.serverLevel().getBlockState(a.player.blockPosition().offset(0, 1, 57)).is(Blocks.STONE),
                            "beam leaves terrain intact");
                    h.assertTrue(ExcaliburChant.activeHand(a.player) == null && a.player.getCooldowns().isOnCooldown(a.sword.getItem()),
                            "completing the chant clears the state and applies cooldown");
                    h.succeed();
                }
            });
        } catch (Throwable e) {
            a.close();
            throw e;
        }
    }

    @GameTest(template = "empty")
    public static void windOnlyDeflectsFrontalArrows(GameTestHelper h) {
        try (Arena a = new Arena(h, 120)) {
            a.sword.use(a.player.level(), a.player, MAIN);
            Arrow arrow = new Arrow(a.player.level(), a.player.getX(), a.player.getEyeY(), a.player.getZ() + 1);
            Cow owner = a.target(0, 5);
            arrow.setOwner(owner);
            arrow.setDeltaMovement(0, 0, -1);
            var front = new ProjectileImpactEvent(arrow, new EntityHitResult(a.player));
            MinecraftForge.EVENT_BUS.post(front);
            h.assertTrue(front.getImpactResult() == ProjectileImpactEvent.ImpactResult.SKIP_ENTITY
                    && arrow.getDeltaMovement().z > 0 && arrow.getOwner() == owner && !arrow.isRemoved(),
                    "incoming frontal arrow is turned away and keeps its owner");
            var back = new ProjectileImpactEvent(arrow, new EntityHitResult(a.player));
            MinecraftForge.EVENT_BUS.post(back);
            h.assertTrue(back.getImpactResult() == ProjectileImpactEvent.ImpactResult.DEFAULT, "rear attacks pass the guard");
            a.player.stopUsingItem();
            arrow.setDeltaMovement(0, 0, -1);
            var idle = new ProjectileImpactEvent(arrow, new EntityHitResult(a.player));
            MinecraftForge.EVENT_BUS.post(idle);
            h.assertTrue(idle.getImpactResult() == ProjectileImpactEvent.ImpactResult.DEFAULT, "idle sword does not guard");
            h.succeed();
        }
    }

    @GameTest(template = "empty", timeoutTicks = CHANT_TEST_TIMEOUT)
    public static void beamHonorsPvpAndRange(GameTestHelper h) {
        Arena a = new Arena(h, 180);
        ServerPlayer other = ChastityTests.playerAt(h, a.player.blockPosition().offset(0, 0, 4));
        var server = a.player.serverLevel().getServer();
        boolean oldPvp = server.isPvpAllowed();
        try {
            server.setPvpAllowed(false);
            Cow inside = a.target(0, 62);
            Cow outside = a.target(0, 66);
            a.toggle();
            a.player.setYRot(90);
            a.sword.use(a.player.level(), a.player, MAIN);
            a.ticks(ExcaliburChant.PREPARE_TICKS);
            h.assertTrue(ExcaliburChant.activeHand(a.player) == MAIN, "holding starts the chant");
            a.player.setYRot(0);
            h.runAfterDelay(ExcaliburChant.DURATION + 3, () -> {
                try (a) {
                    h.assertTrue(!a.player.isUsingItem() && other.getHealth() == other.getMaxHealth()
                                    && inside.getHealth() == 60 && outside.getHealth() == 100,
                            "without releasing, fire once along the final gaze out to 64 blocks with PvP disabled");
                    a.ticks(301);
                    h.assertTrue(ExcaliburChant.activeHand(a.player) == null, "holding across the cooldown cannot repeat the chant");
                    a.player.releaseUsingItem();
                    h.assertTrue(inside.getHealth() == 60, "releasing after the automatic shot deals no second hit");
                    h.succeed();
                } finally {
                    server.setPvpAllowed(oldPvp);
                    ChastityTests.close(other);
                }
            });
        } catch (Throwable e) {
            server.setPvpAllowed(oldPvp);
            ChastityTests.close(other);
            a.close();
            throw e;
        }
    }

    @GameTest(template = "empty", timeoutTicks = CHANT_TEST_TIMEOUT)
    public static void copiedOffhandSwordCannotContinueChant(GameTestHelper h) {
        Arena a = new Arena(h, 195);
        try {
            a.toggle();
            a.player.setItemInHand(MAIN, ItemStack.EMPTY);
            a.player.setItemInHand(OFF, a.sword);
            Cow target = a.target(0, 8);
            a.sword.use(a.player.level(), a.player, OFF);
            a.ticks(ExcaliburChant.PREPARE_TICKS);
            a.player.releaseUsingItem();
            h.assertTrue(ExcaliburChant.activeHand(a.player) == OFF, "server remembers the offhand after key release");
            h.runAfterDelay(SPOKEN_NAME_END_TICK - 1, () -> {
                var viewer = ChastityTests.playerAt(h, a.player.blockPosition().offset(4, 0, 0));
                try {
                    long started = a.player.level().getGameTime() - ExcaliburChant.elapsed(a.player);
                    MinecraftForge.EVENT_BUS.post(new PlayerEvent.StartTracking(viewer, a.player));
                    h.assertTrue(hasChantPacket(viewer, a.player, started, true),
                            "late observer receives the original offhand chant and start timestamp");
                    MinecraftForge.EVENT_BUS.post(new PlayerEvent.StopTracking(viewer, a.player));
                    h.assertTrue(hasChantPacket(viewer, a.player, started, false),
                            "leaving tracking range stops the same chant for this listener");
                    a.player.setItemInHand(OFF, a.sword.copy());
                } finally {
                    ChastityTests.close(viewer);
                }
            });
            h.runAfterDelay(ExcaliburChant.DURATION + 2, () -> {
                try (a) {
                    h.assertTrue(ExcaliburChant.activeHand(a.player) == null && target.getHealth() == 100
                            && !a.player.getCooldowns().isOnCooldown(a.sword.getItem()), "identical replacement cancels without firing or cooldown");
                    h.succeed();
                }
            });
        } catch (Throwable e) {
            a.close();
            throw e;
        }
    }

    private static boolean hasChantPacket(ServerPlayer viewer, ServerPlayer caster, long started, boolean active) {
        var channel = (io.netty.channel.embedded.EmbeddedChannel)viewer.connection.connection.channel();
        channel.runPendingTasks();
        Object outgoing;
        while ((outgoing = channel.readOutbound()) != null) {
            if (!(outgoing instanceof ClientboundCustomPayloadPacket packet)
                    || !packet.getIdentifier().equals(new ResourceLocation(StarcrownDoll.ID, "seven"))) continue;
            var buf = packet.getData();
            try {
                if (buf.readVarInt() == 13 && buf.readUUID().equals(caster.getUUID())
                        && buf.readResourceLocation().equals(caster.level().dimension().location())
                        && buf.readEnum(InteractionHand.class) == OFF && buf.readLong() == started
                        && buf.readBoolean() == active && !buf.readBoolean()) return true;
            } finally {
                buf.release();
            }
        }
        return false;
    }

    @GameTest(template = "empty", timeoutTicks = 950)
    public static void sanctuaryExpiresBeforeCooldown(GameTestHelper h) {
        Arena a = new Arena(h, 140);
        try {
            ItemStack sheath = a.sheath();
            Cow target = a.target(0, 2);
            a.toggle();
            a.player.addEffect(new MobEffectInstance(MobEffects.POISON, 80));
            a.sword.use(a.player.level(), a.player, MAIN);
            a.ticks(ExcaliburChant.PREPARE_TICKS);
            h.assertTrue(ExcaliburChant.activeHand(a.player) == MAIN, "chant active before entering Avalon");
            sheath.use(a.player.level(), a.player, OFF);
            h.assertTrue(!a.player.isUsingItem() && ExcaliburChant.activeHand(a.player) == null && AvalonItem.isSheltered(a.player),
                    "sanctuary immediately interrupts an active chant");
            h.assertTrue(!a.player.hasEffect(MobEffects.POISON) && !a.player.addEffect(new MobEffectInstance(MobEffects.WITHER, 80))
                    && a.player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 150)), "cleanses and rejects only harmful effects");
            h.assertTrue(!a.player.hurt(a.player.damageSources().generic(), 3), "sanctuary rejects incoming damage");
            a.player.attack(target);
            h.assertTrue(target.getHealth() == 100 && a.sword.use(a.player.level(), a.player, MAIN).getResult() == InteractionResult.FAIL,
                    "sanctuary prevents both sword attacks and new casts");
            h.runAtTickTime(104, () -> {
                try {
                    h.assertTrue(!AvalonItem.isSheltered(a.player) && a.player.hurt(a.player.damageSources().generic(), 3),
                            "ordinary damage resumes after five seconds");
                    h.assertTrue(sheath.use(a.player.level(), a.player, OFF).getResult() == InteractionResult.FAIL,
                            "forty-five second cooldown still blocks reactivation");
                } catch (Throwable e) {
                    a.close();
                    throw e;
                }
            });
            h.runAtTickTime(906, () -> {
                try (a) {
                    h.assertTrue(sheath.use(a.player.level(), a.player, OFF).getResult().consumesAction()
                            && AvalonItem.isSheltered(a.player), "after forty-five seconds the same bearer can activate sanctuary again");
                    h.succeed();
                }
            });
        } catch (Throwable e) {
            a.close();
            throw e;
        }
    }

    @GameTest(template = "empty")
    public static void sheathHealingIsPerBearer(GameTestHelper h) {
        try (Arena a = new Arena(h, 160)) {
            a.player.setHealth(6);
            a.sheath();
            a.player.getInventory().setItem(8, new ItemStack(StarcrownDoll.AVALON.get()));
            a.player.tickCount = 40;
            AvalonItem.tick(new TickEvent.PlayerTickEvent(TickEvent.Phase.END, a.player));
            h.assertTrue(a.player.getHealth() == 8, "two sheaths with the sword heal once");
            a.player.setItemInHand(MAIN, ItemStack.EMPTY);
            a.player.tickCount = 120;
            AvalonItem.tick(new TickEvent.PlayerTickEvent(TickEvent.Phase.END, a.player));
            h.assertTrue(a.player.getHealth() == 8, "without a sword the shorter interval does not heal");
            a.player.tickCount = 160;
            AvalonItem.tick(new TickEvent.PlayerTickEvent(TickEvent.Phase.END, a.player));
            h.assertTrue(a.player.getHealth() == 10, "carried sheath heals once at the longer interval");
            a.player.setItemInHand(OFF, ItemStack.EMPTY);
            a.player.getInventory().setItem(8, ItemStack.EMPTY);
            a.player.tickCount = 240;
            AvalonItem.tick(new TickEvent.PlayerTickEvent(TickEvent.Phase.END, a.player));
            h.assertTrue(a.player.getHealth() == 10, "dropping the last sheath ends healing");
            h.succeed();
        }
    }

    @GameTest(template = "empty")
    public static void sheathingPreservesTheOriginalBlade(GameTestHelper h) {
        try (Arena a = new Arena(h, 200)) {
            a.toggle();
            a.sword.setHoverName(Component.literal("Saber"));
            a.sword.enchant(Enchantments.SHARPNESS, 3);
            ItemStack expected = a.sword.copy();
            ItemStack sheath = new ItemStack(StarcrownDoll.AVALON.get());
            a.player.setItemInHand(MAIN, sheath);
            a.player.setItemInHand(OFF, a.sword);
            a.player.setShiftKeyDown(true);
            sheath.use(a.player.level(), a.player, MAIN);
            h.assertTrue(AvalonItem.isSheathed(sheath) && a.player.getOffhandItem().isEmpty(), "sheathing moves exactly one sword");
            // Inventory/level saves serialize the sheath before players can draw the blade again.
            sheath = ItemStack.of(sheath.save(new CompoundTag()));
            a.player.setItemInHand(MAIN, sheath);
            a.ticks(12);
            ItemStack occupied = new ItemStack(Items.APPLE, 3);
            a.player.setItemInHand(OFF, occupied);
            sheath.use(a.player.level(), a.player, MAIN);
            h.assertTrue(AvalonItem.isSheathed(sheath) && a.player.getOffhandItem() == occupied && occupied.getCount() == 3,
                    "drawing cannot overwrite an occupied offhand");
            a.player.setItemInHand(OFF, ItemStack.EMPTY);
            a.ticks(12);
            sheath.use(a.player.level(), a.player, MAIN);
            h.assertTrue(!AvalonItem.isSheathed(sheath) && ItemStack.matches(expected, a.player.getOffhandItem()),
                    "drawing restores the original name, enchantment and revealed state");
            h.succeed();
        }
    }
}
