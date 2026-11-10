package dev.starcrown;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;

final class TwilightTravel {
    private static final int RECHARGE = 12 * 20;
    private static final int ANCHOR_COST = 1000;

    static String blink(ServerPlayer player, CompoundTag data, long now) {
        int charges = data.contains("blink_charges") ? data.getInt("blink_charges") : 2;
        long next = data.getLong("blink_next");
        while (charges < 2 && now >= next) {
            charges++;
            next += RECHARGE;
        }
        if (charges == 0) return "星跃充能中，剩余 " + ((next - now + 19) / 20) + " 秒";
        if (!DivineTravel.blink(player, 64, null)) return "没有找到已加载的安全落点";
        data.putInt("blink_charges", charges - 1);
        data.putLong("blink_next", charges == 2 ? now + RECHARGE : next);
        return "群星已为你开路，剩余 " + (charges - 1) + " 次";
    }

    static String anchor(ServerPlayer player, CompoundTag data, int slot, long now) {
        long ready = data.getLong("anchor_ready");
        if (ready > now) return "归途冷却中，剩余 " + ((ready - now + 19) / 20) + " 秒";
        long xp = experience(player);
        if (xp < ANCHOR_COST) return "返回锚点需要 1000 点经验";
        if (!DivineTravel.stored(player, data.getCompound("anchors").getCompound("slot" + slot), null)) {
            return "锚点未记录、落点不安全或传送被拦截";
        }
        player.totalExperience = (int) Math.min(Integer.MAX_VALUE, xp);
        player.giveExperiencePoints(-ANCHOR_COST);
        data.putLong("anchor_ready", now + 60 * 20);
        return "已返回余晖锚点，消耗 1000 点经验";
    }

    private static long experience(ServerPlayer player) {
        // totalExperience can be stale after enchanting or /experience levels.
        long level = player.experienceLevel;
        long base = level <= 16 ? level * (level + 6)
                : level <= 31 ? (5 * level * level - 81 * level + 720) / 2
                : level > 50_000 ? Integer.MAX_VALUE : (9 * level * level - 325 * level + 4440) / 2;
        return base + Math.round((double) player.experienceProgress * player.getXpNeededForNextLevel());
    }
}
