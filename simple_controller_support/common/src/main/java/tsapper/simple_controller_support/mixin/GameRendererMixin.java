package tsapper.simple_controller_support.mixin;

import net.minecraft.Util;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import tsapper.simple_controller_support.client.input.ControllerInputHandler;

@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
    @Shadow
    private long lastActiveTime;

    /**
     * Vanilla opens the pause menu 500ms after the window loses focus ("pause on lost focus").
     * While the controller is in use, keep marking the game as active so it keeps running in the background.
     */
    @Inject(method = "render", at = @At("HEAD"))
    private void scs$keepActiveForController(DeltaTracker deltaTracker, boolean renderLevel, CallbackInfo ci) {
        if (ControllerInputHandler.isControllerActive()) {
            lastActiveTime = Util.getMillis();
        }
    }
}
