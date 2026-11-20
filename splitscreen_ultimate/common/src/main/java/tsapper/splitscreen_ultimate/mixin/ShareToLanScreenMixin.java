package tsapper.splitscreen_ultimate.mixin;

import net.minecraft.client.gui.screens.ShareToLanScreen;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.world.level.GameType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import tsapper.splitscreen_ultimate.client.session.LocalWorld;

@Mixin(ShareToLanScreen.class)
public abstract class ShareToLanScreenMixin {
    @Redirect(method = "*", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/server/IntegratedServer;publishServer(Lnet/minecraft/world/level/GameType;ZI)Z"))
    private boolean su$publishAdditionalListener(IntegratedServer server, GameType mode, boolean commands, int port) {
        return LocalWorld.publish(server, mode, commands, port);
    }
}
