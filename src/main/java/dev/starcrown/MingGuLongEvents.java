package dev.starcrown;

import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = StarcrownDoll.ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class MingGuLongEvents {
    private MingGuLongEvents() {}

    @SubscribeEvent
    public static void attributes(EntityAttributeCreationEvent event) {
        event.put(StarcrownDoll.MINGGULONG.get(), MingGuLongEntity.createAttributes().build());
    }
}
