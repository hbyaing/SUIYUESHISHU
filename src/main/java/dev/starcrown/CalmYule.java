package dev.starcrown;

import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.ProjectileImpactEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingChangeTargetEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = StarcrownDoll.ID)
public final class CalmYule {
    static final int PEACE_ACTION = 19, PURIFY_ACTION = 20, FROST_ACTION = 21, GIFT_ACTION = 22, CLEAR_PACTS_ACTION = 23;
    private static final String ROOT = "starcrown_yule_options";
    private static final int RADIUS = 12, DURATION = 8 * 20, COOLDOWN = 45 * 20;
    private static final Map<ServerPlayer, Field> FIELDS = new WeakHashMap<>();
    private record Field(ServerLevel level, long until) {}

    public static boolean active(Entity entity) {
        return entity instanceof Player player && player.getInventory() != null && !player.isSpectator()
                && DivinityInventory.equipped(player).is(StarcrownDoll.CALM_ITEM.get());
    }

    static CompoundTag settings(Player player) {
        var data = player.getPersistentData();
        if (!data.contains(ROOT)) data.put(ROOT, new CompoundTag());
        return data.getCompound(ROOT);
    }

    static CompoundTag existingSettings(Player player) {
        return player.getPersistentData().getCompound(ROOT);
    }

    static boolean peaceEnabled(Player player) {
        return !player.getPersistentData().getCompound(ROOT).getBoolean("peace_off");
    }

    static boolean purifyEnabled(Player player) {
        return !player.getPersistentData().getCompound(ROOT).getBoolean("purify_off");
    }

    static boolean frostEnabled(Player player) {
        return !player.getPersistentData().getCompound(ROOT).getBoolean("frost_off");
    }

    static boolean fieldActive(ServerPlayer player) {
        var field = FIELDS.get(player);
        return field != null && field.level() == player.serverLevel() && player.isAlive()
                && active(player) && TwilightCrown.now(field.level()) < field.until();
    }

    private static boolean ordinaryHostile(Entity entity) {
        return entity instanceof Mob mob && YuleTolerance.hostile(mob) && !YuleTolerance.friendly(mob)
                && !TwilightCrown.boss(mob);
    }

    private static boolean protectedTarget(ServerPlayer caster, LivingEntity target) {
        return caster.distanceToSqr(target) <= RADIUS * RADIUS && TwilightCrown.ally(caster, target);
    }

    private static boolean prevented(Entity attacker, LivingEntity target) {
        if (FIELDS.isEmpty() || !(target.level() instanceof ServerLevel level) || !ordinaryHostile(attacker)) return false;
        for (var caster : level.players()) {
            if (fieldActive(caster) && caster.distanceToSqr(attacker) <= RADIUS * RADIUS
                    && protectedTarget(caster, target)) return true;
        }
        return false;
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void target(LivingChangeTargetEvent event) {
        var target = event.getNewTarget();
        if (target != null && prevented(event.getEntity(), target)) event.setNewTarget(null);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void attack(LivingAttackEvent event) {
        if (prevented(event.getSource().getEntity(), event.getEntity())) event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void damage(LivingDamageEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && active(player)
                && !event.getSource().is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            event.setAmount(Math.min(event.getAmount() * .65f, player.getMaxHealth() * .4f));
        }
    }

    @SubscribeEvent
    public static void impact(ProjectileImpactEvent event) {
        var shot = event.getProjectile();
        if (FIELDS.isEmpty() || !(shot.level() instanceof ServerLevel level) || !ordinaryHostile(shot.getOwner())) return;
        for (var caster : level.players()) {
            if (fieldActive(caster) && caster.position().distanceToSqr(event.getRayTraceResult().getLocation()) <= RADIUS * RADIUS) {
                event.setImpactResult(ProjectileImpactEvent.ImpactResult.STOP_AT_CURRENT_NO_DAMAGE);
                shot.discard();
                return;
            }
        }
    }

    @SubscribeEvent
    public static void tick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)) return;
        if (!active(player) || !player.isAlive()) {
            FIELDS.remove(player);
            return;
        }
        if (frostEnabled(player)) YuleChastity.freeze(player, 3);
        if (player.tickCount % 20 == 0) YuleCharity.calmGifts(player);
        if (!fieldActive(player)) {
            FIELDS.remove(player);
            return;
        }
        var area = player.getBoundingBox().inflate(RADIUS);
        var level = player.serverLevel();
        for (var shot : level.getEntitiesOfClass(Projectile.class, area)) {
            if (player.distanceToSqr(shot) <= RADIUS * RADIUS && ordinaryHostile(shot.getOwner())) shot.discard();
        }
        for (var mob : level.getEntitiesOfClass(Mob.class, area)) {
            if (player.distanceToSqr(mob) <= RADIUS * RADIUS && ordinaryHostile(mob)
                    && mob.getTarget() != null && protectedTarget(player, mob.getTarget())) {
                mob.setTarget(null);
                mob.getNavigation().stop();
            }
        }
    }

    @SubscribeEvent
    public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) FIELDS.remove(player);
    }

    @SubscribeEvent
    public static void clone(PlayerEvent.Clone event) {
        var data = event.getOriginal().getPersistentData();
        if (data.contains(ROOT)) {
            event.getEntity().getPersistentData().put(ROOT, data.getCompound(ROOT).copy());
            YuleDiligence.refreshCalm(event.getEntity());
        }
    }

    public static MobEffectInstance shorten(LivingEntity target, MobEffectInstance effect) {
        if (target.level().isClientSide || !active(target) || effect == null || effect.getDuration() <= 1
                || effect.getEffect().getCategory() != MobEffectCategory.HARMFUL) return effect;
        var tag = effect.save(new CompoundTag());
        shortenDuration(tag);
        var shortened = MobEffectInstance.load(tag);
        return shortened == null ? effect : shortened;
    }

    private static void shortenDuration(CompoundTag tag) {
        int duration = tag.getInt("Duration");
        if (duration > 1) tag.putInt("Duration", Math.max(1, (int) ((duration * 2L + 4) / 5)));
        if (tag.contains("HiddenEffect")) shortenDuration(tag.getCompound("HiddenEffect"));
    }

    public static boolean saveDurability(ServerPlayer player, ItemStack stack, RandomSource random) {
        if (!active(player)) return false;
        for (var slot : EquipmentSlot.values()) {
            if (player.getItemBySlot(slot) == stack) return random.nextBoolean();
        }
        return false;
    }

    static void action(ServerPlayer player, int id) {
        boolean calm = active(player);
        if (!player.isAlive() || player.isSpectator() || !(calm || YuleHumility.active(player))) return;
        if (id == GIFT_ACTION) {
            YuleGenerosity.gift(player, calm);
            return;
        }
        var data = settings(player);
        String message;
        switch (id) {
            case PEACE_ACTION -> {
                if (calm) {
                    long now = TwilightCrown.now(player.serverLevel()), ready = data.getLong("calm_peace_ready");
                    if (now < ready) message = "和平领域冷却中，剩余 " + ((ready - now + 19) / 20) + " 秒";
                    else {
                        FIELDS.put(player, new Field(player.serverLevel(), now + DURATION));
                        data.putLong("calm_peace_ready", now + DURATION + COOLDOWN);
                        message = "和平领域已展开";
                    }
                } else {
                    boolean enabled = !peaceEnabled(player);
                    data.putBoolean("peace_off", !enabled);
                    message = enabled ? "和平领域已开启" : "和平领域已关闭";
                }
            }
            case PURIFY_ACTION -> {
                if (calm) return;
                boolean enabled = !purifyEnabled(player);
                data.putBoolean("purify_off", !enabled);
                message = enabled ? "圣光净化已开启" : "圣光净化已关闭";
            }
            case FROST_ACTION -> {
                if (!calm) return;
                boolean enabled = !frostEnabled(player);
                data.putBoolean("frost_off", !enabled);
                message = enabled ? "液体凝结已开启" : "液体凝结已关闭";
            }
            case CLEAR_PACTS_ACTION -> {
                if (!calm) return;
                data.remove("calm_pacts");
                message = "已解除清欢契约";
            }
            default -> { return; }
        }
        player.displayClientMessage(Component.literal(message), true);
    }
}
