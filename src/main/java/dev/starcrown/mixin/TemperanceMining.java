package dev.starcrown.mixin;
import dev.starcrown.DivineHistory;
import dev.starcrown.TwilightCrown;
import dev.starcrown.YuleDiligence;
import net.minecraft.server.level.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
@Mixin(ServerPlayerGameMode.class)
public abstract class TemperanceMining {
 @Unique private ServerPlayer temperance$player;
 @Inject(method="<init>(Lnet/minecraft/server/level/ServerPlayer;)V",at=@At("RETURN"),remap=false,require=1)
 private void bindOwner(ServerPlayer owner,CallbackInfo c){temperance$player=owner;}
 @Unique private final java.util.Deque<Block> temperance$blocks=new java.util.ArrayDeque<>();
 @Inject(method={"destroyBlock(Lnet/minecraft/core/BlockPos;)Z","m_9280_(Lnet/minecraft/core/BlockPos;)Z"},at=@At("HEAD"),remap=false,require=1)
 private void start(BlockPos pos,CallbackInfoReturnable<Boolean> c){temperance$blocks.push(temperance$player.serverLevel().getBlockState(pos).getBlock());}
 @Inject(method={"destroyBlock(Lnet/minecraft/core/BlockPos;)Z","m_9280_(Lnet/minecraft/core/BlockPos;)Z"},at=@At("RETURN"),remap=false,require=1)
 private void end(BlockPos pos,CallbackInfoReturnable<Boolean> c){
  var before=temperance$blocks.pop();
  var level=temperance$player.serverLevel();
  if(!c.getReturnValue()||before==net.minecraft.world.level.block.Blocks.AIR||level.getBlockState(pos).is(before))return;
  YuleDiligence.mined(temperance$player,DivineHistory.removedNatural(level,pos,before.defaultBlockState()));
  if(TwilightCrown.wearing(temperance$player))DivineHistory.twilightMine(temperance$player,pos);
 }
}
