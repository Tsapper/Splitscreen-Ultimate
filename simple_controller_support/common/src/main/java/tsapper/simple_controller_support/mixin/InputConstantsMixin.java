package tsapper.simple_controller_support.mixin;

import com.mojang.blaze3d.platform.InputConstants;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import tsapper.simple_controller_support.client.input.ControllerInputHandler;

@Mixin(InputConstants.class)
public abstract class InputConstantsMixin {
    /**
     * Report shift as held during the controller's quick-move action. Inventories read the shift keys
     * directly through this method (and {@code Screen.hasShiftDown()} does too).
     */
    @Inject(method = "isKeyDown", at = @At("HEAD"), cancellable = true)
    private static void scs$virtualShift(long window, int key, CallbackInfoReturnable<Boolean> cir) {
        if ((key == GLFW.GLFW_KEY_LEFT_SHIFT || key == GLFW.GLFW_KEY_RIGHT_SHIFT)
                && ControllerInputHandler.isControllerActive()
                && ControllerInputHandler.primary().screenInput().isShiftHeld()) {
            cir.setReturnValue(true);
        } else if (!ControllerInputHandler.physicalInputEnabled()) {
            cir.setReturnValue(false);
        }
    }
}
