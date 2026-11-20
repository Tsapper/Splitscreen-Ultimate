package tsapper.splitscreen_ultimate.mixin;

import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import tsapper.splitscreen_ultimate.client.session.*;

/** Higher priority ensures assignment/input gates are installed before SCS's first input poll. */
@Mixin(value = Minecraft.class, priority = 1100)
public abstract class MinecraftSessionMixin {
    @Inject(method = "runTick", at = @At("HEAD"))
    private void su$bootstrap(boolean renderLevel, CallbackInfo ci) {
        if (LaunchConfig.enabled() && SessionClient.INSTANCE == null) new SessionClient(LaunchConfig.read());
    }
    @Inject(method = "disconnect(Lnet/minecraft/client/gui/screens/Screen;Z)V", at = @At("HEAD"))
    private void su$leavingWorld(net.minecraft.client.gui.screens.Screen next, boolean keepResourcePacks, CallbackInfo ci) {
        if (SessionClient.INSTANCE != null) SessionClient.INSTANCE.beforeDisconnect();
    }
}
