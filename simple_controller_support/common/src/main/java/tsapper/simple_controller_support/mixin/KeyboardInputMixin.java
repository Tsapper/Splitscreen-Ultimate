package tsapper.simple_controller_support.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.Input;
import net.minecraft.client.player.KeyboardInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import tsapper.simple_controller_support.client.input.ControllerInputHandler;

@Mixin(KeyboardInput.class)
public abstract class KeyboardInputMixin {
    /** Left-stick movement replaces the WASD result when the stick is pushed. */
    @Inject(method = "tick", at = @At("TAIL"))
    private void scs$applyStickMovement(boolean isSneaking, float sneakingSpeedMultiplier, CallbackInfo ci) {
        if (ControllerInputHandler.isControllerActive() && Minecraft.getInstance().screen == null) {
            ControllerInputHandler.primary().gameplayInput()
                    .applyMovement((Input) (Object) this, isSneaking, sneakingSpeedMultiplier);
        }
    }
}
