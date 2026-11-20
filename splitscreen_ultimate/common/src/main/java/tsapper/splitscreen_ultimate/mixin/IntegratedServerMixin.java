package tsapper.splitscreen_ultimate.mixin;

import java.net.InetAddress;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.client.server.LanServerPinger;
import net.minecraft.server.network.ServerConnectionListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import tsapper.splitscreen_ultimate.client.session.LocalWorld;

@Mixin(IntegratedServer.class)
public abstract class IntegratedServerMixin {
    @Redirect(method = "publishServer", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/network/ServerConnectionListener;startTcpServerListener(Ljava/net/InetAddress;I)V"))
    private void su$privateBind(ServerConnectionListener listener, InetAddress address, int port) throws java.io.IOException {
        listener.startTcpServerListener(LocalWorld.openingPrivate ? InetAddress.getLoopbackAddress() : address, port);
    }
    @Redirect(method = "publishServer", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/server/LanServerPinger;start()V"))
    private void su$privateAdvertisement(LanServerPinger pinger) {
        if (!LocalWorld.openingPrivate) pinger.start();
    }
}
