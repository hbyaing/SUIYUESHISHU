package dev.starcrown;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.TraceableEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = StarcrownDoll.ID)
public final class CrownStrike {
    private static final Map<LivingEntity, Map<UUID, Reprisal>> REPRISALS = new WeakHashMap<>();
    private static final ThreadLocal<Boolean> PULSING = ThreadLocal.withInitial(() -> false);

    private static final class Reprisal {
        long markedUntil, readyAt, nextPulse;
        float damage;
        int pulses;
    }

    public static boolean pulsing() {
        return PULSING.get();
    }

    private static ServerPlayer attacker(LivingEntity target, DamageSource source) {
        if (target instanceof Player || pulsing() || DivineCost.pulsing()
                || source.is(DamageTypes.THORNS) || !(source.getEntity() instanceof ServerPlayer player)
                || player.level() != target.level()) return null;
        return player;
    }

    private static ServerPlayer owner(Entity origin, ServerLevel level) {
        for (int hops = 0; origin != null && origin.level() == level && hops < 8; hops++) {
            if (origin instanceof ServerPlayer player) return player;
            Entity next = origin instanceof TraceableEntity traceable ? traceable.getOwner() : null;
            if (next == null && origin instanceof OwnableEntity owned) next = owned.getOwner();
            if (next == origin) return null;
            origin = next;
        }
        return null;
    }

    public static boolean supremeStrike(LivingEntity target, DamageSource source) {
        if (target instanceof Player || !(target.level() instanceof ServerLevel level)
                || pulsing() || DivineCost.pulsing() || source.is(DamageTypes.THORNS)) return false;
        var player = owner(source.getEntity(), level);
        if (player == null && source.getDirectEntity() != source.getEntity()) player = owner(source.getDirectEntity(), level);
        return player != null && DivineWill.wearing(player) && !DivineSeven.ally(player, target);
    }

    public static float empowerSupreme(LivingEntity target, DamageSource source, float amount) {
        if (amount <= 0 || !Float.isFinite(amount) || !supremeStrike(target, source)) return amount;
        return (float) Math.min(Float.MAX_VALUE, (double) amount * 10);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void empower(LivingHurtEvent event) {
        float amount = event.getAmount();
        if (amount <= 0 || !Float.isFinite(amount)) return;
        var target = event.getEntity();
        var player = attacker(target, event.getSource());
        if (player != null && TwilightCrown.wearing(player) && !TwilightCrown.ally(player, target)) {
            event.setAmount((float) Math.min(Float.MAX_VALUE, (double) amount + Math.min(amount * .5f, 60)));
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void damage(LivingDamageEvent event) {
        var target = event.getEntity();
        if (pulsing() || DivineCost.pulsing() || !(target.level() instanceof ServerLevel level)
                || event.getAmount() <= 0 || !Float.isFinite(event.getAmount())) return;
        long now = TwilightCrown.now(level);
        if (target instanceof ServerPlayer player && TwilightCrown.wearing(player)
                && event.getSource().getEntity() instanceof LivingEntity enemy && TwilightCrown.hostile(player, enemy)) {
            var mark = REPRISALS.computeIfAbsent(enemy, key -> new HashMap<>())
                    .computeIfAbsent(player.getUUID(), key -> new Reprisal());
            mark.markedUntil = now + 200;
        }
        var attacker = attacker(target, event.getSource());
        var marks = REPRISALS.get(target);
        if (marks == null || attacker == null || !TwilightCrown.wearing(attacker) || TwilightCrown.ally(attacker, target)) return;
        var mark = marks.get(attacker.getUUID());
        if (mark == null || mark.markedUntil <= now || mark.readyAt > now) return;
        mark.markedUntil = 0;
        mark.readyAt = now + 240;
        mark.nextPulse = now + 20;
        mark.damage = Math.min(target.getMaxHealth() * .12f, 120) / 4;
        mark.pulses = 4;
    }

    @SubscribeEvent
    public static void tick(LivingEvent.LivingTickEvent event) {
        var target = event.getEntity();
        var marks = REPRISALS.get(target);
        if (marks == null || !(target.level() instanceof ServerLevel level)) return;
        long now = TwilightCrown.now(level);
        var iterator = marks.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            var mark = entry.getValue();
            if (mark.pulses > 0) {
                if (!(level.getPlayerByUUID(entry.getKey()) instanceof ServerPlayer owner)
                        || !owner.isAlive() || !target.isAlive()
                        || !TwilightCrown.wearing(owner) || TwilightCrown.ally(owner, target)) {
                    mark.pulses = 0;
                } else if (now >= mark.nextPulse) {
                    mark.nextPulse = now + 20;
                    mark.pulses--;
                    PULSING.set(true);
                    try {
                        target.hurt(target.damageSources().playerAttack(owner), mark.damage);
                    } finally {
                        PULSING.remove();
                    }
                }
            }
            if (mark.pulses == 0 && now >= mark.markedUntil && now >= mark.readyAt) iterator.remove();
        }
        if (marks.isEmpty()) REPRISALS.remove(target);
    }
}
