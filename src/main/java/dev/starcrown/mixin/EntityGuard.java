package dev.starcrown.mixin;
import dev.starcrown.DivineProtection;
import net.minecraft.world.entity.*;
import net.minecraft.world.damagesource.DamageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
@Mixin(Entity.class)
public abstract class EntityGuard {
 @Inject(method={"setBoundingBox(Lnet/minecraft/world/phys/AABB;)V","m_20011_(Lnet/minecraft/world/phys/AABB;)V"},at=@At("HEAD"),cancellable=true,remap=false,require=1)
 private void protectBounds(net.minecraft.world.phys.AABB box,CallbackInfo callback){
  var entity=(Entity)(Object)this;
  if(DivineProtection.invalidBounds(box)&&DivineProtection.active(entity)){
   DivineProtection.prevent(entity,"Entity.setBoundingBox",0);
   callback.cancel();
  }
 }
 @Inject(method={"setPose(Lnet/minecraft/world/entity/Pose;)V","m_20124_(Lnet/minecraft/world/entity/Pose;)V"},at=@At("HEAD"),cancellable=true,remap=false,require=1)
 private void nativeDeathPose(Pose pose,CallbackInfo ci){if(pose==Pose.DYING&&DivineProtection.active((Entity)(Object)this)){DivineProtection.prevent((Entity)(Object)this,"Entity.dyingPose",0);ci.cancel();}}
 @Inject(method="canUpdate(Z)V",at=@At("HEAD"),cancellable=true,remap=false,require=1)
 private void nativeUpdateState(boolean enabled,CallbackInfo ci){if(!enabled&&DivineProtection.active((Entity)(Object)this)){DivineProtection.prevent((Entity)(Object)this,"Entity.canUpdate",0);ci.cancel();}}
 @Inject(method={"displayFireAnimation()Z","m_6051_()Z"},at=@At("HEAD"),cancellable=true,remap=false,require=1)
 private void divineVisible(CallbackInfoReturnable<Boolean> ci){if((Object)this instanceof net.minecraft.world.entity.player.Player p&&dev.starcrown.DivineWill.wearing(p))ci.setReturnValue(false);}
 @Inject(method={"teleportTo(DDD)V","m_6021_(DDD)V"},at=@At("HEAD"),cancellable=true,remap=false,require=1)
 private void sevenTeleport(double x,double y,double z,CallbackInfo ci){if(dev.starcrown.DivineSeven.ruled((Entity)(Object)this))ci.cancel();}

 @Inject(method={"makeStuckInBlock(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/phys/Vec3;)V","m_7601_(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/phys/Vec3;)V"},at=@At("HEAD"),cancellable=true,remap=false,require=1)
 private void sevenUnstuck(net.minecraft.world.level.block.state.BlockState state,net.minecraft.world.phys.Vec3 speed,CallbackInfo ci){if((Object)this instanceof net.minecraft.world.entity.player.Player p&&dev.starcrown.DivineWill.wearing(p))ci.cancel();}

 @Inject(method={"move(Lnet/minecraft/world/entity/MoverType;Lnet/minecraft/world/phys/Vec3;)V","m_6478_(Lnet/minecraft/world/entity/MoverType;Lnet/minecraft/world/phys/Vec3;)V"},at=@At("HEAD"),cancellable=true,remap=false,require=0)
 private void danceMove(MoverType type,net.minecraft.world.phys.Vec3 delta,CallbackInfo ci){if((Object)this instanceof Mob mob&&dev.starcrown.DollDance.controlled(mob))ci.cancel();}
 @Inject(method={"setRemoved(Lnet/minecraft/world/entity/Entity$RemovalReason;)V","m_142467_(Lnet/minecraft/world/entity/Entity$RemovalReason;)V"},at=@At("HEAD"),cancellable=true,remap=false,require=1)
 private void divineRemove(Entity.RemovalReason reason,CallbackInfo ci){if(DivineProtection.blockRemoval((Entity)(Object)this,reason,"Entity.remove/setRemoved"))ci.cancel();}
 @Inject(method={"remove(Lnet/minecraft/world/entity/Entity$RemovalReason;)V","m_142687_(Lnet/minecraft/world/entity/Entity$RemovalReason;)V"},at=@At("HEAD"),cancellable=true,remap=false,require=1)
 private void nativeRemove(Entity.RemovalReason reason,CallbackInfo ci){if(DivineProtection.blockRemoval((Entity)(Object)this,reason,"Entity.remove"))ci.cancel();}
}
