package dev.starcrown.mixin;

import dev.starcrown.YuleCharity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ItemStack.class)
public abstract class CharityStackMigration {
 @Inject(method={"of(Lnet/minecraft/nbt/CompoundTag;)Lnet/minecraft/world/item/ItemStack;","m_41712_(Lnet/minecraft/nbt/CompoundTag;)Lnet/minecraft/world/item/ItemStack;"},at=@At("RETURN"),remap=false,require=1)
 private static void clearLegacyDeathMark(CompoundTag tag,CallbackInfoReturnable<ItemStack> callback){
  YuleCharity.clearLegacyMark(callback.getReturnValue());
 }
}
