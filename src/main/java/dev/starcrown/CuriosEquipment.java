package dev.starcrown;

import java.lang.reflect.Method;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Predicate;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.items.IItemHandlerModifiable;

/** Optional API boundary; none of Curios' classes are linked when it is absent. */
public final class CuriosEquipment {
    public static final String GOD = "starcrowndoll_divinity", BOOK = "starcrowndoll_as_written";
    private static Method inventory, handler, stacks, renders;
    public static boolean loaded() { return ModList.get().isLoaded("curios"); }
    @SuppressWarnings({"unchecked", "rawtypes"}) public static void init() {
        if (!loaded() || inventory != null) return;
        try {
            var api = Class.forName("top.theillusivec4.curios.api.CuriosApi");
            inventory = api.getMethod("getCuriosInventory", LivingEntity.class);
            handler = Class.forName("top.theillusivec4.curios.api.type.capability.ICuriosItemHandler").getMethod("getStacksHandler", String.class);
            stacks = Class.forName("top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler").getMethod("getStacks");
            renders = Class.forName("top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler").getMethod("getRenders");
            var result = Class.forName("top.theillusivec4.curios.api.SlotResult");
            var context = Class.forName("top.theillusivec4.curios.api.SlotContext");
            var resultContext = result.getMethod("slotContext"); var resultStack = result.getMethod("stack"); var entity = context.getMethod("entity");
            Predicate<Object> validator = value -> {
                try { return entity.invoke(resultContext.invoke(value)) instanceof Player player
                        && DivinityInventory.canEquipDivinity(player, (ItemStack) resultStack.invoke(value)); }
                catch (ReflectiveOperationException error) { throw new IllegalStateException(error); }
            };
            api.getMethod("registerCurioPredicate", ResourceLocation.class, Predicate.class).invoke(null, new ResourceLocation(StarcrownDoll.ID, "divinity"), validator);
            var unequip = Class.forName("top.theillusivec4.curios.api.event.CurioUnequipEvent");
            var eventContext = unequip.getMethod("getSlotContext"); var identifier = context.getMethod("identifier");
            Consumer<Event> listener = event -> {
                try {
                    var value = eventContext.invoke(event);
                    if (GOD.equals(identifier.invoke(value)) && entity.invoke(value) instanceof ServerPlayer player
                            && DivinityInventory.stack(player).is(StarcrownDoll.ITEM.get()) && DivineAnchor.menuAction()) DivineAnchor.unlock(player);
                } catch (ReflectiveOperationException error) { throw new IllegalStateException(error); }
            };
            MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, false, (Class) unequip, (Consumer) listener);
        } catch (ReflectiveOperationException error) { throw new IllegalStateException("Unsupported Curios API", error); }
    }
    public static IItemHandlerModifiable slots(Player player, String name) {
        if (!loaded()) return null;
        init();
        try {
            var value = ((LazyOptional<?>) inventory.invoke(null, player)).resolve();
            if (value.isEmpty()) return null;
            var target = (Optional<?>) handler.invoke(value.get(), name);
            return target.isEmpty() ? null : (IItemHandlerModifiable) stacks.invoke(target.get());
        } catch (ReflectiveOperationException error) { throw new IllegalStateException(error); }
    }
    public static ItemStack stack(Player player) { var target = slots(player, GOD); return target == null ? ItemStack.EMPTY : target.getStackInSlot(0); }
    public static boolean visible(Player player) {
        if (!loaded()) return true;
        init();
        try {
            var value=((LazyOptional<?>)inventory.invoke(null,player)).resolve(); if(value.isEmpty())return true;
            var target=(Optional<?>)handler.invoke(value.get(),GOD);if(target.isEmpty())return true;
            var flags=(java.util.List<?>)renders.invoke(target.get());return flags.isEmpty()||!Boolean.FALSE.equals(flags.get(0));
        } catch(ReflectiveOperationException error){throw new IllegalStateException(error);}
    }
    public static void force(Player player, ItemStack value) { var target = slots(player, GOD); if (target == null) throw new IllegalStateException("Curios divinity slot is not ready"); target.setStackInSlot(0, value); }
    public static void migrate(ServerPlayer player) {
        var god = slots(player, GOD); var books = slots(player, BOOK);
        if (god == null || books == null || god.getSlots() < 1 || books.getSlots() < 2) return;
        var ownGod = DivinityInventory.own(player);
        if (ownGod.curiosBackup) return;
        var ownBooks = AsWrittenInventory.get(player);
        if (ownGod.usedCurios) {
            // The dormant Curios save predates the most recent standalone session.
            // Replace that snapshot rather than returning its stale items as extras.
            god.setStackInSlot(0,ownGod.getStackInSlot(0).copy());
            for(int i=0;i<2;i++)books.setStackInSlot(i,ownBooks.getStackInSlot(i).copy());
        } else {
            transfer(player, ownGod.getStackInSlot(0), god, 0);
            for (int i = 0; i < 2; i++) transfer(player, ownBooks.getStackInSlot(i), books, i);
        }
        ownGod.usedCurios = true;
        ownGod.curiosBackup = true;
        backup(player);
    }
    private static void transfer(ServerPlayer player, ItemStack value, IItemHandlerModifiable target, int slot) {
        if (value.isEmpty()) return;
        if (target.getStackInSlot(slot).isEmpty()) target.setStackInSlot(slot, value.copy());
        else { var extra = value.copy(); if (!player.getInventory().add(extra)) player.drop(extra, false); }
    }
    /** A dormant save snapshot lets a later launch without Curios recover the same gear. */
    public static void backup(ServerPlayer player) {
        var god = slots(player, GOD); var books = slots(player, BOOK);
        if (god == null || books == null) return;
        var ownGod = DivinityInventory.own(player); ownGod.curiosBackup = true; ownGod.usedCurios = true;
        if (!ItemStack.matches(ownGod.getStackInSlot(0), god.getStackInSlot(0))) ownGod.rawSet(god.getStackInSlot(0).copy());
        var ownBooks = AsWrittenInventory.get(player);
        for (int i = 0; i < 2; i++) if (!ItemStack.matches(ownBooks.getStackInSlot(i), books.getStackInSlot(i))) ownBooks.setStackInSlot(i, books.getStackInSlot(i).copy());
    }
}
