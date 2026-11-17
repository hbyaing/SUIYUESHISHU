package dev.starcrown;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;

/** Client rendering budgets only; no server damage, timing or radius changes. */
public final class VfxClientConfig {
    private static final ForgeConfigSpec.Builder BUILDER=new ForgeConfigSpec.Builder();
    public enum HuangwuMode { LITE, LEGACY, BASIC, OFF }
    public static final ForgeConfigSpec.EnumValue<HuangwuMode> HUANGWU_MODE=BUILDER
        .comment("LITE = 12-sample procedural fog (default); LEGACY = original layered volume; BASIC = vanilla geometry; OFF = no Huangwu visuals. Reload resources (F3+T) after changing mode.")
        .defineEnum("huangwuMode",HuangwuMode.LITE);
    public static final ForgeConfigSpec.IntValue HUANGWU_MAX_PIXELS=BUILDER
        .comment("Maximum integration pixels for LITE, shared reusable target. Limits close-up and high-resolution GPU cost; does not limit game FPS.")
        .defineInRange("huangwuLiteMaxPixels",360000,57600,8294400);
    public static final ForgeConfigSpec.DoubleValue HUANGWU_RESOLUTION=BUILDER
        .comment("Huangwu integration width/height relative to the window. 0.5 = balanced, 0.33 = low GPU, 1.0 = native (LITE also respects its pixel cap). Skill logic is unchanged.")
        .defineInRange("huangwuVolumeResolution",0.5,0.25,1.0);
    private static final ForgeConfigSpec SPEC=BUILDER.build();
    public static void register(){ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT,SPEC,"starcrowndoll-client.toml");}
}
