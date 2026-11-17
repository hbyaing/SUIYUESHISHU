package dev.starcrown;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;

/** Client rendering budgets only; no server damage, timing or radius changes. */
public final class VfxClientConfig {
    private static final ForgeConfigSpec.Builder BUILDER=new ForgeConfigSpec.Builder();
    public static final ForgeConfigSpec.DoubleValue HUANGWU_RESOLUTION=BUILDER
        .comment("Huangwu volume width/height relative to the window. 0.5 = balanced (one quarter pixels), 0.33 = low GPU, 1.0 = native. Core/meshes stay native; ray steps and skill logic stay unchanged.")
        .defineInRange("huangwuVolumeResolution",0.5,0.25,1.0);
    private static final ForgeConfigSpec SPEC=BUILDER.build();
    public static void register(){ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT,SPEC,"starcrowndoll-client.toml");}
}
