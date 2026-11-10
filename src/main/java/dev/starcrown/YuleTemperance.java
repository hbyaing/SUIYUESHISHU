package dev.starcrown;
import java.util.*;
import java.lang.ref.WeakReference;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.*;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
@Mod.EventBusSubscriber(modid=StarcrownDoll.ID)
public final class YuleTemperance {
 private static final Map<Player,Boolean> PLAYERS=Collections.synchronizedMap(new WeakHashMap<>());
 private static final Map<FoodData,WeakReference<Player>> FOOD=Collections.synchronizedMap(new WeakHashMap<>());
 private record Owned(WeakReference<Player> player,int slot){}
 private static final Map<ItemStack,Owned> OWNERS=Collections.synchronizedMap(new WeakHashMap<>());
 public static boolean active(Entity e){return YuleHumility.active(e);}
 public static void bind(Player p){PLAYERS.put(p,true);FOOD.put(p.getFoodData(),new WeakReference<>(p));}
 public static boolean locked(FoodData f){var ref=FOOD.get(f);return ref!=null&&active(ref.get());}
 public static Player owner(ItemStack s){
  if(s==ItemStack.EMPTY)return null;
  var known=OWNERS.get(s);if(known!=null){var p=known.player.get();if(p!=null&&active(p)&&(known.slot<0?p.containerMenu.getCarried()==s:p.getInventory().getItem(known.slot)==s))return p;OWNERS.remove(s);}
  synchronized(PLAYERS){for(var p:PLAYERS.keySet())if(active(p)){
   for(int i=0;i<p.getInventory().getContainerSize();i++)if(p.getInventory().getItem(i)==s){OWNERS.put(s,new Owned(new WeakReference<>(p),i));return p;}
   if(p.containerMenu.getCarried()==s){OWNERS.put(s,new Owned(new WeakReference<>(p),-1));return p;}
  }}return null;
 }
 public static boolean equipped(ItemStack s,LivingEntity e){if(!active(e))return false;for(var slot:EquipmentSlot.values())if(e.getItemBySlot(slot)==s)return true;return false;}
 public static boolean durable(ItemStack s){var p=owner(s);return p!=null&&equipped(s,p);}
 public static float exhaustion(FoodData food,float amount){
  var ref=FOOD.get(food);
  return ref!=null&&CalmYule.active(ref.get())?amount*.25f:amount;
 }
 public static void fill(Player p){bind(p);if(active(p)){var f=p.getFoodData();f.setFoodLevel(20);f.setSaturation(20);f.setExhaustion(0);}}
 @SubscribeEvent public static void equipment(net.minecraftforge.event.entity.living.LivingEquipmentChangeEvent e){if(e.getEntity() instanceof Player p)bind(p);}
 public static void enforce(Player p){
  fill(p);
  if(active(p))for(var slot:EquipmentSlot.values()){
   var stack=p.getItemBySlot(slot);
   if(stack.isDamageableItem()&&stack.getDamageValue()>0)stack.setDamageValue(0);
  }
 }
 @SubscribeEvent public static void tick(TickEvent.PlayerTickEvent e){if(e.phase==TickEvent.Phase.END)enforce(e.player);}
}
