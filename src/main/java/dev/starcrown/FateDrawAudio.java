package dev.starcrown;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Sound cues use the same server start tick as the key retrieval animation. */
@Mod.EventBusSubscriber(modid = StarcrownDoll.ID, value = Dist.CLIENT)
public final class FateDrawAudio {
    private record Cue(int tick, Supplier<SoundEvent> event) {}
    private static final List<Cue> CUES = List.of(
            new Cue(0, StarcrownDoll.EA_DRAW_OPEN),
            new Cue(114, StarcrownDoll.EA_DRAW_VOID),
            new Cue(132, StarcrownDoll.EA_DRAW_FLASH),
            new Cue(141, StarcrownDoll.EA_DRAW_BREAK));
    private static final Map<UUID, Playback> PLAYING = new HashMap<>();
    private static ClientLevel lastLevel;

    private static final class Playback {
        final long start;
        final List<HandSound> sounds = new ArrayList<>(CUES.size());
        int next;

        Playback(long start) {
            this.start = start;
        }

        void stop(Minecraft mc) {
            for (HandSound sound : sounds) sound.cancel(mc);
            sounds.clear();
        }
    }

    private static final class HandSound extends AbstractTickableSoundInstance {
        private final Player owner;
        private final boolean offhand;

        HandSound(Player owner, boolean offhand, SoundEvent event) {
            super(event, SoundSource.PLAYERS, RandomSource.create());
            this.owner = owner;
            this.offhand = offhand;
            updatePosition();
        }

        private void updatePosition() {
            HumanoidArm arm = offhand ? owner.getMainArm().getOpposite() : owner.getMainArm();
            double side = arm == HumanoidArm.RIGHT ? .32 : -.32;
            double yaw = Math.toRadians(owner.getYRot());
            x = owner.getX() - Math.cos(yaw) * side - Math.sin(yaw) * .30;
            y = owner.getEyeY() - .16;
            z = owner.getZ() - Math.sin(yaw) * side + Math.cos(yaw) * .30;
        }

        @Override public void tick() {
            if (!owner.isAlive() || owner.isRemoved() || Minecraft.getInstance().level != owner.level()) {
                stop();
                return;
            }
            updatePosition();
        }

        void cancel(Minecraft mc) {
            stop();
            mc.getSoundManager().stop(this);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (lastLevel != mc.level) clear(mc);
        if (mc.level == null || mc.isPaused()) return;
        PLAYING.entrySet().removeIf(entry -> {
            FateNetwork.State cast = FateClient.CASTS.get(entry.getKey());
            Playback playback = entry.getValue();
            if (cast != null && cast.start() == playback.start) return false;
            playback.stop(mc);
            // Keep the cue cursor if a spectator crosses the tracking boundary twice during one draw.
            return cast != null || mc.level.getGameTime() - playback.start > FateItems.DURATION + 2;
        });
        for (FateNetwork.State cast : FateClient.CASTS.values()) {
            if (!(mc.level.getEntity(cast.entity()) instanceof Player owner) || !owner.getUUID().equals(cast.owner())) continue;
            Playback playback = PLAYING.computeIfAbsent(cast.owner(), id -> new Playback(cast.start()));
            long age = mc.level.getGameTime() - cast.start();
            while (playback.next < CUES.size() && CUES.get(playback.next).tick() <= age) {
                Cue cue = CUES.get(playback.next++);
                // A viewer entering tracking range midway hears the upcoming cues on time.
                if (age - cue.tick() > 4) continue;
                HandSound sound = new HandSound(owner, cast.offhand(), cue.event().get());
                playback.sounds.add(sound);
                mc.getSoundManager().play(sound);
            }
        }
    }

    @SubscribeEvent
    public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
        clear(Minecraft.getInstance());
    }

    private static void clear(Minecraft mc) {
        PLAYING.values().forEach(playback -> playback.stop(mc));
        PLAYING.clear();
        lastLevel = mc.level;
    }
}
