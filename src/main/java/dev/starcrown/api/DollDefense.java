package dev.starcrown.api;

import dev.starcrown.DivineProtection;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.damagesource.DamageSource;

/** Optional cooperation API; no registration or third-party mod identification is required. */
public final class DollDefense {
 private DollDefense(){}
 public static boolean isProtected(Entity entity){return DivineProtection.active(entity);}
 public static boolean rejectDamage(LivingEntity target,DamageSource source,float amount){return DivineProtection.blockDamage(target,source,amount,"api.damage");}
 public static boolean rejectDeath(LivingEntity target){return isProtected(target)&&DivineProtection.blockDeath(target,"api.death");}
 public static boolean rejectRemoval(Entity target,Entity.RemovalReason reason){return isProtected(target)&&DivineProtection.blockRemoval(target,reason,"api.remove");}
}
