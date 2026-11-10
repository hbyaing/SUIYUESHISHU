package dev.starcrown;

import java.lang.reflect.Field;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;

/** Resolve once without assuming access transformers leave the field private. */
public final class VitalDataBinding {
 private static final Field ITEMS = resolve();

 private static Field resolve() {
  for (String name : new String[]{"itemsById", "f_135345_"}) {
   try {
    Field field = SynchedEntityData.class.getDeclaredField(name);
    field.setAccessible(true);
    return field;
   } catch (NoSuchFieldException ignored) {}
  }
  throw new IllegalStateException("Missing Minecraft 1.20.1 synchronized data storage");
 }

 public static void bind(SynchedEntityData data, EntityDataAccessor<?> key, Entity owner) {
  try {
   var entries = (Int2ObjectMap<?>) ITEMS.get(data);
   ((OwnedVitalData) entries.get(key.getId())).starcrown$bindVitalOwner(owner);
  } catch (IllegalAccessException error) {
   throw new IllegalStateException("Cannot bind synchronized health owner", error);
  }
 }

 private VitalDataBinding() {}
}
