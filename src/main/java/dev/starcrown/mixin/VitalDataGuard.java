package dev.starcrown.mixin;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
@Mixin(net.minecraft.network.syncher.SynchedEntityData.class)
public abstract class VitalDataGuard {
 @Inject(method={"define(Lnet/minecraft/network/syncher/EntityDataAccessor;Ljava/lang/Object;)V","m_135372_(Lnet/minecraft/network/syncher/EntityDataAccessor;Ljava/lang/Object;)V"},at=@At("RETURN"),remap=false,require=1)
 private void nativeBindHealth(net.minecraft.network.syncher.EntityDataAccessor<?> key,Object value,CallbackInfo ci){if(divine$owner instanceof net.minecraft.server.level.ServerPlayer&&dev.starcrown.DivineCombat.isHealthKey(key))dev.starcrown.VitalDataBinding.bind((net.minecraft.network.syncher.SynchedEntityData)(Object)this,key,divine$owner);}
 @Inject(method={"assignValue(Lnet/minecraft/network/syncher/SynchedEntityData$DataItem;Lnet/minecraft/network/syncher/SynchedEntityData$DataValue;)V","m_135375_(Lnet/minecraft/network/syncher/SynchedEntityData$DataItem;Lnet/minecraft/network/syncher/SynchedEntityData$DataValue;)V"},at=@At("HEAD"),cancellable=true,remap=false,require=1)
 private void nativeBulkHealth(net.minecraft.network.syncher.SynchedEntityData.DataItem<?> item,net.minecraft.network.syncher.SynchedEntityData.DataValue<?> value,CallbackInfo ci){if(dev.starcrown.DivineCombat.blockData(divine$owner,item.getAccessor(),value.value()))ci.cancel();}
 @ModifyVariable(method={"set(Lnet/minecraft/network/syncher/EntityDataAccessor;Ljava/lang/Object;Z)V","m_276349_(Lnet/minecraft/network/syncher/EntityDataAccessor;Ljava/lang/Object;Z)V"},at=@At("HEAD"),argsOnly=true,ordinal=0,remap=false,require=1)
 private Object humilityData(Object value,net.minecraft.network.syncher.EntityDataAccessor<?> key,Object original,boolean force){return dev.starcrown.DivineCombat.humilityData(divine$owner,key,value);}
 @Unique private net.minecraft.world.entity.Entity divine$owner;
 @Inject(method="<init>",at=@At("RETURN"),remap=false,require=1)
 private void divineOwner(net.minecraft.world.entity.Entity owner,CallbackInfo ci){divine$owner=owner;}
 @Inject(method={"set(Lnet/minecraft/network/syncher/EntityDataAccessor;Ljava/lang/Object;Z)V","m_276349_(Lnet/minecraft/network/syncher/EntityDataAccessor;Ljava/lang/Object;Z)V"},at=@At("HEAD"),cancellable=true,remap=false,require=1)
 private void divineData(net.minecraft.network.syncher.EntityDataAccessor<?> key,Object value,boolean force,CallbackInfo ci){if(dev.starcrown.DivineCombat.blockData(divine$owner,key,value))ci.cancel();}
}
