package dev.starcrown.treasury;

import com.mojang.logging.LogUtils;
import dev.starcrown.StarcrownDoll;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.DiggerItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.item.UseAnim;
import net.minecraftforge.common.Tags;
import net.minecraftforge.common.ToolActions;
import net.minecraftforge.event.TagsUpdatedEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

@Mod.EventBusSubscriber(modid = StarcrownDoll.ID)
public final class TreasuryWeaponPool {
    private static final TagKey<Item> INCLUDED = ItemTags.create(new ResourceLocation(StarcrownDoll.ID, "treasury_weapons"));
    private static final TagKey<Item> EXCLUDED = ItemTags.create(new ResourceLocation(StarcrownDoll.ID, "treasury_weapon_exclusions"));
    private static final Set<String> WEAPON_NAMES = Set.of(
            "sword", "greatsword", "longsword", "shortsword", "claymore", "rapier", "saber", "sabre",
            "katana", "dagger", "knife", "spear", "javelin", "lance", "pike", "halberd", "glaive", "warhammer",
            "mace", "battleaxe", "waraxe", "trident", "bow", "longbow", "shortbow", "crossbow",
            "musket", "rifle", "pistol", "revolver", "shotgun", "railgun", "blaster", "gun", "flintlock", "carbine");
    private static List<ItemStack> cached;
    private static final Map<ResourceLocation, List<ItemStack>> creativeVariants = new TreeMap<>();
    private static List<ItemStack> dataVariants = List.of();

    private TreasuryWeaponPool() {}

    @SubscribeEvent
    public static synchronized void tagsUpdated(TagsUpdatedEvent event) {
        if (event.shouldUpdateStaticData()) cached = null;
    }

    @SubscribeEvent
    public static synchronized void serverStopped(ServerStoppedEvent event) {
        replaceDataVariants(List.of());
    }

    static synchronized void replaceDataVariants(List<ItemStack> stacks) {
        dataVariants = List.copyOf(stacks);
        creativeVariants.clear();
        cached = null;
    }

    static synchronized void replaceCreativeTab(ResourceLocation tab, List<ItemStack> stacks) {
        if (stacks.isEmpty()) {
            if (creativeVariants.remove(tab) == null) return;
        } else creativeVariants.put(tab, List.copyOf(stacks));
        cached = null;
    }

    /** Every shuffled round exhausts the models, including distinct NBT variants of the same item. */
    public static List<ItemStack> choose(long seed, int count) {
        if (count < 0) throw new IllegalArgumentException("Negative weapon count: " + count);
        if (count == 0) return List.of();
        var catalog = weapons();
        if (catalog.isEmpty()) return Collections.nCopies(count, ItemStack.EMPTY);
        var random = new Random(seed);
        if (count == 1) return List.of(catalog.get(random.nextInt(catalog.size())).copyWithCount(1));
        var result = new ArrayList<ItemStack>(count);
        if (count <= catalog.size() / 2) {
            // A continuous cast often replaces just a few weapons. Shuffle only the slots it draws.
            var moved = new HashMap<Integer, Integer>();
            for (int i = 0; i < count; i++) {
                int last = catalog.size() - i - 1;
                int chosen = random.nextInt(last + 1);
                int model = moved.getOrDefault(chosen, chosen);
                moved.put(chosen, moved.getOrDefault(last, last));
                result.add(catalog.get(model).copyWithCount(1));
            }
            return List.copyOf(result);
        }
        var deck = new ArrayList<>(catalog);
        for (int i = 0; i < count; i++) {
            int slot = i % deck.size();
            if (slot == 0) Collections.shuffle(deck, random);
            result.add(deck.get(slot).copyWithCount(1));
        }
        return List.copyOf(result);
    }

    private static synchronized List<ItemStack> weapons() {
        if (cached != null) return cached;
        // The complete count-one stack key includes model NBT and Forge capabilities. Minecraft's
        // SNBT writer sorts compound keys, so duplicates collapse regardless of discovery order.
        var weapons = new TreeMap<String, ItemStack>();
        for (var entry : ForgeRegistries.ITEMS.getEntries()) {
            ResourceLocation id = entry.getKey().location();
            try {
                ItemStack stack = entry.getValue().getDefaultInstance();
                if (isWeapon(stack, id)) add(weapons, stack);
            } catch (RuntimeException ex) {
                // A foreign item can require its own context even just to query its default stack.
                LogUtils.getLogger().warn("Skipping {} in the treasury weapon pool", id, ex);
            }
        }
        for (var tab : creativeVariants.values()) {
            for (ItemStack stack : tab) {
                if (allowed(stack)) add(weapons, stack);
            }
        }
        for (ItemStack stack : dataVariants) {
            if (allowed(stack)) add(weapons, stack);
        }
        cached = List.copyOf(weapons.values());
        return cached;
    }

    private static void add(Map<String, ItemStack> models, ItemStack source) {
        ItemStack single = source.copyWithCount(1);
        models.putIfAbsent(single.serializeNBT().getAsString(), single);
    }

    private static boolean allowed(ItemStack stack) {
        if (stack.isEmpty() || stack.is(EXCLUDED) || stack.getItem() instanceof BlockItem) return false;
        Item item = stack.getItem();
        var tag = stack.getTag();
        return !(item instanceof ShieldItem || item instanceof ArmorItem || stack.is(Tags.Items.TOOLS_SHIELDS)
                || stack.is(Tags.Items.ARMORS) || tag != null && (tag.contains("BlockEntityTag") || tag.contains("EntityTag")));
    }

    static boolean isWeapon(ItemStack stack, ResourceLocation id) {
        if (!allowed(stack)) return false;
        Item item = stack.getItem();
        if (stack.is(INCLUDED) || item instanceof SwordItem || item instanceof AxeItem
                || item instanceof TridentItem || item instanceof ProjectileWeaponItem
                || stack.is(ItemTags.SWORDS) || stack.is(ItemTags.AXES)) return true;
        if (stack.canPerformAction(ToolActions.SWORD_SWEEP)) return true;
        if (item instanceof DiggerItem || stack.is(ItemTags.PICKAXES) || stack.is(ItemTags.SHOVELS)
                || stack.is(ItemTags.HOES) || stack.is(Tags.Items.TOOLS_FISHING_RODS)) return false;
        UseAnim pose = stack.getUseAnimation();
        if (pose == UseAnim.SPEAR || pose == UseAnim.BOW || pose == UseAnim.CROSSBOW) return true;
        // Some mods implement ranged weapons as plain Item with no attack attribute or use pose.
        String path = id.getPath();
        String lastWord = path.substring(Math.max(path.lastIndexOf('_'), path.lastIndexOf('/')) + 1);
        if (WEAPON_NAMES.contains(lastWord)) return true;
        return stack.getAttributeModifiers(EquipmentSlot.MAINHAND).get(Attributes.ATTACK_DAMAGE)
                .stream().anyMatch(modifier -> modifier.getAmount() > 0);
    }
}
