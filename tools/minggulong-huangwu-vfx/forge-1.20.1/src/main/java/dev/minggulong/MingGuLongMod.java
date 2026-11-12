package dev.minggulong;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

@Mod(MingGuLongMod.ID)
public final class MingGuLongMod {
    public static final String ID = "minggulong";
    private static final DeferredRegister<EntityType<?>> TYPES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, ID);
    public static final RegistryObject<EntityType<DragonDisplay>> DRAGON = TYPES.register("dragon",
        () -> EntityType.Builder.<DragonDisplay>of(DragonDisplay::new, MobCategory.MISC)
            .sized(4.9F, 3.15F).clientTrackingRange(12).updateInterval(20).build(ID + ":dragon"));
    public MingGuLongMod() { TYPES.register(FMLJavaModLoadingContext.get().getModEventBus()); }
}
