package dev.starcrown;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.syncher.EntityDataAccessor;
/** Common vanilla attack and synchronized-health boundary. Not a sandbox against arbitrary code. */
public final class DivineCombat {
 public static boolean protectedTarget(Object target){return target instanceof Entity e&&(e.level().isClientSide?e instanceof Player p&&p.getInventory()!=null&&DivinityInventory.equipped(p).is(StarcrownDoll.ITEM.get()):DivineProtection.active(e));}
 public static boolean reject(Object target,String path){if(!protectedTarget(target))return false;if(target instanceof Entity e)DivineProtection.prevent(e,path,0);return true;}
 private static class HealthKey {
  static final Object KEY=find();static Object find(){for(String name:new String[]{"DATA_HEALTH_ID","f_20961_"})try{var f=LivingEntity.class.getDeclaredField(name);f.setAccessible(true);return f.get(null);}catch(ReflectiveOperationException ignored){}throw new IllegalStateException("Minecraft 1.20.1 health accessor not found");}
 }
 public static boolean isHealthKey(EntityDataAccessor<?> key){return key==HealthKey.KEY;}
 public static boolean blockData(Entity owner,EntityDataAccessor<?> key,Object value){return owner instanceof LivingEntity e&&value instanceof Float f&&key==HealthKey.KEY&&DivineProtection.active(e)&&DivineProtection.blockHealth(e,f);}
 public static Object humilityData(Entity owner,EntityDataAccessor<?> key,Object value){return owner instanceof LivingEntity e&&value instanceof Float f&&key==HealthKey.KEY?YuleHumility.healthWrite(e,f):value;}
 public static float maximum(LivingEntity e){double n=e.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH);return Double.isFinite(n)&&n>=20?(float)Math.min(n,1024):20;}
}
