package dev.starcrown;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import javax.sound.sampled.AudioFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.sounds.AudioStream;
import net.minecraft.client.sounds.SoundBufferLibrary;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = StarcrownDoll.ID, value = Dist.CLIENT)
public final class ExcaliburChantAudio extends AbstractTickableSoundInstance {
    private static final Map<UUID, ExcaliburChantAudio> VOICES = new HashMap<>();
    // Release is at 11.60 s and the recording ends at 12.15352 s. Allow a full twelve ticks of tail.
    private static final int RELEASE_TAIL_TICKS = 12;

    private final UUID owner;
    private final ClientLevel world;
    private final long start;
    private boolean playing;
    private int offsetTicks;

    private ExcaliburChantAudio(UUID owner, ClientLevel world, long start) {
        super(StarcrownDoll.EXCALIBUR_CHANT.get(), SoundSource.PLAYERS, RandomSource.create());
        this.owner = owner;
        this.world = world;
        this.start = start;
    }

    public static void sync(UUID owner, ResourceLocation dimension, long start, boolean active, boolean completed) {
        var mc = Minecraft.getInstance();
        var level = mc.level;
        if (level == null || !level.dimension().location().equals(dimension)) return;
        var previous = VOICES.get(owner);
        if (previous != null && previous.world == level && previous.start == start) {
            if (!active) {
                VOICES.remove(owner);
                // OpenAL finishes the existing stream at its actual end, including the reverberation.
                if (!completed) previous.silence();
            }
            return;
        }
        if (!active || level.getGameTime() - start >= ExcaliburChant.DURATION) return;
        if (previous != null) previous.silence();
        var voice = new ExcaliburChantAudio(owner, level, start);
        VOICES.put(owner, voice);
        voice.tryStart();
    }

    private void tryStart() {
        var player = world.getPlayerByUUID(owner);
        if (player == null) return; // The tracking packet can arrive before the player spawn.
        offsetTicks = (int)Math.max(0L, world.getGameTime() - start);
        moveWith(player);
        playing = true;
        Minecraft.getInstance().getSoundManager().play(this);
    }

    private void moveWith(Player player) {
        x = player.getX();
        y = player.getEyeY() - .14;
        z = player.getZ();
    }

    @Override
    public void tick() {
        var player = world.getPlayerByUUID(owner);
        if (Minecraft.getInstance().level != world || player == null || !player.isAlive()) stop();
        else moveWith(player);
    }

    @Override
    public CompletableFuture<AudioStream> getStream(SoundBufferLibrary buffers, Sound sound, boolean loop) {
        int elapsed = offsetTicks;
        var audio = buffers.getStream(sound.getPath(), false);
        return elapsed == 0 ? audio : audio.thenApply(stream -> LateEntry.seek(stream, elapsed));
    }

    /** Vorbis reads whole packets. Keep all samples from the exact seek point onward in the first OpenAL buffer. */
    private static final class LateEntry implements AudioStream {
        private final AudioStream source;
        private ByteBuffer first;

        private LateEntry(AudioStream source, ByteBuffer first) {
            this.source = source;
            this.first = first;
        }

        static AudioStream seek(AudioStream source, int elapsed) {
            var format = source.getFormat();
            int bytes = Math.round(format.getFrameRate() * elapsed / 20F) * format.getFrameSize();
            try {
                // Decode the skipped interval while loading the stream, before it reaches the OpenAL mixer.
                var decoded = source.read(bytes);
                if (decoded.remaining() <= bytes) return source;
                decoded.position(decoded.position() + bytes);
                return new LateEntry(source, decoded);
            } catch (IOException error) {
                try { source.close(); } catch (IOException close) { error.addSuppressed(close); }
                throw new CompletionException(error);
            }
        }

        @Override public AudioFormat getFormat() { return source.getFormat(); }

        @Override
        public ByteBuffer read(int size) throws IOException {
            if (first == null) return source.read(size);
            var data = first;
            first = null;
            return data;
        }

        @Override public void close() throws IOException { source.close(); }
    }

    private void silence() {
        stop();
        if (playing) Minecraft.getInstance().getSoundManager().stop(this);
    }

    @SubscribeEvent
    public static void maintain(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || VOICES.isEmpty()) return;
        var level = Minecraft.getInstance().level;
        VOICES.values().removeIf(voice -> {
            if (level == voice.world && !voice.isStopped()) {
                long age = level.getGameTime() - voice.start;
                if (!voice.playing && age < ExcaliburChant.DURATION) voice.tryStart();
                if (age < ExcaliburChant.DURATION + (voice.playing ? RELEASE_TAIL_TICKS : 0)) return false;
            }
            voice.silence();
            return true;
        });
    }
}
