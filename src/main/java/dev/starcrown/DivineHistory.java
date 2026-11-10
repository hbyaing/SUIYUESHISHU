package dev.starcrown;
import java.util.*;
import net.minecraft.nbt.*;
import net.minecraft.core.*;
import net.minecraft.server.level.*;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.tags.BlockTags;
import net.minecraftforge.event.level.*;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.fml.common.Mod;
/** Per-dimension journal, never stores container contents or replays player placements. */
@Mod.EventBusSubscriber(modid=StarcrownDoll.ID)
public final class DivineHistory extends SavedData {
 private record Snapshot(UUID owner,BlockState before,long time){}
 private final Map<Long,Snapshot> blocks=new LinkedHashMap<>();
 private final Set<Long> placed=new HashSet<>();private final Set<UUID> victories=new HashSet<>();
 private static final ThreadLocal<Boolean> MINING=ThreadLocal.withInitial(()->false);
 public static DivineHistory get(ServerLevel level){return level.getDataStorage().computeIfAbsent(DivineHistory::load,DivineHistory::new,"starcrown_chronicle");}
 public void defeat(UUID id){if(victories.size()<65536&&victories.add(id))setDirty();}
 public boolean defeated(UUID id){return victories.contains(id);}
 private static boolean natural(BlockState s){return s.is(BlockTags.BASE_STONE_OVERWORLD)||s.is(BlockTags.BASE_STONE_NETHER)||s.is(BlockTags.DIRT)||s.is(BlockTags.SAND)||s.is(BlockTags.LOGS)||s.is(BlockTags.LEAVES)||s.is(BlockTags.FLOWERS)||s.is(Blocks.GRASS)||s.is(Blocks.TALL_GRASS)||s.is(Blocks.SNOW)||s.is(Blocks.SNOW_BLOCK)||s.is(Blocks.GRAVEL)||s.is(Blocks.WATER)||s.is(Blocks.LAVA);}
 private static final net.minecraft.tags.TagKey<Block> CALM_NATURAL=net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.BLOCK,new net.minecraft.resources.ResourceLocation(StarcrownDoll.ID,"calm_natural_blocks"));
 @SubscribeEvent(priority=EventPriority.LOWEST) public static void placed(BlockEvent.EntityPlaceEvent event){
  if(!(event.getEntity() instanceof ServerPlayer)||!(event.getLevel() instanceof ServerLevel level))return;
  var journal=get(level);
  if(event instanceof BlockEvent.EntityMultiPlaceEvent multi){
   for(var snapshot:multi.getReplacedBlockSnapshots())if(calmNatural(level.getBlockState(snapshot.getPos()))&&journal.placed.add(snapshot.getPos().asLong()))journal.setDirty();
  }else if(calmNatural(event.getPlacedBlock())&&journal.placed.add(event.getPos().asLong()))journal.setDirty();
 }
 public static boolean removedNatural(ServerLevel level,BlockPos pos,BlockState before){
  var journal=get(level);
  if(journal.placed.remove(pos.asLong())){journal.setDirty();return false;}
  return calmNatural(before);
 }
 private static boolean calmNatural(BlockState state){return state.is(CALM_NATURAL)||state.getFluidState().isEmpty()&&natural(state);}
 public void capture(ServerPlayer p,BlockPos pos){var level=p.serverLevel();var state=level.getBlockState(pos);if(p.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(pos))>4096||placed.contains(pos.asLong())||level.getBlockEntity(pos)!=null||!natural(state))return;
  if(blocks.size()>=16384){var it=blocks.keySet().iterator();it.next();it.remove();}blocks.putIfAbsent(pos.asLong(),new Snapshot(p.getUUID(),state,level.getGameTime()));setDirty();
 }
 @SubscribeEvent(priority=EventPriority.LOWEST) public static void breakBlock(BlockEvent.BreakEvent e){if(!(e.getLevel() instanceof ServerLevel level)||MINING.get()||!(e.getPlayer() instanceof ServerPlayer p)||!DivineWill.wearing(p)||!DivineSeven.settings(p).getBoolean("area_mine"))return;
  int radius=DivineSeven.miningRadius(p);MINING.set(true);try{for(int x=-radius;x<=radius;x++)for(int y=-radius;y<=radius;y++)for(int z=-radius;z<=radius;z++){var pos=e.getPos().offset(x,y,z);if(!level.hasChunkAt(pos)||!level.getWorldBorder().isWithinBounds(pos)||level.isOutsideBuildHeight(pos))continue;var state=level.getBlockState(pos);if(pos.equals(e.getPos())||state.isAir()||state.getDestroySpeed(level,pos)<0||state.getDestroySpeed(level,pos)>50||level.getBlockEntity(pos)!=null)continue;p.gameMode.destroyBlock(pos);}}finally{MINING.set(false);}
 }
 public static void twilightMine(ServerPlayer player,BlockPos center){
  if(MINING.get()||!TwilightCrown.wearing(player))return;
  var settings=TwilightCrown.settings(player);
  if(!settings.getBoolean("area_mine"))return;
  var level=player.serverLevel();
  var tool=player.getMainHandItem();
  if(tool.isEmpty()&&!player.isCreative())return;
  int radius=settings.getInt("mining_range")==1?2:1;
  MINING.set(true);
  try{
   for(var pos:BlockPos.betweenClosed(center.offset(-radius,-radius,-radius),center.offset(radius,radius,radius))){
    if(!player.isCreative()&&(tool.isEmpty()||player.getMainHandItem()!=tool))break;
    if(pos.equals(center)||level.isOutsideBuildHeight(pos)||!level.hasChunkAt(pos)||!level.getWorldBorder().isWithinBounds(pos)
       ||!level.mayInteract(player,pos))continue;
    var state=level.getBlockState(pos);
    if(state.isAir()||state.getDestroySpeed(level,pos)<0||!player.isCreative()&&!player.hasCorrectToolForDrops(state))continue;
    player.gameMode.destroyBlock(pos.immutable());
   }
  }finally{MINING.remove();}
 }
 public static int preview(ServerPlayer p){var journal=get(p.serverLevel());int count=0;for(var entry:journal.blocks.entrySet()){var pos=BlockPos.of(entry.getKey());if(entry.getValue().owner.equals(p.getUUID())&&p.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(pos))<=4096){count++;if(count<=256)p.serverLevel().sendParticles(net.minecraft.core.particles.ParticleTypes.END_ROD,pos.getX()+.5,pos.getY()+.5,pos.getZ()+.5,1,0,0,0,0);}}return count;}
 public static int restore(ServerPlayer p){var level=p.serverLevel();var journal=get(level);int count=0;var it=journal.blocks.entrySet().iterator();while(it.hasNext()){var entry=it.next();var snapshot=entry.getValue();var pos=BlockPos.of(entry.getKey());if(!snapshot.owner.equals(p.getUUID())||p.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(pos))>4096||!level.hasChunkAt(pos))continue;
   var actual=level.getBlockState(pos);if(journal.placed.contains(entry.getKey())||level.getBlockEntity(pos)!=null){it.remove();continue;}
   if(level.getGameTime()-snapshot.time>24000){it.remove();continue;}
   if((actual.isAir()||actual.is(Blocks.FIRE)||actual.is(Blocks.SOUL_FIRE)||!actual.getFluidState().isEmpty())&&count<256){
    if((!snapshot.before.getCollisionShape(level,pos).isEmpty()||snapshot.before.is(Blocks.LAVA))&&!level.getEntities(null,new net.minecraft.world.phys.AABB(pos)).isEmpty())continue;
    level.setBlock(pos,snapshot.before,3);if(level.getBlockState(pos.above()).is(Blocks.FIRE)||level.getBlockState(pos.above()).is(Blocks.SOUL_FIRE))level.setBlock(pos.above(),Blocks.AIR.defaultBlockState(),3);count++;it.remove();
   }
  }
  // Remove only fire in the small neighborhoods of recorded blast sites, not arbitrary builds.
  journal.setDirty();return count;
 }
 @Override public CompoundTag save(CompoundTag root){var list=new ListTag();for(var entry:blocks.entrySet()){var t=new CompoundTag();t.putLong("pos",entry.getKey());t.putUUID("owner",entry.getValue().owner);t.putLong("time",entry.getValue().time);t.put("state",NbtUtils.writeBlockState(entry.getValue().before));list.add(t);}root.put("blocks",list);root.putLongArray("placed",placed.stream().mapToLong(Long::longValue).toArray());var wins=new ListTag();for(var id:victories)wins.add(StringTag.valueOf(id.toString()));root.put("wins",wins);return root;}
 private static DivineHistory load(CompoundTag root){var journal=new DivineHistory();for(var raw:root.getList("blocks",Tag.TAG_COMPOUND)){var t=(CompoundTag)raw;if(t.hasUUID("owner"))journal.blocks.put(t.getLong("pos"),new Snapshot(t.getUUID("owner"),NbtUtils.readBlockState(net.minecraft.core.registries.BuiltInRegistries.BLOCK.asLookup(),t.getCompound("state")),t.getLong("time")));}for(long pos:root.getLongArray("placed"))journal.placed.add(pos);for(var raw:root.getList("wins",Tag.TAG_STRING))try{journal.victories.add(UUID.fromString(raw.getAsString()));}catch(IllegalArgumentException ignored){}return journal;}
}
