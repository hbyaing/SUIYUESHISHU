package dev.starcrown;

import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.ProjectileImpactEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = StarcrownDoll.ID)
public final class TwilightCrown {
    static final String ROOT = "starcrown_twilight";
    private static final String LEASE = "starcrown_twilight_flight";
    private static final String RESCUE_READY = "starcrown_twilight_rescue_ready";
    private static final String RESCUE_GRACE = "starcrown_twilight_rescue_grace";
    private static final int RESCUE_COOLDOWN = 180 * 20;
    private static final UUID SPEED = UUID.fromString("1d2df49a-0885-44be-bff0-d66f2fc06f7a");
    private static final TagKey<EntityType<?>> BOSSES = TagKey.create(Registries.ENTITY_TYPE,
            new ResourceLocation(StarcrownDoll.ID, "bosses"));
    private static final Map<ServerPlayer, Domain> DOMAINS = new WeakHashMap<>();
    private record Domain(ServerLevel level, long until) {}

    public static boolean wearing(Player player) {
        return !player.isSpectator() && player.getItemBySlot(EquipmentSlot.HEAD).is(StarcrownDoll.TWILIGHT_ITEM.get());
    }

    static CompoundTag settings(Player player) {
        var data = player.getPersistentData();
        if (!data.contains(ROOT)) data.put(ROOT, new CompoundTag());
        return data.getCompound(ROOT);
    }

    static long now(ServerLevel level) {
        return level.getServer().overworld().getGameTime();
    }

    static boolean ally(ServerPlayer player, LivingEntity target) {
        return target == player || player.isAlliedTo(target)
                || target instanceof TamableAnimal pet && pet.isTame() && player.getUUID().equals(pet.getOwnerUUID())
                || target instanceof AbstractHorse horse && horse.isTamed() && player.getUUID().equals(horse.getOwnerUUID());
    }

    static boolean hostile(ServerPlayer player, LivingEntity target) {
        return !(target instanceof Player) && !ally(player, target)
                && (target instanceof Enemy || boss(target)
                    || target instanceof Mob mob && mob.getTarget() != null && ally(player, mob.getTarget()));
    }

    static boolean boss(Entity entity) {
        return entity.getType().is(BOSSES);
    }

    static boolean domain(ServerPlayer player) {
        var active = DOMAINS.get(player);
        return active != null && active.level() == player.serverLevel() && active.until() > now(active.level())
                && player.isAlive() && wearing(player);
    }

    static boolean sealed(Mob mob, ServerLevel level) {
        if (DOMAINS.isEmpty() || boss(mob)) return false;
        for (var player : level.players()) {
            if (domain(player) && player.distanceToSqr(mob) <= 256 && hostile(player, mob)) return true;
        }
        return false;
    }

    private static boolean intercepted(ServerPlayer player, Projectile shot) {
        return shot.getOwner() instanceof LivingEntity owner && !boss(owner) && hostile(player, owner);
    }

    @SubscribeEvent
    public static void projectileImpact(ProjectileImpactEvent event) {
        if (DOMAINS.isEmpty() || !(event.getProjectile().level() instanceof ServerLevel level)) return;
        for (var player : level.players()) {
            if (domain(player) && player.position().distanceToSqr(event.getRayTraceResult().getLocation()) <= 256
                    && intercepted(player, event.getProjectile())) {
                event.setImpactResult(ProjectileImpactEvent.ImpactResult.STOP_AT_CURRENT_NO_DAMAGE);
                event.getProjectile().discard();
                return;
            }
        }
    }

    static void update(ServerPlayer player, boolean enabled) {
        var persistent = player.getPersistentData();
        var abilities = player.getAbilities();
        if (enabled) {
            if (!persistent.contains(LEASE)) {
                var lease = new CompoundTag();
                lease.putBoolean("mayfly_before", abilities.mayfly);
                persistent.put(LEASE, lease);
            }
            if (!abilities.mayfly) {
                abilities.mayfly = true;
                player.onUpdateAbilities();
            }
            DivineWill.vision(player, persistent.getCompound(LEASE), now(player.serverLevel()),
                    !settings(player).getBoolean("night_off"));
            if (domain(player)) {
                for (var shot : player.serverLevel().getEntitiesOfClass(Projectile.class, player.getBoundingBox().inflate(16))) {
                    if (player.distanceToSqr(shot) <= 256 && intercepted(player, shot)) shot.discard();
                }
            } else {
                DOMAINS.remove(player);
            }
        } else {
            DOMAINS.remove(player);
            if (persistent.contains(LEASE)) {
                var lease = persistent.getCompound(LEASE);
                DivineWill.vision(player, lease, now(player.serverLevel()), false);
                if (!player.isCreative() && !player.isSpectator() && !lease.getBoolean("mayfly_before")) {
                    abilities.mayfly = false;
                    abilities.flying = false;
                    player.onUpdateAbilities();
                }
                persistent.remove(LEASE);
            }
        }
        DivineWill.modifier(player, Attributes.MOVEMENT_SPEED, SPEED, .35,
                AttributeModifier.Operation.MULTIPLY_TOTAL, enabled);
    }

    @SubscribeEvent
    public static void clone(PlayerEvent.Clone event) {
        var from = event.getOriginal().getPersistentData();
        var to = event.getEntity().getPersistentData();
        if (from.contains(ROOT)) to.put(ROOT, from.getCompound(ROOT).copy());
        if (from.contains(LEASE)) to.put(LEASE, from.getCompound(LEASE).copy());
        if (from.contains(RESCUE_READY)) to.putLong(RESCUE_READY, from.getLong(RESCUE_READY));
    }

    @SubscribeEvent
    public static void mining(PlayerEvent.BreakSpeed event) {
        if (wearing(event.getEntity())) event.setNewSpeed(event.getNewSpeed() * 3);
    }

    private static boolean ordinary(DamageSource source) {
        return !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void reduceDamage(LivingDamageEvent event) {
        var target = event.getEntity();
        if (!(target.level() instanceof ServerLevel level) || !ordinary(event.getSource())) return;
        float amount = event.getAmount();
        boolean reduced = false;
        if (!DOMAINS.isEmpty() && event.getSource().getEntity() != null && boss(event.getSource().getEntity())) {
            for (var player : level.players()) {
                if (domain(player) && player.distanceToSqr(target) <= 256
                        && (target == player || target instanceof Player && player.isAlliedTo(target))) {
                    amount *= .8f;
                    reduced = true;
                    break;
                }
            }
        }
        if (target instanceof ServerPlayer player && wearing(player)) {
            amount = Math.min(amount * .4f, player.getMaxHealth() * .35f);
            reduced = true;
        }
        if (reduced) event.setAmount(amount);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void grace(LivingAttackEvent event) {
        var target = event.getEntity();
        if (target.level() instanceof ServerLevel level && ordinary(event.getSource())
                && target.getPersistentData().getLong(RESCUE_GRACE) > now(level)) event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void rescue(LivingDeathEvent event) {
        var target = event.getEntity();
        if (!(target.level() instanceof ServerLevel level) || !ordinary(event.getSource())) return;
        long time = now(level);
        var data = target.getPersistentData();
        if (data.getLong(RESCUE_READY) > time) return;
        // Vanilla checks and consumes the target's own totem before posting LivingDeathEvent.
        ServerPlayer protector = target instanceof ServerPlayer player && wearing(player)
                && settings(player).getLong("rescue_ready") <= time ? player : null;
        if (protector == null) {
            for (var player : level.players()) {
                if (wearing(player) && player.isAlive() && player.distanceToSqr(target) <= 576
                        && ally(player, target) && settings(player).getLong("rescue_ready") <= time
                        && (protector == null || player.distanceToSqr(target) < protector.distanceToSqr(target))) {
                    protector = player;
                }
            }
        }
        if (protector == null) return;
        target.setHealth(target.getMaxHealth() * .4f);
        if (target.getHealth() <= 0) return;
        settings(protector).putLong("rescue_ready", time + RESCUE_COOLDOWN);
        data.putLong(RESCUE_READY, time + RESCUE_COOLDOWN);
        data.putLong(RESCUE_GRACE, time + 60);
        target.deathTime = 0;
        level.broadcastEntityEvent(target, (byte) 35);
        event.setCanceled(true);
    }

    static void action(ServerPlayer player, int action, int value) {
        if (!wearing(player) || !player.isAlive()) return;
        var data = settings(player);
        long time = now(player.serverLevel());
        String message;
        switch (action) {
            case 0 -> {
                long ready = data.getLong("domain_ready");
                if (ready > time) {
                    message = "万象领域冷却中，剩余 " + ((ready - time + 19) / 20) + " 秒";
                } else {
                    DOMAINS.put(player, new Domain(player.serverLevel(), time + 200));
                    data.putLong("domain_ready", time + 200 + 900);
                    message = "万象领域已展开";
                }
            }
            case 2 -> {
                boolean off = !data.getBoolean("night_off");
                data.putBoolean("night_off", off);
                DivineWill.vision(player, player.getPersistentData().getCompound(LEASE), time, !off);
                message = off ? "夜视已关闭" : "夜视已开启";
            }
            case 3 -> {
                boolean enabled = !data.getBoolean("area_mine");
                data.putBoolean("area_mine", enabled);
                message = enabled ? "范围采掘已开启" : "范围采掘已关闭";
            }
            case 4 -> message = TwilightTravel.blink(player, data, time);
            case 5 -> {
                if (value < 0 || value >= 3) return;
                message = DivineTravel.save(player, value, data) ? "余晖锚点已记录" : "当前位置不安全";
            }
            case 6 -> {
                if (value < 0 || value >= 3) return;
                message = TwilightTravel.anchor(player, data, value, time);
            }
            case 18 -> {
                if (value < 0 || value > 1) return;
                data.putInt("mining_range", value);
                message = value == 0 ? "采掘范围 3×3×3" : "采掘范围 5×5×5";
            }
            default -> { return; }
        }
        player.displayClientMessage(Component.literal(message), true);
    }
}
