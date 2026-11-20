package tsapper.splitscreen_ultimate.mixin;

import com.mojang.blaze3d.platform.Window;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import tsapper.splitscreen_ultimate.client.session.LaunchConfig;

@Mixin(Window.class)
public abstract class WindowMixin {
    @Shadow @Final private long window;
    // NeoForge replaces this invocation with its early-window provider. The TAIL hook below is
    // required on both loaders; this optional hook additionally prevents any vanilla window flash.
    @Inject(method = "<init>", at = @At(value = "INVOKE", target = "Lorg/lwjgl/glfw/GLFW;glfwCreateWindow(IILjava/lang/CharSequence;JJ)J"), require = 0)
    private void su$hiddenGuest(CallbackInfo ci) {
        if (LaunchConfig.guest()) GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE, GLFW.GLFW_FALSE);
    }
    @Inject(method = "<init>", at = @At("TAIL"))
    private void su$hideReusedWindow(CallbackInfo ci) {
        if (LaunchConfig.guest()) GLFW.glfwHideWindow(window);
    }
}
