package dev.starcrown;
import java.util.*;
import net.minecraft.nbt.*;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.server.MinecraftServer;
public final class GenerosityRewards extends SavedData {
 private final Set<UUID> players=new HashSet<>();
 private final Map<UUID,Long> calm=new HashMap<>();
 public static GenerosityRewards get(MinecraftServer server){return server.overworld().getDataStorage().computeIfAbsent(GenerosityRewards::load,GenerosityRewards::new,"starcrowndoll_generosity");}
 public static GenerosityRewards load(CompoundTag tag){var data=new GenerosityRewards();for(var t:tag.getList("players",Tag.TAG_COMPOUND)){var e=(CompoundTag)t;if(e.hasUUID("id"))data.players.add(e.getUUID("id"));}for(var t:tag.getList("calm",Tag.TAG_COMPOUND)){var e=(CompoundTag)t;if(e.hasUUID("id"))data.calm.put(e.getUUID("id"),e.getLong("until"));}return data;}
 public boolean has(UUID id){return players.contains(id);}
 public void grant(UUID id){if(players.add(id))setDirty();}
 public void grantCalm(UUID id,long until){calm.values().removeIf(expiry->expiry<=until-1200);calm.put(id,until);setDirty();}
 public long claimCalm(UUID id){var until=calm.remove(id);if(until==null)return 0;setDirty();return until;}
 @Override public CompoundTag save(CompoundTag tag){var list=new ListTag();for(var id:players){var t=new CompoundTag();t.putUUID("id",id);list.add(t);}tag.put("players",list);var pending=new ListTag();calm.forEach((id,until)->{var t=new CompoundTag();t.putUUID("id",id);t.putLong("until",until);pending.add(t);});tag.put("calm",pending);return tag;}
}
