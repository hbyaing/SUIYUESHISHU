package dev.eavfx;

import com.mojang.blaze3d.audio.Channel;
import dev.starcrown.StarcrownDoll;
import java.lang.reflect.Field;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.ChannelAccess;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.sounds.SoundSource;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.sound.PlaySoundEvent;
import net.minecraftforge.client.event.sound.SoundEvent.SoundSourceEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;

@Mod.EventBusSubscriber(modid=StarcrownDoll.ID, value=Dist.CLIENT)
public final class EaChantMusic {
    // SoundManager only exposes a global pause. Use the same audio-thread handles for individual tracks.
    private static final Field CHANNELS = ObfuscationReflectionHelper.findField(SoundEngine.class, "f_120226_");
    private static SoundEngine engine;
    private static volatile boolean suppressing;
    // Accessed exclusively on the sound executor, including Forge's sound-source callbacks.
    private static final Set<Channel> paused = Collections.newSetFromMap(new WeakHashMap<>());

    private EaChantMusic() {}

    private static boolean vanillaMusic(SoundInstance sound) {
        return sound.getSource() == SoundSource.MUSIC && "minecraft".equals(sound.getLocation().getNamespace());
    }

    @SubscribeEvent
    public static void onPlayRequest(PlaySoundEvent event) {
        if (vanillaMusic(event.getOriginalSound())) engine = event.getEngine();
    }

    @SubscribeEvent
    public static void onSourceStarted(SoundSourceEvent event) {
        if (suppressing && vanillaMusic(event.getSound())) pause(event.getChannel());
    }

    private static void pause(Channel channel) {
        if (channel.playing()) {
            channel.pause();
            paused.add(channel);
        }
    }

    @SuppressWarnings("unchecked")
    static void update(Minecraft mc, boolean audibleChant) {
        if (!suppressing && !audibleChant) return;
        suppressing = audibleChant;
        if (engine == null) return;
        boolean gamePaused = mc.isPaused();
        try {
            var channels = (Map<SoundInstance, ChannelAccess.ChannelHandle>) CHANNELS.get(engine);
            channels.forEach((sound, handle) -> {
                if (!vanillaMusic(sound)) return;
                if (audibleChant) handle.execute(EaChantMusic::pause);
                else handle.execute(channel -> {
                    if (paused.remove(channel) && !gamePaused) channel.unpause();
                });
            });
        } catch (IllegalAccessException ex) {
            throw new IllegalStateException("Cannot access Minecraft sound channels", ex);
        }
    }
}
