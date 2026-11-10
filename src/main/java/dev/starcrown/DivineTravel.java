package dev.starcrown;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.*;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.resources.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.*;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
@Mod.EventBusSubscriber(modid=StarcrownDoll.ID)
public final class DivineTravel {
 public static boolean safe(LivingEntity entity,ServerLevel level,Vec3 pos){var block=BlockPos.containing(pos);if(pos.y<level.getMinBuildHeight()+2||pos.y>level.getMaxBuildHeight()-3||!level.hasChunkAt(block)||!level.getWorldBorder().isWithinBounds(block))return false;
  var box=entity.getBoundingBox().move(pos.subtract(entity.position()));if(!level.noCollision(entity,box))return false;
  for(var b:BlockPos.betweenClosed(BlockPos.containing(box.minX,box.minY,box.minZ),BlockPos.containing(box.maxX,box.maxY,box.maxZ)))if(level.getFluidState(b).is(Fluids.LAVA)||level.getBlockState(b).is(Blocks.FIRE)||level.getBlockState(b).is(Blocks.SOUL_FIRE))return false;
  for(int i=1;i<=32;i++)if(!level.getBlockState(block.below(i)).getCollisionShape(level,block.below(i)).isEmpty()&&!level.getFluidState(block.below(i)).is(Fluids.LAVA))return true;
  return false;
 }
 private static CompoundTag location(ServerPlayer p){var tag=new CompoundTag();tag.putString("dimension",p.level().dimension().location().toString());tag.putDouble("x",p.getX());tag.putDouble("y",p.getY());tag.putDouble("z",p.getZ());tag.putFloat("yaw",p.getYRot());tag.putFloat("pitch",p.getXRot());return tag;}
 private static boolean go(ServerPlayer p,ServerLevel level,Vec3 pos,float yaw,float pitch,CompoundTag history){
  if(!safe(p,level,pos))return false;
  var previous=history==null?null:location(p);
  p.teleportTo(level,pos.x,pos.y,pos.z,yaw,pitch);
  if(p.level()!=level||p.position().distanceToSqr(pos)>1e-6)return false;
  p.setDeltaMovement(Vec3.ZERO);p.fallDistance=0;
  if(history!=null){history.put("previous",previous);history.putLong("travel_until",p.getServer().overworld().getGameTime()+40);history.put("safe",location(p));}
  return true;
 }
 private static boolean stored(ServerPlayer p,CompoundTag tag){return stored(p,tag,DivineSeven.settings(p));}
 static boolean stored(ServerPlayer p,CompoundTag tag,CompoundTag history){if(!tag.contains("dimension"))return false;var id=ResourceLocation.tryParse(tag.getString("dimension"));if(id==null)return false;var level=p.getServer().getLevel(ResourceKey.create(Registries.DIMENSION,id));if(level==null)return false;var base=new Vec3(tag.getDouble("x"),tag.getDouble("y"),tag.getDouble("z"));if(!level.getWorldBorder().isWithinBounds(BlockPos.containing(base)))return false;level.getChunk(BlockPos.containing(base));for(int radius=0;radius<=4;radius++)for(int y=0;y<=6;y++)for(int dx=-radius;dx<=radius;dx++)for(int dz=-radius;dz<=radius;dz++){if(radius>0&&Math.max(Math.abs(dx),Math.abs(dz))!=radius)continue;var pos=base.add(dx,y,dz);if(safe(p,level,pos))return go(p,level,pos,tag.getFloat("yaw"),tag.getFloat("pitch"),history);}return false;}
 public static boolean blink(ServerPlayer p){return blink(p,256,DivineSeven.settings(p));}
 static boolean blink(ServerPlayer p,int range,CompoundTag history){var start=p.getEyePosition();var look=p.getLookAngle();var end=start.add(look.scale(range));var hit=p.level().clip(new ClipContext(start,end,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,p));double distance=hit.getType()==HitResult.Type.MISS?range:Math.max(0,start.distanceTo(hit.getLocation())-1);for(double d=distance;d>=2;d-=2){var pos=start.add(look.scale(d)).subtract(0,p.getEyeHeight(),0);if(safe(p,p.serverLevel(),pos))return go(p,p.serverLevel(),pos,p.getYRot(),p.getXRot(),history);}return false;}
 public static boolean save(ServerPlayer p,int slot){return save(p,slot,DivineSeven.settings(p));}
 static boolean save(ServerPlayer p,int slot,CompoundTag data){if(!safe(p,p.serverLevel(),p.position()))return false;var anchors=data.getCompound("anchors");anchors.put("slot"+slot,location(p));data.put("anchors",anchors);return true;}
 public static boolean anchor(ServerPlayer p,int slot){return stored(p,DivineSeven.settings(p).getCompound("anchors").getCompound("slot"+slot));}
 public static boolean back(ServerPlayer p,boolean death){return stored(p,DivineSeven.settings(p).getCompound(death?"death":"previous").copy());}
 public static void tick(ServerPlayer p){var data=DivineSeven.settings(p);long now=p.getServer().overworld().getGameTime();var last=data.getCompound("last_position");boolean displaced=last.contains("dimension")&&last.getString("dimension").equals(p.level().dimension().location().toString())&&p.position().distanceToSqr(new Vec3(last.getDouble("x"),last.getDouble("y"),last.getDouble("z")))>128*128;
  if((p.getY()<p.level().getMinBuildHeight()+4||displaced)&&data.getLong("travel_until")<now)stored(p,data.getCompound("safe").copy());
  if(p.tickCount%10==0&&safe(p,p.serverLevel(),p.position()))data.put("safe",location(p));data.put("last_position",location(p));
 }
 @SubscribeEvent public static void death(LivingDeathEvent e){if(e.getEntity() instanceof ServerPlayer p)DivineSeven.settings(p).put("death",location(p));}
 @SubscribeEvent public static void clone(net.minecraftforge.event.entity.player.PlayerEvent.Clone e){if(e.getOriginal().getPersistentData().contains("starcrown_confiscated_dolls"))e.getEntity().getPersistentData().put("starcrown_confiscated_dolls",e.getOriginal().getPersistentData().getList("starcrown_confiscated_dolls",Tag.TAG_COMPOUND).copy());if(e.getOriginal().getPersistentData().contains(DivineSeven.ROOT))e.getEntity().getPersistentData().put(DivineSeven.ROOT,e.getOriginal().getPersistentData().getCompound(DivineSeven.ROOT).copy());}
}
