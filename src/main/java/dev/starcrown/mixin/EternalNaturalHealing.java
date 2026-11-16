package dev.starcrown.mixin;

import dev.starcrown.EternalReturn;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;

@Mixin(FoodData.class)
public abstract class EternalNaturalHealing {
    @Redirect(method={"tick(Lnet/minecraft/world/entity/player/Player;)V","m_38710_(Lnet/minecraft/world/entity/player/Player;)V"},
            at=@At(value="INVOKE",target="Lnet/minecraft/world/entity/player/Player;heal(F)V"),remap=false,require=0)
    private void naturalHeal(Player player,float amount) {if(!EternalReturn.noNaturalHealing(player))player.heal(amount);}
    @Redirect(method={"tick(Lnet/minecraft/world/entity/player/Player;)V","m_38710_(Lnet/minecraft/world/entity/player/Player;)V"},
            at=@At(value="INVOKE",target="Lnet/minecraft/world/entity/player/Player;m_5634_(F)V"),remap=false,require=0)
    private void naturalHealSrg(Player player,float amount) {if(!EternalReturn.noNaturalHealing(player))player.heal(amount);}
}
