package tsapper.simple_controller_support.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import tsapper.simple_controller_support.client.gui.ControlsScreenRedirect;
import tsapper.simple_controller_support.client.input.ControllerInputHandler;

@Mixin(Minecraft.class)
public abstract class MinecraftMixin {
    /** Poll the controller once per frame, before this frame's ticks (key handling) and rendering. */
    @Inject(method = "runTick", at = @At("HEAD"))
    private void scs$pollController(boolean renderLevel, CallbackInfo ci) {
        ControllerInputHandler.onFrame();
    }

    /** Swap the vanilla Controls screen for the controller controls screen. */
    @ModifyVariable(method = "setScreen", at = @At("HEAD"), argsOnly = true)
    private Screen scs$replaceControlsScreen(Screen screen) {
        return ControlsScreenRedirect.redirect(screen);
    }
}
