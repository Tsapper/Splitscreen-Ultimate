package tsapper.splitscreen_ultimate.mixin;

import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.server.IntegratedServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import tsapper.splitscreen_ultimate.client.session.LocalWorld;

@Mixin(PauseScreen.class)
public abstract class PauseScreenMixin {
    @Redirect(method = "createPauseMenu", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/server/IntegratedServer;isPublished()Z"))
    private boolean su$canOpenLan(IntegratedServer server) {
        return server == LocalWorld.host ? LocalWorld.publicLan : server.isPublished();
    }
}
