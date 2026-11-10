package dev.starcrown;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.*;
/** Automatic deletion anchor; normal menu unequip releases it. Not a JVM security boundary. */
@Mod.EventBusSubscriber(modid=StarcrownDoll.ID)
public final class DivineAnchor {
 private static final java.util.Map<ServerPlayer,ItemStack> WATCH=new java.util.WeakHashMap<>();
 private static final java.util.Map<ServerPlayer,Boolean> WATCH_SLOT=new java.util.WeakHashMap<>();
 private static final java.util.Set<ServerPlayer> RELEASE=java.util.Collections.newSetFromMap(new java.util.WeakHashMap<>());
 public static void creativeUnequip(ServerPlayer p,int menuSlot){if(p.isCreative()&&menuSlot==5&&p.getItemBySlot(EquipmentSlot.HEAD).is(StarcrownDoll.ITEM.get())){unlock(p);DivineSeven.release(p);}}
 public static boolean menuAction(){return StackWalker.getInstance().walk(frames->frames.anyMatch(f->f.getClassName().equals("net.minecraft.world.inventory.AbstractContainerMenu")));}
 /** Records a detached copy before a later in-place mutation can destroy the stack. */
 public static void observe(ServerPlayer p){var stack=DivinityInventory.equipped(p);if(stack.is(StarcrownDoll.ITEM.get())&&!RELEASE.contains(p)){var cached=WATCH.get(p);if(cached==null||!ItemStack.isSameItemSameTags(cached,stack))WATCH.put(p,stack.copyWithCount(1));WATCH_SLOT.put(p,DivinityInventory.stack(p).is(StarcrownDoll.ITEM.get()));}}
 private static void automatic(ServerPlayer p,ItemStack snapshot,String path){var pool=DivinePool.get(p.getServer());var entry=pool.entry(p.getUUID());entry.crown=snapshot.copyWithCount(1).save(new net.minecraft.nbt.CompoundTag());entry.locked=true;entry.divinitySlot=WATCH_SLOT.getOrDefault(p,false);pool.setDirty();RELEASE.remove(p);DivineProtection.prevent(p,path,0);}
 /** Also queried by the life guard, closing a raw-delete-then-kill gap before the next tick. */
 public static boolean recoverMissing(ServerPlayer p){if(locked(p))return true;var snapshot=WATCH.get(p);if(snapshot==null||RELEASE.contains(p))return false;var actual=DivinityInventory.equipped(p);if(actual.is(StarcrownDoll.ITEM.get()))return false;automatic(p,snapshot,"anchor.auto.rawDeletion");return true;}
 public static boolean locked(ServerPlayer p){return p.getServer()!=null&&DivinePool.get(p.getServer()).entry(p.getUUID()).locked;}
 public static void lock(ServerPlayer p){var stack=DivinityInventory.equipped(p);if(!stack.is(StarcrownDoll.ITEM.get()))throw new IllegalStateException("请先佩戴成然玩偶");var pool=DivinePool.get(p.getServer());var entry=pool.entry(p.getUUID());entry.crown=stack.copyWithCount(1).save(new net.minecraft.nbt.CompoundTag());entry.locked=true;entry.divinitySlot=DivinityInventory.stack(p).is(StarcrownDoll.ITEM.get());pool.setDirty();}
 public static void unlock(ServerPlayer p){var pool=DivinePool.get(p.getServer());var entry=pool.entry(p.getUUID());entry.locked=false;entry.crown=new net.minecraft.nbt.CompoundTag();pool.setDirty();WATCH.remove(p);WATCH_SLOT.remove(p);RELEASE.add(p);}
 public static ItemStack crown(ServerPlayer p){var saved=ItemStack.of(DivinePool.get(p.getServer()).entry(p.getUUID()).crown);return saved.is(StarcrownDoll.ITEM.get())?saved.copyWithCount(1):new ItemStack(StarcrownDoll.ITEM.get());}
 public static boolean blockSlot(net.minecraft.world.entity.player.Player owner,int slot,ItemStack next){
  if(!(owner instanceof ServerPlayer p)||(slot!=39&&slot!=DivinityInventory.ANCHOR_SLOT))return false;
  boolean god=slot==DivinityInventory.ANCHOR_SLOT;
  if(locked(p)&&DivinePool.get(p.getServer()).entry(p.getUUID()).divinitySlot!=god)return false;
  var actual=god?DivinityInventory.stack(p):p.getItemBySlot(EquipmentSlot.HEAD);
  if(!actual.is(StarcrownDoll.ITEM.get())&&!locked(p)){if(next.is(StarcrownDoll.ITEM.get())){RELEASE.remove(p);WATCH.remove(p);WATCH_SLOT.remove(p);}return false;}
  // The owner's normal container interaction is an explicit unequip request, even after auto-lock.
  if(menuAction()&&!(next.getCount()==1&&ItemStack.isSameItemSameTags(actual,next))){unlock(p);RELEASE.remove(p);return false;}
  if(!locked(p)){
   if(next.is(StarcrownDoll.ITEM.get())&&next.getCount()==1&&ItemStack.isSameItemSameTags(actual,next)){observe(p);return false;}
   if(RELEASE.remove(p)){WATCH.remove(p);return false;}
   if(menuAction()){WATCH.remove(p);return false;}
   if(!actual.is(StarcrownDoll.ITEM.get()))return false;
   automatic(p,actual,"anchor.auto.headDeletion");
  }
  var expected=crown(p);if(next.getCount()==1&&ItemStack.isSameItemSameTags(expected,next))return false;
  DivineProtection.prevent(p,"anchor.headSlot",0);return true;
 }
 private static boolean movingSection(){return StackWalker.getInstance().walk(frames->frames.anyMatch(f->f.getClassName().equals("net.minecraft.world.level.entity.PersistentEntitySectionManager$Callback")&&(f.getMethodName().equals("onMove")||f.getMethodName().equals("m_142044_"))));}
 public static boolean blockContainer(Object value,String path){
  if(!(value instanceof ServerPlayer p)||p.isRemoved()||p.getServer()==null||p.getServer().isStopped()||movingSection()||!DivineProtection.active(p))return false;
  DivineProtection.prevent(p,path,0);return true;
 }
 public static void repair(ServerPlayer p){recoverMissing(p);if(!locked(p)){observe(p);return;}var expected=crown(p);boolean god=DivinePool.get(p.getServer()).entry(p.getUUID()).divinitySlot;var actual=god?DivinityInventory.stack(p):p.getItemBySlot(EquipmentSlot.HEAD);if(actual.getCount()==1&&ItemStack.isSameItemSameTags(actual,expected))return;
  // A raw list write bypassed slot setters. Keep the displaced stack if one exists.
  if(!actual.isEmpty()){var displaced=actual.copy();if(!p.getInventory().add(displaced))p.drop(displaced,false);}
  if(god)DivinityInventory.force(p,expected);else p.getInventory().armor.set(3,expected);p.getInventory().setChanged();p.inventoryMenu.broadcastChanges();DivineProtection.prevent(p,"anchor.rawHeadRepair",0);
 }
 @SubscribeEvent(priority=EventPriority.HIGHEST) public static void tick(TickEvent.PlayerTickEvent e){if(e.player instanceof ServerPlayer p){if(e.phase==TickEvent.Phase.START)repair(p);else{RELEASE.remove(p);observe(p);}}}
 @SubscribeEvent public static void commands(RegisterCommandsEvent e){e.getDispatcher().register(Commands.literal("yishen")
  .then(Commands.literal("lock").executes(c->{var p=c.getSource().getPlayerOrException();if(!DivinityInventory.equipped(p).is(StarcrownDoll.ITEM.get())){c.getSource().sendFailure(Component.literal("请先佩戴成然玩偶"));return 0;}lock(p);c.getSource().sendSuccess(()->Component.literal("防护锁已开启；使用 /yishen unlock 解锁后取下玩偶。"),false);return 1;}))
  .then(Commands.literal("unlock").executes(c->{unlock(c.getSource().getPlayerOrException());c.getSource().sendSuccess(()->Component.literal("防护锁已解除，可以正常取下玩偶。"),false);return 1;})));
 }
}
