package dev.starcrown;

import java.util.HashMap;
import java.util.Map;
import com.google.gson.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.*;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.*;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

@Mod.EventBusSubscriber(modid=StarcrownDoll.ID)
public final class KindlingValues extends SimpleJsonResourceReloadListener {
    private static volatile Map<ResourceLocation,Integer> values=Map.of();
    private static volatile int ordinary=10, accessory=10_000;
    public KindlingValues() { super(new Gson(), "kindling_values"); }
    @SubscribeEvent public static void reload(AddReloadListenerEvent event) { event.addListener(new KindlingValues()); }
    @Override protected void apply(Map<ResourceLocation,JsonElement> files, ResourceManager manager, ProfilerFiller profiler) {
        Map<ResourceLocation,Integer> next=new HashMap<>();
        int base=10, curio=10_000;
        for (var entry : files.entrySet().stream().sorted(Map.Entry.comparingByKey()).toList()) {
            var root=entry.getValue().getAsJsonObject();
            if(root.has("ordinary")) base=positive(root.get("ordinary"));
            if(root.has("accessory")) curio=positive(root.get("accessory"));
            if(root.has("items")) for(var e:root.getAsJsonObject("items").entrySet())
                next.put(new ResourceLocation(e.getKey()),positive(e.getValue()));
        }
        values=Map.copyOf(next);ordinary=base;accessory=curio;
    }
    private static int positive(JsonElement e) { return Math.max(1,Math.min(EternalReturn.CAP,e.getAsInt())); }
    public static int value(ItemStack stack) {
        Integer explicit=values.get(ForgeRegistries.ITEMS.getKey(stack.getItem()));
        if(explicit!=null)return explicit;
        if(stack.getItem() instanceof top.theillusivec4.curios.api.type.capability.ICurioItem
                || stack.getTags().anyMatch(t->t.location().getNamespace().equals("curios"))) return accessory;
        if(stack.getItem() instanceof TieredItem || stack.getItem() instanceof ArmorItem) return 200;
        return ordinary;
    }
}
