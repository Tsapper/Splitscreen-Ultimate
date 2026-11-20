package tsapper.simple_controller_support.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import tsapper.simple_controller_support.client.input.ControllerInputHandler;
import tsapper.simple_controller_support.client.input.SyntheticMouse;

@Mixin(MouseHandler.class)
public abstract class MouseHandlerMixin implements SyntheticMouse {
    /** True while a controller-generated event is being dispatched, so it doesn't count as real mouse use. */
    @Unique
    private boolean scs$synthetic;

    /** Real mouse movement smaller than this (in pixels) doesn't take input away from the controller. */
    private static final double MOVE_THRESHOLD = 2.0;

    @Shadow
    @Final
    private Minecraft minecraft;
    @Shadow
    private double xpos;
    @Shadow
    private double ypos;
    @Shadow
    private boolean ignoreFirstMove;

    // --- Report the virtual cursor as the mouse position while the controller is in use ---
    // Screens get hover, tooltips and the carried item position from these.

    @Inject(method = "xpos", at = @At("HEAD"), cancellable = true)
    private void scs$virtualX(CallbackInfoReturnable<Double> cir) {
        if (ControllerInputHandler.isControllerActive()) {
            cir.setReturnValue(ControllerInputHandler.primary().cursor().windowX());
        }
    }

    @Inject(method = "ypos", at = @At("HEAD"), cancellable = true)
    private void scs$virtualY(CallbackInfoReturnable<Double> cir) {
        if (ControllerInputHandler.isControllerActive()) {
            cir.setReturnValue(ControllerInputHandler.primary().cursor().windowY());
        }
    }

    /** In-game the controller always "owns" the camera, even if the window is unfocused and the OS mouse isn't grabbed. */
    @Inject(method = "isMouseGrabbed", at = @At("HEAD"), cancellable = true)
    private void scs$grabbedForController(CallbackInfoReturnable<Boolean> cir) {
        if (ControllerInputHandler.isControllerActive() && minecraft.screen == null) {
            cir.setReturnValue(true);
        }
    }

    // --- Switch back to keyboard/mouse when the real mouse is used ---

    @Inject(method = "onMove", at = @At("HEAD"), cancellable = true)
    private void scs$detectMouseMove(long windowPointer, double newX, double newY, CallbackInfo ci) {
        if (!ControllerInputHandler.physicalInputEnabled()) { ci.cancel(); return; }
        if (!ignoreFirstMove && minecraft.isWindowActive()
                && Math.abs(newX - xpos) + Math.abs(newY - ypos) > MOVE_THRESHOLD) {
            ControllerInputHandler.onKeyboardMouseInput();
        }
    }

    @Inject(method = "onPress", at = @At("HEAD"), cancellable = true)
    private void scs$detectMousePress(long windowPointer, int button, int action, int modifiers, CallbackInfo ci) {
        if (!scs$synthetic) {
            if (!ControllerInputHandler.physicalInputEnabled()) { ci.cancel(); return; }
            ControllerInputHandler.onKeyboardMouseInput();
        }
    }

    @Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
    private void scs$detectMouseScroll(long windowPointer, double xOffset, double yOffset, CallbackInfo ci) {
        if (!scs$synthetic) {
            if (!ControllerInputHandler.physicalInputEnabled()) { ci.cancel(); return; }
            ControllerInputHandler.onKeyboardMouseInput();
        }
    }

    // --- Controller-generated clicks/scrolls, sent through the vanilla handlers ---

    @Shadow
    private void onPress(long windowPointer, int button, int action, int modifiers) {
    }

    @Shadow
    private void onScroll(long windowPointer, double xOffset, double yOffset) {
    }

    @Override
    public void scs$sendButton(int button, int action, int modifiers) {
        scs$atVirtualCursor(() -> onPress(minecraft.getWindow().getWindow(), button, action, modifiers));
    }

    @Override
    public void scs$sendScroll(double xOffset, double yOffset) {
        scs$atVirtualCursor(() -> onScroll(minecraft.getWindow().getWindow(), xOffset, yOffset));
    }

    /** onPress/onScroll read the xpos/ypos fields directly, so point them at the virtual cursor for the call. */
    @Unique
    private void scs$atVirtualCursor(Runnable action) {
        double realX = xpos;
        double realY = ypos;
        var cursor = ControllerInputHandler.primary().cursor();
        xpos = cursor.windowX();
        ypos = cursor.windowY();
        scs$synthetic = true;
        try {
            action.run();
        } finally {
            scs$synthetic = false;
            xpos = realX;
            ypos = realY;
        }
    }
}
