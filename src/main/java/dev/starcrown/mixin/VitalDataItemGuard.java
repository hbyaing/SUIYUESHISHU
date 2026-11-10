package dev.starcrown.mixin;

import dev.starcrown.*;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(net.minecraft.network.syncher.SynchedEntityData.DataItem.class)
public abstract class VitalDataItemGuard implements OwnedVitalData {
 @Unique private java.lang.ref.WeakReference<Entity> starcrown$owner;
 @Unique public void starcrown$bindVitalOwner(Entity owner){starcrown$owner=new java.lang.ref.WeakReference<>(owner);}
 @Inject(method={"setValue(Ljava/lang/Object;)V","m_135397_(Ljava/lang/Object;)V"},at=@At("HEAD"),cancellable=true,remap=false,require=1)
 private void nativeHealthItem(Object value,CallbackInfo ci){
  if(starcrown$owner==null)return;var owner=starcrown$owner.get();
  if(owner!=null&&DivineCombat.blockData(owner,((net.minecraft.network.syncher.SynchedEntityData.DataItem<?>)(Object)this).getAccessor(),value))ci.cancel();
 }
}
