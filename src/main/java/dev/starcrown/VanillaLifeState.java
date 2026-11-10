package dev.starcrown;

import net.minecraft.world.entity.LivingEntity;
/** A single cached vanilla field bridge, compatible with development and reobfuscated names. */
final class VanillaLifeState {
 private static final java.lang.reflect.Field DEAD=field();
 private static java.lang.reflect.Field field(){
  for(String name:new String[]{"dead","f_20890_"})try{var field=LivingEntity.class.getDeclaredField(name);field.setAccessible(true);return field;}catch(NoSuchFieldException ignored){}
  throw new IllegalStateException("Minecraft 1.20.1 death-state field unavailable");
 }
 static void repair(LivingEntity entity){try{if(DEAD.getBoolean(entity))DEAD.setBoolean(entity,false);}catch(IllegalAccessException error){throw new IllegalStateException(error);}}
}
