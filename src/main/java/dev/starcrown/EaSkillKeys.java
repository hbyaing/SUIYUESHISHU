package dev.starcrown;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

@Mod.EventBusSubscriber(modid = StarcrownDoll.ID, value = Dist.CLIENT)
public final class EaSkillKeys {
    public static final KeyMapping CYCLE = new KeyMapping("key.starcrowndoll.ea_cycle_skill", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_LEFT_BRACKET, "key.categories.starcrowndoll");
    private static boolean wasDown;

    private EaSkillKeys() {}

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        boolean previousDown = wasDown;
        wasDown = CYCLE.isDown();
        Minecraft minecraft = Minecraft.getInstance();
        while (CYCLE.consumeClick()) {
            if (previousDown || minecraft.player == null || minecraft.screen != null) continue;
            InteractionHand hand = EaSkillNetwork.availableHand(minecraft.player);
            if (hand != null) EaSkillNetwork.request(hand);
            previousDown = true;
        }
    }

    @Mod.EventBusSubscriber(modid = StarcrownDoll.ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class Setup {
        @SubscribeEvent
        public static void register(RegisterKeyMappingsEvent event) {
            event.register(CYCLE);
        }
    }
}
