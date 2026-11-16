package dev.starcrown.mixin;
import dev.starcrown.YuleHumility;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
@Mixin(PlayerRenderer.class)
public abstract class HumilityPlayerRenderer {
 @Inject(method={"setModelProperties(Lnet/minecraft/client/player/AbstractClientPlayer;)V","m_117818_(Lnet/minecraft/client/player/AbstractClientPlayer;)V"},at=@At("TAIL"),remap=false,require=1)
 private void divineSkin(AbstractClientPlayer player,CallbackInfo ci){if(dev.starcrown.DivineWill.wearing(player)&&!dev.starcrown.DollCameraVisibility.isFirstPersonBodyPass(player))((PlayerRenderer)(Object)this).getModel().setAllVisible(true);}
}
