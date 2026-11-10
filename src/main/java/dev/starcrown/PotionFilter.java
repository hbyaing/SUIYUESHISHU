package dev.starcrown;
import java.util.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.*;
import net.minecraftforge.registries.ForgeRegistries;
/** Per-player settings live in DivineSeven's persistent, clone-safe settings compound. */
public final class PotionFilter {
 public static final int MIN_LEVEL=1, MAX_LEVEL=255;
 private static final String ALL_OFF="potions_off", ENABLED="potion_enabled", DISABLED="potion_disabled";

 public static boolean filterable(MobEffect effect){return effect.getCategory()==MobEffectCategory.BENEFICIAL;}

 public static boolean enabled(ServerPlayer p,MobEffect effect){
  if(!filterable(effect))return true;
  var id=ForgeRegistries.MOB_EFFECTS.getKey(effect);var settings=DivineSeven.settings(p);
  if(id==null||effect==MobEffects.NIGHT_VISION&&settings.getBoolean("night_off"))return false;
  return settings.getBoolean(ALL_OFF)?settings.getCompound(ENABLED).getBoolean(id.toString())
     :!settings.getCompound(DISABLED).getBoolean(id.toString());
 }
 public static Set<String> disabled(ServerPlayer p){
  var result=new HashSet<String>();
  for(var effect:ForgeRegistries.MOB_EFFECTS)if(filterable(effect)&&!enabled(p,effect))result.add(ForgeRegistries.MOB_EFFECTS.getKey(effect).toString());
  return result;
 }
 public static void set(ServerPlayer p,ResourceLocation id,boolean enable){
  var effect=ForgeRegistries.MOB_EFFECTS.getValue(id);if(effect==null||!filterable(effect))return;
  var settings=DivineSeven.settings(p);
  boolean allOff=settings.getBoolean(ALL_OFF);
  String key=allOff?ENABLED:DISABLED;
  var overrides=settings.getCompound(key);
  if(allOff?enable:!enable)overrides.putBoolean(id.toString(),true);else overrides.remove(id.toString());
  settings.put(key,overrides);
  if(effect==MobEffects.NIGHT_VISION)settings.putBoolean("night_off",!enable);
  DivineWill.update(p,true,true);
 }
 public static int level(ServerPlayer player){
  var settings=DivineSeven.settings(player);
  return settings.contains("potion_level")?net.minecraft.util.Mth.clamp(settings.getInt("potion_level"),MIN_LEVEL,MAX_LEVEL):MAX_LEVEL;
 }
 static void setLevel(ServerPlayer player,int level){
  DivineSeven.settings(player).putInt("potion_level",level);
  DivineWill.update(player,true,true);
 }
 static void disableAll(ServerPlayer p){
  var settings=DivineSeven.settings(p);
  settings.putBoolean(ALL_OFF,true);
  settings.remove(ENABLED);
  settings.putBoolean("night_off",true);
  DivineWill.update(p,true,true);
 }
 private PotionFilter(){}
}
