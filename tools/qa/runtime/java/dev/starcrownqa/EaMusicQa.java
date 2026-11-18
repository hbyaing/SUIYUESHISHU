package dev.starcrownqa;

import com.mojang.blaze3d.audio.Channel;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.ChannelAccess;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.sounds.SoundSource;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.sound.PlaySoundEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;
import org.lwjgl.openal.AL10;

/** Observe the original streamed music channel on the audio thread in the isolated game. */
@Mod.EventBusSubscriber(modid="starcrownqa",value=Dist.CLIENT)
public final class EaMusicQa {
    private static boolean starting;
    private static SoundInstance song;
    private static SoundEngine engine;

    static void start(Minecraft mc){
        mc.options.getSoundSourceOptionInstance(SoundSource.MUSIC).set(.7);
        mc.getMusicManager().stopPlaying();
        starting=true;
        try{mc.getMusicManager().startPlaying(mc.getSituationalMusic());}
        finally{starting=false;}
    }

    @SubscribeEvent public static void playing(PlaySoundEvent event){
        if(starting && event.getOriginalSound().getSource()==SoundSource.MUSIC){
            song=event.getOriginalSound();
            engine=event.getEngine();
        }
    }

    static boolean matches(Minecraft mc,boolean paused){
        if(engine==null || song==null || !mc.getSoundManager().isActive(song))return false;
        try{
            Field channels=ObfuscationReflectionHelper.findField(SoundEngine.class,"f_120226_");
            Object entry=((Map<?,?>)channels.get(engine)).get(song);
            if(!(entry instanceof ChannelAccess.ChannelHandle handle))return false;
            var result=new CompletableFuture<Integer>();
            handle.execute(channel->{
                try{
                    for(Field field:Channel.class.getDeclaredFields()){
                        if(field.getType()==int.class && Modifier.isFinal(field.getModifiers()) && !Modifier.isStatic(field.getModifiers())){
                            field.setAccessible(true);
                            result.complete(AL10.alGetSourcei(field.getInt(channel),AL10.AL_SOURCE_STATE));
                            return;
                        }
                    }
                    result.completeExceptionally(new IllegalStateException("Missing OpenAL source"));
                }catch(ReflectiveOperationException ex){result.completeExceptionally(ex);}
            });
            int state=result.get(3,TimeUnit.SECONDS);
            float volume=mc.options.getSoundSourceVolume(SoundSource.MUSIC);
            System.out.println("EA MUSIC QA sameSong="+song.getLocation()+" state="+state+" volume="+volume);
            return state==(paused?AL10.AL_PAUSED:AL10.AL_PLAYING) && Math.abs(volume-.7f)<.001f;
        }catch(Exception ex){ex.printStackTrace();return false;}
    }
}
