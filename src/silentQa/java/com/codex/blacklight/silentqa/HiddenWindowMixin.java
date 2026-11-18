package com.codex.blacklight.silentqa;

import com.mojang.blaze3d.platform.Window;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.util.function.*;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Test-only window, never shown or focused. NeoForge earlyWindowControl must be false. */
@Mixin(Window.class)
public abstract class HiddenWindowMixin {
    @WrapOperation(method="<init>",at=@At(value="INVOKE",target="Lnet/neoforged/fml/loading/ImmediateWindowHandler;setupMinecraftWindow(Ljava/util/function/IntSupplier;Ljava/util/function/IntSupplier;Ljava/util/function/Supplier;Ljava/util/function/LongSupplier;)J",remap=false))
    private long blacklight$hidden(IntSupplier width,IntSupplier height,Supplier<String> title,LongSupplier monitor,Operation<Long> original){
        GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE,GLFW.GLFW_FALSE);
        GLFW.glfwWindowHint(GLFW.GLFW_FOCUSED,GLFW.GLFW_FALSE);
        GLFW.glfwWindowHint(GLFW.GLFW_FOCUS_ON_SHOW,GLFW.GLFW_FALSE);
        long window=original.call(width,height,title,monitor);
        if(GLFW.glfwGetWindowAttrib(window,GLFW.GLFW_VISIBLE)!=GLFW.GLFW_FALSE)throw new IllegalStateException("QA window must remain hidden");
        return window;
    }
}
