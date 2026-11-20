package tsapper.simple_controller_support.mixin;

import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import tsapper.simple_controller_support.client.input.ControllerInputHandler;
import tsapper.simple_controller_support.client.input.SyntheticKeyboard;

@Mixin(KeyboardHandler.class)
public abstract class KeyboardHandlerMixin implements SyntheticKeyboard {
    @Shadow
    @Final
    private Minecraft minecraft;

    /** True while a controller-generated key is being dispatched, so it doesn't count as real keyboard use. */
    @Unique
    private boolean scs$synthetic;

    @Shadow
    public abstract void keyPress(long windowPointer, int key, int scanCode, int action, int modifiers);

    @Shadow
    private void charTyped(long windowPointer, int codePoint, int modifiers) {
    }

    /** Any real key press switches back to keyboard/mouse mode. */
    @Inject(method = "keyPress", at = @At("HEAD"), cancellable = true)
    private void scs$detectKeyPress(long windowPointer, int key, int scanCode, int action, int modifiers, CallbackInfo ci) {
        if (!scs$synthetic) {
            if (!ControllerInputHandler.physicalInputEnabled()) { ci.cancel(); return; }
            ControllerInputHandler.onKeyboardMouseInput();
        }
    }

    @Inject(method = "charTyped", at = @At("HEAD"), cancellable = true)
    private void scs$gateTyping(long window, int codePoint, int modifiers, CallbackInfo ci) {
        if (!scs$synthetic && !ControllerInputHandler.physicalInputEnabled()) ci.cancel();
    }

    @Override
    public void scs$sendKey(int key, int scanCode, int action, int modifiers) {
        scs$synthetic = true;
        try {
            keyPress(minecraft.getWindow().getWindow(), key, scanCode, action, modifiers);
        } finally {
            scs$synthetic = false;
        }
    }

    @Override
    public void scs$sendChar(int codePoint, int modifiers) {
        scs$synthetic = true;
        try {
            charTyped(minecraft.getWindow().getWindow(), codePoint, modifiers);
        } finally {
            scs$synthetic = false;
        }
    }
}
