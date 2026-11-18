package dev.starcrown;

import java.util.List;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.ProjectileImpactEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingKnockBackEvent;
import net.minecraftforge.event.entity.living.MobEffectEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.NetworkDirection;

@Mod.EventBusSubscriber(modid = StarcrownDoll.ID)
public final class AvalonItem extends Item {
    static final int DURATION = 100;
    static final int COOLDOWN = 900;
    private static final String SHELTER_UNTIL = "starcrowndoll_avalon_shelter_until";
    private static final String READY_AT = "starcrowndoll_avalon_ready_at";
    private static final String NEXT_NOTICE = "starcrowndoll_avalon_next_notice";
    private static final String STORED_SWORD = "AvalonSword";

    public AvalonItem() {
        super(new Properties().stacksTo(1).fireResistant().rarity(Rarity.EPIC));
    }

    private static long now(ServerPlayer player) {
        return player.serverLevel().getServer().overworld().getGameTime();
    }

    static boolean carries(Player player) {
        return player.getInventory().hasAnyMatching(s -> s.is(StarcrownDoll.AVALON.get()));
    }

    public static boolean isSheltered(Entity entity) {
        return entity instanceof ServerPlayer player && player.isAlive()
                && player.getPersistentData().getLong(SHELTER_UNTIL) > now(player);
    }

    public static boolean isSheathed(ItemStack scabbard) {
        var tag = scabbard.getTag();
        return tag != null && (StarcrownDoll.ID + ":excalibur").equals(tag.getCompound(STORED_SWORD).getString("id"));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (hand == InteractionHand.MAIN_HAND && player.isSecondaryUseActive()) {
            return exchangeSword(level, player, stack);
        }
        if (player instanceof ServerPlayer server && !activate(server)) return InteractionResultHolder.fail(stack);
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    private static InteractionResultHolder<ItemStack> exchangeSword(Level level, Player player, ItemStack scabbard) {
        ItemStack otherHand = player.getOffhandItem();
        boolean drawing = isSheathed(scabbard);
        if (drawing ? !otherHand.isEmpty() : !otherHand.is(StarcrownDoll.EXCALIBUR.get())) {
            if (!level.isClientSide) player.displayClientMessage(Component.translatable(drawing
                    ? "message.starcrowndoll.avalon.empty_offhand" : "message.starcrowndoll.avalon.sword_offhand"), true);
            return InteractionResultHolder.fail(scabbard);
        }
        if (!level.isClientSide) {
            if (drawing) {
                var sword = ItemStack.of(scabbard.getTag().getCompound(STORED_SWORD));
                player.setItemInHand(InteractionHand.OFF_HAND, sword);
                scabbard.removeTagKey(STORED_SWORD);
            } else {
                scabbard.getOrCreateTag().put(STORED_SWORD, otherHand.split(1).save(new CompoundTag()));
            }
            player.getInventory().setChanged();
            player.displayClientMessage(Component.translatable(drawing ? "message.starcrowndoll.avalon.drawn"
                    : "message.starcrowndoll.avalon.sheathed"), true);
        }
        return InteractionResultHolder.sidedSuccess(scabbard, level.isClientSide);
    }

    static boolean activate(ServerPlayer player) {
        if (!player.isAlive() || player.isSpectator() || !carries(player)) return false;
        long time = now(player);
        var data = player.getPersistentData();
        long remaining = data.getLong(READY_AT) - time;
        if (remaining > 0) {
            if (time >= data.getLong(NEXT_NOTICE)) {
                data.putLong(NEXT_NOTICE, time + 20);
                player.displayClientMessage(Component.translatable("message.starcrowndoll.avalon.cooldown",
                        (remaining + 19) / 20), true);
            }
            return false;
        }
        data.putLong(SHELTER_UNTIL, time + DURATION);
        data.putLong(READY_AT, time + COOLDOWN);
        ExcaliburChant.cancel(player);
        player.stopUsingItem();
        player.clearFire();
        player.fallDistance = 0;
        // Snapshot before removal because each removal mutates the player's effect map.
        for (var effect : List.copyOf(player.getActiveEffects())) {
            if (effect.getEffect().getCategory() == MobEffectCategory.HARMFUL) player.removeEffect(effect.getEffect());
        }
        player.displayClientMessage(Component.translatable("message.starcrowndoll.avalon.sanctuary"), true);
        ExcaliburEffects.avalon(player, true);
        return true;
    }

    @SubscribeEvent
    public static void tick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)
                || !player.isAlive() || player.isSpectator()) return;
        if (isSheltered(player)) {
            player.clearFire();
            player.fallDistance = 0;
            player.setAirSupply(player.getMaxAirSupply());
        }
        // Evaluate per bearer so a second copy in another slot never doubles the healing.
        if (player.tickCount % 40 != 0 || player.getHealth() >= player.getMaxHealth() || !carries(player)) return;
        if (player.tickCount % 80 != 0 && !player.isHolding(StarcrownDoll.EXCALIBUR.get())) return;
        float before = player.getHealth();
        player.heal(2);
        if (player.getHealth() > before) ExcaliburEffects.avalon(player, false);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void attack(LivingAttackEvent event) {
        if (event.getSource().is(DamageTypes.GENERIC_KILL)) return;
        if (isSheltered(event.getEntity()) || isSheltered(event.getSource().getEntity())) event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void melee(AttackEntityEvent event) {
        if (isSheltered(event.getEntity()) || isSheltered(event.getTarget())) event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void knockback(LivingKnockBackEvent event) {
        if (isSheltered(event.getEntity())) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void blast(ExplosionEvent.Detonate event) {
        // Vanilla explosion impulse is applied separately from LivingKnockBackEvent.
        if (!event.getLevel().isClientSide) event.getAffectedEntities().removeIf(AvalonItem::isSheltered);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void projectile(ProjectileImpactEvent event) {
        if (event.getRayTraceResult() instanceof EntityHitResult hit && isSheltered(hit.getEntity())) {
            event.setImpactResult(ProjectileImpactEvent.ImpactResult.SKIP_ENTITY);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void harmfulEffect(MobEffectEvent.Applicable event) {
        if (isSheltered(event.getEntity()) && event.getEffectInstance().getEffect().getCategory() == MobEffectCategory.HARMFUL) {
            event.setResult(Event.Result.DENY);
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.translatable("tooltip.starcrowndoll.avalon.heal").withStyle(ChatFormatting.AQUA));
        lines.add(Component.translatable(isSheathed(stack) ? "tooltip.starcrowndoll.avalon.draw"
                : "tooltip.starcrowndoll.avalon.store").withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable("tooltip.starcrowndoll.avalon.sanctuary",
                Component.keybind("key.starcrowndoll.avalon")).withStyle(ChatFormatting.GOLD));
    }

    record WakeSanctuary() {}

    static void registerNetwork() {
        SevenNetwork.CHANNEL.registerMessage(12, WakeSanctuary.class, (message, buf) -> {}, buf -> new WakeSanctuary(),
                (message, supplier) -> {
                    var context = supplier.get();
                    context.enqueueWork(() -> {
                        ServerPlayer player = context.getSender();
                        if (player != null) activate(player);
                    });
                    context.setPacketHandled(true);
                }, Optional.of(NetworkDirection.PLAY_TO_SERVER));
    }
}
