package dev.starcrown.treasury;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.mojang.logging.LogUtils;
import dev.starcrown.StarcrownDoll;
import java.util.ArrayList;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.crafting.CraftingHelper;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.server.ServerLifecycleHooks;

/** Server-owned model declarations: data/<namespace>/treasury_weapon_variants/<name>.json. */
@Mod.EventBusSubscriber(modid = StarcrownDoll.ID)
public final class TreasuryWeaponVariants extends SimpleJsonResourceReloadListener {
    private TreasuryWeaponVariants() {
        super(new Gson(), "treasury_weapon_variants");
    }

    @SubscribeEvent
    public static void reload(AddReloadListenerEvent event) {
        event.addListener(new TreasuryWeaponVariants());
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager manager, ProfilerFiller profiler) {
        var models = new ArrayList<ItemStack>();
        for (var file : files.entrySet()) {
            try {
                var entries = file.getValue().getAsJsonObject().getAsJsonArray("weapons");
                if (entries == null) throw new IllegalArgumentException("Missing weapons array");
                for (JsonElement entry : entries) {
                    try {
                        models.add(CraftingHelper.getItemStack(entry.getAsJsonObject(), true, true).copyWithCount(1));
                    } catch (RuntimeException ex) {
                        LogUtils.getLogger().warn("Skipping invalid treasury weapon in {}", file.getKey(), ex);
                    }
                }
            } catch (RuntimeException ex) {
                LogUtils.getLogger().warn("Cannot read treasury weapon declarations in {}", file.getKey(), ex);
            }
        }
        TreasuryWeaponPool.replaceDataVariants(models);
    }

    @Mod.EventBusSubscriber(modid = StarcrownDoll.ID, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class Creative {
        @SubscribeEvent(priority = EventPriority.LOWEST)
        public static void tab(BuildCreativeModeTabContentsEvent event) {
            // Forge builds these tabs on the logical client. In singleplayer we can observe its
            // local published entries directly; dedicated servers use their own data packs.
            if (ServerLifecycleHooks.getCurrentServer() == null || event.getTab().getType() != CreativeModeTab.Type.CATEGORY) return;
            var models = new ArrayList<ItemStack>();
            for (var entry : event.getEntries()) {
                ItemStack stack = entry.getKey();
                var id = ForgeRegistries.ITEMS.getKey(stack.getItem());
                try {
                    if (id != null && TreasuryWeaponPool.isWeapon(stack, id)) models.add(stack.copyWithCount(1));
                } catch (RuntimeException ex) {
                    LogUtils.getLogger().warn("Skipping creative treasury weapon {} in {}", id, event.getTabKey(), ex);
                }
            }
            TreasuryWeaponPool.replaceCreativeTab(event.getTabKey().location(), models);
        }
    }
}
