package dev.starcrown;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.registries.ForgeRegistries;

/** Tracks effects granted by Supreme Divinity so ordinary potions can take precedence. */
public final class CrownBenefits {
    private static final ThreadLocal<MobEffectInstance> GRANT = new ThreadLocal<>();

    private static int duration(MobEffect type) {
        return type == MobEffects.NIGHT_VISION ? 400 : 240;
    }

    private static boolean owned(MobEffectInstance current, CompoundTag entry) {
        if (current == null || current.isInfiniteDuration() || !current.isAmbient()
                || current.isVisible() || current.showIcon() || current.getDuration() > duration(current.getEffect())) return false;
        int amp = entry.getInt("amp");
        // Minecraft 1.20.1 reads amplifiers above 127 from a signed NBT byte as zero after an unclean stop.
        return current.getAmplifier() == amp || amp > 127 && current.getAmplifier() == 0;
    }

    private static boolean add(ServerPlayer player, MobEffectInstance effect) {
        var previous = GRANT.get();
        GRANT.set(effect);
        try {
            return player.addEffect(effect);
        } finally {
            if (previous == null) GRANT.remove();
            else GRANT.set(previous);
        }
    }

    private static boolean release(ServerPlayer player, MobEffect type, CompoundTag tracked, String id) {
        if (!tracked.contains(id)) return true;
        var entry = tracked.getCompound(id);
        boolean owned = owned(player.getEffect(type), entry);
        if (owned && !player.removeEffect(type)) return false;
        tracked.remove(id);
        if (owned && entry.contains("effect")) {
            long elapsed = Math.max(0, player.serverLevel().getGameTime() - entry.getLong("tick"));
            var previous = MobEffectInstance.load(DivineWill.elapsedEffect(entry.getCompound("effect"), elapsed));
            if (previous != null && (previous.isInfiniteDuration() || previous.getDuration() > 0)) add(player, previous);
        }
        return true;
    }

    static void update(ServerPlayer player, CompoundTag lease, boolean grant) {
        if (lease.contains("vision_owned") || lease.contains("vision_before")) {
            DivineWill.vision(player, lease, player.getServer().overworld().getGameTime(), false);
        }
        var tracked = lease.getCompound("benefits");
        lease.put("benefits", tracked);
        int amp = PotionFilter.level(player) - 1;
        for (var type : ForgeRegistries.MOB_EFFECTS) {
            if (!PotionFilter.filterable(type)) continue;
            String id = ForgeRegistries.MOB_EFFECTS.getKey(type).toString();
            if (!grant || !DivineWill.automaticBenefit(type) || !PotionFilter.enabled(player, type)) {
                release(player, type, tracked, id);
                continue;
            }
            var current = player.getEffect(type);
            if (tracked.contains(id) && owned(current, tracked.getCompound(id))) {
                if (current.getAmplifier() == amp) {
                    if (current.getDuration() <= (type == MobEffects.NIGHT_VISION ? 300 : 60)) {
                        add(player, new MobEffectInstance(type, duration(type), amp, true, false, false));
                    }
                    continue;
                }
                if (!release(player, type, tracked, id)) continue;
                current = player.getEffect(type);
            } else {
                tracked.remove(id);
            }
            if (current != null) continue;
            if (add(player, new MobEffectInstance(type, duration(type), amp, true, false, false))) {
                var entry = new CompoundTag();
                entry.putInt("amp", amp);
                tracked.put(id, entry);
            }
        }
    }

    public static void beforeExternal(LivingEntity entity, MobEffectInstance incoming) {
        if (!(entity instanceof ServerPlayer player) || incoming == GRANT.get()) return;
        var lease = player.getPersistentData().getCompound(DivineWill.LEASE);
        var tracked = lease.getCompound("benefits");
        if (tracked.isEmpty()) return;
        var id = ForgeRegistries.MOB_EFFECTS.getKey(incoming.getEffect());
        if (id != null) release(player, incoming.getEffect(), tracked, id.toString());
    }

    private CrownBenefits() {}
}
