package dev.starcrown;
import java.util.*;
import java.math.BigDecimal;
import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
/** Authoritative life reservoir. UNBOUNDED is a tagged state, never a floating-point health value. */
public final class DivinePool extends SavedData {
 public static final class Entry {
  public boolean active;
  public boolean locked;
  public boolean divinitySlot;
  public CompoundTag crown=new CompoundTag();
  public long prevented;
  public String lastPath="none";
  public BigDecimal finiteAbsorbed=BigDecimal.ZERO;
  public long nonFiniteHits;
 }
 private final Map<UUID,Entry> entries=new HashMap<>();
 public static DivinePool get(MinecraftServer s){return s.overworld().getDataStorage().computeIfAbsent(DivinePool::load,DivinePool::new,"starcrowndoll_divine_pool");}
 public Entry entry(UUID id){return entries.computeIfAbsent(id,k->{setDirty();return new Entry();});}
 public void active(UUID id,boolean active){if(!active&&!entries.containsKey(id))return;Entry e=entry(id);if(e.active!=active){e.active=active;setDirty();}}
 public void prevent(UUID id,String path,float amount){Entry e=entry(id);e.active=true;if(e.prevented<Long.MAX_VALUE)e.prevented++;e.lastPath=path;if(Float.isFinite(amount)&&amount>0)e.finiteAbsorbed=e.finiteAbsorbed.add(new BigDecimal(Float.toString(amount)));else if(!Float.isFinite(amount)&&e.nonFiniteHits<Long.MAX_VALUE)e.nonFiniteHits++;setDirty();}
 public CompoundTag save(CompoundTag tag){ListTag list=new ListTag();entries.forEach((id,e)->{CompoundTag t=new CompoundTag();t.putUUID("uuid",id);t.putString("real_pool_mode","UNBOUNDED");t.putBoolean("active",e.active);t.putBoolean("locked",e.locked);t.putBoolean("divinity_slot",e.divinitySlot);if(e.locked)t.put("locked_crown",e.crown.copy());t.putLong("prevented",e.prevented);t.putString("last_path",e.lastPath);t.putString("finite_absorbed",e.finiteAbsorbed.toString());t.putLong("non_finite_hits",e.nonFiniteHits);list.add(t);});tag.put("players",list);tag.putInt("schema",3);return tag;}
 static DivinePool load(CompoundTag tag){DivinePool pool=new DivinePool();for(Tag raw:tag.getList("players",Tag.TAG_COMPOUND)){CompoundTag t=(CompoundTag)raw;if(!t.hasUUID("uuid"))continue;Entry e=new Entry();e.active=false;e.locked=t.getBoolean("locked");e.divinitySlot=t.getBoolean("divinity_slot");e.crown=t.getCompound("locked_crown").copy();e.prevented=Math.max(0,t.getLong("prevented"));e.lastPath=t.getString("last_path");try{e.finiteAbsorbed=new BigDecimal(t.getString("finite_absorbed"));}catch(NumberFormatException ignored){}e.nonFiniteHits=Math.max(0,t.getLong("non_finite_hits"));pool.entries.put(t.getUUID("uuid"),e);}return pool;}
}
