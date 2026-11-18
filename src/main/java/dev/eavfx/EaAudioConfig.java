package dev.eavfx;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;

public final class EaAudioConfig {
    private static final ForgeConfigSpec SPEC;
    private static final ForgeConfigSpec.IntValue CHANT;
    public static final int DEFAULT_CHANT_TICKS=521;
    public static final int MAX_CHANT_TICKS=1200;
    // The bundled ea_blast.ogg lasts 9.5 seconds at normal pitch.
    public static final int RELEASE_SOUND_TICKS = 190;
    static{
        var builder=new ForgeConfigSpec.Builder();
        CHANT=builder.comment("Length of the chant clip in ticks (20 ticks per second); excludes the initial two seconds.")
                .defineInRange("chantTicks",DEFAULT_CHANT_TICKS,20,MAX_CHANT_TICKS);
        SPEC=builder.build();
    }
    public static void register(){
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON,SPEC,"starcrowndoll-ea.toml");
    }
    static int chantTicks(){return CHANT.get();}
}
