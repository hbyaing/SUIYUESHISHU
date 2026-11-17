package dev.starcrownqa.mixin;

import com.mojang.blaze3d.platform.Window;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;

/** QA-only: no visible or focused window is ever requested. */
@Mixin(Window.class)
public abstract class FateHiddenWindow {
    @Redirect(method="<init>",at=@At(value="INVOKE",target="Lnet/minecraftforge/fml/loading/ImmediateWindowHandler;setupMinecraftWindow(Ljava/util/function/IntSupplier;Ljava/util/function/IntSupplier;Ljava/util/function/Supplier;Ljava/util/function/LongSupplier;)J",remap=false),require=1)
    private long hidden(java.util.function.IntSupplier width,java.util.function.IntSupplier height,java.util.function.Supplier<String> title,java.util.function.LongSupplier monitor){
        org.lwjgl.glfw.GLFW.glfwWindowHint(org.lwjgl.glfw.GLFW.GLFW_VISIBLE,org.lwjgl.glfw.GLFW.GLFW_FALSE);
        org.lwjgl.glfw.GLFW.glfwWindowHint(org.lwjgl.glfw.GLFW.GLFW_FOCUSED,org.lwjgl.glfw.GLFW.GLFW_FALSE);
        org.lwjgl.glfw.GLFW.glfwWindowHint(org.lwjgl.glfw.GLFW.GLFW_FOCUS_ON_SHOW,org.lwjgl.glfw.GLFW.GLFW_FALSE);
        return net.minecraftforge.fml.loading.ImmediateWindowHandler.setupMinecraftWindow(width,height,title,monitor);
    }
}
