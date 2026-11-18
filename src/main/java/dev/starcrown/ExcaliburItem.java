package dev.starcrown;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.ProjectileImpactEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = StarcrownDoll.ID)
public final class ExcaliburItem extends SwordItem {
    private static final String REVEALED = "ExcaliburRevealed";
    public static final int WIND_CHARGE = 12;
    public static final int LIGHT_CHARGE = ExcaliburChant.PREPARE_TICKS + ExcaliburChant.DURATION;
    private static final int HOLD_DURATION = 72_000;

    public ExcaliburItem() {
        super(Tiers.NETHERITE, 7, -2.4F, new Properties().fireResistant().rarity(Rarity.EPIC));
    }

    public static boolean isConcealed(ItemStack stack) {
        return stack.getTag() == null || !stack.getTag().getBoolean(REVEALED);
    }

    @Override
    public boolean isDamageable(ItemStack stack) {
        return false;
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        // The vanilla glint pass would reveal the entire transparent wind shell.
        return !isConcealed(stack) && super.isFoil(stack);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!player.isAlive() || player.getCooldowns().isOnCooldown(this)) {
            return InteractionResultHolder.fail(stack);
        }
        if (player.isSecondaryUseActive()) {
            if (player instanceof ServerPlayer server) {
                ExcaliburChant.cancel(server);
                boolean conceal = !isConcealed(stack);
                stack.getOrCreateTag().putBoolean(REVEALED, !conceal);
                server.getCooldowns().addCooldown(this, 8);
                server.displayClientMessage(modeName(conceal), true);
                ExcaliburEffects.toggle(server, conceal);
            }
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }
        if (ExcaliburChant.activeHand(player) != null || player instanceof ServerPlayer server && AvalonItem.isSheltered(server)) {
            return InteractionResultHolder.fail(stack);
        }
        player.startUsingItem(hand);
        if (player instanceof ServerPlayer server) {
            ExcaliburEffects.startCharge(server, isConcealed(stack));
        }
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public int getUseDuration(ItemStack stack) {
        return HOLD_DURATION;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.NONE;
    }

    @Override
    public void initializeClient(java.util.function.Consumer<net.minecraftforge.client.extensions.common.IClientItemExtensions> extensions) {
        extensions.accept(new ExcaliburChargePose());
    }

    @Override
    public void onUseTick(Level level, LivingEntity user, ItemStack stack, int remaining) {
        if (!(user instanceof ServerPlayer player)) return;
        if (!isConcealed(stack)) {
            if (HOLD_DURATION - remaining + 1 == ExcaliburChant.PREPARE_TICKS) {
                ExcaliburChant.begin(player, stack, player.getUsedItemHand());
            }
        } else if (HOLD_DURATION - remaining == WIND_CHARGE) {
            player.displayClientMessage(Component.translatable("message.starcrowndoll.excalibur.ready"), true);
        }
    }

    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity user, int remaining) {
        if (!(user instanceof ServerPlayer player) || !player.isAlive() || AvalonItem.isSheltered(player)
                || player.getItemInHand(player.getUsedItemHand()) != stack || player.getCooldowns().isOnCooldown(this)) return;
        int held = HOLD_DURATION - remaining;
        if (!isConcealed(stack)) {
            // After the opening word the server owns the cast, even when the button is released.
            if (held < ExcaliburChant.PREPARE_TICKS && ExcaliburChant.activeHand(player) == null) {
                player.displayClientMessage(Component.translatable("message.starcrowndoll.excalibur.interrupted"), true);
            }
            return;
        }
        if (held < WIND_CHARGE) {
            player.displayClientMessage(Component.translatable("message.starcrowndoll.excalibur.interrupted"), true);
            return;
        }
        fire(player, stack, true);
    }

    static void fire(ServerPlayer player, ItemStack stack, boolean wind) {
        Level level = player.level();
        Vec3 start = player.getEyePosition();
        Vec3 direction = player.getLookAngle();
        BlockHitResult hit = trace(player, start, start.add(direction.scale(wind ? 24 : 64)));
        Vec3 end = hit.getLocation();
        double radius = wind ? 1.4 : 2.0;
        double length = start.distanceTo(end);
        if (wind) stack.getOrCreateTag().putBoolean(REVEALED, true);
        player.getCooldowns().addCooldown(stack.getItem(), wind ? 60 : 300);
        player.causeFoodExhaustion(wind ? 1F : 4F);
        int hitFlashes = 0;
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, new AABB(start, end).inflate(radius))) {
            if (!canStrike(player, target) || !withinBeam(player, target.getBoundingBox(), start, direction, length, radius)) continue;
            if (!target.hurt(player.damageSources().playerAttack(player), wind ? 12F : 40F)) continue;
            if (wind) {
                target.knockback(1.5, player.getX() - target.getX(), player.getZ() - target.getZ());
            } else if (hitFlashes < 8) {
                ExcaliburEffects.beamHit(player, target.getBoundingBox().getCenter());
                hitFlashes++;
            }
        }
        ExcaliburEffects.release(player, wind, start, end, hit.getType() == HitResult.Type.BLOCK ? hit.getDirection() : null);
        player.displayClientMessage(Component.translatable(wind ? "message.starcrowndoll.excalibur.wind"
                : "message.starcrowndoll.excalibur.light"), true);
    }

    private static boolean canStrike(ServerPlayer player, LivingEntity target) {
        if (!target.isAlive() || !target.isAttackable() || target.isInvulnerable() || DivineSeven.ally(player, target)) return false;
        return !(target instanceof Player other) || (!other.isCreative() && !other.isSpectator() && player.canHarmPlayer(other));
    }

    private static BlockHitResult trace(ServerPlayer player, Vec3 from, Vec3 to) {
        return player.level().clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
    }

    private static boolean withinBeam(ServerPlayer player, AABB box, Vec3 start, Vec3 direction, double length, double radius) {
        double center = box.getCenter().subtract(start).dot(direction);
        double extent = (Math.abs(direction.x) * box.getXsize() + Math.abs(direction.y) * box.getYsize()
                + Math.abs(direction.z) * box.getZsize()) * .5;
        if (length < 1e-5 || center + extent <= 0 || center - extent >= length) return false;
        // A hit on the centre ray is already visible because it ends at the first solid block.
        Vec3 end = start.add(direction.scale(length));
        if (box.contains(start) || box.clip(start, end).isPresent()) return true;
        // At the edge of the beam, trace to the actual hitbox. A target behind a parallel wall is in cover.
        Vec3 onAxis = start.add(direction.scale(Mth.clamp(center, 0, length)));
        Vec3 onBody = new Vec3(Mth.clamp(onAxis.x, box.minX, box.maxX), Mth.clamp(onAxis.y, box.minY, box.maxY),
                Mth.clamp(onAxis.z, box.minZ, box.maxZ));
        return onAxis.distanceToSqr(onBody) <= radius * radius && trace(player, start, onBody).getType() == HitResult.Type.MISS;
    }

    @Override
    public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        if (attacker instanceof ServerPlayer player) {
            ExcaliburEffects.melee(player, target.getBoundingBox().getCenter(), isConcealed(stack));
        }
        return super.hurtEnemy(stack, target, attacker);
    }

    @SubscribeEvent
    public static void deflectArrow(ProjectileImpactEvent event) {
        if (event.getImpactResult() != ProjectileImpactEvent.ImpactResult.DEFAULT
                || !(event.getRayTraceResult() instanceof EntityHitResult hit)
                || !(hit.getEntity() instanceof ServerPlayer player) || !(event.getProjectile() instanceof AbstractArrow arrow)
                || arrow.getOwner() == player || !(player.getUseItem().getItem() instanceof ExcaliburItem)
                || !isConcealed(player.getUseItem())) return;
        Vec3 incoming = arrow.getDeltaMovement();
        if (incoming.normalize().dot(player.getLookAngle()) > -.5) return;
        // Turn a frontal arrow or trident up and away. Keep its owner, pickup and loyalty behaviour.
        Vec3 outward = incoming.reverse().normalize().add(0, .65, 0).normalize();
        arrow.shoot(outward.x, outward.y, outward.z, (float) Math.min(2, incoming.length()), 0);
        arrow.hurtMarked = true;
        event.setImpactResult(ProjectileImpactEvent.ImpactResult.SKIP_ENTITY);
        ExcaliburEffects.melee(player, hit.getLocation(), true);
    }

    private static Component modeName(boolean concealed) {
        return Component.translatable(concealed ? "tooltip.starcrowndoll.excalibur.concealed" : "tooltip.starcrowndoll.excalibur.revealed")
                .withStyle(concealed ? ChatFormatting.AQUA : ChatFormatting.GOLD);
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> lines, TooltipFlag flag) {
        lines.add(modeName(isConcealed(stack)));
        lines.add(Component.translatable("tooltip.starcrowndoll.excalibur.toggle").withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable(isConcealed(stack) ? "tooltip.starcrowndoll.excalibur.wind"
                : "tooltip.starcrowndoll.excalibur.light").withStyle(ChatFormatting.GRAY));
    }
}
